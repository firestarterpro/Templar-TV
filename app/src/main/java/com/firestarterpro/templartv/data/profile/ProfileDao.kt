package com.firestarterpro.templartv.data.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(profile: ProfileEntity): Long

    @Query("SELECT * FROM profiles WHERE archived = 0 ORDER BY sort_order ASC, created_at ASC")
    fun observeActive(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: String): ProfileEntity?

    @Query("UPDATE profiles SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE profiles SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM profiles")
    suspend fun maxSortOrder(): Int
}
