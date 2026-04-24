package pl.pam.startproject.ui.account

/**
 * Ekran konta użytkownika:
 * - podgląd danych konta,
 * - zmiana hasła,
 * - przejście do panelu admina (dla adminów),
 * - wylogowanie.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.pam.startproject.auth.AuthAction
import pl.pam.startproject.auth.AuthRepository
import pl.pam.startproject.auth.SessionUser
import pl.pam.startproject.auth.mapAuthError
import pl.pam.startproject.auth.passwordValid
import pl.pam.startproject.ui.theme.AppColors
import pl.pam.startproject.ui.theme.AppDimens
import pl.pam.startproject.ui.theme.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    sessionUser: SessionUser?,
    authRepository: AuthRepository,
    onOpenAdmin: () -> Unit,
    onLogout: () -> Unit,
) {
    // Lokalny stan formularza zmiany hasła.
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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.ScreenPaddingHorizontal)
                .padding(top = 16.dp, bottom = AppDimens.ScreenPaddingVertical),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                // Karta informacyjna - podstawowe dane użytkownika.
                Column(
                    modifier = Modifier.padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ChipGap)
                ) {
                    Text("Użytkownik", style = AppText.sectionTitle())
                    Text(sessionUser?.username ?: "-", style = AppText.body())
                    Text(
                        sessionUser?.email ?: "-",
                        style = AppText.body(),
                        color = AppColors.secondaryText()
                    )
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                // Karta akcji - formularz zmiany hasła.
                Column(
                    modifier = Modifier.padding(AppDimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.ContentGapSmall)
                ) {
                    Text("Zmiana hasła", style = AppText.sectionTitle())
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
                                    newPassword.isBlank() -> AppColors.secondaryText()
                                    !newPasswordOk -> AppColors.error()
                                    else -> AppColors.success()
                                },
                                style = AppText.bodySmall()
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
                            color = AppColors.error(),
                            style = AppText.bodySmall()
                        )
                    }
                    changePasswordSuccess?.let {
                        Text(
                            it,
                            color = AppColors.success(),
                            style = AppText.bodySmall()
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
                    Text("Panel admina", modifier = Modifier.padding(start = AppDimens.IconTextGap))
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLogout
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("Wyloguj", modifier = Modifier.padding(start = AppDimens.IconTextGap))
            }
        }
    }
}
