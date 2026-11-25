package com.firestarterpro.templartv.data.watchhistory

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WatchHistoryEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg entries: WatchHistoryEntry)

    @Query("SELECT * FROM watch_history ORDER BY created_timestamp DESC")
    fun getAll(): LiveData<List<WatchHistoryEntry>>

    @Query("SELECT * FROM watch_history WHERE provider = :provider ORDER BY created_timestamp DESC")
    fun getByProvider(provider: String): LiveData<List<WatchHistoryEntry>>

    @Query("SELECT * FROM watch_history WHERE profile_id = :profileId ORDER BY created_timestamp DESC")
    fun getByProfile(profileId: String): LiveData<List<WatchHistoryEntry>>

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()

    @Query("SELECT * FROM watch_history WHERE watched_status = 1 ORDER BY stop_timestamp DESC LIMIT 10")
    fun getWatchedRecent(): LiveData<List<WatchHistoryEntry>>

    @Query("SELECT * FROM watch_history WHERE evidence_source = :source ORDER BY created_timestamp DESC")
    fun getByEvidenceSource(source: String): LiveData<List<WatchHistoryEntry>>
}
