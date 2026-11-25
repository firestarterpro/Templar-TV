package com.firestarterpro.templartv.data.shortcuts

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.firestarterpro.templartv.data.profile.ProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Where a binding acts. IN_APP only inside Templar; GLOBAL over other apps (accessibility service). */
enum class ShortcutScope { IN_APP, GLOBAL }

/** What a binding does. [arg] on the binding carries the app package, shelf id or flag key. */
enum class ShortcutAction {
    OPEN_APP, OPEN_SHELF, OPEN_FAVOURITES, VOICE_SEARCH, QUICK_MENU, GOOGLE_HOME_PANEL,
    GOOGLE_TV_HOME, PLAY_PAUSE, BACK_TO_TEMPLAR, TOGGLE_FEATURE,
}

/** A key or chord bound to an action, per profile. Removed bindings stay and can be restored. */
@Entity(
    tableName = "shortcut_bindings",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "chord", "scope"], unique = true, name = "index_shortcut_bindings_profile_id_chord_scope"),
        Index(value = ["sync_id"], unique = true, name = "index_shortcut_bindings_sync_id"),
    ],
)
data class ShortcutBinding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sync_id") val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "profile_id") val profileId: String,
    /** Canonical chord text from [Chords.canonical], for example "CTRL+F4". */
    val chord: String,
    val scope: ShortcutScope = ShortcutScope.IN_APP,
    val action: ShortcutAction,
    val arg: String = "",
    val enabled: Boolean = true,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    @ColumnInfo(name = "removed_at") val removedAt: Long? = null,
)

@Dao
interface ShortcutDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(binding: ShortcutBinding): Long

    @Update
    suspend fun update(binding: ShortcutBinding)

    @Query("SELECT * FROM shortcut_bindings WHERE profile_id = :profileId AND chord = :chord AND scope = :scope")
    suspend fun find(profileId: String, chord: String, scope: ShortcutScope): ShortcutBinding?

    @Query("SELECT * FROM shortcut_bindings WHERE profile_id = :profileId AND removed_at IS NULL ORDER BY chord ASC")
    fun observe(profileId: String): Flow<List<ShortcutBinding>>

    @Query("SELECT * FROM shortcut_bindings WHERE sync_id = :syncId")
    suspend fun getBySync(syncId: String): ShortcutBinding?

    @Query("SELECT * FROM shortcut_bindings WHERE profile_id = :profileId AND updated_at >= :since ORDER BY updated_at ASC, id ASC")
    suspend fun changedSince(profileId: String, since: Long): List<ShortcutBinding>

    @Query("UPDATE shortcut_bindings SET removed_at = :at, updated_at = :at WHERE id = :id")
    suspend fun markRemoved(id: Long, at: Long)

    @Query("UPDATE shortcut_bindings SET enabled = :enabled, updated_at = :at WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, at: Long)
}

/** Chord text rules. Pure, so they are tested without Android. */
object Chords {

    private val MODIFIER_ORDER = listOf("CTRL", "ALT", "SHIFT", "META")

    /** Keys Templar never binds, in any scope: they belong to the system or to Gemini. */
    val RESERVED = setOf("HOME", "BACK", "POWER", "ASSIST", "VOICE_ASSIST", "SETTINGS", "VOLUME_UP", "VOLUME_DOWN", "VOLUME_MUTE")

    /** "ctrl + f4", "F4+Ctrl" and "CTRL+F4" all become "CTRL+F4". Returns null for an empty chord. */
    fun canonical(text: String): String? {
        val parts = text.uppercase().split('+').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val mods = MODIFIER_ORDER.filter { it in parts }
        val keys = parts.filter { it !in MODIFIER_ORDER }
        if (keys.size != 1) return null
        return (mods + keys).joinToString("+")
    }

    /** Anything may be bound inside Templar except reserved keys. */
    fun isAllowed(chord: String, scope: ShortcutScope): Boolean {
        val c = canonical(chord) ?: return false
        val parts = c.split('+')
        val key = parts.last()
        if (key in RESERVED) return false
        return when (scope) {
            ShortcutScope.IN_APP -> true
            // Over other apps only a modifier chord or a function key acts, so typing is never stolen.
            ShortcutScope.GLOBAL -> parts.size > 1 || Regex("F([1-9]|1[0-2])").matches(key)
        }
    }
}
