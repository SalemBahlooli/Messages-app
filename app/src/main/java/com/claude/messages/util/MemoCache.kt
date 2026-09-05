package com.claude.messages.util

import java.util.concurrent.ConcurrentHashMap

/**
 * A small thread-safe cache that also remembers **misses**.
 *
 * A plain `ConcurrentHashMap<K, V?>` cannot do this: it rejects null values with
 * a NullPointerException, so caching "this key has no value" crashes. Misses are
 * held in a separate key set instead, which is what makes a negative lookup both
 * cacheable and safe.
 */
class MemoCache<K : Any, V : Any> {

    private val hits = ConcurrentHashMap<K, V>()
    private val misses: MutableSet<K> = ConcurrentHashMap.newKeySet()

    /** A previously stored value, or null if absent or known to be a miss. */
    operator fun get(key: K): V? = hits[key]

    /** True when [key] has been looked up before, whether or not it had a value. */
    fun isCached(key: K): Boolean = hits.containsKey(key) || misses.contains(key)

    /** Records a lookup result; a null [value] records a miss. */
    fun put(key: K, value: V?) {
        if (value == null) {
            hits.remove(key)
            misses.add(key)
        } else {
            misses.remove(key)
            hits[key] = value
        }
    }

    fun clear() {
        hits.clear()
        misses.clear()
    }

    val size: Int get() = hits.size + misses.size
}
