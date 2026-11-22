package com.firestarterpro.templartv.data.watchhistory

import android.app.Application
import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchHistoryRepository(application: Application) {
    private val database = WatchHistoryRoomDatabase.getDatabase(application)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val allEntriesLiveData: LiveData<List<WatchHistoryEntry>> = database.watchHistoryDao().getAll()

    fun getAll(): LiveData<List<WatchHistoryEntry>> {
        return allEntriesLiveData
    }

    fun insert(entry: WatchHistoryEntry) {
        scope.launch { database.watchHistoryDao().insert(entry) }
    }

    fun insertAll(vararg entries: WatchHistoryEntry) {
        scope.launch { database.watchHistoryDao().insertAll(*entries) }
    }

    fun clearAll() {
        scope.launch { database.watchHistoryDao().clearAll() }
    }

    fun getByProvider(provider: String): LiveData<List<WatchHistoryEntry>> {
        return database.watchHistoryDao().getByProvider(provider)
    }

    fun getWatchedRecent(): LiveData<List<WatchHistoryEntry>> {
        return database.watchHistoryDao().getWatchedRecent()
    }
}
