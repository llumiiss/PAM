package pl.pam.startproject.auth

/**
 * Wspólne mapowanie błędów autoryzacji na komunikaty dla UI.
 * Dzięki temu ekrany nie duplikują logiki obsługi wyjątków HTTP/IO.
 */
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

enum class AuthAction { Login, Register, ChangePassword }

/** Próbuje odczytać komunikat błędu z payloadu JSON odpowiedzi backendu. */
private fun extractServerErrorMessage(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val trimmed = raw.trim()
    return runCatching {
        val json = JSONObject(trimmed)
        when {
            json.has("message") -> json.optString("message")
            json.has("error") -> json.optString("error")
            else -> null
        }
    }.getOrNull() ?: trimmed
}

fun mapAuthError(throwable: Throwable, action: AuthAction): String {
    if (throwable is IOException) {
        return "Brak połączenia z internetem. Sprawdź sieć i spróbuj ponownie."
    }
    if (throwable is HttpException) {
        val status = throwable.code()
        val serverMessage = extractServerErrorMessage(throwable.response()?.errorBody()?.string())
        val normalized = serverMessage?.lowercase(Locale.getDefault()).orEmpty()
        if (action == AuthAction.Register && (status == 409 || normalized.contains("already exists"))) {
            return "Ten użytkownik już istnieje, dlatego nie można zarejestrować tego konta."
        }
        return when (action) {
            AuthAction.Login -> when (status) {
                400, 401 -> "Nieprawidłowy login lub hasło."
                429 -> "Za dużo prób logowania. Odczekaj chwilę i spróbuj ponownie."
                500, 502, 503 -> "Błąd serwera podczas logowania. Spróbuj ponownie za chwilę."
                else -> serverMessage ?: "Nie udało się zalogować. Spróbuj ponownie."
            }
            AuthAction.Register -> when (status) {
                400 -> "Dane rejestracji są niepoprawne. Sprawdź pola formularza."
                429 -> "Za dużo prób rejestracji. Odczekaj chwilę i spróbuj ponownie."
                500, 502, 503 -> "Błąd serwera podczas rejestracji. Spróbuj ponownie za chwilę."
                else -> serverMessage ?: "Nie udało się zarejestrować konta."
            }
            AuthAction.ChangePassword -> when (status) {
                400 -> "Nowe hasło jest niepoprawne. Upewnij się, że spełnia wymagania."
                401, 403 -> "Stare hasło jest nieprawidłowe."
                429 -> "Za dużo prób zmiany hasła. Odczekaj chwilę i spróbuj ponownie."
                500, 502, 503 -> "Błąd serwera przy zmianie hasła. Spróbuj ponownie za chwilę."
                else -> serverMessage ?: "Nie udało się zmienić hasła."
            }
        }
    }
    return when (action) {
        AuthAction.Login -> throwable.message ?: "Wystąpił nieoczekiwany błąd logowania."
        AuthAction.Register -> throwable.message ?: "Wystąpił nieoczekiwany błąd rejestracji."
        AuthAction.ChangePassword -> throwable.message ?: "Wystąpił nieoczekiwany błąd zmiany hasła."
    }
}

/** Prosta walidacja hasła używana przez formularze auth. */
fun passwordValid(password: String): Boolean {
    val p = password.trim()
    return p.length >= 5 && p.all { it.isLetterOrDigit() }
}
