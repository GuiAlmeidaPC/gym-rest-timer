package com.gymresttimer.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [WorkoutState]. Mutated by [WorkoutEvent.dispatch].
 *
 * The active rest duration is tracked here so events like [WorkoutEvent.FinishSet]
 * know what countdown to start without the service holding extra state.
 */
@Singleton
class WorkoutStateRepository @Inject constructor() {

    private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Idle)
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    @Volatile
    private var restDurationSeconds: Int = DEFAULT_REST_SECONDS

    fun currentRestDuration(): Int = restDurationSeconds

    fun dispatch(event: WorkoutEvent) {
        _state.update { current -> reduce(current, event) }
    }

    private fun reduce(state: WorkoutState, event: WorkoutEvent): WorkoutState = when (event) {
        is WorkoutEvent.StartWorkout -> {
            restDurationSeconds = event.restDurationSeconds.coerceAtLeast(1)
            WorkoutState.ActiveSet(currentSet = 1)
        }
        WorkoutEvent.StopWorkout -> WorkoutState.Idle
        WorkoutEvent.FinishSet -> when (state) {
            is WorkoutState.ActiveSet -> WorkoutState.RestCountdown(
                currentSet = state.currentSet,
                secondsRemaining = restDurationSeconds,
                durationTotal = restDurationSeconds,
            )
            else -> state
        }
        WorkoutEvent.ResetSets -> when (state) {
            WorkoutState.Idle -> state
            else -> WorkoutState.ActiveSet(currentSet = 1)
        }
        is WorkoutEvent.SetRestDuration -> {
            restDurationSeconds = event.restDurationSeconds.coerceAtLeast(1)
            when (state) {
                is WorkoutState.RestCountdown -> state.copy(
                    secondsRemaining = restDurationSeconds,
                    durationTotal = restDurationSeconds,
                    paused = state.paused,
                )
                else -> state
            }
        }
        WorkoutEvent.SkipRest -> when (state) {
            is WorkoutState.RestCountdown -> WorkoutState.ActiveSet(currentSet = state.currentSet + 1)
            else -> state
        }
        WorkoutEvent.Add30s -> when (state) {
            is WorkoutState.RestCountdown -> state.copy(
                secondsRemaining = state.secondsRemaining + 30,
                durationTotal = state.durationTotal + 30,
            )
            else -> state
        }
        WorkoutEvent.Pause -> when (state) {
            is WorkoutState.RestCountdown -> state.copy(paused = true)
            else -> state
        }
        WorkoutEvent.Resume -> when (state) {
            is WorkoutState.RestCountdown -> state.copy(paused = false)
            else -> state
        }
        WorkoutEvent.Tick -> when (state) {
            is WorkoutState.RestCountdown -> {
                if (state.paused) {
                    state
                } else {
                    val next = state.secondsRemaining - 1
                    if (next <= 0) {
                        WorkoutState.ActiveSet(currentSet = state.currentSet + 1)
                    } else {
                        state.copy(secondsRemaining = next)
                    }
                }
            }
            else -> state
        }
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 90
    }
}
