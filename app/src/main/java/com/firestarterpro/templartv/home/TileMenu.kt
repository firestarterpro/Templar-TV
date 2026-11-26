package com.firestarterpro.templartv.home

import com.firestarterpro.templartv.data.shelf.ShelfSource

enum class MenuAction(val label: String) {
    OPEN("Open"),
    ADD_FAVOURITE("Add to Favourites"),
    REMOVE_FAVOURITE("Remove from Favourites"),
    MOVE("Move"),
    REMOVE("Remove"),
    MORE("More…"),
    MARK_WATCHED("Mark watched"),
    MARK_UNWATCHED("Mark not watched"),
    CHANGE_PROVIDER("Change provider"),
    SHOW_EVIDENCE("Show evidence"),
    ADD_SHOW_HERE("Add a show here"),
    EDIT_ROW("Edit this row…"),
}

/** What the tile menu needs to know to decide which actions to show. */
data class TileMenuContext(
    val tile: HomeTile,
    val row: HomeRowModel,
    /** The tile's show is already in Favourite Shows. */
    val isFavourite: Boolean,
    /** Flag: SHELF_EDITING. */
    val shelfEditing: Boolean,
    /** Flag: FAVOURITE_SHOWS. */
    val favouritesOn: Boolean,
    /** Flag: ROW_MANAGER. */
    val rowEditing: Boolean,
    /** False in views where moving this tile is not possible (for example the Wall's "All" filter). */
    val moveAllowed: Boolean,
)

/**
 * The hold-OK menu. Up to five actions, chosen by where the tile came from (design review 3.2):
 * the most likely action first and the rest behind "More…". Plain verbs, never icons alone.
 */
object TileMenu {

    fun primary(c: TileMenuContext): List<MenuAction> {
        val t = c.tile
        val favToggle = when {
            !c.favouritesOn -> null
            c.isFavourite -> MenuAction.REMOVE_FAVOURITE
            else -> MenuAction.ADD_FAVOURITE
        }
        val items: List<MenuAction?> = when (t.kind) {
            TileKind.SHELF -> if (t.source == ShelfSource.PROVIDER_ROW) {
                listOf(MenuAction.OPEN, favToggle, MenuAction.REMOVE.takeIf { c.shelfEditing })
            } else {
                listOf(
                    MenuAction.OPEN, favToggle,
                    MenuAction.MOVE.takeIf { c.shelfEditing && c.moveAllowed },
                    MenuAction.REMOVE.takeIf { c.shelfEditing },
                )
            }
            TileKind.FAVOURITE -> listOf(
                MenuAction.OPEN,
                MenuAction.MOVE.takeIf { c.favouritesOn && c.moveAllowed },
                MenuAction.REMOVE_FAVOURITE.takeIf { c.favouritesOn },
            )
            TileKind.CHANNEL -> listOf(MenuAction.OPEN, favToggle)
            TileKind.ADD -> return listOfNotNull(MenuAction.ADD_SHOW_HERE.takeIf { c.shelfEditing || c.favouritesOn }, MenuAction.EDIT_ROW.takeIf { c.rowEditing })
            TileKind.MORE -> return listOfNotNull(MenuAction.OPEN, MenuAction.EDIT_ROW.takeIf { c.rowEditing })
            TileKind.APP -> return emptyList()
        }
        val actions = items.filterNotNull()
        return if (more(c).isEmpty()) actions.take(5) else actions.take(4) + MenuAction.MORE
    }

    fun more(c: TileMenuContext): List<MenuAction> {
        val t = c.tile
        val out = mutableListOf<MenuAction>()
        if (t.kind == TileKind.SHELF && c.shelfEditing) {
            out += if (t.watched) MenuAction.MARK_UNWATCHED else MenuAction.MARK_WATCHED
            if (t.source != ShelfSource.PROVIDER_ROW) out += MenuAction.CHANGE_PROVIDER
        }
        if (t.kind == TileKind.SHELF || t.kind == TileKind.FAVOURITE) out += MenuAction.SHOW_EVIDENCE
        if (c.row.canAddShow && (c.shelfEditing || c.favouritesOn)) out += MenuAction.ADD_SHOW_HERE
        if (c.rowEditing) out += MenuAction.EDIT_ROW
        return out
    }
}
