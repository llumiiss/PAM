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
    var selectedMode by remember { mutableStateOf("1 km") }

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
                previousLocation?.let { previous ->
                    distanceMeters += previous.distanceTo(latest)
                }
                previousLocation = latest
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

        ModeSelector(
            selectedMode = selectedMode,
            onModeSelected = { mode -> selectedMode = mode }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tryb: $selectedMode")
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
private fun ModeSelector(selectedMode: String, onModeSelected: (String) -> Unit) {
    val modes = listOf("1 km", "1/4 mili", "0-100 km/h")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { mode ->
            Button(
                onClick = { onModeSelected(mode) },
                enabled = selectedMode != mode
            ) {
                Text(mode)
            }
        }
    }
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