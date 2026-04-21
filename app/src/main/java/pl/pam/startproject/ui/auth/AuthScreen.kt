package pl.pam.startproject.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private fun registrationEmailValid(email: String): Boolean {
    val e = email.trim()
    return e.contains('@') && e.contains(".com", ignoreCase = true)
}

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
    val registerReady = username.isNotBlank() && emailOk && password.isNotBlank()
    val loginReady = emailOrUsername.isNotBlank() && password.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Konto użytkownika", style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { registerMode = false }, enabled = !isLoading && registerMode) {
                        Text("Logowanie")
                    }
                    Button(onClick = { registerMode = true }, enabled = !isLoading && !registerMode) {
                        Text("Rejestracja")
                    }
                }

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
                                    email.isBlank() -> "Wymagany adres z symbolem @ oraz domeną zawierającą .com"
                                    !emailOk -> "Nieprawidłowy format (wymagane @ oraz .com w adresie)"
                                    else -> "Format poprawny"
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
                        label = { Text("E-mail lub nazwa użytkownika") },
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hasło") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
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
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        if (registerMode) {
                            onRegister(username, email, password)
                        } else {
                            onLogin(emailOrUsername, password)
                        }
                    },
                    enabled = !isLoading && if (registerMode) registerReady else loginReady
                ) {
                    Text(if (isLoading) "Trwa..." else if (registerMode) "Załóż konto" else "Zaloguj")
                }
            }
        }

        Text(
            "Google/Facebook: backend ma przygotowany punkt rozszerzenia, ale OAuth wymaga kluczy dostawców.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
