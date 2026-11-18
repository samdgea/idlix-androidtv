package com.idlix.data.local

import android.content.Context
import android.content.SharedPreferences
import com.idlix.data.model.AuthState
import com.idlix.data.model.IdlixUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AuthSessionStore {

    private const val PREFS_NAME = "idlix_auth_session"
    private const val KEY_USER_JSON = "cached_user_json"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Guest)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs = p
                    val savedUser = loadUser(p)
                    _authState.value = if (savedUser != null) {
                        AuthState.Authenticated(savedUser)
                    } else {
                        AuthState.Guest
                    }
                }
            }
        }
    }

    fun currentUser(): IdlixUser? = (_authState.value as? AuthState.Authenticated)?.user

    fun isAuthenticated(): Boolean = _authState.value is AuthState.Authenticated

    fun setAuthenticated(user: IdlixUser) {
        _authState.value = AuthState.Authenticated(user)
        try {
            val encoded = json.encodeToString(user)
            prefs?.edit()?.putString(KEY_USER_JSON, encoded)?.apply()
        } catch (_: Exception) {
        }
    }

    fun setGuest() {
        _authState.value = AuthState.Guest
        try {
            prefs?.edit()?.remove(KEY_USER_JSON)?.apply()
        } catch (_: Exception) {
        }
    }

    private fun loadUser(p: SharedPreferences): IdlixUser? {
        val raw = p.getString(KEY_USER_JSON, null)?.takeIf { it.isNotBlank() } ?: return null
        return try {
            json.decodeFromString<IdlixUser>(raw).takeIf { it.id.isNotBlank() || it.username.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
