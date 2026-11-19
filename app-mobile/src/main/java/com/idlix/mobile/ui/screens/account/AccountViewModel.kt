package com.idlix.mobile.ui.screens.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.local.AuthSessionStore
import com.idlix.data.model.AuthLoginResult
import com.idlix.data.model.AuthSessionItem
import com.idlix.data.model.AuthState
import com.idlix.data.repository.AuthRepository
import com.idlix.mobile.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceLimitPrompt(
    val limit: Int,
    val currentCount: Int
)

data class MobileAccountUiState(
    val authState: AuthState = AuthSessionStore.authState.value,
    val identifier: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSigningOut: Boolean = false,
    val isLoadingSessions: Boolean = false,
    val sessions: List<AuthSessionItem> = emptyList(),
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val deviceLimitPrompt: DeviceLimitPrompt? = null
)

class AccountViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(
        MobileAccountUiState(authState = AuthSessionStore.authState.value)
    )
    val uiState: StateFlow<MobileAccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            AuthSessionStore.authState.collect { state ->
                _uiState.value = _uiState.value.copy(authState = state)
                if (state is AuthState.Guest) {
                    _uiState.value = _uiState.value.copy(sessions = emptyList())
                }
            }
        }
    }

    fun updateIdentifier(value: String) {
        _uiState.value = _uiState.value.copy(
            identifier = value,
            errorMessage = null,
            statusMessage = null
        )
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(
            password = value,
            errorMessage = null,
            statusMessage = null
        )
    }

    fun signIn(replaceExistingSessions: Boolean = false) {
        val current = _uiState.value
        if (current.isSigningIn) return
        val app = getApplication<Application>()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSigningIn = true,
                errorMessage = null,
                statusMessage = null,
                deviceLimitPrompt = null
            )

            when (
                val result = authRepository.login(
                    identifier = current.identifier,
                    password = current.password,
                    replaceExistingSessions = replaceExistingSessions
                )
            ) {
                is AuthLoginResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSigningIn = false,
                        password = "",
                        errorMessage = null,
                        statusMessage = app.getString(R.string.account_sign_in_success, result.user.effectiveName),
                        deviceLimitPrompt = null
                    )
                    loadSessions()
                }
                is AuthLoginResult.DeviceLimitReached -> {
                    _uiState.value = _uiState.value.copy(
                        isSigningIn = false,
                        deviceLimitPrompt = DeviceLimitPrompt(
                            limit = result.limit,
                            currentCount = result.currentCount
                        )
                    )
                }
                is AuthLoginResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSigningIn = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun confirmReplaceSessions() {
        signIn(replaceExistingSessions = true)
    }

    fun dismissDeviceLimitPrompt() {
        _uiState.value = _uiState.value.copy(deviceLimitPrompt = null)
    }

    fun refreshAccount() {
        if (_uiState.value.isRefreshing) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRefreshing = true,
                errorMessage = null,
                statusMessage = null
            )
            authRepository.refreshUser().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        statusMessage = app.getString(R.string.account_refreshed_success)
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        errorMessage = err.localizedMessage ?: "Failed to refresh account"
                    )
                }
            )
        }
    }

    fun signOut(everywhere: Boolean = false) {
        if (_uiState.value.isSigningOut) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSigningOut = true,
                errorMessage = null,
                statusMessage = null
            )
            authRepository.logout(everywhere = everywhere).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSigningOut = false,
                        sessions = emptyList(),
                        statusMessage = app.getString(R.string.account_signed_out_success)
                    )
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        isSigningOut = false,
                        errorMessage = err.localizedMessage ?: "Sign out failed"
                    )
                }
            )
        }
    }

    fun loadSessions() {
        if (_uiState.value.isLoadingSessions) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSessions = true)
            authRepository.getSessions().fold(
                onSuccess = { list ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingSessions = false,
                        sessions = list
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoadingSessions = false)
                }
            )
        }
    }

    fun revokeSession(sessionHandle: String) {
        viewModelScope.launch {
            authRepository.revokeSession(sessionHandle)
                .onSuccess { loadSessions() }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage)
                }
        }
    }

    fun logoutOtherSessions() {
        viewModelScope.launch {
            authRepository.logoutOtherSessions()
                .onSuccess { loadSessions() }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage)
                }
        }
    }
}
