package com.firestarterpro.templartv.home

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A request another screen made of the home screen. */
enum class HomeRequest { ROW_MANAGER, VIEW_PICKER, NEXT_VIEW, PREVIOUS_VIEW }

/**
 * Ways for other screens (the quick menu, a keyboard shortcut) to ask the home screen to open one
 * of its editors.
 *
 * If the big-box home is not composed yet (for example the quick menu was opened over another app
 * and Templar's home starts fresh), the last request is kept and the home screen picks it up once
 * with [consumePending] when it appears. Only one request is kept; a newer one replaces it.
 */
object HomeActions {
    private val _openRowManager = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _openViewPicker = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _cycleView = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private val pending = AtomicReference<HomeRequest?>(null)

    val openRowManager: SharedFlow<Unit> = _openRowManager.asSharedFlow()
    val openViewPicker: SharedFlow<Unit> = _openViewPicker.asSharedFlow()
    /** +1 for the next view, -1 for the previous one. */
    val cycleView: SharedFlow<Int> = _cycleView.asSharedFlow()

    fun openRowManager() {
        if (_openRowManager.subscriptionCount.value > 0) _openRowManager.tryEmit(Unit) else pending.set(HomeRequest.ROW_MANAGER)
    }

    fun openViewPicker() {
        if (_openViewPicker.subscriptionCount.value > 0) _openViewPicker.tryEmit(Unit) else pending.set(HomeRequest.VIEW_PICKER)
    }

    fun cycleView(direction: Int) {
        if (direction == 0) return
        if (_cycleView.subscriptionCount.value > 0) {
            _cycleView.tryEmit(direction)
        } else {
            pending.set(if (direction > 0) HomeRequest.NEXT_VIEW else HomeRequest.PREVIOUS_VIEW)
        }
    }

    /** The request made while no home screen was listening, at most once. */
    fun consumePending(): HomeRequest? = pending.getAndSet(null)
}
