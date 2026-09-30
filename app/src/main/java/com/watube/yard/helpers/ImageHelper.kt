package com.watube.yard.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.storage.StorageManager
import android.util.Log
import android.widget.ImageView
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.imageLoader
import coil3.load
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.watube.yard.BuildConfig
import com.watube.yard.api.RetrofitInstance
import com.watube.yard.extensions.TAG
import com.watube.yard.extensions.toAndroidUri
import com.watube.yard.util.DataSaverMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.nio.file.Path

object ImageHelper {
    private const val HTTP_SCHEME = "http"

    /** Images may only take half of the app cache quota, the rest is for http and temp files. */
    private const val DISK_CACHE_FRACTION = 0.5
    private const val MIN_DISK_CACHE_BYTES = 32L * 1024 * 1024

    private val Context.coilFile get() = cacheDir.resolve("coil")

    /**
     * Builds the one and only [ImageLoader] of the app.
     *
     * It is registered as Coil's singleton from [WatubeApp], which means every
     * `ImageView.load(...)` call, the data saver lookups and the manual cache clearing
     * all share the very same memory and disk caches as well as one shared OkHttp client.
     */
    fun buildImageLoader(context: Context): ImageLoader {
        val httpClient = OkHttpClient.Builder()
            // thumbnails must not leak `okhttp/x.y.z` either: same UA as the API calls
            .addInterceptor(com.watube.yard.api.GenericUserAgentInterceptor())

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            httpClient.addInterceptor(loggingInterceptor)
        }

        return ImageLoader.Builder(context.applicationContext)
            .crossfade(true)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(httpClient.build())
                )
            }
            .apply {
                diskCachePolicy(CachePolicy.ENABLED)
                memoryCachePolicy(CachePolicy.ENABLED)

                val storageManager = context.getSystemService<StorageManager>()!!
                val availableCache = storageManager.getCacheQuotaBytes(
                    storageManager.getUuidForPath(context.coilFile)
                )
                val diskCache = DiskCache.Builder()
                    .directory(context.coilFile)
                    // only use a certain percentage of the available cache size for images
                    .maxSizeBytes(
                        (availableCache * DISK_CACHE_FRACTION).toLong().coerceAtLeast(MIN_DISK_CACHE_BYTES)
                    )
                    .build()
                diskCache(diskCache)
            }
            .build()
    }

    /**
     * Warms up the shared image loader off the main thread so that the first image requests
     * don't pay for building it while the first frames are being rendered.
     */
    fun initializeImageLoader(context: Context) {
        context.applicationContext.imageLoader
    }

    /**
     * Checks if the corresponding image for the given key (e.g. a url) is cached.
     */
    private fun isCached(context: Context, key: String): Boolean {
        val cacheSnapshot = context.applicationContext.imageLoader.diskCache?.openSnapshot(key)
        val isCacheHit = cacheSnapshot?.data?.toFile()?.exists()
        cacheSnapshot?.close()

        return isCacheHit ?: false
    }

    /**
     * load an image from a url into an imageView
     */
    fun loadImage(url: String?, target: ImageView, whiteBackground: Boolean = false) {
        if (url.isNullOrEmpty()) {
            clearImage(target)
            return
        }

        val urlToLoad = ProxyHelper.rewriteUrlUsingProxyPreference(url)

        // only load online images if the data saver mode is disabled
        if (DataSaverMode.isEnabled(target.context) &&
            urlToLoad.startsWith(HTTP_SCHEME) && !isCached(target.context, urlToLoad)
        ) {
            clearImage(target)
            return
        }

        // the view already displays this exact image, don't clear and decode it again
        if (target.tag == urlToLoad) return
        target.tag = urlToLoad

        // clear image to avoid loading issues at fast scrolling
        target.setImageBitmap(null)

        target.load(urlToLoad) {
            listener(
                onSuccess = { _, _ ->
                    // set the background to white for transparent images
                    if (whiteBackground) target.setBackgroundColor(Color.WHITE)
                },
                onError = { _, _ ->
                    // allow a later rebind to retry the request
                    target.tag = null
                }
            )
        }
    }

    /**
     * Empties a recycled target so it can never keep displaying the thumbnail of the
     * item it previously held (including the early returns above, which otherwise left
     * the previous image in place while scrolling).
     */
    private fun clearImage(target: ImageView) {
        target.tag = null
        target.setImageBitmap(null)
    }

    suspend fun downloadImage(context: Context, url: String, path: Path) {
        val bitmap = getImage(context, url) ?: return
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(path.toAndroidUri())?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 25, it)
            }
        }
    }

    suspend fun getImage(context: Context, url: String?): Bitmap? {
        return getImage(context, url?.toUri())
    }

    suspend fun getImage(context: Context, url: Uri?): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()

        return context.applicationContext.imageLoader.execute(request).image?.toBitmap()
    }

    /**
     * Total size of the app cache directory in bytes (images, http responses, temp files).
     */
    suspend fun getCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        directorySize(context.cacheDir)
    }

    /**
     * Empties every cache of the app and returns the amount of freed bytes.
     *
     * Only caches are touched: downloads, subscriptions, playlists and history stay untouched.
     */
    suspend fun clearCache(context: Context): Long = withContext(Dispatchers.IO) {
        val before = directorySize(context.cacheDir)
        val applicationContext = context.applicationContext

        runCatching { applicationContext.imageLoader.diskCache?.clear() }
            .onFailure { Log.e(TAG(), "Failed to clear the image disk cache", it) }
        runCatching { applicationContext.imageLoader.memoryCache?.clear() }
            .onFailure { Log.e(TAG(), "Failed to clear the image memory cache", it) }
        runCatching { RetrofitInstance.clearHttpCache() }
            .onFailure { Log.e(TAG(), "Failed to clear the http cache", it) }

        // remove what is left (webview cache, temp files) but keep the managed cache
        // directories alive so that Coil and OkHttp can keep using their instances
        val managedDirectories = setOf("coil", RetrofitInstance.HTTP_CACHE_DIR)
        applicationContext.cacheDir.listFiles()
            ?.filter { it.name !in managedDirectories }
            ?.forEach { file -> runCatching { file.deleteRecursively() } }

        (before - directorySize(applicationContext.cacheDir)).coerceAtLeast(0L)
    }

    private fun directorySize(directory: File): Long {
        if (!directory.isDirectory) return directory.length()

        var total = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    fun insertText(bitmap: Bitmap, text: String, posX: Float, posY: Float, fontSize: Float) {
        val canvas = Canvas(bitmap)

        canvas.drawBitmap(bitmap, null, Rect(0, 0, bitmap.width, bitmap.height), null)
        canvas.drawText(text, bitmap.width * posX, bitmap.height * posY, Paint().apply {
            style = Paint.Style.FILL
            textSize = fontSize
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        })
    }
}
