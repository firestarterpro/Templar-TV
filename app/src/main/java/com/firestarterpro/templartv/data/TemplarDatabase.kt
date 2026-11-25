package com.firestarterpro.templartv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.firestarterpro.templartv.data.favourites.FavouriteShow
import com.firestarterpro.templartv.data.favourites.FavouriteShowDao
import com.firestarterpro.templartv.data.layout.RowLayoutDao
import com.firestarterpro.templartv.data.layout.RowLayoutEntry
import com.firestarterpro.templartv.data.profile.ProfileDao
import com.firestarterpro.templartv.data.profile.ProfileEntity
import com.firestarterpro.templartv.data.settings.ProfileSetting
import com.firestarterpro.templartv.data.settings.ProfileSettingDao
import com.firestarterpro.templartv.data.shelf.ShelfDao
import com.firestarterpro.templartv.data.shelf.ShelfEntry
import com.firestarterpro.templartv.data.shortcuts.ShortcutBinding
import com.firestarterpro.templartv.data.shortcuts.ShortcutDao
import com.firestarterpro.templartv.data.watchhistory.MatchCandidateEntity
import com.firestarterpro.templartv.data.watchhistory.WatchEvidenceEntity
import com.firestarterpro.templartv.data.watchhistory.WatchHistoryDao
import com.firestarterpro.templartv.data.watchhistory.WatchHistoryEntry

/**
 * Templar TV's one local database. It started as the watch-history database (version 1) and keeps
 * that file name so existing data carries over through [Migrations.MIGRATION_1_2].
 *
 * Schemas are exported to app/schemas. A schema change needs a version bump, a migration, and a
 * migration test. Nothing in this database is ever deleted by the app; "remove" sets a timestamp.
 */
@Database(
    entities = [
        ProfileEntity::class,
        WatchHistoryEntry::class,
        WatchEvidenceEntity::class,
        MatchCandidateEntity::class,
        ShelfEntry::class,
        FavouriteShow::class,
        RowLayoutEntry::class,
        ShortcutBinding::class,
        ProfileSetting::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class TemplarDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun shelfDao(): ShelfDao
    abstract fun favouriteShowDao(): FavouriteShowDao
    abstract fun rowLayoutDao(): RowLayoutDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun profileSettingDao(): ProfileSettingDao

    companion object {
        /** Same file name as version 1 so the existing database is upgraded in place. */
        const val FILE_NAME = "templar-tv-watchhistory"

        /** The default profile must exist before anything that points at it is written. */
        val ensureDefaultProfile = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL(Migrations.insertDefaultProfileSql(System.currentTimeMillis()))
            }
        }

        @Volatile
        private var instance: TemplarDatabase? = null

        fun get(context: Context): TemplarDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, TemplarDatabase::class.java, FILE_NAME)
                    .addMigrations(*Migrations.ALL)
                    .addCallback(ensureDefaultProfile)
                    .build()
                    .also { instance = it }
            }
    }
}
