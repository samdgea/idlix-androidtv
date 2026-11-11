package com.idlix.data.repository

import com.idlix.data.api.ApiClient
import com.idlix.data.api.IdlixApiService
import com.idlix.data.api.MajorplayApiService
import com.idlix.data.model.ClaimRequest
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.Genre
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieItem
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
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
        onCountdown: ((Int) -> Unit)? = null
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
            } catch (e: Exception) {}
        }

        val res = resolveStreamInternal(contentType, contentId, contentTitle, onCountdown)
        res.onSuccess { resolved ->
            playbackCache[cacheKey] = CachedPlayback(resolved)
        }
        res
    }

    private suspend fun resolveStreamInternal(
        contentType: String,
        contentId: String,
        contentTitle: String,
        onCountdown: ((Int) -> Unit)? = null
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

            // Fetch gate token and unlock schedule
            val playInfo: PlayInfoResponse = if (targetType == "episode") {
                idlixApi.getEpisodePlayInfo(targetId)
            } else {
                idlixApi.getMoviePlayInfo(targetId)
            }

            if (playInfo.gateToken.isBlank()) {
                return@withContext Result.failure(Exception("Gagal memperoleh token pemutaran"))
            }

            // Server-synchronized countdown until gate unlocks
            val diffMs = if (playInfo.serverNow > 0 && playInfo.unlockAt > playInfo.serverNow) {
                playInfo.unlockAt - playInfo.serverNow
            } else {
                15000L
            }

            // Resolve session device ID (fallback to ApiClient device ID if gateToken v2 does not expose owner)
            val didFromToken = try {
                val rawP0 = playInfo.gateToken.split(".")[0]
                val decodedBytes = try {
                    java.util.Base64.getUrlDecoder().decode(rawP0)
                } catch (e: Throwable) {
                    android.util.Base64.decode(rawP0, android.util.Base64.URL_SAFE)
                }
                val p0 = String(decodedBytes, Charsets.UTF_8)
                """"owner":"d:([^"]+)"""".toRegex().find(p0)?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
            val activeDid = if (!didFromToken.isNullOrBlank()) didFromToken else ApiClient.getDeviceId()
            val didCookie = "did=$activeDid"

            var secondsLeft = (diffMs / 1000).toInt()
            while (secondsLeft > 1) {
                onCountdown?.invoke(secondsLeft)
                delay(1000)
                secondsLeft--
            }
            if (secondsLeft > 0) {
                onCountdown?.invoke(1)
            }

            // Poll session claim until unlocked
            var validClaimRes: com.idlix.data.model.ClaimResponse? = null

            for (attempt in 1..15) {
                try {
                    val res = idlixApi.claimSession(
                        request = ClaimRequest(gateToken = playInfo.gateToken),
                        cookie = didCookie
                    )
                    if (res.kind == "pentos" && res.claim.isNotBlank()) {
                        validClaimRes = res
                        break
                    }
                } catch (_: Exception) {}
                delay(600)
            }

            onCountdown?.invoke(0)

            val claimRes = validClaimRes
            if (claimRes == null || claimRes.claim.isBlank()) {
                return@withContext Result.failure(Exception("Gagal mengklaim sesi pemutaran. Silakan coba lagi."))
            }

            // Redeem final HLS playback URL
            val redeemCandidates = buildRedeemCandidateUrls(claimRes.redeemUrl)
            var redeemRes: com.idlix.data.model.RedeemResponse? = null
            for (candidateUrl in redeemCandidates) {
                try {
                    val res = majorplayApi.redeemPlay(
                        url = candidateUrl,
                        cookie = didCookie,
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
}
