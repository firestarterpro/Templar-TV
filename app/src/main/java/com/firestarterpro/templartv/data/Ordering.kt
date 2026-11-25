package com.firestarterpro.templartv.data

/** Pure helpers for the user-orderable lists (shelves, favourites, rows). No Android or Room here. */
object Ordering {

    /**
     * Returns [ids] with [id] moved to [toIndex] (clamped to the list). If [id] is not in the list
     * the list is returned unchanged.
     */
    fun <T> moveTo(ids: List<T>, id: T, toIndex: Int): List<T> {
        val from = ids.indexOf(id)
        if (from < 0) return ids
        val without = ids.toMutableList().also { it.removeAt(from) }
        without.add(toIndex.coerceIn(0, without.size), id)
        return without
    }

    /**
     * Applies an order sent by another device. Items in [desired] come first, in that order (unknown
     * and repeated ids are ignored); items it did not mention follow in their current order.
     */
    fun <T> applyOrder(current: List<T>, desired: List<T>): List<T> {
        val known = current.toSet()
        val first = desired.filter { it in known }.distinct()
        val firstSet = first.toSet()
        return first + current.filter { it !in firstSet }
    }
}

/** Builds the keys that stop the same show from appearing twice on a shelf or in favourites. */
object DedupeKey {

    /** Lowercase letters and digits only, single spaces. "Law & Order: SVU" and "law order svu" match. */
    fun normalize(title: String): String =
        title.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /**
     * One tile per series: episodes of the same series share a key. Without a series title the
     * provider's content id is used, then the title.
     */
    fun forShelf(seriesTitle: String, providerContentId: String, title: String): String = when {
        seriesTitle.isNotBlank() -> "s:" + normalize(seriesTitle)
        providerContentId.isNotBlank() -> "c:" + providerContentId.trim()
        else -> "t:" + normalize(title)
    }

    /** A TMDB id is the best identity for a favourite show; otherwise the normalized title. */
    fun forFavourite(tmdbId: Long, title: String): String =
        if (tmdbId > 0) "tmdb:$tmdbId" else "t:" + normalize(title)
}
