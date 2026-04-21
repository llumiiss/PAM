package pl.pam.startproject.auth

import android.content.Context

data class SessionUser(
    val id: Long,
    val username: String,
    val email: String,
    val displayName: String?,
    val role: String
) {
    val isAdmin: Boolean
        get() = role == "admin"
}

class SessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getUser(): SessionUser? {
        val id = prefs.getLong(KEY_USER_ID, -1L)
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)
        val role = prefs.getString(KEY_ROLE, "user") ?: "user"
        return if (id > 0L) SessionUser(id = id, username = username, email = email, displayName = displayName, role = role) else null
    }

    fun saveSession(token: String, user: SessionUser) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_ROLE, user.role)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "pam_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ROLE = "role"
    }
}
