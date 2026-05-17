package com.gymresttimer.domain

sealed interface WorkoutState {
    data object Idle : WorkoutState

    data class ActiveSet(
        val currentSet: Int,
    ) : WorkoutState

    data class RestCountdown(
        val currentSet: Int,
        val secondsRemaining: Int,
        val durationTotal: Int,
        val paused: Boolean = false,
    ) : WorkoutState
}
