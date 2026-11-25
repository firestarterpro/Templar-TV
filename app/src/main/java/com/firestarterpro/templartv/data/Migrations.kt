package com.firestarterpro.templartv.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written migrations. Room checks the result against the exported schema when the database
 * opens, so a mismatch fails loudly in MigrationTest instead of on a TV.
 */
object Migrations {

    fun insertDefaultProfileSql(now: Long) =
        "INSERT OR IGNORE INTO profiles (id, name, sort_order, archived, created_at) VALUES ('${ProfileIds.DEFAULT}', 'Me', 0, 0, $now)"

    /**
     * v1 had only watch_history, never used by the app. v2 keeps it and its rows, adds review state,
     * a Jellyfin id string and a removed timestamp, then adds profiles, evidence, candidates,
     * shelves, favourites, row layout, shortcuts and settings.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE watch_history ADD COLUMN review_state TEXT NOT NULL DEFAULT 'NEEDS_REVIEW'")
            db.execSQL("ALTER TABLE watch_history ADD COLUMN jellyfin_item_ref TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE watch_history ADD COLUMN removed_at INTEGER")
            db.execSQL("UPDATE watch_history SET profile_id = '${ProfileIds.DEFAULT}' WHERE profile_id = ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_history_profile_id_stop_timestamp ON watch_history (profile_id, stop_timestamp)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS profiles (id TEXT NOT NULL, name TEXT NOT NULL, sort_order INTEGER NOT NULL, " +
                    "archived INTEGER NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id))",
            )
            db.execSQL(insertDefaultProfileSql(System.currentTimeMillis()))

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS watch_evidence (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, watch_id INTEGER NOT NULL, " +
                    "source TEXT NOT NULL, confidence REAL NOT NULL, detail TEXT NOT NULL, observed_at INTEGER NOT NULL, " +
                    "FOREIGN KEY(watch_id) REFERENCES watch_history(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_evidence_watch_id ON watch_evidence (watch_id)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS match_candidates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, watch_id INTEGER NOT NULL, " +
                    "title TEXT NOT NULL, content_type TEXT NOT NULL, tmdb_id INTEGER NOT NULL, imdb_id TEXT NOT NULL, " +
                    "confidence REAL NOT NULL, source TEXT NOT NULL, chosen INTEGER NOT NULL, created_at INTEGER NOT NULL, " +
                    "FOREIGN KEY(watch_id) REFERENCES watch_history(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_match_candidates_watch_id ON match_candidates (watch_id)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS shelf_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sync_id TEXT NOT NULL, " +
                    "profile_id TEXT NOT NULL, provider TEXT NOT NULL, dedupe_key TEXT NOT NULL, title TEXT NOT NULL, " +
                    "series_title TEXT NOT NULL, content_type TEXT NOT NULL, season_number INTEGER NOT NULL, episode_number INTEGER NOT NULL, " +
                    "episode_title TEXT NOT NULL, provider_content_id TEXT NOT NULL, deep_link TEXT NOT NULL, package_name TEXT NOT NULL, " +
                    "tmdb_id INTEGER NOT NULL, imdb_id TEXT NOT NULL, artwork_ref TEXT NOT NULL, progress_ms INTEGER NOT NULL, " +
                    "runtime_ms INTEGER NOT NULL, progress_kind TEXT NOT NULL, source TEXT NOT NULL, watch_history_id INTEGER, " +
                    "position INTEGER NOT NULL, pinned INTEGER NOT NULL, hidden INTEGER NOT NULL, watched INTEGER NOT NULL, " +
                    "user_confirmed INTEGER NOT NULL, added_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, removed_at INTEGER, " +
                    "FOREIGN KEY(profile_id) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(watch_history_id) REFERENCES watch_history(id) ON UPDATE NO ACTION ON DELETE SET NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shelf_entries_profile_id_provider_dedupe_key ON shelf_entries (profile_id, provider, dedupe_key)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shelf_entries_watch_history_id ON shelf_entries (watch_history_id)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shelf_entries_sync_id ON shelf_entries (sync_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_shelf_entries_profile_id_updated_at ON shelf_entries (profile_id, updated_at)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS favourite_shows (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sync_id TEXT NOT NULL, " +
                    "profile_id TEXT NOT NULL, dedupe_key TEXT NOT NULL, title TEXT NOT NULL, tmdb_id INTEGER NOT NULL, imdb_id TEXT NOT NULL, " +
                    "tvdb_id TEXT NOT NULL, preferred_provider TEXT NOT NULL, artwork_ref TEXT NOT NULL, position INTEGER NOT NULL, " +
                    "added_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, removed_at INTEGER, " +
                    "FOREIGN KEY(profile_id) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favourite_shows_profile_id_dedupe_key ON favourite_shows (profile_id, dedupe_key)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favourite_shows_sync_id ON favourite_shows (sync_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_favourite_shows_profile_id_updated_at ON favourite_shows (profile_id, updated_at)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS row_layout (profile_id TEXT NOT NULL, row_id TEXT NOT NULL, kind TEXT NOT NULL, provider TEXT NOT NULL, " +
                    "title_override TEXT NOT NULL, position INTEGER NOT NULL, visible INTEGER NOT NULL, tile_size TEXT NOT NULL, " +
                    "options_json TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(profile_id, row_id), " +
                    "FOREIGN KEY(profile_id) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS shortcut_bindings (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sync_id TEXT NOT NULL, " +
                    "profile_id TEXT NOT NULL, chord TEXT NOT NULL, scope TEXT NOT NULL, action TEXT NOT NULL, arg TEXT NOT NULL, " +
                    "enabled INTEGER NOT NULL, updated_at INTEGER NOT NULL, removed_at INTEGER, " +
                    "FOREIGN KEY(profile_id) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shortcut_bindings_profile_id_chord_scope ON shortcut_bindings (profile_id, chord, scope)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_shortcut_bindings_sync_id ON shortcut_bindings (sync_id)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS profile_settings (profile_id TEXT NOT NULL, `key` TEXT NOT NULL, value TEXT NOT NULL, " +
                    "PRIMARY KEY(profile_id, `key`), " +
                    "FOREIGN KEY(profile_id) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
