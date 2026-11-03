package com.watube.yard.util

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger

// region Networking
/**
 * At most [MAX_REDIRECTS] redirects per request, instead of OkHttp's 20: each hop is one more
 * host that may learn the user's address, and the services the app talks to need one or two.
 *
 * The count travels with the request: [budget] tags it once, and since OkHttp builds every follow
 * up request from the previous one, the tag reaches each hop, where [limit] counts the redirects.
 *
 * [limit] is a network interceptor, which OkHttp calls once connected: the hop it refuses has
 * already been reached (name lookup, TLS handshake), only the request itself is not sent.
 */
object RedirectLimit {
    const val MAX_REDIRECTS = 5

    private class Budget {
        /** Redirect responses received so far: the next hop follows the last one. */
        val redirects = AtomicInteger()
    }

    /** Application interceptor: gives the request the counter of its redirects. */
    private val budget = Interceptor { chain ->
        val request = chain.request()
        if (request.tag(Budget::class.java) != null) return@Interceptor chain.proceed(request)
        chain.proceed(request.newBuilder().tag(Budget::class.java, Budget()).build())
    }

    /** Network interceptor: refuses the hop that would follow one redirect too many. */
    private val limit = Interceptor { chain ->
        val budget = chain.request().tag(Budget::class.java)
        if (budget != null && budget.redirects.get() > MAX_REDIRECTS) {
            throw RefusedRequestException("More than $MAX_REDIRECTS redirects")
        }
        val response = chain.proceed(chain.request())
        if (budget != null && response.isRedirect) budget.redirects.incrementAndGet()
        response
    }

    /** Both interceptors on [builder]; [limit] comes after the network interceptors already there. */
    fun install(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
        .addInterceptor(budget)
        .addNetworkInterceptor(limit)
}
// endregion
