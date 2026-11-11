package com.idlix.mobile.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.idlix.data.model.SubtitleTrack

@OptIn(UnstableApi::class)
class QueryPreservingDataSource(
    private val upstream: DataSource,
    private val tokenQuery: String
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val uriStr = uri.toString()
        val finalUri = if (tokenQuery.isNotBlank() && !uriStr.contains("t=")) {
            val delimiter = if (uriStr.contains("?")) "&" else "?"
            Uri.parse("$uriStr$delimiter$tokenQuery")
        } else {
            uri
        }
        val finalSpec = if (finalUri != uri) {
            dataSpec.buildUpon().setUri(finalUri).build()
        } else {
            dataSpec
        }
        return upstream.open(finalSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
    }
}

@OptIn(UnstableApi::class)
class QueryPreservingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val tokenQuery: String
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return QueryPreservingDataSource(upstreamFactory.createDataSource(), tokenQuery)
    }
}

@OptIn(UnstableApi::class)
object MediaSourceHelper {

    fun createHttpDataSourceFactory(tokenQuery: String): DataSource.Factory {
        val baseHttp = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://z2.idlixku.com/",
                    "Origin" to "https://z2.idlixku.com"
                )
            )
        return QueryPreservingDataSourceFactory(baseHttp, tokenQuery)
    }

    fun buildMediaSource(
        context: Context,
        masterUrl: String,
        sub: SubtitleTrack?,
        httpDataSourceFactory: DataSource.Factory
    ): MediaSource {
        val masterUri = Uri.parse(masterUrl)
        val combinedDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(masterUri)
            .setMimeType(MimeTypes.APPLICATION_M3U8)

        if (sub != null) {
            val mimeType = if (sub.path.endsWith(".srt", ignoreCase = true)) {
                MimeTypes.APPLICATION_SUBRIP
            } else {
                MimeTypes.TEXT_VTT
            }

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.path))
                .setMimeType(mimeType)
                .setLanguage(sub.lang)
                .setLabel(sub.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .build()

            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        return DefaultMediaSourceFactory(combinedDataSourceFactory)
            .createMediaSource(mediaItemBuilder.build())
    }

    fun createRenderersFactory(context: Context): DefaultRenderersFactory {
        return DefaultRenderersFactory(context)
    }
}
