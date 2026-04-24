package pl.pam.startproject.ui.measure

/**
 * Główny ekran pomiaru "drag":
 * - obsługa sesji pomiarowej i faz (Idle/Countdown/Armed/Running),
 * - odbiór GPS i obliczenia prędkości/dystansu,
 * - auto-stop po osiągnięciu celu,
 * - zapis wyniku do lokalnej bazy i sync.
 */
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.pam.startproject.auth.SessionUser
import pl.pam.startproject.data.MeasureType
import pl.pam.startproject.data.MeasurementAttemptEntity
import pl.pam.startproject.data.SpeedProfileCodec
import pl.pam.startproject.gps.GpsSpeedEstimator
import pl.pam.startproject.sync.MeasurementSyncRepository
import pl.pam.startproject.ui.theme.AppColors
import pl.pam.startproject.ui.theme.AppDimens
import pl.pam.startproject.ui.theme.AppText
import pl.pam.startproject.ui.theme.StartProjectTheme
import pl.pam.startproject.ui.theme.TimerFont
import java.util.Locale
import java.util.UUID
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun DragMeasureScreen(
    modifier: Modifier = Modifier,
    sessionUser: SessionUser?,
) {
    // Dostęp do usług systemowych i repozytorium zapisu wyników.
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val syncRepository = remember { MeasurementSyncRepository.get(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Stan uprawnień i stanu sesji.
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

    // Bieżące metryki pokazywane użytkownikowi.
    var elapsedTimeMs by remember { mutableLongStateOf(0L) }
    var distanceMeters by remember { mutableStateOf(0f) }
    var speedKmh by remember { mutableStateOf(0f) }
    var maxSpeedKmhPeak by remember { mutableStateOf(0f) }
    var previousLocation by remember { mutableStateOf<Location?>(null) }
    var armedAnchor by remember { mutableStateOf<Location?>(null) }
    var lastArmedLocation by remember { mutableStateOf<Location?>(null) }
    var runAnchorTimeMs by remember { mutableLongStateOf(0L) }
    val speedSamples = remember { ArrayList<Pair<Long, Float>>(256) }

    var preset by remember {
        mutableStateOf<MeasurePreset>(
            MeasurePreset.Distance(1000f, "1 km")
        )
    }
    val presetState = rememberUpdatedState(preset)
    val runPhaseState = rememberUpdatedState(runPhase)
    val runPhaseForTimer = rememberUpdatedState(runPhase)
    // Domknięcie sesji: reset stanu + opcjonalny zapis wyniku.
    val handleSessionEnd = rememberUpdatedState { shouldSave: Boolean ->
        val wasRunning = runPhase == RunPhase.Running
        val snapElapsed = elapsedTimeMs
        val snapDist = distanceMeters
        val snapMax = max(maxSpeedKmhPeak, speedKmh)
        val snapPreset = preset
        val snapStrategy = startStrategy
        val snapSpeedProfile = speedSamples.toList()
        speedSamples.clear()
        runAnchorTimeMs = 0L
        lastArmedLocation = null

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
                    speedProfileJson = SpeedProfileCodec.encode(snapSpeedProfile),
                    ownerUserId = sessionUser?.id,
                    ownerUsername = sessionUser?.username,
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
        runAnchorTimeMs = 0L
        speedSamples.clear()
    }

    // Serce pomiaru: reakcja na nowe punkty GPS.
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.lastLocation ?: return
                val phase = runPhaseState.value

                if (phase == RunPhase.Armed) {
                    val estArmed = GpsSpeedEstimator.estimateKmh(lastArmedLocation, latest)
                    speedKmh = estArmed
                    lastArmedLocation = latest
                    val anchor = armedAnchor
                    if (anchor == null) {
                        armedAnchor = latest
                    } else {
                        val movedFar = anchor.distanceTo(latest) >= MOTION_MIN_DISPLACEMENT_M
                        val fastEnough = latest.hasSpeed() && latest.speed >= MOTION_MIN_SPEED_MS
                        if (movedFar || fastEnough) {
                            runPhase = RunPhase.Running
                            resetMeasurementBaselines()
                            val startKmh = GpsSpeedEstimator.estimateKmh(null, latest)
                            speedKmh = startKmh
                            maxSpeedKmhPeak = max(maxSpeedKmhPeak, startKmh)
                            previousLocation = latest
                            runAnchorTimeMs = latest.time
                            appendSpeedSample(speedSamples, 0L, startKmh)
                        }
                    }
                    return
                }

                if (phase != RunPhase.Running) return

                val kmh = GpsSpeedEstimator.estimateKmh(previousLocation, latest)
                speedKmh = kmh
                maxSpeedKmhPeak = max(maxSpeedKmhPeak, kmh)
                var newDistance = distanceMeters
                previousLocation?.let { previous ->
                    newDistance += previous.distanceTo(latest)
                }
                distanceMeters = newDistance
                previousLocation = latest
                if (runAnchorTimeMs == 0L) runAnchorTimeMs = latest.time
                val tMs = (latest.time - runAnchorTimeMs).coerceAtLeast(0L)
                appendSpeedSample(speedSamples, tMs, kmh)
                when (val p = presetState.value) {
                    is MeasurePreset.Distance -> {
                        if (newDistance >= p.meters) {
                            handleSessionEnd.value.invoke(true)
                        }
                    }
                    is MeasurePreset.SpeedAccel -> {
                        if (kmh >= p.targetKmh) {
                            handleSessionEnd.value.invoke(true)
                        }
                    }
                }
            }
        }
    }

    // Efekt odliczania 5..1 dla trybu CountdownThenMeasure.
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

    // "Soft timer" aktualizujący stoper co 100 ms.
    LaunchedEffect(runPhase) {
        while (runPhaseForTimer.value == RunPhase.Running) {
            delay(100L)
            elapsedTimeMs += 100L
        }
    }

    // Subskrypcja GPS tylko wtedy, gdy jest potrzebna.
    val needsGps = runPhase == RunPhase.Armed || runPhase == RunPhase.Running
    DisposableEffect(needsGps, hasLocationPermission) {
        if (!hasLocationPermission || !needsGps) {
            onDispose { }
        } else {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                100L
            )
                .setMinUpdateIntervalMillis(50L)
                .setMaxUpdateDelayMillis(200L)
                .build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
            onDispose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    // Sekcja UI: karty konfiguracji, metryki i przyciski Start/Stop.
    val sessionActive = runPhase != RunPhase.Idle
    val idle = runPhase == RunPhase.Idle
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(bottom = AppDimens.ScreenPaddingHorizontal)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap)
        ) {
            if (!hasLocationPermission) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(AppDimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.ContentGap)
                    ) {
                        Text("Lokalizacja", style = AppText.sectionTitle())
                        Text(
                            "Zezwól na GPS, aby mierzyć prędkość i dystans.",
                            style = AppText.body(),
                            color = AppColors.secondaryText()
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        ) {
                            Text("Zezwól na lokalizację")
                        }
                    }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ContentGap)
                ) {
                    Text("Przed startem", style = AppText.sectionTitle())
                    Text(
                        "Jak zacząć",
                        style = AppText.sectionLabel(),
                        color = AppColors.secondaryText()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)
                    ) {
                        FilterChip(
                            selected = startStrategy == StartStrategy.CountdownThenMeasure,
                            onClick = {
                                if (idle) startStrategy = StartStrategy.CountdownThenMeasure
                            },
                            enabled = idle,
                            label = { Text("Odliczanie 5 s") }
                        )
                        FilterChip(
                            selected = startStrategy == StartStrategy.ArmedWaitForMotion,
                            onClick = {
                                if (idle) startStrategy = StartStrategy.ArmedWaitForMotion
                            },
                            enabled = idle,
                            label = { Text("Po pierwszym ruchu") }
                        )
                    }
                    HorizontalDivider()
                    Text(
                        "Tryb",
                        style = AppText.sectionLabel(),
                        color = AppColors.secondaryText()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)
                    ) {
                        FilterChip(
                            selected = preset is MeasurePreset.Distance,
                            onClick = {
                                if (idle) preset = MeasurePreset.Distance(1000f, "1 km")
                            },
                            enabled = idle,
                            label = { Text("Dystans") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = preset is MeasurePreset.SpeedAccel,
                            onClick = {
                                if (idle) preset = MeasurePreset.SpeedAccel(100f)
                            },
                            enabled = idle,
                            label = { Text("Przyspieszenie 0→V") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    MeasurePresetOptions(
                        preset = preset,
                        onPresetChange = { preset = it },
                        enabled = idle
                    )
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(AppDimens.CardPaddingLarge),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap)
                ) {
                    Text(
                        text = measureGoalLine(preset),
                        style = AppText.bodySmall(),
                        color = AppColors.secondaryText()
                    )
                    when (runPhase) {
                        RunPhase.Countdown ->
                            Text(
                                text = "Start za  $countdownTick",
                                style = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.primary),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        RunPhase.Armed ->
                            Text(
                                text = "Czekam na ruch…",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        else -> {}
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBlock(
                            label = "Czas",
                            value = formatElapsedTime(elapsedTimeMs),
                            valueStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = TimerFont)
                        )
                        StatBlock(
                            label = "km/h",
                            value = "%.1f".format(Locale.US, speedKmh)
                        )
                        StatBlock(
                            label = "Dystans m",
                            value = "%.0f".format(Locale.US, distanceMeters)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.ContentGap)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
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
                                lastArmedLocation = null
                                resetMeasurementBaselines()
                                runPhase = RunPhase.Armed
                            }
                        }
                    }
                ) {
                    Text("Start")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = sessionActive,
                    onClick = {
                        val save = runPhase == RunPhase.Running
                        handleSessionEnd.value.invoke(save)
                    }
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.StatGap)
    ) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = AppText.statLabel(),
            color = AppColors.secondaryText()
        )
        Text(value, style = valueStyle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasurePresetOptions(
    preset: MeasurePreset,
    onPresetChange: (MeasurePreset) -> Unit,
    enabled: Boolean,
) {
    when (val p = preset) {
        is MeasurePreset.Distance -> {
            Text(
                "Dystans",
                style = AppText.sectionLabel(),
                color = AppColors.secondaryText()
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)
                ) {
                    distanceOptions.take(2).forEach { opt ->
                        DistanceFilterChip(
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
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)
                ) {
                    distanceOptions.drop(2).forEach { opt ->
                        DistanceFilterChip(
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
            Text(
                "Próg prędkości",
                style = AppText.sectionLabel(),
                color = AppColors.secondaryText()
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)
                ) {
                    speedTargetOptions.take(2).forEach { kmh ->
                        SpeedFilterChip(
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
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)
                ) {
                    speedTargetOptions.drop(2).forEach { kmh ->
                        SpeedFilterChip(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DistanceFilterChip(
    option: DistanceOption,
    current: MeasurePreset.Distance,
    enabled: Boolean,
    onSelect: (MeasurePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = current.label == option.label
    FilterChip(
        selected = selected,
        onClick = { onSelect(MeasurePreset.Distance(option.meters, option.label)) },
        enabled = enabled,
        label = { Text(option.label) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedFilterChip(
    targetKmh: Float,
    current: MeasurePreset.SpeedAccel,
    enabled: Boolean,
    onSelect: (MeasurePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = current.targetKmh == targetKmh
    FilterChip(
        selected = selected,
        onClick = { onSelect(MeasurePreset.SpeedAccel(targetKmh)) },
        enabled = enabled,
        label = { Text("${targetKmh.toInt()} km/h") },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun DragMeasurePreview() {
    StartProjectTheme {
        DragMeasureScreen(
            sessionUser = SessionUser(id = 1, username = "demo", email = "demo@example.com", displayName = "Demo", role = "user")
        )
    }
}
