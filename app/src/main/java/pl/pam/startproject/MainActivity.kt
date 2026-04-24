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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.painterResource
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
import pl.pam.startproject.admin.AdminRepository
import pl.pam.startproject.auth.AuthRepository
import pl.pam.startproject.auth.SessionManager
import pl.pam.startproject.auth.SessionUser
import pl.pam.startproject.data.MeasureType
import pl.pam.startproject.data.MeasurementAttemptEntity
import pl.pam.startproject.data.MeasurementDao
import pl.pam.startproject.data.PamDatabase
import pl.pam.startproject.data.SpeedProfileCodec
import pl.pam.startproject.gps.GpsSpeedEstimator
import pl.pam.startproject.leaderboard.LeaderboardRepository
import pl.pam.startproject.sync.MeasurementSyncRepository
import pl.pam.startproject.ui.auth.AuthScreen
import pl.pam.startproject.ui.admin.AdminScreen
import pl.pam.startproject.ui.history.HistoryScreen
import pl.pam.startproject.ui.leaderboard.LeaderboardScreen
import pl.pam.startproject.ui.theme.StartProjectTheme
import pl.pam.startproject.ui.theme.TimerFont
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow

private val QUARTER_MILE_METERS = (1609.344 / 4.0).toFloat()

/** Minimalny dystans od punktu uzbrojenia, by uznać „ruszenie” (redukcja fałszywych startów przy dryfie GPS). */
private const val MOTION_MIN_DISPLACEMENT_M = 4f

/** Minimalna prędkość z GPS (m/s), gdy [Location.hasSpeed] — ok. 3,2 km/h. */
private const val MOTION_MIN_SPEED_MS = 0.9f

/** Min. odstęp czasu między punktami profilu prędkości (zmniejsza rozmiar JSON). */
private const val SPEED_SAMPLE_MIN_INTERVAL_MS = 40L

/** Górny limit punktów profilu prędkości na jedną próbę. */
private const val SPEED_SAMPLE_MAX_POINTS = 450

private enum class AuthAction { Login, Register, ChangePassword }

private fun extractServerErrorMessage(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    return runCatching {
        val json = JSONObject(trimmed)
        when {
            json.has("message") -> json.optString("message")
            json.has("error") -> json.optString("error")
            else -> null
        }
    }.getOrNull() ?: trimmed
}

private fun mapAuthError(throwable: Throwable, action: AuthAction): String {
    if (throwable is IOException) {
        return "Brak połączenia z internetem. Sprawdź sieć i spróbuj ponownie."
    }
    if (throwable is HttpException) {
        val status = throwable.code()
        val serverMessage = extractServerErrorMessage(throwable.response()?.errorBody()?.string())
        val normalized = serverMessage?.lowercase(Locale.getDefault()).orEmpty()
        if (action == AuthAction.Register && (status == 409 || normalized.contains("already exists"))) {
            return "Ten użytkownik już istnieje, dlatego nie można zarejestrować tego konta."
        }
        return when (action) {
            AuthAction.Login -> when (status) {
                400, 401 -> "Nieprawidłowy login lub hasło."
                429 -> "Za dużo prób logowania. Odczekaj chwilę i spróbuj ponownie."
                500, 502, 503 -> "Błąd serwera podczas logowania. Spróbuj ponownie za chwilę."
                else -> serverMessage ?: "Nie udało się zalogować. Spróbuj ponownie."
            }
            AuthAction.Register -> when (status) {
                400 -> "Dane rejestracji są niepoprawne. Sprawdź pola formularza."
                429 -> "Za dużo prób rejestracji. Odczekaj chwilę i spróbuj ponownie."
                500, 502, 503 -> "Błąd serwera podczas rejestracji. Spróbuj ponownie za chwilę."
                else -> serverMessage ?: "Nie udało się zarejestrować konta."
            }
            AuthAction.ChangePassword -> when (status) {
                400 -> "Nowe hasło jest niepoprawne. Upewnij się, że spełnia wymagania."
                401, 403 -> "Stare hasło jest nieprawidłowe."
                429 -> "Za dużo prób zmiany hasła. Odczekaj chwilę i spróbuj ponownie."
                500, 502, 503 -> "Błąd serwera przy zmianie hasła. Spróbuj ponownie za chwilę."
                else -> serverMessage ?: "Nie udało się zmienić hasła."
            }
        }
    }
    return when (action) {
        AuthAction.Login -> throwable.message ?: "Wystąpił nieoczekiwany błąd logowania."
        AuthAction.Register -> throwable.message ?: "Wystąpił nieoczekiwany błąd rejestracji."
        AuthAction.ChangePassword -> throwable.message ?: "Wystąpił nieoczekiwany błąd zmiany hasła."
    }
}

private fun passwordValid(password: String): Boolean {
    val p = password.trim()
    return p.length >= 5 && p.all { it.isLetterOrDigit() }
}

