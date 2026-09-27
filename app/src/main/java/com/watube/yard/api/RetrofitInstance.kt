package com.watube.yard.api

import com.watube.yard.BuildConfig
import com.watube.yard.LibreTubeApp
import com.watube.yard.constants.PreferenceKeys
import com.watube.yard.helpers.PreferenceHelper
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.io.File

object RetrofitInstance {
    const val PIPED_API_URL = "https://pipedapi.kavin.rocks"

    /** Shared http cache: avoids re-downloading trending feeds, configs, suggestions, ... */
    const val HTTP_CACHE_DIR = "http_cache"
    private const val HTTP_CACHE_MAX_BYTES = 20L * 1024 * 1024

    val authUrl
        get() = if (
            PreferenceHelper.getBoolean(
                PreferenceKeys.AUTH_INSTANCE_TOGGLE,
                false
            )
        ) {
           PreferenceHelper.getString(
                PreferenceKeys.AUTH_INSTANCE,
                PIPED_API_URL
            )
        } else {
            PipedMediaServiceRepository.apiUrl
        }

    val apiLazyMgr = resettableManager()
    val kotlinxConverterFactory = JsonHelper.json
        .asConverterFactory("application/json".toMediaType())

    private val cacheLock = Any()

    @Volatile
    private var httpCache: Cache? = null

    @Volatile
    private var httpClientInstance: OkHttpClient? = null

    val httpClient: OkHttpClient
        get() = httpClientInstance ?: synchronized(cacheLock) {
            httpClientInstance ?: buildClient().also { httpClientInstance = it }
        }

    private val cacheDirectory: File
        get() = File(LibreTubeApp.instance.cacheDir, HTTP_CACHE_DIR)

    private fun httpCache(): Cache = httpCache ?: synchronized(cacheLock) {
        httpCache ?: Cache(cacheDirectory, HTTP_CACHE_MAX_BYTES).also { httpCache = it }
    }

    /**
     * Deletes the shared http cache and rebuilds every client / service that was created
     * with the now closed [Cache] instance.
     */
    fun clearHttpCache() {
        synchronized(cacheLock) {
            runCatching { httpCache?.delete() }
                .onFailure { it.printStackTrace() }
            httpCache = null

            // the old cache instance is closed: drop the client and every retrofit service
            // that captured it so that the next call builds a fresh one
            httpClientInstance = null
            cacheDirectory.mkdirs()
            apiLazyMgr.reset()
        }
    }

    val authApi by resettableLazy(apiLazyMgr) {
        buildRetrofitInstance<PipedAuthApi>(authUrl)
    }

    // the url provided here isn't actually used anywhere in the external api
    val externalApi by resettableLazy(apiLazyMgr) {
        buildRetrofitInstance<ExternalApi>(PIPED_API_URL)
    }

    private fun buildClient(): OkHttpClient {
        val httpClient = OkHttpClient().newBuilder()
            .cache(httpCache())

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            httpClient.addInterceptor(loggingInterceptor)
        }

        return httpClient.build()
    }

    inline fun <reified T: Any> buildRetrofitInstance(apiUrl: String): T = Retrofit.Builder()
        .baseUrl(apiUrl)
        .client(httpClient)
        .addConverterFactory(kotlinxConverterFactory)
        .build()
        .create<T>()
}
