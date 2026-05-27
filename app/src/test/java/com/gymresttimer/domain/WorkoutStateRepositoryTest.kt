package com.gymresttimer.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutStateRepositoryTest {
    @Test
    fun changingRestDurationPreservesPausedCountdownState() {
        val repository = WorkoutStateRepository()

        repository.dispatch(WorkoutEvent.StartWorkout(90))
        repository.dispatch(WorkoutEvent.FinishSet)
        repository.dispatch(WorkoutEvent.Pause)
        repository.dispatch(WorkoutEvent.SetRestDuration(45))

        assertEquals(
            WorkoutState.RestCountdown(
                currentSet = 1,
                secondsRemaining = 45,
                durationTotal = 45,
                paused = true,
            ),
            repository.state.value,
        )
    }
}
