package com.watube.yard.util

import com.sun.net.httpserver.HttpServer
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownServiceException

/**
 * The app's clients speak https only; [LocalInstanceRouter] lets plain http through to the phone
 * itself and nowhere else, against a local server whose /redirect leads to another address.
 */
class LocalInstanceRouterTest {
    private lateinit var server: HttpServer

    /** Same connection settings as SharedHttpClient.base; every name resolves to the phone. */
    private val httpsOnly = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
        .dns { listOf(InetAddress.getLoopbackAddress()) }
        .build()

    private val router = LocalInstanceRouter(httpsOnly)

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            if (exchange.requestURI.path == "/redirect") {
                exchange.responseHeaders.add("Location", "http://example.com:${server.address.port}/")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val body = "local".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.write(body)
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    private fun request(host: String, path: String = "/") =
        Request.Builder().url("http://$host:${server.address.port}$path").build()

    @Test
    fun `the https only client refuses plain http, even to the phone`() {
        assertThrows(UnknownServiceException::class.java) {
            httpsOnly.newCall(request("127.0.0.1")).execute()
        }
    }

    @Test
    fun `plain http reaches an instance on the phone`() {
        val body = router.newCall(request("127.0.0.1")).execute().use { it.body.string() }
        assertEquals("local", body)
    }

    @Test
    fun `plain http to any other host stays refused`() {
        // example.com resolves to the phone here: only the name decides, not the address
        assertThrows(UnknownServiceException::class.java) {
            router.newCall(request("example.com")).execute()
        }
    }

    @Test
    fun `a local instance cannot redirect the request elsewhere`() {
        val response = router.newCall(request("127.0.0.1", "/redirect")).execute()
        response.use {
            assertEquals(302, it.code)
            assertEquals("127.0.0.1", it.request.url.host)
        }
    }

    @Test
    fun `only plain http to the phone's own names counts as local`() {
        assertTrue(LocalInstanceRouter.isLocalInstance("http://localhost:8080/".toHttpUrl()))
        assertTrue(LocalInstanceRouter.isLocalInstance("http://127.0.0.1/".toHttpUrl()))
        assertTrue(LocalInstanceRouter.isLocalInstance("http://[::1]:8080/".toHttpUrl()))
        assertFalse(LocalInstanceRouter.isLocalInstance("https://localhost/".toHttpUrl()))
        assertFalse(LocalInstanceRouter.isLocalInstance("http://192.168.1.2/".toHttpUrl()))
        assertFalse(LocalInstanceRouter.isLocalInstance("http://localhost.example.com/".toHttpUrl()))
    }
}
