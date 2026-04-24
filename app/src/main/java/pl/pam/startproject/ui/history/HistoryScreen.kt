package pl.pam.startproject.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import pl.pam.startproject.data.SpeedProfileCodec
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    attemptsFlow: Flow<List<MeasurementAttemptEntity>>,
    isAdmin: Boolean,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(HistoryFilter.All) }
    var sort by remember { mutableStateOf(HistorySort.DateNewest) }
    var detailAttempt by remember { mutableStateOf<MeasurementAttemptEntity?>(null) }

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

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Historia", style = MaterialTheme.typography.headlineSmall)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Typ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
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
                        label = { Text("0→V") }
                    )
                    VerticalDivider(
                        modifier = Modifier.height(28.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
                    Text(
                        "Sort.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    FilterChip(
                        selected = sort == HistorySort.DateNewest,
                        onClick = { sort = HistorySort.DateNewest },
                        label = { Text("Data") }
                    )
                    FilterChip(
                        selected = sort == HistorySort.BestTime,
                        onClick = { sort = HistorySort.BestTime },
                        label = { Text("Czas") }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayed, key = { it.id }) { row ->
                        AttemptRow(
                            entity = row,
                            isAdmin = isAdmin,
                            onOpenDetail = { detailAttempt = row }
                        )
                    }
                }
            }

            detailAttempt?.let { picked ->
                ModalBottomSheet(onDismissRequest = { detailAttempt = null }) {
                    AttemptDetailSheetContent(
                        entity = picked,
                        onClose = { detailAttempt = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttemptRow(
    entity: MeasurementAttemptEntity,
    isAdmin: Boolean,
    onOpenDetail: () -> Unit,
) {
    val dateStr = remember(entity.measuredAtEpochMs) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entity.measuredAtEpochMs))
    }
    val typeLabel = when (entity.measureType) {
        MeasureType.DISTANCE -> "Dystans"
        MeasureType.SPEED_ACCEL -> "Przyspieszenie"
        else -> entity.measureType
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entity.modeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isAdmin) {
                Text(
                    "Użytkownik: ${entity.ownerUsername ?: "nieznany"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${formatDuration(entity.durationMs)} · ${"%.0f".format(Locale.US, entity.distanceM)} m · ${"%.0f".format(Locale.US, entity.maxSpeedKmh)} km/h",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Szczegóły ›",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AttemptDetailSheetContent(
    entity: MeasurementAttemptEntity,
    onClose: () -> Unit,
) {
    val samples = remember(entity.speedProfileJson) { SpeedProfileCodec.decode(entity.speedProfileJson) }
    val avgKmh = remember(samples) {
        if (samples.isEmpty()) null
        else samples.map { it.second.toDouble() }.average()
    }
    val durationSec = remember(entity.durationMs) { entity.durationMs / 1000.0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Szczegóły próby", style = MaterialTheme.typography.titleLarge)
        Text(entity.modeLabel, style = MaterialTheme.typography.titleMedium)
        Text(
            "Czas: ${formatDuration(entity.durationMs)} · Dystans: ${"%.1f".format(Locale.US, entity.distanceM)} m",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Vmax (zapis): ${"%.1f".format(Locale.US, entity.maxSpeedKmh)} km/h",
            style = MaterialTheme.typography.bodyMedium
        )
        avgKmh?.let {
            Text(
                "Średnia z profilu (próbki): ${"%.1f".format(Locale.US, it)} km/h",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (durationSec > 0.5 && entity.distanceM > 1.0) {
            val implied = (entity.distanceM / durationSec) * 3.6
            Text(
                "Średnia z dystans/czas: ${"%.1f".format(Locale.US, implied)} km/h",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("Prędkość w czasie", style = MaterialTheme.typography.titleSmall)
        if (samples.size >= 2) {
            SpeedOverTimeChart(samples = samples)
            Text(
                "Oś X: czas od startu pomiaru (ms), oś Y: prędkość (km/h).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Brak zapisanego profilu (próba sprzed aktualizacji lub zbyt krótka).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Zamknij")
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
