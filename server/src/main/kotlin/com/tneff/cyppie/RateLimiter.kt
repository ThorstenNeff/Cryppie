package com.tneff.cyppie

import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal in-memory fixed-window rate limiter (KAN-112 abuse guard) — no external dependency. Each
 * client (by remote host) gets [limitPerWindow] requests per [windowMillis]; further requests in the
 * window are rejected so a single caller can't exhaust the upstream key. The clock is injectable so
 * the window behaviour is deterministically testable.
 */
class FixedWindowRateLimiter(
    private val limitPerWindow: Int,
    private val windowMillis: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Window(var startMs: Long, var count: Int)

    private val windows = ConcurrentHashMap<String, Window>()
    private val sinceSweep = java.util.concurrent.atomic.AtomicInteger(0)

    /** @return true if the request is within budget; false if [clientId] exceeded its window quota. */
    fun allow(clientId: String): Boolean {
        maybeEvictStale()
        val t = now()
        val window = windows.getOrPut(clientId) { Window(t, 0) }
        synchronized(window) {
            if (t - window.startMs >= windowMillis) {
                window.startMs = t
                window.count = 0
            }
            if (window.count >= limitPerWindow) return false
            window.count++
            return true
        }
    }

    /**
     * Bound memory (ADR-0022 B): with one entry per client IP the map would grow unbounded under a
     * public load / spoofed IPs. Every [SWEEP_INTERVAL] calls, drop windows whose interval has fully
     * elapsed — an evicted client simply gets a fresh window on its next request (same as a reset).
     */
    private fun maybeEvictStale() {
        if (sinceSweep.incrementAndGet() < SWEEP_INTERVAL) return
        sinceSweep.set(0)
        val cutoff = now() - windowMillis
        windows.entries.removeIf { (_, w) -> synchronized(w) { w.startMs < cutoff } }
    }

    private companion object {
        const val SWEEP_INTERVAL = 1_000
    }
}
