package com.firestarterpro.templartv.data.settings

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.firestarterpro.templartv.data.profile.ProfileEntity
import kotlinx.coroutines.flow.Flow

/** One per-profile setting as a key and a text value. Typed access lives in [SettingKeys]. */
@Entity(
    tableName = "profile_settings",
    primaryKeys = ["profile_id", "key"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProfileSetting(
    @ColumnInfo(name = "profile_id") val profileId: String,
    val key: String,
    val value: String,
)

@Dao
interface ProfileSettingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: ProfileSetting)

    @Query("SELECT value FROM profile_settings WHERE profile_id = :profileId AND key = :key")
    suspend fun get(profileId: String, key: String): String?

    @Query("SELECT value FROM profile_settings WHERE profile_id = :profileId AND key = :key")
    fun observe(profileId: String, key: String): Flow<String?>

    @Query("SELECT * FROM profile_settings WHERE profile_id = :profileId")
    suspend fun all(profileId: String): List<ProfileSetting>

    /** Removes the stored override so the default applies again. Settings are not user content. */
    @Query("DELETE FROM profile_settings WHERE profile_id = :profileId AND key = :key")
    suspend fun reset(profileId: String, key: String)
}

/** Known setting keys with their defaults. The UI picks values; this layer only stores them. */
object SettingKeys {
    /** rows | columns | rail_grid | spotlight | wall | list. Rows always works; Columns is the default. */
    const val HOME_VIEW = "home_view"
    /** How many columns the Columns view shows at once (3 to 6). */
    const val COLUMNS_COUNT = "columns_count"
    const val AUTO_ADD_FROM_PLAYBACK = "auto_add_from_playback"
    const val MIN_WATCH_SECONDS = "min_watch_seconds"
    const val SHELF_LENGTH = "shelf_length"

    val defaults: Map<String, String> = mapOf(
        HOME_VIEW to "columns",
        COLUMNS_COUNT to "4",
        AUTO_ADD_FROM_PLAYBACK to "true",
        MIN_WATCH_SECONDS to "120",
        SHELF_LENGTH to "20",
    )
}
