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
        val moved = QuickMenuLayout.DEFAULT.move(QuickMenuSection.QUICK_SETTINGS, -2)
        assertEquals(
            listOf(QuickMenuSection.QUICK_SETTINGS, QuickMenuSection.RESUME, QuickMenuSection.APPS, QuickMenuSection.TEMPLAR_SHORTCUTS),
            moved.visibleSections,
        )
        assertEquals(moved, moved.move(QuickMenuSection.QUICK_SETTINGS, -5)) // already first
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
            .move(QuickMenuSection.TEMPLAR_SHORTCUTS, -3)
            .setHidden(QuickMenuSection.QUICK_SETTINGS, true)
        val stored = layout.encode()
        assertEquals("templar_shortcuts,resume,apps,-quick_settings", stored)
        assertEquals(layout, QuickMenuLayout.decode(stored))
    }

    @Test fun decodeIsTolerant() {
        assertEquals(QuickMenuLayout.DEFAULT, QuickMenuLayout.decode(null))
        assertEquals(QuickMenuLayout.DEFAULT, QuickMenuLayout.decode("  "))
        assertEquals(QuickMenuLayout.DEFAULT, QuickMenuLayout.decode("nonsense,,"))
        // Duplicates: first wins. Missing sections are appended, shown, in default order.
        val d = QuickMenuLayout.decode("apps, -apps ,resume")
        assertEquals(
            listOf(QuickMenuSection.APPS, QuickMenuSection.RESUME, QuickMenuSection.QUICK_SETTINGS, QuickMenuSection.TEMPLAR_SHORTCUTS),
            d.sections.map { it.section },
        )
        assertFalse(d.isHidden(QuickMenuSection.APPS))
    }

    @Test fun flagDefaultsOffAndIsExperimental() {
        assertEquals("quick_menu", Flag.QUICK_MENU.key)
        assertFalse(Flag.QUICK_MENU.defaultEnabled)
        assertTrue(Flag.QUICK_MENU.experimental)
    }

    @Test fun flagKeysStayUnique() {
        assertEquals(Flag.entries.size, Flag.entries.map { it.key }.toSet().size)
    }
}
