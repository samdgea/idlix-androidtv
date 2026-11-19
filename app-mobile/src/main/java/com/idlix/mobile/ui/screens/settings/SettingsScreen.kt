package com.idlix.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.idlix.data.api.ApiClient
import com.idlix.data.local.AuthSessionStore
import com.idlix.data.model.AuthState
import com.idlix.data.update.AppPlatform
import com.idlix.data.update.AppUpdateManager
import com.idlix.data.update.AppUpdateState
import com.idlix.mobile.BuildConfig
import com.idlix.mobile.R
import com.idlix.mobile.ui.components.resolveMobileUpdateErrorMessage
import com.idlix.mobile.ui.theme.BackgroundDark
import com.idlix.mobile.ui.theme.IdlixAccentCyan
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.SurfaceDark
import com.idlix.mobile.ui.theme.SurfaceVariantDark
import com.idlix.mobile.ui.theme.TextDim
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenSubtitlePositionEditor: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val updateState by AppUpdateManager.state.collectAsState()
    val authState by AuthSessionStore.authState.collectAsState()
    var isSubdlKeyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = TextWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings),
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // IDLIX Account Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            tint = if (authState is AuthState.Authenticated) Color(0xFF4CAF50) else IdlixRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.account_header),
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            when (val currentAuth = authState) {
                                is AuthState.Authenticated -> {
                                    Text(
                                        text = stringResource(
                                            R.string.account_signed_in_as,
                                            currentAuth.user.effectiveName
                                        ),
                                        color = Color(0xFF4CAF50),
                                        fontSize = 12.sp
                                    )
                                }
                                is AuthState.Guest -> {
                                    Text(
                                        text = stringResource(R.string.account_guest_title),
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onOpenAccount,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (authState is AuthState.Authenticated) SurfaceVariantDark else IdlixRed
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (authState is AuthState.Authenticated) {
                                stringResource(R.string.account_manage_btn)
                            } else {
                                stringResource(R.string.account_sign_in_btn)
                            },
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Backend API Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backend_api_header),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.backend_api_desc),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    if (uiState.activeBackendUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.active_backend_url, uiState.activeBackendUrl),
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.backendUrl,
                        onValueChange = { viewModel.updateBackendUrl(it) },
                        label = { Text(stringResource(R.string.server_url_label), color = TextDim) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceVariantDark,
                            unfocusedContainerColor = SurfaceVariantDark,
                            focusedBorderColor = IdlixRed,
                            unfocusedBorderColor = SurfaceVariantDark,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.backendTestResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.backendTestResult ?: "",
                            color = if (uiState.backendTestResult!!.contains("success", ignoreCase = true) || uiState.backendTestResult!!.contains("valid", ignoreCase = true)) Color(0xFF4CAF50) else IdlixRed,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testBackend() },
                            enabled = !uiState.isTestingBackend,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (uiState.isTestingBackend) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IdlixRed)
                            } else {
                                Text(stringResource(R.string.test_connection), color = TextWhite, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { viewModel.saveBackendUrl() },
                            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.save_url), maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { viewModel.resetBackendUrl() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = TextDim
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.reset_to_default), color = TextDim, fontSize = 12.sp)
                    }
                }
            }

            // SubDL API Key Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.subdl_api_header),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.subdl_api_desc),
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.subdlKey,
                        onValueChange = { viewModel.updateSubdlKey(it) },
                        label = { Text(stringResource(R.string.api_key_label), color = TextDim) },
                        singleLine = true,
                        visualTransformation = if (isSubdlKeyVisible) VisualTransformation.None else PasswordVisualTransformation('*'),
                        trailingIcon = {
                            IconButton(onClick = { isSubdlKeyVisible = !isSubdlKeyVisible }) {
                                Icon(
                                    imageVector = if (isSubdlKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isSubdlKeyVisible) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key),
                                    tint = TextDim
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceVariantDark,
                            unfocusedContainerColor = SurfaceVariantDark,
                            focusedBorderColor = IdlixRed,
                            unfocusedBorderColor = SurfaceVariantDark,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.subdlTestResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.subdlTestResult ?: "",
                            color = if (uiState.subdlTestResult!!.contains("valid", ignoreCase = true) || uiState.subdlTestResult!!.contains("success", ignoreCase = true)) Color(0xFF4CAF50) else IdlixRed,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testSubdl() },
                            enabled = !uiState.isTestingSubdl,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (uiState.isTestingSubdl) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IdlixRed)
                            } else {
                                Text(stringResource(R.string.test_key), color = TextWhite, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { viewModel.saveSubdlKey() },
                            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.save_key), maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { viewModel.resetSubdlKey() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = TextDim
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.reset_to_default), color = TextDim, fontSize = 12.sp)
                    }
                }
            }

            // Player & Subtitle Appearance Card
            PlayerAppearanceCard(
                onOpenPositionEditor = onOpenSubtitlePositionEditor
            )

            // DNS over HTTPS (DoH) Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.doh_header),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.doh_desc),
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val dnsProviders = listOf(
                        Triple(ApiClient.DNS_PROVIDER_GOOGLE, stringResource(R.string.dns_google), stringResource(R.string.dns_google_desc)),
                        Triple(ApiClient.DNS_PROVIDER_CLOUDFLARE, stringResource(R.string.dns_cloudflare), stringResource(R.string.dns_cloudflare_desc)),
                        Triple(ApiClient.DNS_PROVIDER_ADGUARD, stringResource(R.string.dns_adguard), stringResource(R.string.dns_adguard_desc)),
                        Triple(ApiClient.DNS_PROVIDER_SYSTEM, stringResource(R.string.dns_system), stringResource(R.string.dns_system_desc))
                    )

                    dnsProviders.forEach { (id, title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateDnsProvider(id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedDnsProvider == id,
                                onClick = { viewModel.updateDnsProvider(id) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = IdlixRed,
                                    unselectedColor = TextDim
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    color = if (uiState.selectedDnsProvider == id) TextWhite else TextMuted,
                                    fontSize = 14.sp,
                                    fontWeight = if (uiState.selectedDnsProvider == id) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = desc,
                                    color = TextDim,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    if (uiState.dnsTestResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.dnsTestResult ?: "",
                            color = if (uiState.dnsTestResult!!.contains("resolved", ignoreCase = true) || uiState.dnsTestResult!!.contains("success", ignoreCase = true)) Color(0xFF4CAF50) else IdlixRed,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testDns() },
                            enabled = !uiState.isTestingDns,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            if (uiState.isTestingDns) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IdlixRed)
                            } else {
                                Text(if (uiState.isTestingDns) stringResource(R.string.testing_dns) else stringResource(R.string.test_dns), color = TextWhite, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { viewModel.saveDnsProvider() },
                            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.save_dns), maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = { viewModel.resetDnsProvider() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = TextDim
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.reset_to_default), color = TextDim, fontSize = 12.sp)
                    }
                }
            }

            // Storage & Cache
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.storage_cache_header),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.storage_cache_desc),
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.clearImageCache() },
                        enabled = !uiState.cacheCleared,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.cacheCleared) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.cache_cleared), color = Color(0xFF4CAF50))
                        } else {
                            Text(stringResource(R.string.clear_image_cache), color = TextWhite)
                        }
                    }
                }
            }

            // App Update Card (l10n)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = IdlixRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.update_section_header),
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.update_section_desc),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                        color = TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                                        color = TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (currentUpdateState.totalBytes > 0L) {
                                        val downloadedMb = currentUpdateState.downloadedBytes.toFloat() / (1024f * 1024f)
                                        val totalMb = currentUpdateState.totalBytes.toFloat() / (1024f * 1024f)
                                        Text(
                                            text = stringResource(R.string.update_downloading_bytes, downloadedMb, totalMb),
                                            color = TextDim,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { (currentUpdateState.progressPercent / 100f).coerceIn(0f, 1f) },
                                    color = IdlixRed,
                                    trackColor = SurfaceVariantDark,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                )
                            }
                        }

                        is AppUpdateState.ReadyToInstall -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.update_ready_status, currentUpdateState.latestVersion),
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        AppUpdateManager.checkForUpdate(
                                            context = context,
                                            platform = AppPlatform.MOBILE,
                                            currentVersion = BuildConfig.VERSION_NAME,
                                            isManualCheck = true
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_recheck_btn),
                                        color = TextWhite,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = {
                                        AppUpdateManager.installApk(context, currentUpdateState.apkFile)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_install_btn, currentUpdateState.latestVersion),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        else -> {
                            val isChecking = currentUpdateState is AppUpdateState.Checking

                            if (currentUpdateState is AppUpdateState.UpToDate) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.update_up_to_date, currentUpdateState.currentVersion),
                                        color = Color(0xFF4CAF50),
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            } else if (currentUpdateState is AppUpdateState.Error) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = IdlixRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = resolveMobileUpdateErrorMessage(currentUpdateState),
                                        color = IdlixRed,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Button(
                                onClick = {
                                    AppUpdateManager.checkForUpdate(
                                        context = context,
                                        platform = AppPlatform.MOBILE,
                                        currentVersion = BuildConfig.VERSION_NAME,
                                        isManualCheck = true
                                    )
                                },
                                enabled = !isChecking,
                                colors = ButtonDefaults.buttonColors(containerColor = IdlixRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = TextWhite,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.update_checking))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.update_check_btn))
                                }
                            }
                        }
                    }
                }
            }

            // About Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_idlix_header),
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_idlix_version_format, BuildConfig.VERSION_NAME),
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
