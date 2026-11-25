package com.firestarterpro.templartv.data.profile

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.firestarterpro.templartv.data.ProfileIds
import com.firestarterpro.templartv.data.layout.RowLayoutRepository
import com.firestarterpro.templartv.data.settings.ProfileSetting
import com.firestarterpro.templartv.data.settings.ProfileSettingDao
import com.firestarterpro.templartv.data.settings.SettingKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Local profiles and the active-profile choice. Nothing here deletes a profile: [archive] hides it
 * and [unarchive] brings it back, with all its history, shelves and favourites intact.
 */
class ProfileRepository(
    private val db: RoomDatabase,
    private val dao: ProfileDao,
    private val settings: ProfileSettingDao,
    private val rows: RowLayoutRepository,
    private val activeStore: ActiveProfileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val activeProfileId: StateFlow<String> get() = activeStore.flow

    fun observeProfiles(): Flow<List<ProfileEntity>> = dao.observeActive()

    /** Makes sure the default profile and its default rows exist. Safe to call on every start. */
    suspend fun ensureDefault() {
        dao.insertIfMissing(ProfileEntity(ProfileIds.DEFAULT, "Me", 0, false, clock()))
        rows.ensureDefaults(ProfileIds.DEFAULT)
    }

    suspend fun create(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        db.withTransaction {
            dao.insertIfMissing(ProfileEntity(id, trimmed, dao.maxSortOrder() + 1, false, clock()))
            rows.ensureDefaults(id)
        }
        return id
    }

    suspend fun rename(id: String, name: String) {
        if (name.isNotBlank()) dao.rename(id, name.trim())
    }

    /** Hides a profile. The default profile cannot be archived. */
    suspend fun archive(id: String): Boolean {
        if (id == ProfileIds.DEFAULT) return false
        dao.setArchived(id, true)
        if (activeStore.flow.value == id) activeStore.set(ProfileIds.DEFAULT)
        return true
    }

    suspend fun unarchive(id: String) = dao.setArchived(id, false)

    /** Switches profile. Returns false if it does not exist or is archived. */
    suspend fun switchTo(id: String): Boolean {
        val profile = dao.get(id) ?: return false
        if (profile.archived) return false
        activeStore.set(id)
        return true
    }

    // Per-profile settings (home view, star action, ...). Defaults come from SettingKeys.

    suspend fun setting(profileId: String, key: String): String? =
        settings.get(profileId, key) ?: SettingKeys.defaults[key]

    fun observeSetting(profileId: String, key: String): Flow<String?> =
        settings.observe(profileId, key).map { it ?: SettingKeys.defaults[key] }

    suspend fun putSetting(profileId: String, key: String, value: String) =
        settings.put(ProfileSetting(profileId, key, value))

    /** Back to the default for this setting. */
    suspend fun resetSetting(profileId: String, key: String) = settings.reset(profileId, key)
}

/** Remembers which profile is active, in a small private file, and exposes it as a flow. */
class ActiveProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("templar_profiles", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        runCatching { prefs.getString(KEY, null) }.getOrNull() ?: ProfileIds.DEFAULT,
    )
    val flow: StateFlow<String> get() = state

    fun set(id: String) {
        prefs.edit().putString(KEY, id).apply()
        state.value = id
    }

    private companion object { const val KEY = "active_profile_id" }
}
