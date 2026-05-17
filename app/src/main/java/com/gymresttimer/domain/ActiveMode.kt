package com.gymresttimer.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ActiveMode { None, Timer, Stopwatch }

/**
 * Tracks which feature currently owns the foreground notification and overlay.
 * Set when the user starts a workout or stopwatch; cleared when both are idle.
 */
@Singleton
class ActiveModeHolder @Inject constructor() {
    private val _mode = MutableStateFlow(ActiveMode.None)
    val mode: StateFlow<ActiveMode> = _mode.asStateFlow()

    fun set(mode: ActiveMode) { _mode.value = mode }
    fun current(): ActiveMode = _mode.value
}
