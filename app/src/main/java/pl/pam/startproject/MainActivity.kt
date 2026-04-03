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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import pl.pam.startproject.ui.theme.StartProjectTheme
import java.util.Locale

private val QUARTER_MILE_METERS = (1609.344 / 4.0).toFloat()

private data class DistanceOption(val meters: Float, val label: String)

private val distanceOptions = listOf(
    DistanceOption(100f, "100 m"),
    DistanceOption(500f, "500 m"),
    DistanceOption(1000f, "1 km"),
    DistanceOption(QUARTER_MILE_METERS, "1/4 mili"),
)

private val speedTargetOptions = listOf(50f, 75f, 100f, 120f)

/** Aktualny preset pomiaru: albo próg dystansu, albo próg prędkości (0→X). */
private sealed class MeasurePreset {
    data class Distance(val meters: Float, val label: String) : MeasurePreset()
    data class SpeedAccel(val targetKmh: Float) : MeasurePreset()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StartProjectTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DragMeasureScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DragMeasureScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isMeasuring by remember { mutableStateOf(false) }
    var elapsedTimeMs by remember { mutableLongStateOf(0L) }
    var distanceMeters by remember { mutableStateOf(0f) }
    var speedKmh by remember { mutableStateOf(0f) }
    var previousLocation by remember { mutableStateOf<Location?>(null) }
    var preset by remember {
        mutableStateOf<MeasurePreset>(
            MeasurePreset.Distance(1000f, "1 km")
        )
    }
    val presetState = rememberUpdatedState(preset)
    val finishMeasurement = rememberUpdatedState { isMeasuring = false }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.lastLocation ?: return
                speedKmh = latest.speed * 3.6f
                var newDistance = distanceMeters
                previousLocation?.let { previous ->
                    newDistance += previous.distanceTo(latest)
                }
                distanceMeters = newDistance
                previousLocation = latest
                when (val p = presetState.value) {
                    is MeasurePreset.Distance -> {
                        if (newDistance >= p.meters) {
                            finishMeasurement.value.invoke()
                        }
                    }
                    is MeasurePreset.SpeedAccel -> {
                        if (speedKmh >= p.targetKmh) {
                            finishMeasurement.value.invoke()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isMeasuring) {
        while (isMeasuring) {
            delay(100L)
            elapsedTimeMs += 100L
        }
    }

    DisposableEffect(isMeasuring, hasLocationPermission) {
        if (!hasLocationPermission || !isMeasuring) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "PAM Drag Measure",
            style = MaterialTheme.typography.headlineSmall
        )

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

        MeasurePresetSelector(
            preset = preset,
            onPresetChange = { preset = it },
            enabled = !isMeasuring
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = measurePresetSummary(preset))
                Text(text = autoStopGoalDescription(preset))
                Text("Czas: ${formatElapsedTime(elapsedTimeMs)}")
                Text("Prędkość: ${"%.1f".format(Locale.US, speedKmh)} km/h")
                Text("Dystans: ${"%.1f".format(Locale.US, distanceMeters)} m")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = hasLocationPermission && !isMeasuring,
                onClick = {
                    isMeasuring = true
                    elapsedTimeMs = 0L
                    distanceMeters = 0f
                    speedKmh = 0f
                    previousLocation = null
                }
            ) { Text("Start") }

            Button(
                enabled = isMeasuring,
                onClick = { isMeasuring = false }
            ) { Text("Stop") }
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
        DragMeasureScreen()
    }
}
