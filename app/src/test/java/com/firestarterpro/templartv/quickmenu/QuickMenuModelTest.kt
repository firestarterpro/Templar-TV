package com.firestarterpro.templartv.quickmenu

import com.firestarterpro.templartv.Flag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMenuModelTest {

    private val s = QuickMenuSection.entries

    @Test fun defaultOrderPutsResumeFirstAndShowsEverything() {
        val layout = QuickMenuLayout.DEFAULT
        assertEquals(QuickMenuSection.RESUME, layout.visibleSections.first())
        assertEquals(s.toList(), layout.visibleSections)
    }

    @Test fun footerIsFixedAndInOrder() {
        assertEquals(
            listOf(FooterItem.GOOGLE_HOME, FooterItem.GOOGLE_TV_HOME, FooterItem.TEMPLAR_HOME),
            QuickMenuLayout.FOOTER,
        )
    }

    @Test fun footerCannotBeHiddenOrStored() {
        FooterItem.entries.forEach { assertFalse(it.id, QuickMenuLayout.canHide(it.id)) }
        QuickMenuSection.entries.forEach { assertTrue(it.id, QuickMenuLayout.canHide(it.id)) }
        // A stored value that tries to hide or reorder the footer is ignored.
        val decoded = QuickMenuLayout.decode("-google_home,templar_home,apps,-google_tv_home")
        assertEquals(QuickMenuSection.entries.size, decoded.sections.size)
        assertEquals(QuickMenuSection.APPS, decoded.sections.first().section)
        assertFalse(decoded.encode().contains("google"))
        assertEquals(3, QuickMenuLayout.FOOTER.size)
    }

    @Test fun hideIsAFlagAndShowBringsItBack() {
        val hidden = QuickMenuLayout.DEFAULT.setHidden(QuickMenuSection.APPS, true)
        assertEquals(s.size, hidden.sections.size) // never removed
        assertFalse(QuickMenuSection.APPS in hidden.visibleSections)
        val shown = hidden.toggleHidden(QuickMenuSection.APPS)
        assertEquals(QuickMenuLayout.DEFAULT, shown)
    }

    @Test fun hidingEverySectionLeavesOnlyTheFooter() {
        var layout = QuickMenuLayout.DEFAULT
        s.forEach { layout = layout.setHidden(it, true) }
        assertTrue(layout.visibleSections.isEmpty())
        assertEquals(3, QuickMenuLayout.FOOTER.size)
    }

    @Test fun moveReordersAndClamps() {
        val moved = QuickMenuLayout.DEFAULT.move(QuickMenuSection.QUICK_SETTINGS, -1)
        assertEquals(
            listOf(
                QuickMenuSection.RESUME, QuickMenuSection.CONTINUE_WATCHING, QuickMenuSection.FAVOURITE_SHOWS,
                QuickMenuSection.APPS, QuickMenuSection.CHANGE_VIEW, QuickMenuSection.QUICK_SETTINGS,
                QuickMenuSection.ROW_MANAGER, QuickMenuSection.TEMPLAR_SHORTCUTS,
            ),
            moved.visibleSections,
        )
        val first = QuickMenuLayout.DEFAULT.move(QuickMenuSection.QUICK_SETTINGS, -99)
        assertEquals(QuickMenuSection.QUICK_SETTINGS, first.sections.first().section)
        assertEquals(first, first.move(QuickMenuSection.QUICK_SETTINGS, -5)) // already first
        val last = QuickMenuLayout.DEFAULT.move(QuickMenuSection.RESUME, 99)
        assertEquals(QuickMenuSection.RESUME, last.sections.last().section)
    }

    @Test fun moveKeepsHiddenState() {
        val layout = QuickMenuLayout.DEFAULT.setHidden(QuickMenuSection.RESUME, true).move(QuickMenuSection.RESUME, 2)
        assertTrue(layout.isHidden(QuickMenuSection.RESUME))
        assertEquals(QuickMenuSection.RESUME, layout.sections[2].section)
    }

    @Test fun encodeDecodeRoundTrip() {
        val layout = QuickMenuLayout.DEFAULT
            .move(QuickMenuSection.TEMPLAR_SHORTCUTS, -99)
            .setHidden(QuickMenuSection.QUICK_SETTINGS, true)
        val stored = layout.encode()
        assertEquals(
            "templar_shortcuts,resume,continue_watching,favourite_shows,apps,change_view,row_manager,-quick_settings",
            stored,
        )
        assertEquals(layout, QuickMenuLayout.decode(stored))
    }

    @Test fun decodeIsTolerant() {
        assertEquals(QuickMenuLayout.DEFAULT, QuickMenuLayout.decode(null))
        assertEquals(QuickMenuLayout.DEFAULT, QuickMenuLayout.decode("  "))
        assertEquals(QuickMenuLayout.DEFAULT, QuickMenuLayout.decode("nonsense,,"))
        // Duplicates: first wins. Missing sections are appended, shown, in default order.
        val d = QuickMenuLayout.decode("apps, -apps ,resume")
        assertEquals(
            listOf(
                QuickMenuSection.APPS, QuickMenuSection.RESUME, QuickMenuSection.CONTINUE_WATCHING, QuickMenuSection.FAVOURITE_SHOWS,
                QuickMenuSection.CHANGE_VIEW, QuickMenuSection.ROW_MANAGER, QuickMenuSection.QUICK_SETTINGS, QuickMenuSection.TEMPLAR_SHORTCUTS,
            ),
            d.sections.map { it.section },
        )
        assertFalse(d.isHidden(QuickMenuSection.APPS))
    }

    @Test fun flagDefaultsOffAndIsExperimental() {
        assertEquals("quick_menu", Flag.QUICK_MENU.key)
        assertFalse(Flag.QUICK_MENU.defaultEnabled)
        assertTrue(Flag.QUICK_MENU.experimental)
    }

    @Test fun layoutStoredByAnOlderBuildGainsTheNewSectionsShown() {
        // What PR #12 could store before Continue watching, Favourite shows, Change view and Rows existed.
        val old = QuickMenuLayout.decode("templar_shortcuts,resume,apps,-quick_settings")
        assertEquals(s.size, old.sections.size)
        assertTrue(old.isHidden(QuickMenuSection.QUICK_SETTINGS))
        listOf(QuickMenuSection.CONTINUE_WATCHING, QuickMenuSection.FAVOURITE_SHOWS, QuickMenuSection.CHANGE_VIEW, QuickMenuSection.ROW_MANAGER)
            .forEach { assertTrue(it.id, it in old.visibleSections) }
    }

    @Test fun newSectionsFollowTheirFeatureFlags() {
        val all = QuickMenuLayout.DEFAULT
        assertEquals(s.toList(), all.visibleSections { true })
        val noBigBox = all.visibleSections { it != Flag.BIG_BOX_HOME }
        assertFalse(QuickMenuSection.CHANGE_VIEW in noBigBox)
        assertFalse(QuickMenuSection.ROW_MANAGER in noBigBox)
        assertTrue(QuickMenuSection.CONTINUE_WATCHING in noBigBox)
        val noRowManager = all.visibleSections { it != Flag.ROW_MANAGER }
        assertFalse(QuickMenuSection.ROW_MANAGER in noRowManager)
        assertTrue(QuickMenuSection.CHANGE_VIEW in noRowManager)
        assertFalse(QuickMenuSection.CONTINUE_WATCHING in all.visibleSections { it != Flag.PROVIDER_RECENT_SHELVES })
        assertFalse(QuickMenuSection.FAVOURITE_SHOWS in all.visibleSections { it != Flag.FAVOURITE_SHOWS })
        // Sections without a flag are always offered.
        assertEquals(
            listOf(QuickMenuSection.RESUME, QuickMenuSection.APPS, QuickMenuSection.QUICK_SETTINGS, QuickMenuSection.TEMPLAR_SHORTCUTS),
            all.visibleSections { false },
        )
    }

    @Test fun openRoutesToInAppPanelThenActivity() {
        var inApp = 0
        var activity = 0
        val emit = { inApp++; true }
        val start = { activity++; true }
        assertFalse(QuickMenuController.route(enabled = false, templarInFront = true, openInApp = emit, startActivity = start))
        assertEquals(0, inApp + activity)
        assertTrue(QuickMenuController.route(enabled = true, templarInFront = true, openInApp = emit, startActivity = start))
        assertEquals(1 to 0, inApp to activity)
        assertTrue(QuickMenuController.route(enabled = true, templarInFront = false, openInApp = emit, startActivity = start))
        assertEquals(1 to 1, inApp to activity)
        // In front, but the in-app panel did not take it: start the activity.
        assertTrue(QuickMenuController.route(enabled = true, templarInFront = true, openInApp = { false }, startActivity = start))
        assertEquals(2, activity)
    }

    @Test fun companionOpenMenuUsesQuickMenuThenLegacyPanel() {
        var legacy = 0
        assertTrue(QuickMenuController.openOrLegacy(quickMenu = { true }, legacy = { legacy++; true }))
        assertEquals(0, legacy) // quick menu flag on: the old panel is not started
        assertTrue(QuickMenuController.openOrLegacy(quickMenu = { false }, legacy = { legacy++; true }))
        assertEquals(1, legacy) // flag off: the old quick-settings panel still opens
    }

    @Test fun starAndVoiceFlagsAreGone() {
        val keys = Flag.entries.map { it.key }
        assertFalse("star_key_capture" in keys)
        assertFalse("custom_voice_routing" in keys)
        assertTrue("home_return" in keys)
        assertTrue("settings_key_capture" in keys)
        assertFalse(Flag.SETTINGS_KEY_CAPTURE.defaultEnabled)
    }

    @Test fun flagKeysStayUnique() {
        assertEquals(Flag.entries.size, Flag.entries.map { it.key }.toSet().size)
    }
}
