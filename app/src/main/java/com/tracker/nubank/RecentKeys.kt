package com.tracker.nubank

/**
 * Tracks recently-seen notification keys so re-posted notifications (some ROMs,
 * re-posts with the same key) aren't logged twice. Bounded so a long-running
 * service doesn't grow its memory without limit.
 */
class RecentKeys(
    private val maxSize: Int = 100,
    private val windowMs: Long = 60_000
) {
    // LinkedHashMap with access-order so the eldest (least-recently seen) is evicted.
    private val map = LinkedHashMap<String, Long>(maxSize, 0.75f, true)

    @Synchronized
    fun wasRecentlySeen(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = map[key]
        if (last != null && now - last < windowMs) return true
        map[key] = now
        while (map.size > maxSize) {
            map.remove(map.keys.first())
        }
        return false
    }
}
