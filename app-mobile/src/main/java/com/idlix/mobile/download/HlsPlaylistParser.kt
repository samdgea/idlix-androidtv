package com.idlix.mobile.download

import java.net.URI

data class HlsMasterSelection(
    val videoPlaylistUrl: String,
    val audioPlaylistUrl: String? = null,
    val qualityLabel: String = "HD",
    val height: Int = 0,
    val bandwidth: Long = 0L
)

data class HlsSegmentTask(
    val index: Int,
    val remoteUrl: String,
    val localRelativePath: String,
    val isInitOrKey: Boolean = false
)

data class ParsedMediaPlaylist(
    val segments: List<HlsSegmentTask>,
    val rewrittenPlaylistContent: String
)

object HlsPlaylistParser {

    private val REGEX_BANDWIDTH = Regex("""BANDWIDTH=(\d+)""")
    private val REGEX_RESOLUTION = Regex("""RESOLUTION=(\d+)x(\d+)""")
    private val REGEX_AUDIO_GROUP = Regex("""AUDIO="([^"]+)"""")
    private val REGEX_GROUP_ID = Regex("""GROUP-ID="([^"]+)"""")
    private val REGEX_URI_ATTR = Regex("""URI="([^"]+)"""")
    private val REGEX_DEFAULT_YES = Regex("""DEFAULT=YES""", RegexOption.IGNORE_CASE)

    fun extractTokenQuery(url: String): String {
        return try {
            val rawQuery = URI(url).rawQuery.orEmpty()
            if (rawQuery.contains("t=")) rawQuery else ""
        } catch (_: Exception) {
            val qIndex = url.indexOf('?')
            if (qIndex >= 0) {
                val query = url.substring(qIndex + 1)
                if (query.contains("t=")) query else ""
            } else {
                ""
            }
        }
    }

    fun resolveUriWithToken(baseUrl: String, targetUri: String, tokenQuery: String): String {
        val trimmed = targetUri.trim()
        val resolved = try {
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                val base = URI(baseUrl)
                base.resolve(trimmed).toString()
            }
        } catch (_: Exception) {
            val baseNoQuery = baseUrl.substringBefore('?')
            val parent = baseNoQuery.substringBeforeLast('/', "")
            if (trimmed.startsWith("/")) {
                val origin = Regex("""^https?://[^/]+""").find(baseNoQuery)?.value.orEmpty()
                "$origin$trimmed"
            } else if (parent.isNotEmpty()) {
                "$parent/$trimmed"
            } else {
                trimmed
            }
        }

        return if (tokenQuery.isNotBlank() && !resolved.contains("t=")) {
            val delimiter = if (resolved.contains("?")) "&" else "?"
            "$resolved$delimiter$tokenQuery"
        } else {
            resolved
        }
    }

    fun selectBestVariant(
        masterContent: String,
        masterUrl: String,
        tokenQuery: String = extractTokenQuery(masterUrl)
    ): HlsMasterSelection {
        val lines = masterContent.lines().map { it.trim() }
        if (lines.none { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }) {
            return HlsMasterSelection(
                videoPlaylistUrl = masterUrl,
                audioPlaylistUrl = null,
                qualityLabel = "HD"
            )
        }

        data class AudioTrackInfo(val groupId: String, val uri: String, val isDefault: Boolean)
        data class VariantCandidate(
            val uri: String,
            val bandwidth: Long,
            val height: Int,
            val audioGroup: String?
        )

        val audioTracks = mutableListOf<AudioTrackInfo>()
        val variants = mutableListOf<VariantCandidate>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) &&
                line.contains("TYPE=AUDIO", ignoreCase = true)
            ) {
                val groupId = REGEX_GROUP_ID.find(line)?.groupValues?.get(1)
                val rawUri = REGEX_URI_ATTR.find(line)?.groupValues?.get(1)
                val isDefault = REGEX_DEFAULT_YES.containsMatchIn(line)
                if (!groupId.isNullOrBlank() && !rawUri.isNullOrBlank()) {
                    audioTracks.add(
                        AudioTrackInfo(
                            groupId = groupId,
                            uri = resolveUriWithToken(masterUrl, rawUri, tokenQuery),
                            isDefault = isDefault
                        )
                    )
                }
            } else if (line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) {
                val bandwidth = REGEX_BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val height = REGEX_RESOLUTION.find(line)?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val audioGroup = REGEX_AUDIO_GROUP.find(line)?.groupValues?.get(1)

                var nextIdx = i + 1
                while (nextIdx < lines.size && (lines[nextIdx].isBlank() || lines[nextIdx].startsWith("#"))) {
                    nextIdx++
                }
                if (nextIdx < lines.size) {
                    val variantUri = resolveUriWithToken(masterUrl, lines[nextIdx], tokenQuery)
                    variants.add(
                        VariantCandidate(
                            uri = variantUri,
                            bandwidth = bandwidth,
                            height = height,
                            audioGroup = audioGroup
                        )
                    )
                    i = nextIdx
                }
            }
            i++
        }

