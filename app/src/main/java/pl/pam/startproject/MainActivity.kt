package pl.pam.startproject

/**
 * Główny punkt wejścia aplikacji.
 * Ten plik celowo jest "orchestratorem": inicjalizuje zależności, trzyma stan sesji
 * i przełącza ekrany, ale szczegóły UI/logiki znajdują się już w osobnych plikach.
 */
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.pam.startproject.admin.AdminRepository
import pl.pam.startproject.auth.AuthAction
import pl.pam.startproject.auth.AuthRepository
import pl.pam.startproject.auth.SessionManager
import pl.pam.startproject.auth.SessionUser
import pl.pam.startproject.auth.mapAuthError
import pl.pam.startproject.data.MeasurementAttemptEntity
import pl.pam.startproject.data.MeasurementDao
import pl.pam.startproject.data.PamDatabase
import pl.pam.startproject.leaderboard.LeaderboardRepository
import pl.pam.startproject.sync.MeasurementSyncRepository
import pl.pam.startproject.ui.account.AccountScreen
import pl.pam.startproject.ui.admin.AdminScreen
import pl.pam.startproject.ui.auth.AuthScreen
import pl.pam.startproject.ui.history.HistoryScreen
import pl.pam.startproject.ui.launch.LaunchImageScreen
import pl.pam.startproject.ui.leaderboard.LeaderboardScreen
import pl.pam.startproject.ui.measure.DragMeasureScreen
import pl.pam.startproject.ui.theme.StartProjectTheme

private enum class AppScreen { Auth, Measure, History, Account, Leaderboard, Admin }

/** Zwraca strumień historii dopasowany do aktualnej roli użytkownika. */
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Po starcie aplikacji próbujemy od razu zsynchronizować lokalne wyniki.
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
            // Ochrona przed crashami historii u admina: usuwamy rekordy legacy/uszkodzone.
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    measurementDao.deleteCorruptedRows()
                }
            }
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
                // Ekran powitalny z krótką animacją.
                if (showLaunchScreen) {
                    LaunchImageScreen(onFinished = { showLaunchScreen = false })
                } else {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
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
                        // Centralny router ekranu aplikacji.
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
