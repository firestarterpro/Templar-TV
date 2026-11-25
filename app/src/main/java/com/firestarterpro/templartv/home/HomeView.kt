package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.Flag

/**
 * The ways the home screen can be laid out. Every view reads the same shelves, favourites and row
 * order, so switching never loses an edit. [ROWS] has no flag: it is the view that always works.
 */
enum class HomeView(val key: String, val label: String, val description: String, val flag: Flag?) {
    ROWS("rows", "Rows", "Shelves stacked top to bottom, tiles left to right.", null),
    COLUMNS("columns", "Columns", "Each shelf is a column of posters. See every shelf at once.", Flag.HOME_VIEW_COLUMNS),
    RAIL_GRID("rail_grid", "Rail + grid", "Shelf names on the left, the chosen shelf as a grid. Fastest to jump around.", Flag.HOME_VIEW_RAIL_GRID),
    SPOTLIGHT("spotlight", "Spotlight", "Big artwork for the focused show with one strip below.", Flag.HOME_VIEW_SPOTLIGHT),
    WALL("wall", "Wall", "One big poster grid with filter chips across the top.", Flag.HOME_VIEW_WALL),
    LIST("list", "List", "Text-first list: title, provider, episode and progress.", Flag.HOME_VIEW_LIST);

    companion object {
        fun fromKey(key: String?): HomeView? = entries.firstOrNull { it.key == key }

        /** The view to show: the saved one if it exists and its flag is on, otherwise [ROWS]. */
        fun resolve(key: String?, isOn: (Flag) -> Boolean): HomeView {
            val view = fromKey(key) ?: return ROWS
            val flag = view.flag
            return if (flag == null || isOn(flag)) view else ROWS
        }

        /** Views the picker offers, in order. [ROWS] is always first and always there. */
        fun available(isOn: (Flag) -> Boolean): List<HomeView> =
            entries.filter { it.flag == null || isOn(it.flag) }
    }
}
