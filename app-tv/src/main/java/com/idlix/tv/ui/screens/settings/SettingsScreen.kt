package com.idlix.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.tv.BuildConfig
import com.idlix.tv.R
import com.idlix.data.api.ApiClient
import com.idlix.data.local.AuthSessionStore
import com.idlix.data.model.AuthState
import com.idlix.data.repository.SubdlRepository
import com.idlix.data.repository.SubdlTestResult
import com.idlix.data.update.AppPlatform
import com.idlix.data.update.AppUpdateManager
import com.idlix.data.update.AppUpdateState
import com.idlix.tv.ui.components.resolveTvUpdateErrorMessage
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccount: () -> Unit = {},
    onOpenSubtitlePositionEditor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    var currentActiveUrl by remember { mutableStateOf(ApiClient.getBaseUrl()) }
    var inputUrl by remember { mutableStateOf(currentActiveUrl) }
    var isUrlInputFocused by remember { mutableStateOf(false) }

    var backendTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingBackend by remember { mutableStateOf(false) }
    var isBackendTestSuccess by remember { mutableStateOf(false) }

    var currentUseDefaultSubdl by remember { mutableStateOf(SubdlRepository.isUsingDefaultApiKey()) }
    var useDefaultSubdl by remember { mutableStateOf(currentUseDefaultSubdl) }
    var inputApiKey by remember { mutableStateOf(SubdlRepository.getCustomApiKey()) }
    var isApiKeyInputFocused by remember { mutableStateOf(false) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var subdlTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingSubdl by remember { mutableStateOf(false) }
    var isSubdlTestSuccess by remember { mutableStateOf(false) }

    var currentActiveDns by remember { mutableStateOf(ApiClient.getDnsProvider()) }
    var selectedDns by remember { mutableStateOf(currentActiveDns) }
    var dnsTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingDns by remember { mutableStateOf(false) }
    var isDnsTestSuccess by remember { mutableStateOf(false) }

    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    val updateState by AppUpdateManager.state.collectAsState()
    val authState by AuthSessionStore.authState.collectAsState()

    LaunchedEffect(Unit) {
        if (updateState !is AppUpdateState.ReadyToInstall) {
            AppUpdateManager.checkForUpdate(
                context = context,
                platform = AppPlatform.TV,
                currentVersion = BuildConfig.VERSION_NAME,
                isManualCheck = false
            )
        }
    }

    fun runTestBackend(targetUrl: String) {
        scope.launch {
            isTestingBackend = true
            backendTestStatus = context.getString(R.string.backend_testing)
            saveMessage = null

            val formatted = if (targetUrl.endsWith("/")) targetUrl else "$targetUrl/"
            val testEndpoint = "${formatted}api/movies?page=1&limit=1"

            val result = withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                try {
                    val req = Request.Builder()
                        .url(testEndpoint)
                        .get()
                        .build()
                    ApiClient.okHttpClient.newCall(req).execute().use { resp ->
                        val duration = System.currentTimeMillis() - start
                        if (resp.isSuccessful) {
                            Pair(true, context.getString(R.string.backend_connected, resp.code, duration))
                        } else {
                            Pair(false, context.getString(R.string.backend_failed_http, resp.code))
                        }
                    }
                } catch (e: Exception) {
                    Pair(false, context.getString(R.string.backend_failed_error, e.localizedMessage ?: "Timeout"))
                }
            }

            isTestingBackend = false
            isBackendTestSuccess = result.first
            backendTestStatus = result.second
        }
    }

    // Test SubDL API Key
    fun runTestSubdl() {
        if (!useDefaultSubdl && inputApiKey.trim().isBlank()) {
            isSubdlTestSuccess = false
            subdlTestStatus = context.getString(R.string.subdl_key_empty_error)
            saveMessage = null
            saveErrorMessage = null
            return
        }
        scope.launch {
            isTestingSubdl = true
            subdlTestStatus = context.getString(R.string.subdl_testing)
            saveMessage = null
            saveErrorMessage = null

            val result = SubdlRepository.testSubdlConnection(
                useDefault = useDefaultSubdl,
                customKey = inputApiKey
            )

            val mapped = when (result) {
                is SubdlTestResult.Success ->
                    Pair(true, context.getString(R.string.subdl_valid))
                is SubdlTestResult.QuotaOrInvalidResponse ->
                    Pair(false, context.getString(R.string.subdl_quota_error))
                is SubdlTestResult.HttpError ->
                    Pair(false, context.getString(R.string.subdl_invalid_http, result.code))
                is SubdlTestResult.EmptyKey ->
                    Pair(false, context.getString(R.string.subdl_key_empty_error))
                is SubdlTestResult.NetworkError ->
                    Pair(false, context.getString(R.string.subdl_failed_error, result.message ?: ""))
            }

            isTestingSubdl = false
            isSubdlTestSuccess = mapped.first
            subdlTestStatus = mapped.second
        }
    }

    // Test DNS
    fun runTestDns(targetProvider: String) {
        scope.launch {
            isTestingDns = true
            dnsTestStatus = "Testing DNS resolution..."
            saveMessage = null

            val result = withContext(Dispatchers.IO) {
                ApiClient.testDns(targetProvider)
            }

            isTestingDns = false
            result.fold(
                onSuccess = {
                    isDnsTestSuccess = true
                    dnsTestStatus = it
                },
                onFailure = {
                    isDnsTestSuccess = false
                    dnsTestStatus = "Failed: ${it.localizedMessage ?: "Unknown DNS error"}"
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Page Title Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = NetflixRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.settings),
                    color = NetflixWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.settings_desc),
                    color = NetflixLightGray,
                    fontSize = 13.sp
                )
            }
        }

        // IDLIX Account Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NetflixDarkCard, RoundedCornerShape(10.dp))
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (authState is AuthState.Authenticated) Color(0xFF4CAF50) else NetflixWhite,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.account_header),
                        color = NetflixWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    when (val currentAuth = authState) {
                        is AuthState.Authenticated -> {
                            Text(
                                text = stringResource(R.string.account_signed_in_as, currentAuth.user.effectiveName),
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        is AuthState.Guest -> {
                            Text(
                                text = stringResource(R.string.account_guest_title),
                                color = NetflixLightGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onOpenAccount,
                colors = ButtonDefaults.colors(
                    containerColor = if (authState is AuthState.Authenticated) NetflixBlack else NetflixRed,
                    focusedContainerColor = NetflixWhite,
                    contentColor = NetflixWhite,
                    focusedContentColor = NetflixBlack
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                modifier = Modifier.height(38.dp)
            ) {
                Text(
                    text = if (authState is AuthState.Authenticated) {
                        stringResource(R.string.account_manage_btn)
                    } else {
                        stringResource(R.string.account_sign_in_btn)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NetflixDarkCard, RoundedCornerShape(10.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = NetflixWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.backend_api_header),
                    color = NetflixWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Current URL Info
            Text(
                text = stringResource(R.string.active_backend_url, currentActiveUrl),
                color = NetflixLightGray,
                fontSize = 12.sp
            )

            // Input Field URL
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(NetflixBlack, RoundedCornerShape(6.dp))
                    .border(
                        width = if (isUrlInputFocused) 2.dp else 1.dp,
                        color = if (isUrlInputFocused) NetflixWhite else NetflixDarkCard,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        saveMessage = null
                        backendTestStatus = null
                    },
                    textStyle = TextStyle(
                        color = NetflixWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(NetflixRed),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isUrlInputFocused = it.isFocused },
                    decorationBox = { inner ->
                        if (inputUrl.isEmpty()) {
                            Text(
                                text = "http://192.168.x.x:5376/",
                                color = NetflixLightGray,
                                fontSize = 13.sp
                            )
                        }
                        inner()
                    }
                )
            }

            // Backend Actions
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { runTestBackend(inputUrl) },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixBlack,
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixWhite,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTestingBackend) stringResource(R.string.testing) else stringResource(R.string.test_backend_connection),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        inputUrl = BuildConfig.BACKEND_API_URL
                        backendTestStatus = null
                        saveMessage = null
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixBlack.copy(alpha = 0.5f),
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(text = stringResource(R.string.reset_default_url), fontSize = 12.sp)
                }
            }

            if (backendTestStatus != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBackendTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isBackendTestSuccess) Color(0xFF4CAF50) else NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = backendTestStatus ?: "",
                        color = if (isBackendTestSuccess) Color(0xFF4CAF50) else NetflixRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NetflixDarkCard, RoundedCornerShape(10.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = NetflixWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.subdl_api_header),
                    color = NetflixWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (currentUseDefaultSubdl) {
                    stringResource(R.string.subdl_active_default)
                } else {
                    stringResource(R.string.subdl_active_custom)
                },
                color = if (currentUseDefaultSubdl) Color(0xFF4CAF50) else NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = stringResource(R.string.subdl_api_desc),
                color = NetflixLightGray,
                fontSize = 12.sp
            )

            // SubDL Source Selector: Built-in Default vs Custom API Key
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val subdlModes = listOf(
                    Pair(true, stringResource(R.string.subdl_mode_default)),
                    Pair(false, stringResource(R.string.subdl_mode_custom))
                )
                subdlModes.forEach { (isDefaultOption, label) ->
                    val isSelected = useDefaultSubdl == isDefaultOption
                    Button(
                        onClick = {
                            useDefaultSubdl = isDefaultOption
                            subdlTestStatus = null
                            saveMessage = null
                            saveErrorMessage = null
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) NetflixRed else NetflixBlack.copy(alpha = 0.5f),
                            focusedContainerColor = if (isSelected) NetflixRed else NetflixWhite,
                            contentColor = if (isSelected) NetflixWhite else NetflixLightGray,
                            focusedContentColor = if (isSelected) NetflixWhite else NetflixBlack
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (useDefaultSubdl) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NetflixBlack, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.subdl_default_hidden_info),
                        color = NetflixLightGray,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { runTestSubdl() },
                        enabled = !isTestingSubdl,
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixBlack,
                            focusedContainerColor = NetflixWhite,
                            contentColor = NetflixWhite,
                            focusedContentColor = NetflixBlack
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTestingSubdl) stringResource(R.string.testing) else stringResource(R.string.subdl_test_connection),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                // Custom API Key Input Field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(NetflixBlack, RoundedCornerShape(6.dp))
                        .border(
                            width = if (isApiKeyInputFocused) 2.dp else 1.dp,
                            color = if (isApiKeyInputFocused) NetflixWhite else NetflixDarkCard,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (isApiKeyInputFocused) NetflixWhite else NetflixLightGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = inputApiKey,
                        onValueChange = {
                            inputApiKey = it
                            saveMessage = null
                            saveErrorMessage = null
                            subdlTestStatus = null
                        },
                        textStyle = TextStyle(
                            color = NetflixWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(NetflixRed),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation('*'),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isApiKeyInputFocused = it.isFocused },
                        decorationBox = { inner ->
                            if (inputApiKey.isEmpty()) {
                                Text(
                                    text = "subdl__your_api_key_here",
                                    color = NetflixLightGray,
                                    fontSize = 13.sp
                                )
                            }
                            inner()
                        }
                    )
                }

                // Custom SubDL Actions
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { runTestSubdl() },
                        enabled = !isTestingSubdl,
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixBlack,
                            focusedContainerColor = NetflixWhite,
                            contentColor = NetflixWhite,
                            focusedContentColor = NetflixBlack
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTestingSubdl) stringResource(R.string.testing) else stringResource(R.string.test_subdl_key),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Button(
                        onClick = { isApiKeyVisible = !isApiKeyVisible },
                        colors = ButtonDefaults.colors(
                            containerColor = NetflixBlack.copy(alpha = 0.5f),
                            focusedContainerColor = NetflixWhite,
                            contentColor = NetflixLightGray,
                            focusedContentColor = NetflixBlack
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isApiKeyVisible) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (subdlTestStatus != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSubdlTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSubdlTestSuccess) Color(0xFF4CAF50) else NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = subdlTestStatus ?: "",
                        color = if (isSubdlTestSuccess) Color(0xFF4CAF50) else NetflixRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        PlayerAppearanceSection(
            onOpenPositionEditor = onOpenSubtitlePositionEditor
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NetflixDarkCard, RoundedCornerShape(8.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = NetflixWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.doh_header),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NetflixWhite
                    )
                    Text(
                        text = stringResource(R.string.doh_desc),
                        color = NetflixLightGray,
                        fontSize = 12.sp
                    )
                }
            }

            val dnsOptions = listOf(
                Pair(ApiClient.DNS_PROVIDER_GOOGLE, stringResource(R.string.dns_google)),
                Pair(ApiClient.DNS_PROVIDER_CLOUDFLARE, stringResource(R.string.dns_cloudflare)),
                Pair(ApiClient.DNS_PROVIDER_ADGUARD, stringResource(R.string.dns_adguard)),
                Pair(ApiClient.DNS_PROVIDER_SYSTEM, stringResource(R.string.dns_system))
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                dnsOptions.forEach { (id, label) ->
                    val isSelected = selectedDns == id
                    Button(
                        onClick = {
                            selectedDns = id
                            dnsTestStatus = null
                            saveMessage = null
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) NetflixRed else NetflixBlack.copy(alpha = 0.5f),
                            focusedContainerColor = if (isSelected) NetflixRed else NetflixWhite,
                            contentColor = if (isSelected) NetflixWhite else NetflixLightGray,
                            focusedContentColor = if (isSelected) NetflixWhite else NetflixBlack
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Button(
                    onClick = { runTestDns(selectedDns) },
                    enabled = !isTestingDns,
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixBlack,
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixWhite,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(
                        text = if (isTestingDns) stringResource(R.string.testing_dns) else stringResource(R.string.test_dns),
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        selectedDns = ApiClient.DEFAULT_DNS_PROVIDER
                        dnsTestStatus = null
                        saveMessage = null
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = NetflixBlack.copy(alpha = 0.5f),
                        focusedContainerColor = NetflixWhite,
                        contentColor = NetflixLightGray,
                        focusedContentColor = NetflixBlack
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(text = stringResource(R.string.reset_default_dns), fontSize = 12.sp)
                }
            }

            if (dnsTestStatus != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDnsTestSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isDnsTestSuccess) Color(0xFF4CAF50) else NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dnsTestStatus ?: "",
                        color = if (isDnsTestSuccess) Color(0xFF4CAF50) else NetflixRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // App Update Card (l10n)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NetflixDarkCard, RoundedCornerShape(10.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdateAlt,
                    contentDescription = null,
                    tint = NetflixWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.update_section_header),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NetflixWhite
                    )
                    Text(
                        text = stringResource(R.string.update_section_desc),
                        color = NetflixLightGray,
                        fontSize = 12.sp
                    )
                }
            }

            Text(
                text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                color = NetflixLightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            when (val currentUpdateState = updateState) {
                is AppUpdateState.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.update_downloading_progress,
                                    currentUpdateState.latestVersion,
                                    currentUpdateState.progressPercent
                                ),
                                color = NetflixWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (currentUpdateState.totalBytes > 0L) {
                                val downloadedMb = currentUpdateState.downloadedBytes.toFloat() / (1024f * 1024f)
                                val totalMb = currentUpdateState.totalBytes.toFloat() / (1024f * 1024f)
                                Text(
                                    text = stringResource(R.string.update_downloading_bytes, downloadedMb, totalMb),
                                    color = NetflixLightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(NetflixBlack, RoundedCornerShape(4.dp))
                        ) {
                            val fraction = (currentUpdateState.progressPercent / 100f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(8.dp)
                                    .background(NetflixRed, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                is AppUpdateState.ReadyToInstall -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_ready_status, currentUpdateState.latestVersion),
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                AppUpdateManager.installApk(context, currentUpdateState.apkFile)
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixRed,
                                focusedContainerColor = NetflixWhite,
                                contentColor = NetflixWhite,
                                focusedContentColor = NetflixBlack
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdateAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.update_install_btn, currentUpdateState.latestVersion),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                AppUpdateManager.checkForUpdate(
                                    context = context,
                                    platform = AppPlatform.TV,
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    isManualCheck = true
                                )
                            },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixBlack.copy(alpha = 0.5f),
                                focusedContainerColor = NetflixWhite,
                                contentColor = NetflixLightGray,
                                focusedContentColor = NetflixBlack
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_recheck_btn),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                else -> {
                    val isChecking = currentUpdateState is AppUpdateState.Checking

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                AppUpdateManager.checkForUpdate(
                                    context = context,
                                    platform = AppPlatform.TV,
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    isManualCheck = true
                                )
                            },
                            enabled = !isChecking,
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixBlack,
                                focusedContainerColor = NetflixWhite,
                                contentColor = NetflixWhite,
                                focusedContentColor = NetflixBlack
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isChecking) {
                                        stringResource(R.string.update_checking)
                                    } else {
                                        stringResource(R.string.update_check_btn)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (currentUpdateState is AppUpdateState.UpToDate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.update_up_to_date, currentUpdateState.currentVersion),
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (currentUpdateState is AppUpdateState.Error) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = NetflixRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = resolveTvUpdateErrorMessage(currentUpdateState),
                                color = NetflixRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        ) {
            Button(
                onClick = {
                    if (!useDefaultSubdl && inputApiKey.trim().isBlank()) {
                        val err = context.getString(R.string.subdl_key_empty_error)
                        isSubdlTestSuccess = false
                        subdlTestStatus = err
                        saveMessage = null
                        saveErrorMessage = err
                        return@Button
                    }

                    // Save Backend URL
                    val formattedUrl = if (inputUrl.endsWith("/")) inputUrl else "$inputUrl/"
                    ApiClient.setBaseUrl(formattedUrl)
                    currentActiveUrl = ApiClient.getBaseUrl()
                    inputUrl = currentActiveUrl

                    // Save SubDL Key Mode & Custom Key
                    SubdlRepository.setSubdlConfig(
                        useDefault = useDefaultSubdl,
                        customKey = inputApiKey
                    )
                    currentUseDefaultSubdl = SubdlRepository.isUsingDefaultApiKey()
                    useDefaultSubdl = currentUseDefaultSubdl
                    inputApiKey = SubdlRepository.getCustomApiKey()

                    // Save DNS Provider
                    ApiClient.setDnsProvider(selectedDns)
                    currentActiveDns = ApiClient.getDnsProvider()
                    selectedDns = currentActiveDns

                    saveErrorMessage = null
                    saveMessage = context.getString(R.string.settings_saved_success)
                },
                colors = ButtonDefaults.colors(
                    containerColor = NetflixRed,
                    focusedContainerColor = Color(0xFFE50914).copy(alpha = 0.8f),
                    contentColor = NetflixWhite,
                    focusedContentColor = NetflixWhite
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                modifier = Modifier.height(46.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.save_all_settings),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            if (saveMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = saveMessage ?: "",
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (saveErrorMessage != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = saveErrorMessage ?: "",
                        color = NetflixRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
