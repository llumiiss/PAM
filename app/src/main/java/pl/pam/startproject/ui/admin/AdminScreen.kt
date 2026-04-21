package pl.pam.startproject.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.pam.startproject.admin.AdminRepository
import pl.pam.startproject.admin.AdminUserDto

@Composable
fun AdminScreen(
    repository: AdminRepository,
    authToken: String,
    currentUserId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }

    suspend fun reload() {
        loading = true
        error = null
        runCatching { repository.loadUsers(authToken) }
            .onSuccess { users = it }
            .onFailure { error = it.message ?: "Błąd pobierania użytkowników" }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Pomiar") }
            Button(onClick = { scope.launch { reload() } }, enabled = !loading) { Text("Odśwież") }
        }
        Text("Panel administratora", style = MaterialTheme.typography.headlineSmall)
        if (loading) Text("Ładowanie...")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users, key = { it.id }) { user ->
                var passwordRevealed by remember(user.id) { mutableStateOf(false) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${user.username} (${user.role})", style = MaterialTheme.typography.titleMedium)
                        Text("Email: ${user.email}")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (passwordRevealed) {
                                    "Hasło: ${user.passwordPlain ?: "— (brak zapisu jawnym tekstem)"}"
                                } else {
                                    "Hasło: ••••••••"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { passwordRevealed = !passwordRevealed }) {
                                Text(if (passwordRevealed) "Ukryj" else "Pokaż")
                            }
                        }
                        Text("Prób: ${user.attemptsCount}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val canDelete = user.id != currentUserId
                            Button(
                                onClick = {
                                    scope.launch {
                                        runCatching { repository.deleteUser(authToken, user.id) }
                                            .onSuccess { reload() }
                                            .onFailure { error = it.message ?: "Nie udało się usunąć użytkownika" }
                                    }
                                },
                                enabled = canDelete && !loading
                            ) {
                                Text("Usuń użytkownika")
                            }
                        }
                    }
                }
            }
        }
    }
}
