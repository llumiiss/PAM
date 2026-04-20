package pl.pam.startproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.pam.startproject.data.MeasureType
import pl.pam.startproject.data.MeasurementAttemptEntity
import pl.pam.startproject.data.PamDatabase
import pl.pam.startproject.sync.MeasurementSyncRepository
import pl.pam.startproject.ui.history.HistoryScreen
import pl.pam.startproject.ui.theme.StartProjectTheme
import java.util.Locale
import java.util.UUID
import kotlin.math.max

private val QUARTER_MILE_METERS = (1609.344 / 4.0).toFloat()

/** Minimalny dystans od punktu uzbrojenia, by uznać „ruszenie” (redukcja fałszywych startów przy dryfie GPS). */
private const val MOTION_MIN_DISPLACEMENT_M = 4f

/** Minimalna prędkość z GPS (m/s), gdy [Location.hasSpeed] — ok. 3,2 km/h. */
private const val MOTION_MIN_SPEED_MS = 0.9f

private data class DistanceOption(val meters: Float, val label: String)

private val distanceOptions = listOf(
    DistanceOption(100f, "100 m"),
    DistanceOption(500f, "500 m"),
    DistanceOption(1000f, "1 km"),
    DistanceOption(QUARTER_MILE_METERS, "1/4 mili"),
)

private val speedTargetOptions = listOf(50f, 75f, 100f, 120f)

private enum class StartStrategy {
    /** Po Start: 5→1 (co 1 s), potem od razu pomiar (GPS + stoper). */
    CountdownThenMeasure,

    /** Po Start: uzbrojenie; pomiar dopiero po pierwszym ruchu wg GPS. */
    ArmedWaitForMotion,
}

private enum class RunPhase {
    Idle,
    Countdown,
    Armed,
    Running,
}

/** Aktualny preset pomiaru: albo próg dystansu, albo próg prędkości (0→X). */
private sealed class MeasurePreset {
    data class Distance(val meters: Float, val label: String) : MeasurePreset()
    data class SpeedAccel(val targetKmh: Float) : MeasurePreset()
}

