package pl.pam.startproject.ui.measure

/**
 * Wspólne modele i utility dla modułu pomiaru.
 * Ten plik nie renderuje UI - tylko typy, stałe i funkcje pomocnicze.
 */
import java.util.Locale

private val QUARTER_MILE_METERS = (1609.344 / 4.0).toFloat()

/** Minimalny dystans od punktu uzbrojenia, by uznać „ruszenie” (redukcja fałszywych startów przy dryfie GPS). */
const val MOTION_MIN_DISPLACEMENT_M = 4f

/** Minimalna prędkość z GPS (m/s), gdy Location.hasSpeed — ok. 3,2 km/h. */
const val MOTION_MIN_SPEED_MS = 0.9f

/** Min. odstęp czasu między punktami profilu prędkości (zmniejsza rozmiar JSON). */
private const val SPEED_SAMPLE_MIN_INTERVAL_MS = 40L

/** Górny limit punktów profilu prędkości na jedną próbę. */
private const val SPEED_SAMPLE_MAX_POINTS = 450

enum class StartStrategy {
    /** Start po odliczaniu 5..1, niezależnie od ruchu. */
    CountdownThenMeasure,
    /** Najpierw uzbrojenie, potem start po wykryciu ruchu GPS. */
    ArmedWaitForMotion,
}

enum class RunPhase {
    /** Brak aktywnej sesji. */
    Idle,
    /** Trwa odliczanie przed pomiarem. */
    Countdown,
    /** Czekamy na pierwszy realny ruch pojazdu. */
    Armed,
    /** Aktywny pomiar czasu/prędkości/dystansu. */
    Running,
}

sealed class MeasurePreset {
    data class Distance(val meters: Float, val label: String) : MeasurePreset()
    data class SpeedAccel(val targetKmh: Float) : MeasurePreset()
}

data class DistanceOption(val meters: Float, val label: String)

val distanceOptions = listOf(
    DistanceOption(100f, "100 m"),
    DistanceOption(500f, "500 m"),
    DistanceOption(1000f, "1 km"),
    DistanceOption(QUARTER_MILE_METERS, "1/4 mili"),
)

val speedTargetOptions = listOf(50f, 75f, 100f, 120f)

fun appendSpeedSample(
    samples: ArrayList<Pair<Long, Float>>,
    tMs: Long,
    vKmh: Float,
) {
    // Ograniczamy gęstość próbek, żeby profil prędkości nie urósł nadmiernie.
    if (samples.size >= SPEED_SAMPLE_MAX_POINTS) return
    val last = samples.lastOrNull()
    if (last == null || tMs - last.first >= SPEED_SAMPLE_MIN_INTERVAL_MS) {
        samples.add(tMs to vKmh)
    }
}

fun measureGoalLine(preset: MeasurePreset): String = when (preset) {
    is MeasurePreset.Distance ->
        "${preset.label} (${"%.0f".format(Locale.US, preset.meters)} m) · auto-stop po dystansie"
    is MeasurePreset.SpeedAccel ->
        "0–${preset.targetKmh.toInt()} km/h · auto-stop po osiągnięciu prędkości"
}

fun formatElapsedTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(Locale.US, minutes, seconds, milliseconds)
}
