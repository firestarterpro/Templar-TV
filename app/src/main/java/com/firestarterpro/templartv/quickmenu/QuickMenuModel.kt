package com.firestarterpro.templartv.quickmenu

import com.firestarterpro.templartv.Flag

/**
 * Pure model for the Templar quick menu ([com.firestarterpro.templartv.Flag.QUICK_MENU]).
 * No Android types, so ordering, hiding and the pinned footer are unit tested on the JVM.
 *
 * The menu is: header, the reorderable/hideable [QuickMenuSection]s in the user's order, then the
 * pinned [FooterItem]s. The footer is not part of [QuickMenuLayout] at all, so it can't be hidden,
 * moved or dropped by a stored value.
 */
enum class QuickMenuSection(val id: String, val label: String, val flags: List<Flag> = emptyList()) {
    RESUME("resume", "Resume"),
    /** Recently played shelf tiles (B3), opened through ShelfLauncher. */
    CONTINUE_WATCHING("continue_watching", "Continue watching", listOf(Flag.PROVIDER_RECENT_SHELVES)),
    FAVOURITE_SHOWS("favourite_shows", "Favourite shows", listOf(Flag.FAVOURITE_SHOWS)),
    APPS("apps", "Apps"),
    /** Home view picker and previous / next view (B4 HomeActions). */
    CHANGE_VIEW("change_view", "Change view", listOf(Flag.BIG_BOX_HOME)),
    /** Row Manager (B4 HomeActions.openRowManager). */
    ROW_MANAGER("row_manager", "Rows", listOf(Flag.BIG_BOX_HOME, Flag.ROW_MANAGER)),
    QUICK_SETTINGS("quick_settings", "Quick settings"),
    TEMPLAR_SHORTCUTS("templar_shortcuts", "Templar"),
    ;

    /** Shown only while every flag it depends on is on. */
    fun isAvailable(isOn: (Flag) -> Boolean): Boolean = flags.all(isOn)

    companion object {
        fun fromId(id: String): QuickMenuSection? = entries.firstOrNull { it.id == id }
    }
}

/** Always shown, always last, in this order. Never stored, never hideable. */
enum class FooterItem(val id: String, val label: String) {
    GOOGLE_HOME("google_home", "Google Home"),
    GOOGLE_TV_HOME("google_tv_home", "Google TV Home"),
    TEMPLAR_HOME("templar_home", "Templar Home"),
}

data class SectionState(val section: QuickMenuSection, val hidden: Boolean = false)

/**
 * The user's section order and visibility. Hiding is a flag, never a removal: every section is
 * always present in [sections], so "show again" always works.
 */
data class QuickMenuLayout(val sections: List<SectionState>) {

    init {
        require(sections.map { it.section }.toSet().size == sections.size) { "duplicate section" }
        require(sections.size == QuickMenuSection.entries.size) { "every section must be present" }
    }

    val visibleSections: List<QuickMenuSection> get() = sections.filterNot { it.hidden }.map { it.section }

    /** Visible sections whose feature flags are on. Hidden-by-flag sections keep their place. */
    fun visibleSections(isOn: (Flag) -> Boolean): List<QuickMenuSection> = visibleSections.filter { it.isAvailable(isOn) }

    fun isHidden(section: QuickMenuSection): Boolean = sections.first { it.section == section }.hidden

    fun setHidden(section: QuickMenuSection, hidden: Boolean): QuickMenuLayout =
        copy(sections = sections.map { if (it.section == section) it.copy(hidden = hidden) else it })

    fun toggleHidden(section: QuickMenuSection): QuickMenuLayout = setHidden(section, !isHidden(section))

    /** Moves a section by [delta] places, clamped to the list. The footer is unaffected. */
    fun move(section: QuickMenuSection, delta: Int): QuickMenuLayout {
        val from = sections.indexOfFirst { it.section == section }
        val to = (from + delta).coerceIn(0, sections.lastIndex)
        if (from == to) return this
        val list = sections.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return copy(sections = list)
    }

    /** Stored form, e.g. `resume,apps,-quick_settings,templar_shortcuts` (`-` = hidden). */
    fun encode(): String = sections.joinToString(",") { (if (it.hidden) "-" else "") + it.section.id }

    companion object {
        val DEFAULT = QuickMenuLayout(QuickMenuSection.entries.map { SectionState(it) })

        /**
         * Tolerant decode: unknown ids (including footer ids) and duplicates are ignored, and
         * sections missing from the stored value (for example ones added in a later build) are
         * appended, shown, in their default order. Null or blank gives [DEFAULT].
         */
        fun decode(stored: String?): QuickMenuLayout {
            if (stored.isNullOrBlank()) return DEFAULT
            val seen = LinkedHashMap<QuickMenuSection, Boolean>()
            stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
                val hidden = token.startsWith("-")
                val section = QuickMenuSection.fromId(token.removePrefix("-")) ?: return@forEach
                if (section !in seen) seen[section] = hidden
            }
            QuickMenuSection.entries.forEach { if (it !in seen) seen[it] = false }
            return QuickMenuLayout(seen.map { (s, h) -> SectionState(s, h) })
        }

        /** Footer ids are never hideable; sections are. Unknown ids are not hideable. */
        fun canHide(id: String): Boolean = QuickMenuSection.fromId(id) != null

        /** The footer the menu renders, always, whatever the layout says. */
        val FOOTER: List<FooterItem> = FooterItem.entries.toList()
    }
}
