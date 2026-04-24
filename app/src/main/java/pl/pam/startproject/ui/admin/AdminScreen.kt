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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Admin", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
                        Text(if (loading) "…" else "Odśwież")
                    }
                    FilledTonalButton(onClick = onBack) {
                        Text("Draggy")
                    }
                }
            }

            if (loading) {
                Text("Ładowanie…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    var passwordRevealed by remember(user.id) { mutableStateOf(false) }
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    user.username,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    user.role,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (passwordRevealed) {
                                        "Hasło: ${user.passwordPlain ?: "—"}"
                                    } else {
                                        "Hasło: ••••••"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { passwordRevealed = !passwordRevealed }) {
                                    Text(if (passwordRevealed) "Ukryj" else "Pokaż")
                                }
                            }
                            Text(
                                "Próby: ${user.attemptsCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val canDelete = user.id != currentUserId
                            if (canDelete) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { repository.deleteUser(authToken, user.id) }
                                                .onSuccess { reload() }
                                                .onFailure { error = it.message ?: "Nie udało się usunąć" }
                                        }
                                    },
                                    enabled = !loading
                                ) {
                                    Text("Usuń konto", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
