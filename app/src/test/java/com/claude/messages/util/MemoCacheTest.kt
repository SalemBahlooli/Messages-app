package com.claude.messages.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoCacheTest {

    /**
     * The regression this class exists for: a ConcurrentHashMap throws on a null
     * value, so caching "no contact for this number" used to crash the app for
     * every sender that wasn't in the phone book.
     */
    @Test
    fun `caching a miss does not throw`() {
        val cache = MemoCache<String, String>()
        cache.put("+15551234567", null)
        assertTrue(cache.isCached("+15551234567"))
        assertNull(cache["+15551234567"])
    }

    @Test
    fun `an unseen key is not cached`() {
        val cache = MemoCache<String, String>()
        assertFalse(cache.isCached("nobody"))
        assertNull(cache["nobody"])
    }

    @Test
    fun `a stored value is returned`() {
        val cache = MemoCache<String, String>()
        cache.put("a", "Alice")
        assertEquals("Alice", cache["a"])
        assertTrue(cache.isCached("a"))
    }

    @Test
    fun `a miss can later become a hit`() {
        val cache = MemoCache<String, String>()
        cache.put("a", null)
        cache.put("a", "Alice")
        assertEquals("Alice", cache["a"])
        assertEquals(1, cache.size)
    }

    @Test
    fun `a hit can later become a miss`() {
        val cache = MemoCache<String, String>()
        cache.put("a", "Alice")
        cache.put("a", null)
        assertNull(cache["a"])
        assertTrue(cache.isCached("a"))
        assertEquals(1, cache.size)
    }

    @Test
    fun `clear forgets both hits and misses`() {
        val cache = MemoCache<String, String>()
        cache.put("a", "Alice")
        cache.put("b", null)
        cache.clear()
        assertFalse(cache.isCached("a"))
        assertFalse(cache.isCached("b"))
        assertEquals(0, cache.size)
    }

    @Test
    fun `concurrent writes of hits and misses stay consistent`() {
        val cache = MemoCache<Int, String>()
        val threads = (0 until 8).map { t ->
            Thread {
                repeat(500) { i ->
                    val key = (t * 500 + i)
                    if (key % 2 == 0) cache.put(key, "v$key") else cache.put(key, null)
                }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(4000, cache.size)
        assertEquals("v0", cache[0])
        assertNull(cache[1])
    }
}
