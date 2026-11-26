package com.firestarterpro.templartv.home

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLayoutTest {
    private val apps = listOf("a", "b", "c", "d")
    private fun arrange(fav: Set<String> = emptySet(), order: List<String> = emptyList(), hidden: Set<String> = emptySet()) =
        AppLayoutStore.arrangeBy(apps, { it }, fav, order, hidden)

    @Test fun favouritesFirstWithoutSavedOrder() = assertEquals(listOf("c", "a", "b", "d"), arrange(fav = setOf("c")))

    @Test fun savedOrderWinsAndNewAppsFollow() = assertEquals(listOf("d", "b", "a", "c"), arrange(order = listOf("d", "b")))

    @Test fun hiddenAppsAreDropped() = assertEquals(listOf("a", "d"), arrange(hidden = setOf("b", "c")))

    @Test fun unknownSavedPackagesAreIgnored() = assertEquals(listOf("b", "a", "c", "d"), arrange(order = listOf("gone", "b")))
}
