package com.idlix.data.repository

import com.idlix.data.api.ApiClient
import com.idlix.data.api.IdlixApiService
import com.idlix.data.api.MajorplayApiService
import com.idlix.data.model.ApiErrorResponse
import com.idlix.data.model.ClaimRequest
import com.idlix.data.model.ClaimResponse
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.Genre
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieItem
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
import com.idlix.data.model.PlaybackVerificationChallenge
import com.idlix.data.model.RedeemRequest
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.SearchResultItem
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesItem
import com.idlix.data.model.SeriesListResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class IdlixRepository(
    private val idlixApiProvider: () -> IdlixApiService = { ApiClient.idlixApi },
    private val majorplayApiProvider: () -> MajorplayApiService = { ApiClient.majorplayApi }
) {
    constructor(
        idlixApi: IdlixApiService,
        majorplayApi: MajorplayApiService
    ) : this(
        idlixApiProvider = { idlixApi },
        majorplayApiProvider = { majorplayApi }
    )

    private val idlixApi: IdlixApiService get() = idlixApiProvider()
    private val majorplayApi: MajorplayApiService get() = majorplayApiProvider()

    suspend fun getMovies(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): MovieListResponse = withContext(Dispatchers.IO) {
        idlixApi.getMovies(page = page, limit = limit, sort = sort, genre = genre)
    }

    suspend fun getSeries(
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null,
        genre: String? = null
    ): SeriesListResponse = withContext(Dispatchers.IO) {
        idlixApi.getSeries(page = page, limit = limit, sort = sort, genre = genre)
    }

    suspend fun getTrendingMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            idlixApi.getMovies(page = 1, limit = 20, sort = "popularity").data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            idlixApi.getMovies(page = 1, limit = 20).data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMoviesBySort(sort: String): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            idlixApi.getMovies(page = 1, limit = 20, sort = sort).data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMoviesByGenre(genreSlug: String): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            idlixApi.getMovies(page = 1, limit = 20, genre = genreSlug).data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPopularSeries(): List<SeriesItem> = withContext(Dispatchers.IO) {
        try {
            idlixApi.getSeries(page = 1, limit = 20, sort = "popularity").data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSeriesBySort(sort: String): List<SeriesItem> = withContext(Dispatchers.IO) {
        try {
            idlixApi.getSeries(page = 1, limit = 20, sort = sort).data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSeriesByGenre(genreSlug: String): List<SeriesItem> = withContext(Dispatchers.IO) {
        try {
            idlixApi.getSeries(page = 1, limit = 20, genre = genreSlug).data
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMovieDetail(slug: String): MovieDetail? = withContext(Dispatchers.IO) {
        try {
            idlixApi.getMovieDetail(slug)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSeriesDetail(slug: String): SeriesDetail? = withContext(Dispatchers.IO) {
        try {
            idlixApi.getSeriesDetail(slug)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getEpisodeDetail(slug: String, season: Int, episode: Int): EpisodeResponse? = withContext(Dispatchers.IO) {
        try {
            idlixApi.getEpisodeDetail(slug, season, episode)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        try {
            val res = idlixApi.search(trimmed)
            res.results
        } catch (e: Exception) {
            android.util.Log.w("IdlixRepository", "Backend search error: ${e.message}")
            emptyList()
        }
    }

    private data class CachedPlayback(
        val playback: ResolvedPlayback,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private val playbackCache = ConcurrentHashMap<String, CachedPlayback>()
        private val activePrewarms = ConcurrentHashMap<String, Deferred<Result<ResolvedPlayback>>>()
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 60 minutes
        private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    /**
     * Pre-warms the stream resolution in the background while the user is reading
     * details or looking at synopsis. When they click "Play", the stream is already unlocked!
     */
    fun prewarmStream(contentType: String, contentId: String, contentTitle: String) {
        val cacheKey = "$contentType:$contentId"
        val cached = playbackCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return
        }

        if (activePrewarms.containsKey(cacheKey)) {
            return
        }

        val deferred = repoScope.async {
            try {
                val res = resolveStreamInternal(contentType, contentId, contentTitle, onCountdown = null)
                res.onSuccess { resolved ->
                    playbackCache[cacheKey] = CachedPlayback(resolved)
                }
                res
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                activePrewarms.remove(cacheKey)
            }
        }
        activePrewarms[cacheKey] = deferred
    }

    suspend fun resolveStream(
        contentType: String,
        contentId: String,
        contentTitle: String,
        onCountdown: ((Int) -> Unit)? = null,
        onVerificationRequired: (suspend (PlaybackVerificationChallenge) -> String?)? = null
    ): Result<ResolvedPlayback> = withContext(Dispatchers.IO) {
        val cacheKey = "$contentType:$contentId"

        // In-memory cache hit
        val cached = playbackCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            onCountdown?.invoke(0)
            return@withContext Result.success(cached.playback)
        }

        // Await background pre-warming resolution if ongoing
        val ongoing = activePrewarms[cacheKey]
        if (ongoing != null) {
            try {
                val res = ongoing.await()
                if (res.isSuccess) {
                    onCountdown?.invoke(0)
                    return@withContext res
                }
            } catch (_: Exception) {}
        }

        val res = resolveStreamInternal(
            contentType = contentType,
            contentId = contentId,
            contentTitle = contentTitle,
            onCountdown = onCountdown,
            onVerificationRequired = onVerificationRequired
        )
        res.onSuccess { resolved ->
            playbackCache[cacheKey] = CachedPlayback(resolved)
        }
        res
    }

    private suspend fun resolveStreamInternal(
        contentType: String,
        contentId: String,
        contentTitle: String,
        onCountdown: ((Int) -> Unit)? = null,
        onVerificationRequired: (suspend (PlaybackVerificationChallenge) -> String?)? = null
    ): Result<ResolvedPlayback> = withContext(Dispatchers.IO) {
        try {
            var targetType = contentType
            var targetId = contentId

            // If a TV series ID is passed instead of an episode ID, retrieve episode 1 of season 1
            if (contentType == "tv_series") {
                val series = getSeriesDetail(contentId)
                val firstEp = series?.defaultSeason?.episodes?.firstOrNull()
                    ?: series?.seasons?.firstOrNull()?.let { s ->
                        getEpisodeDetail(series.slug, s.seasonNumber, 1)?.episode
                    }

                if (firstEp != null) {
                    targetType = "episode"
                    targetId = firstEp.id
                } else {
                    return@withContext Result.failure(Exception("Tidak ada episode yang tersedia untuk serial ini."))
                }
            }

            // High-performance path: If no interactive countdown listener is needed, use backend one-shot resolver
            if (onCountdown == null) {
                try {
                    val resolved = idlixApi.resolvePlayback(
                        contentType = targetType,
                        contentId = targetId,
                        contentTitle = contentTitle
                    )
                    if (resolved.hlsMasterUrl.isNotBlank()) {
                        return@withContext Result.success(resolved)
                    }
                } catch (e: Exception) {
                    android.util.Log.d("IdlixRepository", "One-shot resolver fallback: ${e.message}")
                }
            }

            // Fetch gate token, unlock schedule, and final resolved origin (handles 301 mirror redirects)
            val playInfoResp = if (targetType == "episode") {
                idlixApi.getEpisodePlayInfo(targetId)
            } else {
                idlixApi.getMoviePlayInfo(targetId)
            }

            if (!playInfoResp.isSuccessful) {
                val err = parseApiError(playInfoResp.errorBody()?.string())
                val msg = err?.error?.ifBlank { null } ?: "Gagal memperoleh info pemutaran (HTTP ${playInfoResp.code()})"
                return@withContext Result.failure(Exception(msg))
            }

            val playInfo: PlayInfoResponse = playInfoResp.body()
                ?: return@withContext Result.failure(Exception("Gagal memperoleh token pemutaran"))

            if (playInfo.gateToken.isBlank()) {
                return@withContext Result.failure(Exception("Gagal memperoleh token pemutaran"))
            }

            val resolvedOrigin = extractOriginFromPlayInfo(playInfoResp.raw().request.url)

            // Server-synchronized countdown until gate unlocks
            val diffMs = if (playInfo.serverNow > 0 && playInfo.unlockAt > playInfo.serverNow) {
                playInfo.unlockAt - playInfo.serverNow
            } else {
                15000L
            }

            val activeDid = extractDidFromGateToken(playInfo.gateToken) ?: ApiClient.getDeviceId()
            val didCookie = "did=$activeDid"

            var secondsLeft = (diffMs / 1000).toInt()
            while (secondsLeft > 1) {
                onCountdown?.invoke(secondsLeft)
                delay(1000)
                secondsLeft--
            }
            if (secondsLeft > 0) {
                onCountdown?.invoke(1)
                delay(1000)
            }
            onCountdown?.invoke(0)

            var verificationToken: String? = null
            var verificationPrompts = 0
            val gateVerification = playInfo.verification
            if (gateVerification != null && gateVerification.siteKey.isNotBlank()) {
                if (onVerificationRequired == null) {
                    return@withContext Result.failure(Exception("Verifikasi keamanan diperlukan."))
                }
                verificationPrompts++
                verificationToken = onVerificationRequired.invoke(
                    PlaybackVerificationChallenge(
                        siteKey = gateVerification.siteKey,
                        action = gateVerification.action.ifBlank { "play" },
                        originUrl = "$resolvedOrigin/"
                    )
                )
                if (verificationToken.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Verifikasi keamanan dibatalkan."))
                }
            }

            // Poll session claim against the resolved origin so POST never hits a 301 POST->GET redirect
            val claimUrl = "$resolvedOrigin/api/watch/session/claim"
            var validClaimRes: ClaimResponse? = null
            var transientFailures = 0

            for (attempt in 1..12) {
                val claimResp = try {
                    idlixApi.claimSession(
                        url = claimUrl,
                        request = ClaimRequest(
                            gateToken = playInfo.gateToken,
                            verificationToken = verificationToken
                        ),
                        cookie = didCookie,
                        origin = resolvedOrigin,
                        referer = "$resolvedOrigin/"
                    )
                } catch (e: Exception) {
                    transientFailures++
                    if (transientFailures >= 3) {
                        return@withContext Result.failure(
                            Exception("Gagal menghubungi server klaim pemutaran: ${e.localizedMessage ?: "Network error"}")
                        )
                    }
                    delay(600)
                    continue
                }

                if (claimResp.isSuccessful) {
                    val res = claimResp.body()
                    if (res != null && res.kind == "pentos" && res.claim.isNotBlank()) {
                        validClaimRes = res
                        break
                    }
                    if (res != null && res.kind == "pending") {
                        val waitMs = (res.remainingMs + 125L).coerceIn(250L, 5000L)
                        delay(waitMs)
                        continue
                    }
                    delay(600)
                    continue
                }

                val err = parseApiError(claimResp.errorBody()?.string())
                when (err?.code) {
                    "VERIFICATION_REQUIRED" -> {
                        verificationToken = null
                        val siteKey = playInfo.verification?.siteKey?.takeIf { it.isNotBlank() }
                            ?: err.siteKey.orEmpty()
                        if (siteKey.isNotBlank() && onVerificationRequired != null && verificationPrompts < 2) {
                            verificationPrompts++
                            verificationToken = onVerificationRequired.invoke(
                                PlaybackVerificationChallenge(
                                    siteKey = siteKey,
                                    action = playInfo.verification?.action?.ifBlank { "play" } ?: "play",
                                    originUrl = "$resolvedOrigin/"
                                )
                            )
                            if (verificationToken.isNullOrBlank()) {
                                return@withContext Result.failure(Exception("Verifikasi keamanan dibatalkan."))
                            }
                            continue
                        }
                        return@withContext Result.failure(
                            Exception(err.error.ifBlank { "Verifikasi keamanan diperlukan untuk memutar video." })
                        )
                    }
                    "VERIFICATION_UNAVAILABLE" -> {
                        return@withContext Result.failure(
                            Exception(err.error.ifBlank { "Verifikasi pemutaran sedang tidak tersedia. Silakan coba lagi." })
                        )
                    }
                    "PLAYBACK_TITLE_LIMIT" -> {
                        return@withContext Result.failure(Exception(formatLimitError(err)))
                    }
                }

                val code = claimResp.code()
                if (code in 400..499 && code != 429) {
                    val msg = err?.error?.ifBlank { null } ?: "Gagal mengklaim sesi pemutaran (HTTP $code)."
                    return@withContext Result.failure(Exception(msg))
                }

                transientFailures++
                if (transientFailures >= 4) {
                    val msg = err?.error?.ifBlank { null } ?: "Gagal mengklaim sesi pemutaran (HTTP $code)."
                    return@withContext Result.failure(Exception(msg))
                }
                delay(600)
            }

            val claimRes = validClaimRes
            if (claimRes == null || claimRes.claim.isBlank()) {
                return@withContext Result.failure(Exception("Gagal mengklaim sesi pemutaran. Silakan coba lagi."))
            }

            // Redeem final HLS playback URL using the resolved origin
            val redeemCandidates = buildRedeemCandidateUrls(claimRes.redeemUrl, baseUrl = resolvedOrigin)
            var redeemRes: com.idlix.data.model.RedeemResponse? = null
            for (candidateUrl in redeemCandidates) {
                try {
                    val includeDidCookie = candidateUrl.startsWith(resolvedOrigin) || candidateUrl.contains("idlix")
                    val res = majorplayApi.redeemPlay(
                        url = candidateUrl,
                        cookie = if (includeDidCookie) didCookie else null,
                        referer = "$resolvedOrigin/",
                        origin = resolvedOrigin,
                        request = RedeemRequest(claim = claimRes.claim, mode = "browser")
                    )
                    if (res.url.isNotBlank()) {
                        redeemRes = res
                        break
                    }
                } catch (_: Exception) {
                }
            }

            if (redeemRes == null || redeemRes.url.isBlank()) {
                return@withContext Result.failure(Exception("Gagal mengambil master playlist HLS."))
            }

            val finalSubtitles = redeemRes.subtitles.toMutableList()
            val prefLang = com.idlix.data.util.LocaleHelper.getPreferredLanguage()
            val hasPrefSub = finalSubtitles.any { it.lang.equals(prefLang, ignoreCase = true) }

            // SubDL Third-party Subtitle Fallback:
            // If the content has no subtitle matching preferred device language (Indonesian or English fallback), fetch from subdl.com
            if (!hasPrefSub) {
                try {
                    val subdlSub = ApiClient.applicationContext?.let { ctx ->
                        SubdlRepository.fetchSubtitleForLanguage(
                            title = contentTitle,
                            type = if (targetType == "episode") "tv" else "movie",
                            targetLang = prefLang,
                            context = ctx
                        )
                    }
                    if (subdlSub != null) {
                        finalSubtitles.add(subdlSub)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("IdlixRepository", "SubDL fallback failed: ${e.message}")
                }
            }

            Result.success(
                ResolvedPlayback(
                    title = claimRes.title.ifBlank { contentTitle },
                    hlsMasterUrl = redeemRes.url,
                    subtitles = finalSubtitles,
                    durationSec = claimRes.durationSec,
                    videoId = claimRes.videoId
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun buildRedeemCandidateUrls(
        rawRedeemUrl: String,
        baseUrl: String = ApiClient.getBaseUrl()
    ): List<String> {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val trimmed = rawRedeemUrl.trim()
        val candidates = LinkedHashSet<String>()

        if (trimmed.isNotBlank()) {
            when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> {
                    if (trimmed.contains("idlix") &&
                        trimmed.contains("/watch/") &&
                        !trimmed.contains("/api/watch/")
                    ) {
                        candidates.add(trimmed.replaceFirst("/watch/", "/api/watch/"))
                    }
                    candidates.add(trimmed)
                }
                trimmed.startsWith("/api/") -> {
                    candidates.add("$cleanBase$trimmed")
                }
                trimmed.startsWith("api/") -> {
                    candidates.add("$cleanBase/$trimmed")
                }
                trimmed.startsWith("/") -> {
                    candidates.add("$cleanBase/api$trimmed")
                    candidates.add("$cleanBase$trimmed")
                }
                else -> {
                    candidates.add("$cleanBase/api/$trimmed")
                    candidates.add("$cleanBase/$trimmed")
                }
            }
        }

        candidates.add("$cleanBase/api/watch/session/redeem")
        candidates.add("https://e2e.majorplay.net/api/play")
        return candidates.toList()
    }

    private fun extractOriginFromPlayInfo(url: HttpUrl): String {
        val defaultPort = if (url.scheme == "https") 443 else 80
        val portSuffix = if (url.port != defaultPort) ":${url.port}" else ""
        return "${url.scheme}://${url.host}$portSuffix"
    }

    private fun extractDidFromGateToken(gateToken: String): String? {
        return try {
            val rawP0 = gateToken.split(".")[0]
            val decodedBytes = try {
                java.util.Base64.getUrlDecoder().decode(rawP0)
            } catch (_: Throwable) {
                android.util.Base64.decode(rawP0, android.util.Base64.URL_SAFE)
            }
            val p0 = String(decodedBytes, Charsets.UTF_8)
            """"owner":"d:([^"]+)"""".toRegex().find(p0)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseApiError(rawBody: String?): ApiErrorResponse? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            ApiClient.json.decodeFromString<ApiErrorResponse>(rawBody)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatLimitError(err: ApiErrorResponse): String {
        if (err.error.isNotBlank()) return err.error
        val waitSec = err.retryAfter ?: 60L
        return when (err.scope) {
            "guest" -> "Batas tontonan tamu tercapai. Coba lagi dalam ${waitSec}d."
            "network" -> "Batas pemutaran jaringan tercapai. Coba lagi dalam ${waitSec}d."
            "account_daily" -> "Batas pemutaran harian akun tercapai."
            else -> "Batas pemutaran tercapai. Silakan coba lagi nanti."
        }
    }
}
