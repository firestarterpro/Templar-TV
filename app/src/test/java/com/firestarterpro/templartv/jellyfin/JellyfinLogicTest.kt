package com.firestarterpro.templartv.jellyfin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinLogicTest {

    private val user = JellyfinUser("u1", isAdmin = false, disabled = false)
    private val admin = JellyfinUser("a1", isAdmin = true, disabled = false)
    private val off = JellyfinUser("x1", isAdmin = true, disabled = true)

    @Test fun picksSavedUserWhenStillEnabled() {
        assertEquals("u1", JellyfinClient.pickUser(listOf(admin, user), "u1")?.id)
    }

    @Test fun prefersAdminOverFirstUser() {
        assertEquals("a1", JellyfinClient.pickUser(listOf(user, admin), "")?.id)
    }

    @Test fun skipsDisabledAndUnknownSaved() {
        assertEquals("u1", JellyfinClient.pickUser(listOf(off, user), "gone")?.id)
        assertNull(JellyfinClient.pickUser(listOf(off), ""))
    }

    @Test fun previewLibrariesAreLeftOut() {
        val libs = listOf(
            JellyfinLibrary("1", "Music", "music"),
            JellyfinLibrary("2", "Music (preview)", "music"),
            JellyfinLibrary("3", "Movies", "movies"),
        )
        assertEquals(listOf("1"), JellyfinClient.pickLibraries(libs, JellyfinKind.ALBUM).map { it.id })
        assertEquals(listOf("3"), JellyfinClient.pickLibraries(libs, JellyfinKind.MOVIE).map { it.id })
        assertTrue(JellyfinClient.pickLibraries(libs, JellyfinKind.SERIES).isEmpty())
    }

    @Test fun duplicateAlbumsShowOnce() {
        val a = JellyfinItem("1", "Discovery", "Daft Punk", "", "MusicAlbum", true)
        val b = a.copy(id = "2", title = "discovery ")
        val c = a.copy(id = "3", subtitle = "Someone Else")
        assertEquals(listOf("1", "3"), JellyfinClient.dedupe(listOf(a, b, c)).map { it.id })
    }

    @Test fun parsesEpisodesAndTracks() {
        val ep = with(JellyfinClient) {
            JSONObject("""{"Id":"e1","Name":"Pilot","Type":"Episode","IndexNumber":1,"IsFolder":false}""").toItem("http://s")
        }
        assertEquals("Episode 1", ep.subtitle)
        assertFalse(ep.isFolder)
        assertEquals("http://s/Items/e1/Images/Primary?maxWidth=420", ep.imageUrl)
        val track = with(JellyfinClient) {
            JSONObject("""{"Id":"t1","Name":"One More Time","Type":"Audio","IndexNumber":1,"Artists":["Daft Punk"]}""").toItem("http://s")
        }
        assertEquals("1. One More Time", track.title)
        assertEquals("Daft Punk", track.subtitle)
        assertTrue(JellyfinClient.streamUrl("http://s", track).startsWith("http://s/Audio/t1/stream"))
    }

    @Test fun keyIsSentAsMediaBrowserToken() {
        val h = JellyfinClient.authHeader("abc")
        assertTrue(h.startsWith("MediaBrowser "))
        assertTrue(h.contains("Token=\"abc\""))
    }

    @Test fun firstStartPlaysFromIntent() {
        assertEquals(Triple(2, 0L, true), PlayerResume.startPoint(5, 2, null))
    }

    @Test fun wakeResumesPausedAtSavedSpot() {
        assertEquals(Triple(3, 61_000L, false), PlayerResume.startPoint(5, 0, Resume(3, 61_000L)))
        assertEquals(Triple(4, 0L, false), PlayerResume.startPoint(5, 0, Resume(9, -5L)))
    }
}
