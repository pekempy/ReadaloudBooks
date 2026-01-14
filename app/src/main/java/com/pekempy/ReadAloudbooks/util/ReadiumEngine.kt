package com.pekempy.ReadAloudbooks.util

import android.content.Context
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.shared.publication.services.search.StringSearchService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReadiumEngine(context: Context) {
    private val app = context.applicationContext
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(app.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = app,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null
        )
    )

    suspend fun open(file: File): Try<Publication, Exception> = withContext(Dispatchers.IO) {
        try {
            val url = AbsoluteUrl(file.toURI().toString())!!
            val asset: Asset = assetRetriever.retrieve(url).getOrElse { throw Exception(it.toString()) }
            val result = publicationOpener.open(asset, allowUserInteraction = false)
            result.mapFailure { error -> Exception(error.toString()) }
        } catch (e: Exception) {
            Try.failure<Exception>(e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ReadiumEngine? = null

        fun getInstance(context: Context): ReadiumEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReadiumEngine(context).also { INSTANCE = it }
            }
        }
    }
}