        if (variants.isEmpty()) {
            return HlsMasterSelection(
                videoPlaylistUrl = masterUrl,
                audioPlaylistUrl = null,
                qualityLabel = "HD"
            )
        }

        val best = variants.sortedWith(
            compareByDescending<VariantCandidate> { it.height }
                .thenByDescending { it.bandwidth }
        ).first()

        val matchedAudio = best.audioGroup?.let { group ->
            val groupTracks = audioTracks.filter { it.groupId == group }
            (groupTracks.firstOrNull { it.isDefault } ?: groupTracks.firstOrNull())?.uri
        }

        val label = when {
            best.height >= 1080 -> "1080p"
            best.height >= 720 -> "720p"
            best.height >= 480 -> "480p"
            best.height > 0 -> "${best.height}p"
            else -> "HD"
        }

        return HlsMasterSelection(
            videoPlaylistUrl = best.uri,
            audioPlaylistUrl = matchedAudio,
            qualityLabel = label,
            height = best.height,
            bandwidth = best.bandwidth
        )
    }

    fun parseMediaPlaylist(
        playlistContent: String,
        playlistUrl: String,
        tokenQuery: String = extractTokenQuery(playlistUrl),
        filePrefix: String = "seg"
    ): ParsedMediaPlaylist {
        val segments = mutableListOf<HlsSegmentTask>()
        val outputLines = mutableListOf<String>()

        var taskCounter = 0
        var initCounter = 0
        var keyCounter = 0
        var mediaCounter = 0
        var hasEndList = false

        for (rawLine in playlistContent.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val match = REGEX_URI_ATTR.find(line)
                    if (match != null) {
                        val rawUri = match.groupValues[1]
                        val remoteUrl = resolveUriWithToken(playlistUrl, rawUri, tokenQuery)
                        val localRel = "segments/${filePrefix}_init_${initCounter++}.mp4"
                        segments.add(
                            HlsSegmentTask(
                                index = taskCounter++,
                                remoteUrl = remoteUrl,
                                localRelativePath = localRel,
                                isInitOrKey = true
                            )
                        )
                        outputLines.add(line.replace(match.value, """URI="$localRel""""))
                    } else {
                        outputLines.add(line)
                    }
                }

                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val match = REGEX_URI_ATTR.find(line)
                    if (match != null) {
                        val rawUri = match.groupValues[1]
                        val remoteUrl = resolveUriWithToken(playlistUrl, rawUri, tokenQuery)
                        val localRel = "segments/${filePrefix}_key_${keyCounter++}.bin"
                        segments.add(
                            HlsSegmentTask(
                                index = taskCounter++,
                                remoteUrl = remoteUrl,
                                localRelativePath = localRel,
                                isInitOrKey = true
                            )
                        )
                        outputLines.add(line.replace(match.value, """URI="$localRel""""))
                    } else {
                        outputLines.add(line)
                    }
                }

                line.startsWith("#EXT-X-ENDLIST", ignoreCase = true) -> {
                    hasEndList = true
                    outputLines.add(line)
                }

                line.startsWith("#") -> {
                    outputLines.add(line)
                }

                else -> {
                    val remoteUrl = resolveUriWithToken(playlistUrl, line, tokenQuery)
                    val pathPart = remoteUrl.substringBefore('?').lowercase()
                    val ext = when {
                        pathPart.endsWith(".m4s") -> ".m4s"
                        pathPart.endsWith(".mp4") -> ".mp4"
                        pathPart.endsWith(".aac") -> ".aac"
                        else -> ".ts"
                    }
                    val localRel = String.format("segments/%s_%05d%s", filePrefix, mediaCounter++, ext)
                    segments.add(
                        HlsSegmentTask(
                            index = taskCounter++,
                            remoteUrl = remoteUrl,
                            localRelativePath = localRel,
                            isInitOrKey = false
                        )
                    )
                    outputLines.add(localRel)
                }
            }
        }

        if (!hasEndList && segments.isNotEmpty()) {
            outputLines.add("#EXT-X-ENDLIST")
        }

        return ParsedMediaPlaylist(
            segments = segments,
            rewrittenPlaylistContent = outputLines.joinToString("\n") + "\n"
        )
    }

    fun buildLocalMasterPlaylist(
        videoPlaylistFileName: String,
        audioPlaylistFileName: String,
        bandwidth: Long,
        height: Int
    ): String {
        val bw = if (bandwidth > 0L) bandwidth else 2_500_000L
        val resAttr = if (height > 0) ",RESOLUTION=${(height * 16) / 9}x$height" else ""
        return buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("""#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Default",DEFAULT=YES,AUTOSELECT=YES,URI="$audioPlaylistFileName"""")
            appendLine("""#EXT-X-STREAM-INF:BANDWIDTH=$bw$resAttr,AUDIO="audio"""")
            appendLine(videoPlaylistFileName)
        }
    }
}
