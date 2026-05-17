package com.gymresttimer.domain

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StopwatchEvent {
    data object Start : StopwatchEvent
    data object Pause : StopwatchEvent
    data object Resume : StopwatchEvent
    data object Reset : StopwatchEvent
    data object Lap : StopwatchEvent
}

@Singleton
class StopwatchStateRepository @Inject constructor() {
    private val _state = MutableStateFlow(StopwatchState())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()

    fun dispatch(event: StopwatchEvent) {
        val now = SystemClock.elapsedRealtime()
        _state.update { current -> reduce(current, event, now) }
    }

    private fun reduce(state: StopwatchState, event: StopwatchEvent, now: Long): StopwatchState =
        when (event) {
            StopwatchEvent.Start -> StopwatchState(runningSinceElapsedMs = now)
            StopwatchEvent.Resume -> if (state.isRunning) state else state.copy(runningSinceElapsedMs = now)
            StopwatchEvent.Pause -> if (!state.isRunning) state else state.copy(
                accumulatedMs = state.elapsedAt(now),
                runningSinceElapsedMs = null,
            )
            StopwatchEvent.Reset -> StopwatchState()
            StopwatchEvent.Lap -> {
                val elapsed = state.elapsedAt(now)
                state.copy(laps = listOf(elapsed) + state.laps)
            }
        }
}
