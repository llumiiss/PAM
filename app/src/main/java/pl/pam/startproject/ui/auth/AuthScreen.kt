package pl.pam.startproject.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private fun registrationEmailValid(email: String): Boolean {
    val e = email.trim()
    return e.contains('@') && e.contains(".com", ignoreCase = true)
}

private fun passwordValid(password: String): Boolean {
    val p = password.trim()
    return p.length >= 5 && p.all { it.isLetterOrDigit() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var emailOrUsername by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val emailOk = registrationEmailValid(email)
    val passwordOk = passwordValid(password)
    val registerReady = username.isNotBlank() && emailOk && passwordOk
    val loginReady = emailOrUsername.isNotBlank() && password.isNotBlank()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Draggy",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Logowanie lub nowe konto",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !registerMode,
                        onClick = { registerMode = false },
                        enabled = !isLoading,
                        label = { Text("Logowanie") }
                    )
                    FilterChip(
                        selected = registerMode,
                        onClick = { registerMode = true },
                        enabled = !isLoading,
                        label = { Text("Rejestracja") }
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (registerMode) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nazwa użytkownika") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("E-mail") },
                            singleLine = true,
                            isError = email.isNotBlank() && !emailOk,
                            supportingText = {
                                Text(
                                    when {
                                        email.isBlank() -> "@ oraz domena z .com"
                                        !emailOk -> "Wymagane @ i .com"
                                        else -> "OK"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        email.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                                        !emailOk -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        )
                    } else {
                        OutlinedTextField(
                            value = emailOrUsername,
                            onValueChange = { emailOrUsername = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("E-mail lub login") },
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hasło") },
                        singleLine = true,
                        isError = registerMode && password.isNotBlank() && !passwordOk,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        supportingText = {
                            if (registerMode) {
                                Text(
                                    when {
                                        password.isBlank() -> "Min. 5 znaków: tylko litery lub cyfry"
                                        !passwordOk -> "Hasło musi mieć min. 5 znaków (litery/cyfry)"
                                        else -> "OK"
                                    },
                                    color = when {
                                        password.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                                        !passwordOk -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = { passwordVisible = !passwordVisible },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    if (passwordVisible) "Ukryj" else "Pokaż",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    )

                    errorMessage?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (registerMode) {
                                onRegister(username, email, password)
                            } else {
                                onLogin(emailOrUsername, password)
                            }
                        },
                        enabled = !isLoading && if (registerMode) registerReady else loginReady
                    ) {
                        Text(if (isLoading) "Trwa…" else if (registerMode) "Załóż konto" else "Zaloguj")
                    }
                }
            }
        }
    }
}
