package com.idlix.mobile.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idlix.data.api.ApiClient
import com.idlix.data.repository.SubdlRepository
import com.idlix.data.repository.SubdlTestResult
import com.idlix.mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val activeBackendUrl: String = "",
    val activeUseDefaultSubdl: Boolean = true,
    val backendUrl: String = "",
    val useDefaultSubdl: Boolean = true,
    val subdlKey: String = "",
    val activeDnsProvider: String = ApiClient.DEFAULT_DNS_PROVIDER,
    val selectedDnsProvider: String = ApiClient.DEFAULT_DNS_PROVIDER,
    val isTestingBackend: Boolean = false,
    val backendTestResult: String? = null,
    val isTestingSubdl: Boolean = false,
    val subdlTestResult: String? = null,
    val isSubdlTestSuccess: Boolean? = null,
    val isTestingDns: Boolean = false,
    val dnsTestResult: String? = null,
    val cacheCleared: Boolean = false
)

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            activeBackendUrl = ApiClient.getBaseUrl(),
            activeUseDefaultSubdl = SubdlRepository.isUsingDefaultApiKey(),
            backendUrl = ApiClient.getBaseUrl(),
            useDefaultSubdl = SubdlRepository.isUsingDefaultApiKey(),
            subdlKey = SubdlRepository.getCustomApiKey(),
            activeDnsProvider = ApiClient.getDnsProvider(),
            selectedDnsProvider = ApiClient.getDnsProvider()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateBackendUrl(url: String) {
        _uiState.value = _uiState.value.copy(backendUrl = url, backendTestResult = null)
    }

    fun selectSubdlMode(useDefault: Boolean) {
        _uiState.value = _uiState.value.copy(
            useDefaultSubdl = useDefault,
            subdlTestResult = null,
            isSubdlTestSuccess = null
        )
    }

    fun updateSubdlKey(key: String) {
        _uiState.value = _uiState.value.copy(
            subdlKey = key,
            subdlTestResult = null,
            isSubdlTestSuccess = null
        )
    }

    fun resetBackendUrl() {
        val defaultUrl = ApiClient.getDefaultUrl()
        _uiState.value = _uiState.value.copy(backendUrl = defaultUrl, backendTestResult = null)
    }

    fun resetSubdlKey() {
        _uiState.value = _uiState.value.copy(
            useDefaultSubdl = true,
            subdlTestResult = null,
            isSubdlTestSuccess = null
        )
    }

    fun saveBackendUrl() {
        val app = getApplication<Application>()
        val targetUrl = _uiState.value.backendUrl.trim()
        if (targetUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(backendTestResult = app.getString(R.string.url_empty_error))
            return
        }
        ApiClient.setBaseUrl(targetUrl)
        val newActive = ApiClient.getBaseUrl()
        _uiState.value = _uiState.value.copy(
            activeBackendUrl = newActive,
            backendUrl = newActive,
            backendTestResult = app.getString(R.string.url_saved_success)
        )
    }

    fun saveSubdlKey() {
        val app = getApplication<Application>()
        val useDefault = _uiState.value.useDefaultSubdl
        val targetKey = _uiState.value.subdlKey.trim()
        if (!useDefault && targetKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                subdlTestResult = app.getString(R.string.subdl_key_empty_error),
                isSubdlTestSuccess = false
            )
            return
        }
        SubdlRepository.setSubdlConfig(useDefault = useDefault, customKey = targetKey)
        val activeUseDefault = SubdlRepository.isUsingDefaultApiKey()
        _uiState.value = _uiState.value.copy(
            activeUseDefaultSubdl = activeUseDefault,
            useDefaultSubdl = activeUseDefault,
            subdlKey = SubdlRepository.getCustomApiKey(),
            subdlTestResult = if (activeUseDefault) {
                app.getString(R.string.subdl_default_saved_success)
            } else {
                app.getString(R.string.subdl_key_saved_success)
            },
            isSubdlTestSuccess = true
        )
    }

    fun testBackend() {
        val app = getApplication<Application>()
        val targetUrl = _uiState.value.backendUrl.trim()
        if (targetUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isTestingBackend = false,
                backendTestResult = app.getString(R.string.url_empty_error)
            )
            return
        }
        if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            _uiState.value = _uiState.value.copy(
                isTestingBackend = false,
                backendTestResult = app.getString(R.string.url_invalid_protocol)
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingBackend = true, backendTestResult = null)
            val result = ApiClient.testBackendConnection(targetUrl)
            _uiState.value = _uiState.value.copy(
                isTestingBackend = false,
                backendTestResult = result.fold(
                    onSuccess = { it },
                    onFailure = { "Failed: ${it.localizedMessage ?: "Unknown network error"}" }
                )
            )
        }
    }

    fun testSubdl() {
        val app = getApplication<Application>()
        val useDefault = _uiState.value.useDefaultSubdl
        val targetKey = _uiState.value.subdlKey.trim()
        if (!useDefault && targetKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isTestingSubdl = false,
                subdlTestResult = app.getString(R.string.subdl_key_empty_error),
                isSubdlTestSuccess = false
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingSubdl = true,
                subdlTestResult = null,
                isSubdlTestSuccess = null
            )
            val result = SubdlRepository.testSubdlConnection(
                useDefault = useDefault,
                customKey = targetKey
            )
            val mapped = when (result) {
                is SubdlTestResult.Success ->
                    Pair(true, app.getString(R.string.subdl_valid))
                is SubdlTestResult.QuotaOrInvalidResponse ->
                    Pair(false, app.getString(R.string.subdl_quota_error))
                is SubdlTestResult.HttpError ->
                    Pair(false, app.getString(R.string.subdl_invalid_http, result.code))
                is SubdlTestResult.EmptyKey ->
                    Pair(false, app.getString(R.string.subdl_key_empty_error))
                is SubdlTestResult.NetworkError ->
                    Pair(false, app.getString(R.string.subdl_failed_error, result.message ?: "Unknown network error"))
            }
            _uiState.value = _uiState.value.copy(
                isTestingSubdl = false,
                subdlTestResult = mapped.second,
                isSubdlTestSuccess = mapped.first
            )
        }
    }

    fun updateDnsProvider(provider: String) {
        _uiState.value = _uiState.value.copy(selectedDnsProvider = provider, dnsTestResult = null)
    }

    fun saveDnsProvider() {
        val app = getApplication<Application>()
        val target = _uiState.value.selectedDnsProvider
        ApiClient.setDnsProvider(target)
        val active = ApiClient.getDnsProvider()
        _uiState.value = _uiState.value.copy(
            activeDnsProvider = active,
            selectedDnsProvider = active,
            dnsTestResult = app.getString(R.string.dns_saved_success)
        )
    }

    fun resetDnsProvider() {
        val defaultProvider = ApiClient.DEFAULT_DNS_PROVIDER
        _uiState.value = _uiState.value.copy(selectedDnsProvider = defaultProvider, dnsTestResult = null)
    }

    fun testDns() {
        val target = _uiState.value.selectedDnsProvider
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingDns = true, dnsTestResult = null)
            val result = ApiClient.testDns(target)
            _uiState.value = _uiState.value.copy(
                isTestingDns = false,
                dnsTestResult = result.fold(
                    onSuccess = { it },
                    onFailure = { "Failed: ${it.localizedMessage ?: "DNS resolution failed"}" }
                )
            )
        }
    }

    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val cacheDir = context.cacheDir.resolve("image_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(cacheCleared = true)
                }
            } catch (_: Exception) {}
        }
    }
}
