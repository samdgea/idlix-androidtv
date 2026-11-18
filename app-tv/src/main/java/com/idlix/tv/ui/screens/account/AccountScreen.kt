package com.idlix.tv.ui.screens.account

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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.idlix.data.model.AuthState
import com.idlix.tv.R
import com.idlix.tv.ui.theme.NetflixBlack
import com.idlix.tv.ui.theme.NetflixDarkCard
import com.idlix.tv.ui.theme.NetflixLightGray
import com.idlix.tv.ui.theme.NetflixRed
import com.idlix.tv.ui.theme.NetflixWhite

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AccountScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmDialogFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.authState) {
        if (uiState.authState is AuthState.Authenticated && uiState.sessions.isEmpty()) {
            viewModel.loadSessions()
        }
    }

    LaunchedEffect(uiState.deviceLimitPrompt) {
        if (uiState.deviceLimitPrompt != null) {
            try {
                confirmDialogFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NetflixBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 36.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.account_header),
                        color = NetflixWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (val auth = uiState.authState) {
                            is AuthState.Authenticated -> stringResource(
                                R.string.account_signed_in_as,
                                auth.user.effectiveName
                            )
                            is AuthState.Guest -> stringResource(R.string.account_sign_in_subtitle)
                        },
                        color = NetflixLightGray,
                        fontSize = 13.sp
                    )
                }
            }

            if (uiState.statusMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1B3A20), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.statusMessage ?: "",
                        color = NetflixWhite,
                        fontSize = 13.sp
                    )
                }
            }

            if (uiState.errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3E1418), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = NetflixRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = NetflixWhite,
                        fontSize = 13.sp
                    )
                }
            }

            when (val auth = uiState.authState) {
                is AuthState.Guest -> {
                    var isIdentifierFocused by remember { mutableStateOf(false) }
                    var isPasswordFocused by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NetflixDarkCard, RoundedCornerShape(10.dp))
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.account_sign_in_title),
                            color = NetflixWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.account_guest_desc),
                            color = NetflixLightGray,
                            fontSize = 13.sp
                        )

                        // Identifier Input
                        Text(
                            text = stringResource(R.string.account_identifier_label),
                            color = NetflixLightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(NetflixBlack, RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isIdentifierFocused) 2.dp else 1.dp,
                                    color = if (isIdentifierFocused) NetflixWhite else NetflixDarkCard,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isIdentifierFocused) NetflixWhite else NetflixLightGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = uiState.identifier,
                                onValueChange = { viewModel.updateIdentifier(it) },
                                textStyle = TextStyle(
                                    color = NetflixWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(NetflixRed),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = {
                                        try {
                                            passwordFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isIdentifierFocused = it.isFocused },
                                decorationBox = { inner ->
                                    if (uiState.identifier.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.account_identifier_hint),
                                            color = NetflixLightGray,
                                            fontSize = 13.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                        }

                        // Password Input
                        Text(
                            text = stringResource(R.string.account_password_label),
                            color = NetflixLightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(NetflixBlack, RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isPasswordFocused) 2.dp else 1.dp,
                                    color = if (isPasswordFocused) NetflixWhite else NetflixDarkCard,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isPasswordFocused) NetflixWhite else NetflixLightGray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            BasicTextField(
                                value = uiState.password,
                                onValueChange = { viewModel.updatePassword(it) },
                                textStyle = TextStyle(
                                    color = NetflixWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(NetflixRed),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        viewModel.signIn()
                                    }
                                ),
                                singleLine = true,
                                visualTransformation = if (uiState.isPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation('*')
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(passwordFocusRequester)
                                    .onFocusChanged { isPasswordFocused = it.isFocused },
                                decorationBox = { inner ->
                                    if (uiState.password.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.account_password_hint),
                                            color = NetflixLightGray,
                                            fontSize = 13.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.signIn()
                                },
                                enabled = !uiState.isSigningIn,
                                colors = ButtonDefaults.colors(
                                    containerColor = NetflixRed,
                                    focusedContainerColor = Color(0xFFFF1F2B),
                                    contentColor = NetflixWhite,
                                    focusedContentColor = NetflixWhite
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Text(
                                    text = if (uiState.isSigningIn) {
                                        stringResource(R.string.account_signing_in)
                                    } else {
                                        stringResource(R.string.account_sign_in_btn)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = { viewModel.togglePasswordVisibility() },
                                colors = ButtonDefaults.colors(
                                    containerColor = NetflixBlack.copy(alpha = 0.6f),
                                    focusedContainerColor = NetflixWhite,
                                    contentColor = NetflixLightGray,
                                    focusedContentColor = NetflixBlack
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (uiState.isPasswordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.isPasswordVisible) {
                                            stringResource(R.string.account_hide_password)
                                        } else {
                                            stringResource(R.string.account_show_password)
                                        },
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                is AuthState.Authenticated -> {
                    val user = auth.user
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NetflixDarkCard, RoundedCornerShape(10.dp))
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(NetflixRed, RoundedCornerShape(26.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.effectiveName.take(1).uppercase(),
                                    color = NetflixWhite,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = user.effectiveName,
                                    color = NetflixWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!user.email.isNullOrBlank()) {
                                    Text(
                                        text = user.email.orEmpty(),
                                        color = NetflixLightGray,
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    text = user.planDisplayLabel ?: if (user.isSubscriberOrPremium) {
                                        stringResource(R.string.account_tier_premium)
                                    } else {
                                        stringResource(R.string.account_tier_free)
                                    },
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Button(
                                onClick = { viewModel.refreshAccount() },
                                enabled = !uiState.isRefreshing,
                                colors = ButtonDefaults.colors(
                                    containerColor = NetflixBlack,
                                    focusedContainerColor = NetflixWhite,
                                    contentColor = NetflixWhite,
                                    focusedContentColor = NetflixBlack
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.account_refresh_btn),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.signOut(everywhere = false) },
                                enabled = !uiState.isSigningOut,
                                colors = ButtonDefaults.colors(
                                    containerColor = NetflixRed,
                                    focusedContainerColor = Color(0xFFFF1F2B),
                                    contentColor = NetflixWhite,
                                    focusedContentColor = NetflixWhite
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.account_sign_out),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.signOut(everywhere = true) },
                                enabled = !uiState.isSigningOut,
                                colors = ButtonDefaults.colors(
                                    containerColor = NetflixBlack.copy(alpha = 0.6f),
                                    focusedContainerColor = NetflixWhite,
                                    contentColor = NetflixLightGray,
                                    focusedContentColor = NetflixBlack
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.account_sign_out_everywhere),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Active Sessions Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NetflixDarkCard, RoundedCornerShape(10.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = NetflixWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.account_active_sessions),
                                    color = NetflixWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.loadSessions() },
                                    enabled = !uiState.isLoadingSessions,
                                    colors = ButtonDefaults.colors(
                                        containerColor = NetflixBlack,
                                        focusedContainerColor = NetflixWhite,
                                        contentColor = NetflixWhite,
                                        focusedContentColor = NetflixBlack
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.account_load_sessions),
                                        fontSize = 12.sp
                                    )
                                }

                                if (uiState.sessions.size > 1) {
                                    Button(
                                        onClick = { viewModel.logoutOtherSessions() },
                                        colors = ButtonDefaults.colors(
                                            containerColor = NetflixBlack.copy(alpha = 0.6f),
                                            focusedContainerColor = NetflixWhite,
                                            contentColor = NetflixLightGray,
                                            focusedContentColor = NetflixBlack
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.account_logout_others),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        uiState.sessions.forEach { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NetflixBlack, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = session.displayLabel,
                                        color = NetflixWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (session.isCurrent) {
                                        Text(
                                            text = stringResource(R.string.account_current_device),
                                            color = Color(0xFF4CAF50),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (!session.isCurrent && session.sessionHandle.isNotBlank()) {
                                    Button(
                                        onClick = { viewModel.revokeSession(session.sessionHandle) },
                                        colors = ButtonDefaults.colors(
                                            containerColor = NetflixDarkCard,
                                            focusedContainerColor = NetflixRed,
                                            contentColor = NetflixWhite,
                                            focusedContentColor = NetflixWhite
                                        ),
                                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.account_revoke_session),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Device Limit Confirmation Overlay (D-Pad friendly)
        val limitPrompt = uiState.deviceLimitPrompt
        if (limitPrompt != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(460.dp)
                        .background(NetflixDarkCard, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account_device_limit_title),
                        color = NetflixWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.account_device_limit_desc,
                            limitPrompt.currentCount,
                            limitPrompt.limit
                        ),
                        color = NetflixLightGray,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Button(
                            onClick = { viewModel.confirmReplaceSessions() },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixRed,
                                focusedContainerColor = Color(0xFFFF1F2B),
                                contentColor = NetflixWhite,
                                focusedContentColor = NetflixWhite
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier
                                .height(40.dp)
                                .focusRequester(confirmDialogFocusRequester)
                        ) {
                            Text(
                                text = stringResource(R.string.account_device_limit_confirm),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.dismissDeviceLimitPrompt() },
                            colors = ButtonDefaults.colors(
                                containerColor = NetflixBlack,
                                focusedContainerColor = NetflixWhite,
                                contentColor = NetflixWhite,
                                focusedContentColor = NetflixBlack
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
