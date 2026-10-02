package com.watube.yard.api

import com.watube.yard.helpers.PrivacyHelper
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Watube anti-fingerprinting: normalize the User-Agent of every outgoing request
 * that doesn't already carry a service-specific one. OkHttp otherwise appends its
 * own default UA (`okhttp/x.y.z`), which leaks the library version and lets
 * third parties single Watube traffic out from real browser traffic. This makes all
 * hardened users look identical (Mullvad/Tor "blend into the crowd" policy).
 *
 * Every OkHttpClient of the app (API, images, raw downloads) must install it, otherwise
 * the missing client is exactly the one that fingerprints the app.
 */
class GenericUserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header("User-Agent") != null) return chain.proceed(original)

        return chain.proceed(
            original.newBuilder()
                .header("User-Agent", PrivacyHelper.userAgent)
                .build(),
        )
    }
}