@Composable
private fun rememberHistoryFlow(
    measurementDao: MeasurementDao,
    sessionUser: SessionUser?,
): Flow<List<MeasurementAttemptEntity>> {
    val userId = sessionUser?.id
    val isAdmin = sessionUser?.isAdmin == true
    return remember(measurementDao, userId, isAdmin) {
        if (isAdmin) measurementDao.observeAllByDateDesc()
        else measurementDao.observeByOwnerByDateDesc(userId ?: -1L)
    }
}

private fun appendSpeedSample(
    samples: ArrayList<Pair<Long, Float>>,
    tMs: Long,
    vKmh: Float,
) {
    if (samples.size >= SPEED_SAMPLE_MAX_POINTS) return
    val last = samples.lastOrNull()
    if (last == null || tMs - last.first >= SPEED_SAMPLE_MIN_INTERVAL_MS) {
        samples.add(tMs to vKmh)
    }
}

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

private enum class AppScreen { Auth, Measure, History, Account, Leaderboard, Admin }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val syncRepository = MeasurementSyncRepository.get(this)
        syncRepository.enqueueSyncNow()
        setContent {
            val scope = rememberCoroutineScope()
            val database = remember { PamDatabase.get(this) }
            val sessionManager = remember { SessionManager(this) }
            val authRepository = remember { AuthRepository(this) }
            val leaderboardRepository = remember { LeaderboardRepository(this) }
            val adminRepository = remember { AdminRepository(this) }
            val measurementDao = remember { database.measurementDao() }
            var sessionUser by remember { mutableStateOf<SessionUser?>(sessionManager.getUser()) }
            val attemptsFlow = rememberHistoryFlow(measurementDao, sessionUser)
            var authLoading by remember { mutableStateOf(false) }
            var authError by remember { mutableStateOf<String?>(null) }
            var showLaunchScreen by remember { mutableStateOf(true) }
            var appScreen by remember {
                mutableStateOf(if (sessionManager.getToken() != null) AppScreen.Measure else AppScreen.Auth)
            }
            val performLogout: () -> Unit = {
                sessionManager.clear()
                sessionUser = null
                appScreen = AppScreen.Auth
            }
            StartProjectTheme {
                if (showLaunchScreen) {
                    LaunchImageScreen(onFinished = { showLaunchScreen = false })
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        bottomBar = {
                            if (appScreen != AppScreen.Auth && appScreen != AppScreen.Admin) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = appScreen == AppScreen.Measure,
                                        onClick = { appScreen = AppScreen.Measure },
                                        icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                                        label = { Text("Pomiar") }
                                    )
                                    NavigationBarItem(
                                        selected = appScreen == AppScreen.History,
                                        onClick = { appScreen = AppScreen.History },
                                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                                        label = { Text("Historia") }
                                    )
                                    NavigationBarItem(
                                        selected = appScreen == AppScreen.Account,
                                        onClick = { appScreen = AppScreen.Account },
                                        icon = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                                        label = { Text("Konto") }
                                    )
                                    NavigationBarItem(
                                        selected = appScreen == AppScreen.Leaderboard,
                                        onClick = { appScreen = AppScreen.Leaderboard },
                                        icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
                                        label = { Text("Ranking") }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        when (appScreen) {
                            AppScreen.Auth -> AuthScreen(
                                modifier = Modifier.padding(innerPadding),
                                isLoading = authLoading,
                                errorMessage = authError,
                                onLogin = { emailOrUsername, password ->
                                    scope.launch {
                                        authLoading = true
                                        authError = null
                                        runCatching { authRepository.login(emailOrUsername, password) }
                                            .onSuccess {
                                                sessionUser = it
                                                appScreen = AppScreen.Measure
                                                syncRepository.enqueueSyncNow()
                                            }
                                            .onFailure { authError = mapAuthError(it, AuthAction.Login) }
                                        authLoading = false
                                    }
                                },
                                onRegister = { username, email, password ->
                                    scope.launch {
                                        authLoading = true
                                        authError = null
                                        runCatching { authRepository.register(username, email, password) }
                                            .onSuccess {
                                                sessionUser = it
                                                appScreen = AppScreen.Measure
                                                syncRepository.enqueueSyncNow()
                                            }
                                            .onFailure { authError = mapAuthError(it, AuthAction.Register) }
                                        authLoading = false
                                    }
                                }
                            )
                            AppScreen.Measure -> DragMeasureScreen(
                                modifier = Modifier.padding(innerPadding),
                                sessionUser = sessionUser
                            )
                            AppScreen.History -> HistoryScreen(
                                modifier = Modifier.padding(innerPadding),
                                attemptsFlow = attemptsFlow,
                                isAdmin = sessionUser?.isAdmin == true
                            )
                            AppScreen.Account -> AccountScreen(
                                modifier = Modifier.padding(innerPadding),
                                sessionUser = sessionUser,
                                authRepository = authRepository,
                                onOpenAdmin = {
                                    if (sessionUser?.isAdmin == true) appScreen = AppScreen.Admin
                                },
                                onLogout = performLogout
                            )
                            AppScreen.Leaderboard -> LeaderboardScreen(
                                modifier = Modifier.padding(innerPadding),
                                repository = leaderboardRepository,
                                isAdmin = sessionUser?.isAdmin == true,
                                onDeleteAttempt = { attemptId ->
                                    val token = sessionManager.getToken()
                                    if (token != null) {
                                        adminRepository.deleteAttempt(token, attemptId)
                                    }
                                }
                            )
                            AppScreen.Admin -> {
                                val token = sessionManager.getToken()
                                val user = sessionUser
                                if (token == null || user == null || !user.isAdmin) {
                                    appScreen = AppScreen.Measure
                                } else {
                                    AdminScreen(
                                        modifier = Modifier.padding(innerPadding),
                                        repository = adminRepository,
                                        authToken = token,
                                        currentUserId = user.id,
                                        onBack = { appScreen = AppScreen.Measure }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchImageScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "launchAlpha"
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(1500L)
        visible = false
        delay(500L)
        onFinished()
    }
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.launch_car),
            contentDescription = "Ekran startowy",
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
private fun DragMeasureScreen(
    modifier: Modifier = Modifier,
    sessionUser: SessionUser?,
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
            // Wyższa częstotliwość = szybsza reakcja na ruch i gęstszy profil (większe zużycie baterii).
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

    val sessionActive = runPhase != RunPhase.Idle
    val idle = runPhase == RunPhase.Idle
    Column(modifier = modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Draggy", style = MaterialTheme.typography.titleLarge)
                    Text(
                        sessionUser?.username ?: "Offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasLocationPermission) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Lokalizacja", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Zezwól na GPS, aby mierzyć prędkość i dystans.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Przed startem", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Jak zacząć",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = measureGoalLine(preset),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        else -> { }
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountScreen(
    modifier: Modifier = Modifier,
    sessionUser: SessionUser?,
    authRepository: AuthRepository,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var changePasswordError by remember { mutableStateOf<String?>(null) }
    var changePasswordSuccess by remember { mutableStateOf<String?>(null) }
    val newPasswordOk = passwordValid(newPassword)
    val canChangePassword = currentPassword.isNotBlank() && newPasswordOk

    Column(modifier = modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Konto", style = MaterialTheme.typography.titleLarge) }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Użytkownik", style = MaterialTheme.typography.titleMedium)
                    Text(sessionUser?.username ?: "-", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        sessionUser?.email ?: "-",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Zmiana hasła", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            changePasswordError = null
                            changePasswordSuccess = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Stare hasło") },
                        singleLine = true,
                        visualTransformation = if (currentPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                                Text(if (currentPasswordVisible) "Ukryj" else "Pokaż")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            changePasswordError = null
                            changePasswordSuccess = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nowe hasło") },
                        singleLine = true,
                        isError = newPassword.isNotBlank() && !newPasswordOk,
                        visualTransformation = if (newPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        supportingText = {
                            Text(
                                when {
                                    newPassword.isBlank() -> "Min. 5 znaków: tylko litery lub cyfry"
                                    !newPasswordOk -> "Hasło musi mieć min. 5 znaków (litery/cyfry)"
                                    else -> "OK"
                                },
                                color = when {
                                    newPassword.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                                    !newPasswordOk -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingIcon = {
                            TextButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Text(if (newPasswordVisible) "Ukryj" else "Pokaż")
                            }
                        }
                    )
                    changePasswordError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    changePasswordSuccess?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canChangePassword && !isSaving,
                        onClick = {
                            if (!newPasswordOk) {
                                changePasswordError = "Nowe hasło musi mieć min. 5 znaków (litery/cyfry)."
                                return@Button
                            }
                            scope.launch {
                                isSaving = true
                                changePasswordError = null
                                changePasswordSuccess = null
                                runCatching {
                                    authRepository.changePassword(
                                        currentPassword = currentPassword,
                                        newPassword = newPassword
                                    )
                                }
                                    .onSuccess {
                                        currentPassword = ""
                                        newPassword = ""
                                        changePasswordSuccess = "Hasło zostało zmienione."
                                    }
                                    .onFailure {
                                        changePasswordError = mapAuthError(it, AuthAction.ChangePassword)
                                    }
                                isSaving = false
                            }
                        }
                    ) {
                        Text(if (isSaving) "Zmieniam…" else "Zmień hasło")
                    }
                }
            }
            if (sessionUser?.isAdmin == true) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenAdmin
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Text("Panel admina", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLogout
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("Wyloguj", modifier = Modifier.padding(start = 8.dp))
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

private fun measureGoalLine(preset: MeasurePreset): String = when (preset) {
    is MeasurePreset.Distance ->
        "${preset.label} (${"%.0f".format(Locale.US, preset.meters)} m) · auto-stop po dystansie"
    is MeasurePreset.SpeedAccel ->
        "0–${preset.targetKmh.toInt()} km/h · auto-stop po osiągnięciu prędkości"
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
        DragMeasureScreen(
            sessionUser = SessionUser(id = 1, username = "demo", email = "demo@example.com", displayName = "Demo", role = "user")
        )
    }
}
