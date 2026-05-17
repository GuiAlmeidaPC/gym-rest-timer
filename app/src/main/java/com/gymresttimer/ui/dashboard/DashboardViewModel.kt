package com.gymresttimer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymresttimer.data.RestProfile
import com.gymresttimer.data.RestProfileRepository
import com.gymresttimer.domain.StopwatchState
import com.gymresttimer.domain.StopwatchStateRepository
import com.gymresttimer.domain.WorkoutState
import com.gymresttimer.domain.WorkoutStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val profiles: List<RestProfile> = emptyList(),
    val selectedProfile: RestProfile? = null,
    val workout: WorkoutState = WorkoutState.Idle,
    val stopwatch: StopwatchState = StopwatchState(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val profileRepository: RestProfileRepository,
    val workoutRepository: WorkoutStateRepository,
    val stopwatchRepository: StopwatchStateRepository,
) : ViewModel() {

    private val selectedId = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        profileRepository.observeProfiles(),
        selectedId,
        workoutRepository.state,
        stopwatchRepository.state,
    ) { profiles, sel, workout, stopwatch ->
        val selected = profiles.firstOrNull { it.id == sel } ?: profiles.firstOrNull()
        DashboardUiState(
            profiles = profiles,
            selectedProfile = selected,
            workout = workout,
            stopwatch = stopwatch,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectProfile(profile: RestProfile) {
        selectedId.value = profile.id
    }
}
