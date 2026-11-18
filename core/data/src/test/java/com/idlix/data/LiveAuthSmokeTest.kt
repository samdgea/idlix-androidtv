package com.idlix.data

import com.idlix.data.api.ApiClient
import com.idlix.data.local.AuthSessionStore
import com.idlix.data.model.AuthLoginResult
import com.idlix.data.model.AuthState
import com.idlix.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveAuthSmokeTest {

    @Test
    fun testLiveAuthLifecycleAgainstZ3() = runBlocking {
        val email = System.getenv("IDLIX_TEST_EMAIL")?.trim().orEmpty()
        val password = System.getenv("IDLIX_TEST_PASSWORD").orEmpty()
        assumeTrue("Skipping live auth test without credentials", email.isNotBlank() && password.isNotBlank())

        ApiClient.cookieJar.clearAll()
        AuthSessionStore.setGuest()

        val authRepo = AuthRepository()
        val baseUrl = "https://z3.idlixku.com/".toHttpUrl()

        // 1. Login
        var loginRes = authRepo.login(email, password, replaceExistingSessions = false)
        if (loginRes is AuthLoginResult.DeviceLimitReached) {
            loginRes = authRepo.login(email, password, replaceExistingSessions = true)
        }

        assertTrue("Expected Success but got $loginRes", loginRes is AuthLoginResult.Success)
        val user = (loginRes as AuthLoginResult.Success).user
        assertTrue(user.id.isNotBlank() || user.username.isNotBlank())
        assertTrue(ApiClient.cookieJar.hasAuthCookies(baseUrl))
        assertTrue(AuthSessionStore.authState.value is AuthState.Authenticated)

        // 2. Verify /api/auth/me and plan metadata
        val meRes = authRepo.refreshUser()
        assertTrue("Expected getMe success but got ${meRes.exceptionOrNull()}", meRes.isSuccess)
        val meUser = meRes.getOrNull()!!
        assertEquals("Regular • 1080p", meUser.planDisplayLabel)

        // 3. Verify /api/auth/sessions filters active sessions and detects isCurrent
        val sessionsRes = authRepo.getSessions()
        assertTrue("Expected sessions success", sessionsRes.isSuccess)
        val activeSessions = sessionsRes.getOrNull().orEmpty()
        assertTrue("Expected at least 1 active session", activeSessions.isNotEmpty())
        assertTrue("Expected current session marked isCurrent=true", activeSessions.any { it.isCurrent })
        assertTrue("Expected only active sessions", activeSessions.all { it.status.equals("active", ignoreCase = true) })

        // 4. Verify authenticated play-info unlocks higher viewerTier / maxHeight
        val movies = ApiClient.idlixApi.getMovies(page = 1, limit = 1)
        val firstMovie = movies.data.firstOrNull()
        assertNotNull("Expected at least 1 movie from catalog", firstMovie)
        val playInfoResp = ApiClient.idlixApi.getMoviePlayInfo(firstMovie!!.id)
        assertTrue("Expected 200 play-info but got ${playInfoResp.code()}", playInfoResp.isSuccessful)
        val playInfo = playInfoResp.body()!!
        println("LIVE_PLAY_INFO viewerTier=${playInfo.viewerTier} maxHeight=${playInfo.maxHeight} hasVerification=${playInfo.verification != null}")
        assertFalse("Expected viewerTier != guest when authenticated", playInfo.viewerTier.equals("guest", ignoreCase = true))
        assertTrue("Expected maxHeight >= 1080 when authenticated, got ${playInfo.maxHeight}", playInfo.maxHeight >= 1080)

        // 5. Refresh session directly
        val refreshResp = ApiClient.idlixApi.refreshSession(bootstrap = 0)
        assertTrue("Expected refresh success, got ${refreshResp.code()}", refreshResp.isSuccessful)

        // 6. Logout cleanly so no dangling session remains
        val logoutRes = authRepo.logout(everywhere = false)
        assertTrue("Expected logout success", logoutRes.isSuccess)
        assertEquals(AuthState.Guest, AuthSessionStore.authState.value)
        assertFalse(ApiClient.cookieJar.hasAuthCookies(baseUrl))
    }
}
