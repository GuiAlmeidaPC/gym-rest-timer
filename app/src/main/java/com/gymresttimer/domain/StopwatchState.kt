package com.gymresttimer.domain

/**
 * Stopwatch is tracked as accumulated millis + an optional running anchor.
 * When [runningSinceElapsedMs] is non-null, the live elapsed time is
 * `accumulatedMs + (SystemClock.elapsedRealtime() - runningSinceElapsedMs)`.
 * When null, the stopwatch is paused or idle and [accumulatedMs] is the
 * frozen value.
 */
data class StopwatchState(
    val accumulatedMs: Long = 0L,
    val runningSinceElapsedMs: Long? = null,
    val laps: List<Long> = emptyList(),
) {
    val isRunning: Boolean get() = runningSinceElapsedMs != null
    val isIdle: Boolean get() = accumulatedMs == 0L && runningSinceElapsedMs == null && laps.isEmpty()

    fun elapsedAt(nowElapsedMs: Long): Long =
        accumulatedMs + (runningSinceElapsedMs?.let { nowElapsedMs - it } ?: 0L)
}
