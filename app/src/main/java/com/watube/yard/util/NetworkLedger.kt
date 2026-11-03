package com.watube.yard.util

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

// region Privacy
/**
 * Local record of the servers the app talked to since it was opened, shown in the privacy tab.
 *
 * Only host names with their counters and a count of requests per 10 seconds are kept, in
 * memory: no address, path, query or header of a request, nothing written to disk and nothing ever sent. It is lost when the app
 * process ends.
 */
object NetworkLedger {

    /** What the privacy tab shows for one host. */
    data class HostStats(val host: String, val allowed: Int, val refused: Int)

    private class Counters {
        val allowed = AtomicInteger()
        val refused = AtomicInteger()
    }

    /** bounded: a response steering the app to many hosts must not grow this map for ever */
    private const val MAX_HOSTS = 200

    private val hosts = ConcurrentHashMap<String, Counters>()
    private val startedAt = AtomicLong(System.currentTimeMillis())

    /** requests that went out without encryption (only self-hosted instances on the phone) */
    private val cleartext = AtomicInteger()

    /** Width of one activity bucket, and how many are kept (five minutes). */
    const val ACTIVITY_BUCKET_MS = 10_000L
    const val ACTIVITY_BUCKETS = 30

    // ring of request counts per 10 s: the slot number tells whether a bucket is still current
    private val activity = AtomicIntegerArray(ACTIVITY_BUCKETS)
    private val activitySlot = AtomicLongArray(ACTIVITY_BUCKETS)

    /** When the current record started: the start of the app process. */
    val since: Long get() = startedAt.get()

    /**
     * Network interceptor of the shared client: every hop of every client is counted once,
     * as allowed when it went out, as refused when a policy of the app stopped it.
     */
    val interceptor = Interceptor { chain ->
        val host = chain.request().url.host
        val response: Response = try {
            chain.proceed(chain.request())
        } catch (e: RefusedRequestException) {
            counters(host)?.refused?.incrementAndGet()
            throw e
        }
        counters(host)?.allowed?.incrementAndGet()
        if (!chain.request().isHttps) cleartext.incrementAndGet()
        recordActivity(System.currentTimeMillis())
        response
    }

    private fun counters(host: String): Counters? =
        hosts[host] ?: if (hosts.size < MAX_HOSTS) hosts.computeIfAbsent(host) { Counters() } else null

    /** The hosts, most used first. */
    fun snapshot(): List<HostStats> = hosts.entries
        .map { (host, counters) -> HostStats(host, counters.allowed.get(), counters.refused.get()) }
        .sortedByDescending { it.allowed + it.refused }

    /** Requests sent without encryption since [since]. */
    val cleartextRequests: Int get() = cleartext.get()

    /** approximate on purpose: two requests racing a bucket change may lose one count */
    private fun recordActivity(now: Long) {
        val slot = now / ACTIVITY_BUCKET_MS
        val index = (slot % ACTIVITY_BUCKETS).toInt()
        if (activitySlot.getAndSet(index, slot) != slot) activity.set(index, 0)
        activity.incrementAndGet(index)
    }

    /** Requests per 10 s over the last five minutes, oldest first. */
    fun recentActivity(now: Long = System.currentTimeMillis()): IntArray {
        val currentSlot = now / ACTIVITY_BUCKET_MS
        return IntArray(ACTIVITY_BUCKETS) { position ->
            val slot = currentSlot - (ACTIVITY_BUCKETS - 1 - position)
            val index = (slot % ACTIVITY_BUCKETS).toInt()
            if (activitySlot.get(index) == slot) activity.get(index) else 0
        }
    }

}

/** A request stopped by a network policy of the app, counted as refused by [NetworkLedger]. */
class RefusedRequestException(message: String) : IOException(message)
// endregion
