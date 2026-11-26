package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.data.settings.SettingKeys
import com.firestarterpro.templartv.data.shelf.ShelfEntry
import com.firestarterpro.templartv.data.shelf.ShelfSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wires between B3 (ShelfLauncher), B4 (big-box home) and B5 (quick menu). */
class HomeWiringTest {

    private val entry = ShelfEntry(id = 7, profileId = "p", provider = "peacock", dedupeKey = "k", title = "Show", source = ShelfSource.MEDIA_SESSION)

    @Test fun shelfTileGoesThroughShelfLauncher() = runBlocking {
        var launched: ShelfEntry? = null
        var fallbacks = 0
        val ok = ShelfOpen.open(7, launcherOn = true, lookup = { if (it == 7L) entry else null }, launch = { launched = it; true }, fallback = { fallbacks++; true })
        assertTrue(ok)
        assertEquals(entry, launched)
        assertEquals(0, fallbacks)
    }

    @Test fun shelfTileFallsBackWhenFlagOffEntryGoneOrLaunchFails() = runBlocking {
        var launches = 0
        var fallbacks = 0
        val launch: suspend (ShelfEntry) -> Boolean = { launches++; false }
        val fallback = { fallbacks++; true }
        assertTrue(ShelfOpen.open(7, launcherOn = false, lookup = { entry }, launch = launch, fallback = fallback))
        assertEquals(0, launches)
        assertTrue(ShelfOpen.open(7, launcherOn = true, lookup = { null }, launch = launch, fallback = fallback))
        assertEquals(0, launches)
        assertTrue(ShelfOpen.open(7, launcherOn = true, lookup = { entry }, launch = launch, fallback = fallback))
        assertEquals(1, launches)
        assertTrue(ShelfOpen.open(7, launcherOn = true, lookup = { error("db") }, launch = launch, fallback = fallback))
        assertEquals(4, fallbacks)
        assertFalse(ShelfOpen.open(0, launcherOn = true, lookup = { entry }, launch = { true }, fallback = { false }))
    }

    @Test fun homeRequestsWaitForTheHomeScreenWhenNobodyListens() {
        HomeActions.consumePending()
        HomeActions.openRowManager()
        assertEquals(HomeRequest.ROW_MANAGER, HomeActions.consumePending())
        assertNull(HomeActions.consumePending()) // only once
        HomeActions.openViewPicker()
        HomeActions.cycleView(-1) // a newer request replaces the older one
        assertEquals(HomeRequest.PREVIOUS_VIEW, HomeActions.consumePending())
        HomeActions.cycleView(1)
        assertEquals(HomeRequest.NEXT_VIEW, HomeActions.consumePending())
        HomeActions.cycleView(0)
        assertNull(HomeActions.consumePending())
    }

    @Test fun defaultHomeViewIsColumns() {
        assertEquals("columns", SettingKeys.defaults[SettingKeys.HOME_VIEW])
        assertEquals(HomeView.COLUMNS, HomeView.resolve(SettingKeys.defaults[SettingKeys.HOME_VIEW]) { true })
        // Columns flag off: Rows, which always works.
        assertEquals(HomeView.ROWS, HomeView.resolve(SettingKeys.defaults[SettingKeys.HOME_VIEW]) { false })
    }

    @Test fun starAndVoiceSettingsAreGone() {
        assertFalse("star_action" in SettingKeys.defaults)
        assertFalse("voice_fallback" in SettingKeys.defaults)
    }
}
