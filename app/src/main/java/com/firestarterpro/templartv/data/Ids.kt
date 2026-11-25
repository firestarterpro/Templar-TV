package com.firestarterpro.templartv.data

/** Ids shared by every table. Kept in one place so providers are spelled the same everywhere. */
object ProfileIds {
    /** The profile that always exists. Rows from before profiles existed are moved onto it. */
    const val DEFAULT = "default"
}

/** Stable provider ids stored in the database. Display names live in the UI layer. */
object ProviderIds {
    const val PEACOCK = "peacock"
    const val PARAMOUNT_PLUS = "paramount_plus"
    const val PRIME_VIDEO = "prime_video"
    const val MAX = "max"
    const val NETFLIX = "netflix"
    const val HULU = "hulu"
    const val JELLYFIN = "jellyfin"

    /** Initial priority order from the product plan. Netflix and Hulu come later. */
    val PRIORITY = listOf(PEACOCK, PARAMOUNT_PLUS, PRIME_VIDEO, MAX, NETFLIX, HULU)
}
