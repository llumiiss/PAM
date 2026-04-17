package pl.pam.startproject.ui.history

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import pl.pam.startproject.data.MeasureType
import pl.pam.startproject.data.MeasurementAttemptEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter { All, Distance, SpeedAccel }

private enum class HistorySort {
    /** Najnowsze na górze */
    DateNewest,

    /** Krótszy czas = lepszy (typowe dla drag / 0–X). */
    BestTime,
}

@Composable
fun HistoryScreen(
    attemptsFlow: Flow<List<MeasurementAttemptEntity>>,
    onBackToMeasure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(HistoryFilter.All) }
    var sort by remember { mutableStateOf(HistorySort.DateNewest) }

    val raw by attemptsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val filtered = remember(raw, filter) {
        when (filter) {
            HistoryFilter.All -> raw
            HistoryFilter.Distance -> raw.filter { it.measureType == MeasureType.DISTANCE }
            HistoryFilter.SpeedAccel -> raw.filter { it.measureType == MeasureType.SPEED_ACCEL }
        }
    }

    val displayed = remember(filtered, sort) {
        when (sort) {
            HistorySort.DateNewest -> filtered.sortedByDescending { it.measuredAtEpochMs }
            HistorySort.BestTime -> filtered.sortedBy { it.durationMs }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Historia prób", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onBackToMeasure) { Text("Pomiar") }
        }

        Text("Typ pomiaru", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == HistoryFilter.All,
                onClick = { filter = HistoryFilter.All },
                label = { Text("Wszystkie") }
            )
            FilterChip(
                selected = filter == HistoryFilter.Distance,
                onClick = { filter = HistoryFilter.Distance },
                label = { Text("Dystans") }
            )
            FilterChip(
                selected = filter == HistoryFilter.SpeedAccel,
                onClick = { filter = HistoryFilter.SpeedAccel },
                label = { Text("Przyspieszenie") }
            )
        }

        Text("Sortowanie", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sort == HistorySort.DateNewest,
                onClick = { sort = HistorySort.DateNewest },
                label = { Text("Data") }
            )
            FilterChip(
                selected = sort == HistorySort.BestTime,
                onClick = { sort = HistorySort.BestTime },
                label = { Text("Najlepszy czas") }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayed, key = { it.id }) { row ->
                AttemptRow(entity = row)
            }
        }
    }
}

@Composable
private fun AttemptRow(entity: MeasurementAttemptEntity) {
    val dateStr = remember(entity.measuredAtEpochMs) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entity.measuredAtEpochMs))
    }
    val typeLabel = when (entity.measureType) {
        MeasureType.DISTANCE -> "Dystans"
        MeasureType.SPEED_ACCEL -> "Przyspieszenie"
        else -> entity.measureType
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("$dateStr · $typeLabel", style = MaterialTheme.typography.labelMedium)
            Text(entity.modeLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                "Czas: ${formatDuration(entity.durationMs)} · Dystans: ${"%.1f".format(Locale.US, entity.distanceM)} m · Vmax: ${"%.1f".format(Locale.US, entity.maxSpeedKmh)} km/h",
                style = MaterialTheme.typography.bodyMedium
            )
            entity.startStrategy?.let {
                Text("Start: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
