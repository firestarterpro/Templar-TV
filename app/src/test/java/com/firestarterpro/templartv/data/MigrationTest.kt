package com.firestarterpro.templartv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.firestarterpro.templartv.data.shelf.ShelfEntry
import com.firestarterpro.templartv.data.shelf.ShelfSource
import com.firestarterpro.templartv.data.watchhistory.ReviewState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a version 1 database by hand (the exact table the app shipped), then opens it with the
 * current Room database. Room checks the migrated tables against the entities, so a wrong migration
 * fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    // name to SQL type, in the order Room created them in version 1. Every column is NOT NULL.
    private val v1Columns = listOf(
        "profile_id" to "TEXT", "provider" to "TEXT", "provider_alias" to "TEXT", "package_name" to "TEXT",
        "content_type" to "TEXT", "canonical_title" to "TEXT", "original_title" to "TEXT", "series_title" to "TEXT",
        "season_number" to "INTEGER", "episode_number" to "INTEGER", "episode_title" to "TEXT",
        "provider_content_id" to "TEXT", "tmdb_id" to "INTEGER", "imdb_id" to "TEXT", "tvdb_id" to "TEXT",
        "jellyfin_item_id" to "INTEGER", "start_timestamp" to "INTEGER", "stop_timestamp" to "INTEGER",
        "duration_watched" to "INTEGER", "playback_position" to "INTEGER", "media_runtime" to "INTEGER",
        "completion_percentage" to "INTEGER", "playback_state" to "TEXT", "watched_status" to "INTEGER",
        "rewatch_count" to "INTEGER", "candidate_matches" to "TEXT", "match_confidence" to "REAL",
        "evidence_source" to "TEXT", "evidence_details" to "TEXT", "manual_correction" to "INTEGER",
        "artwork_reference" to "TEXT", "metadata_source" to "TEXT", "sync_state" to "TEXT",
        "created_timestamp" to "INTEGER", "updated_timestamp" to "INTEGER",
    )

    @Test fun v1ToV2KeepsRowsAndMatchesTheSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }

        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        val cols = v1Columns.joinToString(", ") { "${it.first} ${it.second} NOT NULL" }
        raw.execSQL("CREATE TABLE IF NOT EXISTS watch_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, $cols)")
        val values = v1Columns.joinToString(", ") { (n, t) ->
            when {
                n == "canonical_title" -> "'Old Show'"
                n == "stop_timestamp" -> "5"
                t == "TEXT" -> "''"
                else -> "0"
            }
        }
        raw.execSQL("INSERT INTO watch_history (${v1Columns.joinToString(", ") { it.first }}) VALUES ($values)")
        raw.version = 1
        raw.close()

        val db = Room.databaseBuilder(context, TemplarDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .addCallback(TemplarDatabase.ensureDefaultProfile)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                // The old row survives, moves onto the default profile, and waits for review.
                val queue = db.watchHistoryDao().observeReviewQueue(ProfileIds.DEFAULT).first()
                assertEquals(listOf("Old Show"), queue.map { it.canonicalTitle })
                assertEquals(ReviewState.NEEDS_REVIEW, queue.single().reviewState)

                // New tables work, and the default profile exists for their foreign keys.
                assertNotNull(db.profileDao().get(ProfileIds.DEFAULT))
                val id = db.shelfDao().insert(
                    ShelfEntry(profileId = ProfileIds.DEFAULT, provider = "max", dedupeKey = "t:x", title = "X", source = ShelfSource.MANUAL),
                )
                assertEquals(id, db.shelfDao().get(id)!!.id)
            }
        } finally {
            db.close()
        }
    }
}
