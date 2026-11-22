package com.firestarterpro.templartv.data.watchhistory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WatchHistoryEntry::class], version = 1, exportSchema = false)
abstract class WatchHistoryRoomDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: WatchHistoryRoomDatabase? = null

        fun getDatabase(context: Context): WatchHistoryRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val database = Room.databaseBuilder(
                    context.applicationContext,
                    WatchHistoryRoomDatabase::class.java,
                    "templar-tv-watchhistory"
                ).build()
                INSTANCE = database
                database
            }
        }
    }
}