package org.jellyfin.androidtv.aeon

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Shared thumbnail loader for the Originals side: ONE ImageLoader with an
 * explicit on-disk cache (100 MB in cacheDir/originals_thumbnails), so
 * posters for shows & movies download once and are served from disk on
 * every later visit — even after the app restarts. archive.org item
 * thumbnails never change, so entries are kept aggressively.
 *
 * (TV port of the phone OriginalThumbnails; Coil 3 has no
 * respectCacheHeaders switch — the keyed disk cache already keeps
 * entries long-term.)
 */
object OriginalThumbnails {
    @Volatile
    private var loader: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        val existing = loader
        if (existing != null) return existing
        return synchronized(this) {
            loader ?: build(context.applicationContext).also { loader = it }
        }
    }

    private fun build(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "originals_thumbnails").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .build()

    /** A request that always reads/writes both cache layers. */
    fun request(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()

    /** Warm the cache for [urls] in the background (fire and forget). */
    fun prefetch(context: Context, urls: List<String>) {
        val imageLoader = get(context)
        val appContext = context.applicationContext
        for (url in urls.distinct()) {
            imageLoader.enqueue(request(appContext, url))
        }
    }
}
