package com.firestarterpro.templartv.jellyfin

/** Item and position to return to after the player was stopped (TV slept, Home pressed). */
data class Resume(val index: Int, val positionMs: Long)

object PlayerResume {
    /**
     * Index, position and autoplay for a start: fresh from the intent and playing, or back at
     * [resume] and paused, so a wake from sleep never starts sound on its own.
     */
    fun startPoint(count: Int, first: Int, resume: Resume?): Triple<Int, Long, Boolean> {
        val last = (count - 1).coerceAtLeast(0)
        return if (resume == null) Triple(first.coerceIn(0, last), 0L, true)
        else Triple(resume.index.coerceIn(0, last), resume.positionMs.coerceAtLeast(0), false)
    }
}
