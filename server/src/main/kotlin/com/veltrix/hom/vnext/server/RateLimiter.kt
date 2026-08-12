package com.veltrix.hom.vnext.server

import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class RequestRateLimiter(
    private val maxRequests: Int = 180,
    private val windowSeconds: Long = 60,
    private val maxKeys: Int = 20_000,
) {
    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun allow(key: String, now: Instant = Instant.now()): Boolean {
        if (key.isBlank()) return false
        if (windows.size > maxKeys) cleanup(now.epochSecond)
        val epoch = now.epochSecond
        val q = windows.computeIfAbsent(key.take(180)) { ArrayDeque() }
        synchronized(q) {
            while (q.isNotEmpty() && epoch - q.first() >= windowSeconds) q.removeFirst()
            if (q.size >= maxRequests) return false
            q.addLast(epoch)
            return true
        }
    }

    private fun cleanup(now: Long) {
        windows.entries.removeIf { (_, q) -> synchronized(q) { q.isEmpty() || now - q.last() >= windowSeconds } }
    }
}
