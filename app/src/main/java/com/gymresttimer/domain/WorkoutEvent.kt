package com.gymresttimer.domain

sealed interface WorkoutEvent {
    data class StartWorkout(val restDurationSeconds: Int) : WorkoutEvent
    data object StopWorkout : WorkoutEvent
    data object FinishSet : WorkoutEvent
    data object SkipRest : WorkoutEvent
    data object Add30s : WorkoutEvent
    data object Pause : WorkoutEvent
    data object Resume : WorkoutEvent
    data object Tick : WorkoutEvent
}
