package team.shade.lmdbviewer.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure list-logic tests for [RecentEnvironmentsService]: MRU ordering, de-duplication, the size
 * cap, removal, and a state round-trip through [RecentEnvironmentsService.loadState].
 */
class RecentEnvironmentsServiceTest {

    @Test
    fun addPrependsAndDeduplicates() {
        val svc = RecentEnvironmentsService()
        svc.add("/a")
        svc.add("/b")
        svc.add("/a") // re-adding moves it back to the front
        assertEquals(listOf("/a", "/b"), svc.recentPaths)
    }

    @Test
    fun addCapsAtFifteenDroppingOldest() {
        val svc = RecentEnvironmentsService()
        (1..16).forEach { svc.add("/p$it") }
        val paths = svc.recentPaths
        assertEquals(15, paths.size)
        assertEquals("/p16", paths.first()) // most recent
        assertEquals("/p2", paths.last())   // "/p1" was evicted
    }

    @Test
    fun removeDeletesPathAndIgnoresUnknown() {
        val svc = RecentEnvironmentsService()
        svc.add("/a")
        svc.add("/b")
        svc.remove("/a")
        svc.remove("/missing") // no-op
        assertEquals(listOf("/b"), svc.recentPaths)
    }

    @Test
    fun clearEmptiesTheList() {
        val svc = RecentEnvironmentsService()
        svc.add("/a")
        svc.add("/b")
        svc.clear()
        assertEquals(emptyList<String>(), svc.recentPaths)
    }

    @Test
    fun loadStateRestoresPersistedPaths() {
        val loaded = RecentEnvironmentsService.State().apply {
            recentPaths = mutableListOf("/x", "/y")
        }
        val svc = RecentEnvironmentsService()
        svc.loadState(loaded)
        assertEquals(listOf("/x", "/y"), svc.recentPaths)
    }
}
