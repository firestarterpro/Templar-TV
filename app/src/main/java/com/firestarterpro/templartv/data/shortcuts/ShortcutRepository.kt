package com.firestarterpro.templartv.data.shortcuts

import com.firestarterpro.templartv.Flag
import com.firestarterpro.templartv.data.FlagGate
import kotlinx.coroutines.flow.Flow

/** Key chord bindings per profile. Needs [Flag.KEYBOARD_SHORTCUTS]; GLOBAL bindings also need [Flag.GLOBAL_SHORTCUTS]. */
class ShortcutRepository(
    private val dao: ShortcutDao,
    private val gate: FlagGate = FlagGate.AllOn,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(profileId: String): Flow<List<ShortcutBinding>> = dao.observe(profileId)

    /**
     * Binds [chord] to [action], replacing (or reviving) any binding for the same chord and scope.
     * Returns null when the chord is empty, reserved, not allowed in that scope, or a flag is off.
     */
    suspend fun bind(
        profileId: String, chord: String, scope: ShortcutScope, action: ShortcutAction, arg: String = "",
    ): Long? {
        if (!gate.isOn(Flag.KEYBOARD_SHORTCUTS)) return null
        if (scope == ShortcutScope.GLOBAL && !gate.isOn(Flag.GLOBAL_SHORTCUTS)) return null
        if (!Chords.isAllowed(chord, scope)) return null
        val canonical = Chords.canonical(chord) ?: return null
        val now = clock()
        val existing = dao.find(profileId, canonical, scope)
        return if (existing == null) {
            dao.insert(ShortcutBinding(profileId = profileId, chord = canonical, scope = scope, action = action, arg = arg, updatedAt = now))
        } else {
            dao.update(existing.copy(action = action, arg = arg, enabled = true, removedAt = null, updatedAt = now))
            existing.id
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Boolean {
        if (!gate.isOn(Flag.KEYBOARD_SHORTCUTS)) return false
        dao.setEnabled(id, enabled, clock())
        return true
    }

    /** Unbinds a chord. The row stays so it can be restored by binding the chord again. */
    suspend fun unbind(id: Long): Boolean {
        if (!gate.isOn(Flag.KEYBOARD_SHORTCUTS)) return false
        dao.markRemoved(id, clock())
        return true
    }

    suspend fun changedSince(profileId: String, since: Long): List<ShortcutBinding> = dao.changedSince(profileId, since)
}
