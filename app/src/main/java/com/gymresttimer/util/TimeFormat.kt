package com.gymresttimer.util

fun formatTime(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    val r = s % 60
    return "%d:%02d".format(m, r)
}

/** mm:ss.cs (centiseconds) — compact stopwatch format. */
fun formatStopwatch(totalMs: Long): String {
    val ms = totalMs.coerceAtLeast(0)
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val cs = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(m, s, cs)
}

/** mm:ss — used in the compact notification line for the stopwatch. */
fun formatStopwatchShort(totalMs: Long): String {
    val s = (totalMs.coerceAtLeast(0) / 1000).toInt()
    return formatTime(s)
}