private enum class AppScreen { Measure, History }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val syncRepository = MeasurementSyncRepository.get(this)
        syncRepository.enqueueSyncNow()
        setContent {
            val database = remember { PamDatabase.get(this) }
            val attemptsFlow = remember { database.measurementDao().observeAllByDateDesc() }
            var appScreen by remember { mutableStateOf(AppScreen.Measure) }
            StartProjectTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (appScreen) {
                        AppScreen.Measure -> DragMeasureScreen(
                            modifier = Modifier.padding(innerPadding),
                            onOpenHistory = { appScreen = AppScreen.History }
                        )
                        AppScreen.History -> HistoryScreen(
                            modifier = Modifier.padding(innerPadding),
                            attemptsFlow = attemptsFlow,
                            onBackToMeasure = { appScreen = AppScreen.Measure }
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DragMeasureScreen(
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncRepository = remember { MeasurementSyncRepository.get(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var runPhase by remember { mutableStateOf(RunPhase.Idle) }
    var startStrategy by remember { mutableStateOf(StartStrategy.CountdownThenMeasure) }
    var countdownTick by remember { mutableIntStateOf(0) }

    var elapsedTimeMs by remember { mutableLongStateOf(0L) }
    var distanceMeters by remember { mutableStateOf(0f) }
    var speedKmh by remember { mutableStateOf(0f) }
    var maxSpeedKmhPeak by remember { mutableStateOf(0f) }
    var previousLocation by remember { mutableStateOf<Location?>(null) }
    var armedAnchor by remember { mutableStateOf<Location?>(null) }

    var preset by remember {
        mutableStateOf<MeasurePreset>(
            MeasurePreset.Distance(1000f, "1 km")
        )
    }
    val presetState = rememberUpdatedState(preset)
    val runPhaseState = rememberUpdatedState(runPhase)
    val runPhaseForTimer = rememberUpdatedState(runPhase)
    val handleSessionEnd = rememberUpdatedState { shouldSave: Boolean ->
        val wasRunning = runPhase == RunPhase.Running
        val snapElapsed = elapsedTimeMs
        val snapDist = distanceMeters
        val snapMax = max(maxSpeedKmhPeak, speedKmh)
        val snapPreset = preset
        val snapStrategy = startStrategy

        runPhase = RunPhase.Idle
        armedAnchor = null
        countdownTick = 0

        val save = shouldSave && wasRunning && (snapElapsed > 0L || snapDist > 0f)
        if (save) {
            scope.launch(Dispatchers.IO) {
                val (measureType, modeLabel) = when (snapPreset) {
                    is MeasurePreset.Distance -> MeasureType.DISTANCE to snapPreset.label
                    is MeasurePreset.SpeedAccel ->
                        MeasureType.SPEED_ACCEL to "0–${snapPreset.targetKmh.toInt()} km/h"
                }
                val strategyStr = when (snapStrategy) {
                    StartStrategy.CountdownThenMeasure -> "countdown_then_measure"
                    StartStrategy.ArmedWaitForMotion -> "armed_wait_for_motion"
                }
                val row = MeasurementAttemptEntity(
                    clientRecordId = UUID.randomUUID().toString(),
                    measuredAtEpochMs = System.currentTimeMillis(),
                    measureType = measureType,
                    modeLabel = modeLabel,
                    startStrategy = strategyStr,
                    maxSpeedKmh = snapMax.toDouble(),
                    durationMs = snapElapsed,
                    distanceM = snapDist.toDouble(),
                )
                syncRepository.insertPendingAndSync(row)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    fun resetMeasurementBaselines() {
        elapsedTimeMs = 0L
        distanceMeters = 0f
        speedKmh = 0f
        maxSpeedKmhPeak = 0f
        previousLocation = null
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.lastLocation ?: return
                val phase = runPhaseState.value

                if (phase == RunPhase.Armed) {
                    speedKmh = latest.speed * 3.6f
                    val anchor = armedAnchor
                    if (anchor == null) {
                        armedAnchor = latest
                    } else {
                        val movedFar = anchor.distanceTo(latest) >= MOTION_MIN_DISPLACEMENT_M
                        val fastEnough = latest.hasSpeed() && latest.speed >= MOTION_MIN_SPEED_MS
                        if (movedFar || fastEnough) {
                            runPhase = RunPhase.Running
                            resetMeasurementBaselines()
                            speedKmh = latest.speed * 3.6f
                            previousLocation = latest
                        }
                    }
                    return
                }

                if (phase != RunPhase.Running) return

                speedKmh = latest.speed * 3.6f
                maxSpeedKmhPeak = max(maxSpeedKmhPeak, speedKmh)
                var newDistance = distanceMeters
                previousLocation?.let { previous ->
                    newDistance += previous.distanceTo(latest)
                }
                distanceMeters = newDistance
                previousLocation = latest
                when (val p = presetState.value) {
                    is MeasurePreset.Distance -> {
                        if (newDistance >= p.meters) {
                            handleSessionEnd.value.invoke(true)
                        }
                    }
                    is MeasurePreset.SpeedAccel -> {
                        if (speedKmh >= p.targetKmh) {
                            handleSessionEnd.value.invoke(true)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(runPhase) {
        if (runPhase != RunPhase.Countdown) return@LaunchedEffect
        for (i in 5 downTo 1) {
            countdownTick = i
            delay(1000L)
            if (runPhaseState.value != RunPhase.Countdown) return@LaunchedEffect
        }
        countdownTick = 0
        if (runPhaseState.value == RunPhase.Countdown) {
            resetMeasurementBaselines()
            runPhase = RunPhase.Running
        }
    }

    LaunchedEffect(runPhase) {
        while (runPhaseForTimer.value == RunPhase.Running) {
            delay(100L)
            elapsedTimeMs += 100L
        }
    }

    val needsGps = runPhase == RunPhase.Armed || runPhase == RunPhase.Running
    DisposableEffect(needsGps, hasLocationPermission) {
        if (!hasLocationPermission || !needsGps) {
            onDispose { }
        } else {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            ).setMinUpdateIntervalMillis(500L).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
            onDispose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    val sessionActive = runPhase != RunPhase.Idle
    val idle = runPhase == RunPhase.Idle

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "PAM Drag Measure",
                style = MaterialTheme.typography.headlineSmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onOpenHistory) { Text("Historia") }
            }
        }

        if (!hasLocationPermission) {
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }) {
                Text("Nadaj uprawnienia lokalizacji")
            }
        }

        StartStrategyRow(
            strategy = startStrategy,
            onStrategyChange = { startStrategy = it },
            enabled = idle
        )

        MeasurePresetSelector(
            preset = preset,
            onPresetChange = { preset = it },
            enabled = idle
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = measurePresetSummary(preset))
                Text(text = autoStopGoalDescription(preset))
                when (runPhase) {
                    RunPhase.Countdown ->
                        Text(
                            text = "Start za: $countdownTick",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    RunPhase.Armed ->
                        Text(
                            text = "Uzbrojono — czekam na pierwszy ruch (GPS)…",
                            style = MaterialTheme.typography.titleMedium
                        )
                    else -> { }
                }
                Text("Czas: ${formatElapsedTime(elapsedTimeMs)}")
                Text("Prędkość: ${"%.1f".format(Locale.US, speedKmh)} km/h")
                Text("Dystans: ${"%.1f".format(Locale.US, distanceMeters)} m")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = hasLocationPermission && idle,
                onClick = {
                    when (startStrategy) {
                        StartStrategy.CountdownThenMeasure -> {
                            resetMeasurementBaselines()
                            runPhase = RunPhase.Countdown
                            countdownTick = 5
                        }
                        StartStrategy.ArmedWaitForMotion -> {
                            armedAnchor = null
                            resetMeasurementBaselines()
                            runPhase = RunPhase.Armed
                        }
                    }
                }
            ) { Text("Start") }

            Button(
                enabled = sessionActive,
                onClick = {
                    val save = runPhase == RunPhase.Running
                    handleSessionEnd.value.invoke(save)
                }
            ) { Text("Stop") }
        }
    }
}

@Composable
private fun StartStrategyRow(
    strategy: StartStrategy,
    onStrategyChange: (StartStrategy) -> Unit,
    enabled: Boolean
) {
    val countdownSelected = strategy == StartStrategy.CountdownThenMeasure
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Strategia startu", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (countdownSelected) {
                    "5→1, potem od razu pomiar (GPS + stoper)"
                } else {
                    "Uzbrojenie — pomiar od pierwszego ruchu GPS"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (countdownSelected) "5→1" else "Ruch",
                style = MaterialTheme.typography.labelMedium
            )
            Switch(
                checked = countdownSelected,
                onCheckedChange = { checked ->
                    onStrategyChange(
                        if (checked) StartStrategy.CountdownThenMeasure
                        else StartStrategy.ArmedWaitForMotion
                    )
                },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun MeasurePresetSelector(
    preset: MeasurePreset,
    onPresetChange: (MeasurePreset) -> Unit,
    enabled: Boolean
) {
    val isDistance = preset is MeasurePreset.Distance
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rodzaj pomiaru", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onPresetChange(MeasurePreset.Distance(1000f, "1 km"))
                },
                modifier = Modifier.weight(1f),
                enabled = enabled && !isDistance
            ) {
                Text("Dystans")
            }
            Button(
                onClick = {
                    onPresetChange(MeasurePreset.SpeedAccel(100f))
                },
                modifier = Modifier.weight(1f),
                enabled = enabled && isDistance
            ) {
                Text("Przyspieszenie")
            }
        }

        when (val p = preset) {
            is MeasurePreset.Distance -> {
                Text("Wybór dystansu", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        distanceOptions.take(2).forEach { opt ->
                            DistanceOptionButton(
                                option = opt,
                                current = p,
                                enabled = enabled,
                                onSelect = onPresetChange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        distanceOptions.drop(2).forEach { opt ->
                            DistanceOptionButton(
                                option = opt,
                                current = p,
                                enabled = enabled,
                                onSelect = onPresetChange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            is MeasurePreset.SpeedAccel -> {
                Text("Docelowa prędkość (0 → X)", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speedTargetOptions.take(2).forEach { kmh ->
                            SpeedTargetButton(
                                targetKmh = kmh,
                                current = p,
                                enabled = enabled,
                                onSelect = onPresetChange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speedTargetOptions.drop(2).forEach { kmh ->
                            SpeedTargetButton(
                                targetKmh = kmh,
                                current = p,
                                enabled = enabled,
                                onSelect = onPresetChange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistanceOptionButton(
    option: DistanceOption,
    current: MeasurePreset.Distance,
    enabled: Boolean,
    onSelect: (MeasurePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = current.label == option.label
    Button(
        onClick = { onSelect(MeasurePreset.Distance(option.meters, option.label)) },
        enabled = enabled && !selected,
        modifier = modifier
    ) {
        Text(option.label)
    }
}

@Composable
private fun SpeedTargetButton(
    targetKmh: Float,
    current: MeasurePreset.SpeedAccel,
    enabled: Boolean,
    onSelect: (MeasurePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = current.targetKmh == targetKmh
    Button(
        onClick = { onSelect(MeasurePreset.SpeedAccel(targetKmh)) },
        enabled = enabled && !selected,
        modifier = modifier
    ) {
        Text("${targetKmh.toInt()} km/h")
    }
}

private fun measurePresetSummary(preset: MeasurePreset): String = when (preset) {
    is MeasurePreset.Distance -> "Tryb: dystans — ${preset.label}"
    is MeasurePreset.SpeedAccel -> "Tryb: przyspieszenie — 0–${preset.targetKmh.toInt()} km/h"
}

private fun autoStopGoalDescription(preset: MeasurePreset): String = when (preset) {
    is MeasurePreset.Distance ->
        "Cel: ${preset.label} (${"%.1f".format(Locale.US, preset.meters)} m), auto-stop po dystansie"
    is MeasurePreset.SpeedAccel ->
        "Cel: ≥ ${preset.targetKmh.toInt()} km/h (auto-stop po prędkości)"
}

private fun formatElapsedTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(Locale.US, minutes, seconds, milliseconds)
}

@Preview(showBackground = true)
@Composable
private fun DragMeasurePreview() {
    StartProjectTheme {
        DragMeasureScreen(onOpenHistory = {})
    }
}
