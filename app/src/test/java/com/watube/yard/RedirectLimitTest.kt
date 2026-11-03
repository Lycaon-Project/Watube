package com.watube.yard

import com.sun.net.httpserver.HttpServer
import com.watube.yard.util.RedirectLimit
import com.watube.yard.util.RefusedRequestException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * No request follows more than [RedirectLimit.MAX_REDIRECTS] redirects (OkHttp alone follows 20),
 * against a local server whose /hops/N redirects N times before answering.
 */
class RedirectLimitTest {
    private lateinit var server: HttpServer
    private val client = RedirectLimit.install(OkHttpClient.Builder()).build()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/hops/") { exchange ->
            val left = exchange.requestURI.path.substringAfterLast('/').toInt()
            if (left > 0) {
                exchange.responseHeaders.add("Location", "/hops/${left - 1}")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val body = "arrived".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.write(body)
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    private fun get(redirects: Int): String {
        val url = "http://127.0.0.1:${server.address.port}/hops/$redirects"
        return client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
    }

    @Test
    fun followsUpToTheLimit() {
        assertEquals("arrived", get(0))
        assertEquals("arrived", get(RedirectLimit.MAX_REDIRECTS))
    }

    @Test
    fun refusesOneRedirectMore() {
        assertThrows(RefusedRequestException::class.java) { get(RedirectLimit.MAX_REDIRECTS + 1) }
    }

    @Test
    fun eachRequestHasItsOwnCount() {
        // a count shared between calls would refuse the second one
        repeat(3) { assertEquals("arrived", get(RedirectLimit.MAX_REDIRECTS)) }
    }
}
