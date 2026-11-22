package com.firestarterpro.templartv.data.watchhistory

import android.app.Application
import androidx.lifecycle.LiveData

class WatchHistoryRepository(application: Application) {
    private val database = WatchHistoryRoomDatabase.getDatabase(application)
    private val allEntriesLiveData: LiveData<List<WatchHistoryEntry>> = database.watchHistoryDao().getAll()

    fun getAll(): LiveData<List<WatchHistoryEntry>> {
        return allEntriesLiveData
    }

    fun insert(entry: WatchHistoryEntry) {
        database.executeAsync { database.watchHistoryDao().insert(entry) }
    }

    fun insertAll(vararg entries: WatchHistoryEntry) {
        database.executeAsync { database.watchHistoryDao().insertAll(entries) }
    }

    fun clearAll() {
        database.executeAsync { database.watchHistoryDao().clearAll() }
    }

    fun getByProvider(provider: String): LiveData<List<WatchHistoryEntry>> {
        return database.watchHistoryDao().getByProvider(provider)
    }

    fun getWatchedRecent(): LiveData<List<WatchHistoryEntry>> {
        return database.watchHistoryDao().getWatchedRecent()
    }
}