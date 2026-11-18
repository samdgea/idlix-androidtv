package com.idlix.data.api

import com.idlix.data.model.AuthMeResponse
import com.idlix.data.model.AuthSessionsResponse
import com.idlix.data.model.ClaimRequest
import com.idlix.data.model.ClaimResponse
import com.idlix.data.model.EpisodeResponse
import com.idlix.data.model.GenreListResponse
import com.idlix.data.model.LoginRequest
import com.idlix.data.model.LogoutRequest
import com.idlix.data.model.MovieDetail
import com.idlix.data.model.MovieListResponse
import com.idlix.data.model.PlayInfoResponse
import com.idlix.data.model.ResolvedPlayback
import com.idlix.data.model.RevokeSessionRequest
import com.idlix.data.model.SearchResponse
import com.idlix.data.model.SeriesDetail
import com.idlix.data.model.SeriesListResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface IdlixApiService {

    @GET("api/movies")
    suspend fun getMovies(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("genre") genre: String? = null
    ): MovieListResponse

    @GET("api/series")
    suspend fun getSeries(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("genre") genre: String? = null
    ): SeriesListResponse

    @GET("api/movies/{slug}")
    suspend fun getMovieDetail(
        @Path("slug") slug: String
    ): MovieDetail

    @GET("api/series/{slug}")
    suspend fun getSeriesDetail(
        @Path("slug") slug: String
    ): SeriesDetail

    @GET("api/series/{slug}/season/{season}/episode/{episode}")
    suspend fun getEpisodeDetail(
        @Path("slug") slug: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int
    ): EpisodeResponse

    @GET("api/genres")
    suspend fun getGenres(): GenreListResponse

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String
    ): SearchResponse

    @GET("api/watch/play-info/movie/{id}")
    suspend fun getMoviePlayInfo(
        @Path("id") movieId: String
    ): Response<PlayInfoResponse>

    @GET("api/watch/play-info/episode/{id}")
    suspend fun getEpisodePlayInfo(
        @Path("id") episodeId: String
    ): Response<PlayInfoResponse>

    @POST
    suspend fun claimSession(
        @Url url: String,
        @Body request: ClaimRequest,
        @Header("Origin") origin: String,
        @Header("Referer") referer: String
    ): Response<ClaimResponse>

    @GET("api/playback/resolve")
    suspend fun resolvePlayback(
        @Query("contentType") contentType: String,
        @Query("contentId") contentId: String,
        @Query("contentTitle") contentTitle: String? = null
    ): ResolvedPlayback

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest,
        @Header("X-Client-Fp") fingerprint: String
    ): Response<AuthMeResponse>

    @GET("api/auth/me")
    suspend fun getMe(): Response<AuthMeResponse>

    @POST("api/auth/refresh")
    suspend fun refreshSession(
        @Query("bootstrap") bootstrap: Int = 0
    ): Response<AuthMeResponse>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest = LogoutRequest()
    ): Response<Unit>

    @GET("api/auth/sessions")
    suspend fun getSessions(): Response<AuthSessionsResponse>

    @POST("api/auth/sessions/revoke")
    suspend fun revokeSession(
        @Body request: RevokeSessionRequest
    ): Response<Unit>

    @POST("api/auth/sessions/logout-others")
    suspend fun logoutOtherSessions(): Response<Unit>
}
