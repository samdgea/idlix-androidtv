package com.idlix.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
    val replaceExistingSessions: Boolean? = null
)

@Serializable
data class LogoutRequest(
    val scope: String = "local"
)

@Serializable
data class RevokeSessionRequest(
    val sessionHandle: String
)

@Serializable
data class UserSubscription(
    val tier: String? = null,
    val status: String? = null,
    val planName: String? = null,
    val expiresAt: String? = null
)

@Serializable
data class UserPlan(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val type: String = "",
    val quality: String = "",
    val maxStreams: Int? = null
)

@Serializable
data class IdlixUser(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val role: String = "user",
    val emailVerified: Boolean = false,
    val isPremium: Boolean? = null,
    val tier: String? = null,
    val planId: String? = null,
    val planExpiresAt: String? = null,
    val plan: UserPlan? = null,
    val subscription: UserSubscription? = null
) {
    val effectiveName: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "IDLIX User"

    val isSubscriberOrPremium: Boolean
        get() = isPremium == true ||
            role.equals("admin", ignoreCase = true) ||
            role.equals("moderator", ignoreCase = true) ||
            subscription?.status.equals("active", ignoreCase = true) ||
            (plan != null && plan.type.isNotBlank() && !plan.type.equals("free", ignoreCase = true)) ||
            tier.equals("premium", ignoreCase = true) ||
            tier.equals("plus", ignoreCase = true) ||
            tier.equals("subscriber", ignoreCase = true)

    val planDisplayLabel: String?
        get() {
            val planName = plan?.name?.takeIf { it.isNotBlank() } ?: return null
            val quality = plan.quality.takeIf { it.isNotBlank() }
            return if (quality != null) "$planName • $quality" else planName
        }
}

@Serializable
data class AuthMeResponse(
    val user: IdlixUser? = null
)

@Serializable
data class AuthSessionClient(
    val os: String? = null,
    val browser: String? = null,
    val surface: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val appVersionCode: String? = null,
    val browserVersion: String? = null
)

@Serializable
data class AuthSessionItem(
    val sessionHandle: String = "",
    val client: AuthSessionClient? = null,
    val status: String = "active",
    val isCurrent: Boolean = false,
    val firstSeenAt: String? = null,
    val lastRefreshedAt: String? = null,
    val expiresAt: String? = null,
    val endedAt: String? = null
) {
    val displayLabel: String
        get() {
            val parts = listOfNotNull(
                client?.surface?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
                client?.os?.takeIf { it.isNotBlank() },
                client?.browser?.takeIf { it.isNotBlank() },
                client?.appVersion?.takeIf { it.isNotBlank() }?.let { "v$it" }
            )
            return if (parts.isNotEmpty()) parts.joinToString(" • ") else "Device Session"
        }
}

@Serializable
data class AuthSessionsResponse(
    val sessions: List<AuthSessionItem> = emptyList(),
    val activeCount: Int = 0,
    val maxActiveSessions: Int = 0
)

sealed interface AuthState {
    data object Guest : AuthState
    data class Authenticated(val user: IdlixUser) : AuthState
}

sealed interface AuthLoginResult {
    data class Success(val user: IdlixUser) : AuthLoginResult
    data class DeviceLimitReached(
        val limit: Int,
        val currentCount: Int
    ) : AuthLoginResult
    data class Error(
        val message: String,
        val code: String? = null,
        val retryAfter: Long? = null
    ) : AuthLoginResult
}
