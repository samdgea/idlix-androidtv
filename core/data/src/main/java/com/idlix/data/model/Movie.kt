package com.idlix.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieListResponse(
    val data: List<MovieItem> = emptyList(),
    val pagination: Pagination? = null
)

@Serializable
data class MovieItem(
    val id: String = "",
    val title: String = "",
    val slug: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val voteAverage: String? = null,
    val viewCount: Long = 0,
    val quality: String? = null,
    val country: String? = null,
    val runtime: Int? = null,
    val originalLanguage: String? = null,
    val contentType: String = "movie",
    val genres: List<Genre> = emptyList(),
    val hasVideo: Boolean = true
) {
    val fullPosterUrl: String
        get() = when {
            posterPath.isNullOrBlank() -> ""
            posterPath.startsWith("http") -> posterPath
            else -> "https://image.tmdb.org/t/p/w342$posterPath"
        }

    val fullBackdropUrl: String
        get() = when {
            backdropPath.isNullOrBlank() -> ""
            backdropPath.startsWith("http") -> backdropPath
            else -> "https://image.tmdb.org/t/p/w1280$backdropPath"
        }

    val formattedRating: String
        get() = voteAverage?.toDoubleOrNull()?.let { String.format("%.1f", it) } ?: "N/A"
}

@Serializable
data class MovieDetail(
    val id: String = "",
    val tmdbId: Long? = null,
    val imdbId: String? = null,
    val title: String = "",
    val slug: String = "",
    val originalTitle: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val duration: Int? = null,
    val runtime: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val voteAverage: String? = null,
    val viewCount: Long = 0,
    val quality: String? = null,
    val country: String? = null,
    val genres: List<Genre> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val trailerUrl: String? = null
) {
    val fullPosterUrl: String
        get() = when {
            posterPath.isNullOrBlank() -> ""
            posterPath.startsWith("http") -> posterPath
            else -> "https://image.tmdb.org/t/p/w342$posterPath"
        }

    val fullBackdropUrl: String
        get() = when {
            backdropPath.isNullOrBlank() -> ""
            backdropPath.startsWith("http") -> backdropPath
            else -> "https://image.tmdb.org/t/p/w1280$backdropPath"
        }
}

@Serializable
data class Genre(
    val id: String = "",
    val name: String = "",
    val slug: String = ""
)

@Serializable
data class CastMember(
    val id: String = "",
    val name: String = "",
    val character: String? = null,
    val profilePath: String? = null
) {
    val fullProfileUrl: String
        get() = if (profilePath != null) "https://image.tmdb.org/t/p/w342$profilePath" else ""
}

@Serializable
data class Pagination(
    val page: Int = 1,
    val limit: Int = 20,
    val total: Long = 0,
    val totalPages: Int = 1
)

@Serializable
data class GenreListResponse(
    val data: List<Genre> = emptyList()
)
