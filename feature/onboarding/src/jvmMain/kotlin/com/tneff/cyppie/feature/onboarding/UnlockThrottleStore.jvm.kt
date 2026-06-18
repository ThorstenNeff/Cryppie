package com.tneff.cyppie.feature.onboarding

import com.tneff.cyppie.storage.CiphertextStore
import com.tneff.cyppie.storage.defaultSeedFilePath

private fun throttleStore(): CiphertextStore {
    val dir = defaultSeedFilePath().substringBeforeLast('/', ".")
    return CiphertextStore.file("$dir/unlock_throttle")
}

private fun nowMillis(): Long = System.currentTimeMillis()

actual object UnlockThrottleStore {
    actual suspend fun load(): ThrottleSnapshot {
        val (failures, lastMillis) = read()
        val since = if (lastMillis <= 0L) Long.MAX_VALUE else ((nowMillis() - lastMillis) / 1000L).coerceAtLeast(0L)
        return ThrottleSnapshot(failures, since)
    }

    actual suspend fun recordFailure(): Int {
        val (failures, _) = read()
        val next = failures + 1
        throttleStore().write("$next,${nowMillis()}".encodeToByteArray())
        return next
    }

    actual suspend fun clear() {
        throttleStore().clear()
    }

    private suspend fun read(): Pair<Int, Long> {
        val bytes = throttleStore().read() ?: return 0 to 0L
        val parts = bytes.decodeToString().split(",")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }
}
