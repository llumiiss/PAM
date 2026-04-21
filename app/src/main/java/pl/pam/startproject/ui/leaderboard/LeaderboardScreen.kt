package pl.pam.startproject.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.pam.startproject.leaderboard.LeaderboardRepository
import pl.pam.startproject.leaderboard.LeaderboardRowDto
import java.util.Locale

@Composable
fun LeaderboardScreen(
    repository: LeaderboardRepository,
    isAdmin: Boolean,
    onDeleteAttempt: suspend (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var top1km by remember { mutableStateOf<List<LeaderboardRowDto>>(emptyList()) }
    var top0100 by remember { mutableStateOf<List<LeaderboardRowDto>>(emptyList()) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        error = null
        runCatching { repository.fetch() }
            .onSuccess {
                top1km = it.top1km
                top0100 = it.top0100
            }
            .onFailure { error = it.message ?: "Błąd pobierania rankingu" }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Pomiar") }
            Button(onClick = { scope.launch { load() } }, enabled = !loading) { Text("Odśwież") }
        }

        Text("Ranking globalny", style = MaterialTheme.typography.headlineSmall)
        if (loading) Text("Ładowanie...")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Text("Najlepszy czas — 1 km", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(top1km, key = { _, row -> "1km-${row.id}" }) { index, row ->
                LeaderboardRow(
                    position = index + 1,
                    row = row,
                    isAdmin = isAdmin,
                    onDelete = {
                        scope.launch {
                            runCatching { onDeleteAttempt(row.id) }
                                .onSuccess { load() }
                                .onFailure { error = it.message ?: "Nie udało się usunąć rekordu" }
                        }
                    }
                )
            }
            item { Text("Najlepszy czas — 0–100 km/h", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(top0100, key = { _, row -> "0100-${row.id}" }) { index, row ->
                LeaderboardRow(
                    position = index + 1,
                    row = row,
                    isAdmin = isAdmin,
                    onDelete = {
                        scope.launch {
                            runCatching { onDeleteAttempt(row.id) }
                                .onSuccess { load() }
                                .onFailure { error = it.message ?: "Nie udało się usunąć rekordu" }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LeaderboardRow(
    position: Int,
    row: LeaderboardRowDto,
    isAdmin: Boolean,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "#$position ${row.username ?: "anon"}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(row.modeLabel)
            Text(
                "Czas: ${formatDuration(row.durationMs)} · Vmax: ${"%.1f".format(Locale.US, row.maxSpeedKmh)} km/h",
                style = MaterialTheme.typography.bodySmall
            )
            if (isAdmin) {
                Button(onClick = onDelete) { Text("Usuń rekord") }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(Locale.US, minutes, seconds, centis)
}
