package com.firestarterpro.templartv.home

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Ways for other screens (the quick menu, a keyboard shortcut) to ask the home screen to open one
 * of its editors. They do nothing when the home screen is not showing.
 */
object HomeActions {
    private val _openRowManager = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _openViewPicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _cycleView = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    val openRowManager: SharedFlow<Unit> = _openRowManager.asSharedFlow()
    val openViewPicker: SharedFlow<Unit> = _openViewPicker.asSharedFlow()
    /** +1 for the next view, -1 for the previous one. */
    val cycleView: SharedFlow<Int> = _cycleView.asSharedFlow()

    fun openRowManager() { _openRowManager.tryEmit(Unit) }
    fun openViewPicker() { _openViewPicker.tryEmit(Unit) }
    fun cycleView(direction: Int) { _cycleView.tryEmit(direction) }
}
