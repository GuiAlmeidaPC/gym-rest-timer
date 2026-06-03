package com.gymresttimer

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gymresttimer.domain.ActiveMode
import com.gymresttimer.domain.ActiveModeHolder
import com.gymresttimer.domain.StopwatchState
import com.gymresttimer.domain.StopwatchStateRepository
import com.gymresttimer.domain.WorkoutState
import com.gymresttimer.domain.WorkoutStateRepository
import com.gymresttimer.service.PipActionReceiver
import com.gymresttimer.service.TimerService
import com.gymresttimer.ui.dashboard.DashboardScreen
import com.gymresttimer.ui.pip.PipContent
import com.gymresttimer.ui.theme.GymRestTimerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workoutRepo: WorkoutStateRepository
    @Inject lateinit var stopwatchRepo: StopwatchStateRepository
    @Inject lateinit var activeMode: ActiveModeHolder

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* state re-checked on resume */ }

    private val inPipMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        // Keep PIP params (auto-enter + actions) in sync with state. On API 31+ auto-enter
        // slides the activity into PIP when the user navigates away from it.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    activeMode.mode,
                    workoutRepo.state,
                    stopwatchRepo.state,
                ) { mode, w, s -> Triple(mode, w, s) }
                    .distinctUntilChanged()
                    .collect { (mode, w, s) -> updatePipParams(mode, w, s) }
            }
        }

        setContent {
            GymRestTimerTheme {
                if (inPipMode.value) {
                    PipContent(
                        timerRepo = workoutRepo,
                        stopwatchRepo = stopwatchRepo,
                        activeMode = activeMode,
                    )
                } else {
                    DashboardChrome()
                }
            }
        }
    }

    @Composable
    private fun DashboardChrome() {
        var resumeTick by remember { mutableStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumeTick++
            onPauseOrDispose { }
        }
        @Suppress("UNUSED_EXPRESSION") resumeTick

        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        DashboardScreen(
            hasNotificationPermission = hasNotificationPermission,
            onTimerStart = { restSeconds -> TimerService.startTimer(this, restSeconds) },
            onTimerStop = { TimerService.send(this, TimerService.ACTION_STOP_WORKOUT) },
            onTimerFinishSet = { TimerService.send(this, TimerService.ACTION_FINISH_SET) },
            onTimerResetSets = { TimerService.send(this, TimerService.ACTION_RESET_SETS) },
            onTimerRestDurationChange = { restSeconds -> TimerService.setRestDuration(this, restSeconds) },
            onTimerSkip = { TimerService.send(this, TimerService.ACTION_SKIP_REST) },
            onTimerAdd30 = { TimerService.send(this, TimerService.ACTION_ADD_30S) },
            onTimerPauseResume = { paused ->
                TimerService.send(
                    this,
                    if (paused) TimerService.ACTION_RESUME_TIMER else TimerService.ACTION_PAUSE_TIMER,
                )
            },
            onStopwatchStart = { TimerService.send(this, TimerService.ACTION_STOPWATCH_START) },
            onStopwatchPause = { TimerService.send(this, TimerService.ACTION_STOPWATCH_PAUSE) },
            onStopwatchResume = { TimerService.send(this, TimerService.ACTION_STOPWATCH_RESUME) },
            onStopwatchReset = { TimerService.send(this, TimerService.ACTION_STOPWATCH_RESET) },
            onStopwatchLap = { TimerService.send(this, TimerService.ACTION_STOPWATCH_LAP) },
            onRequestNotifications = ::requestNotificationPermission,
        )
    }

    private fun updatePipParams(mode: ActiveMode, timer: WorkoutState, stopwatch: StopwatchState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val running = mode != ActiveMode.None
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(buildActions(mode, timer, stopwatch))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(running)
            builder.setSeamlessResizeEnabled(true)
        }
        runCatching { setPictureInPictureParams(builder.build()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildActions(
        mode: ActiveMode,
        timer: WorkoutState,
        stopwatch: StopwatchState,
    ): List<RemoteAction> = when (mode) {
        ActiveMode.Timer -> when (timer) {
            is WorkoutState.ActiveSet -> listOf(
                action(R.drawable.ic_check, "Finish set", TimerService.ACTION_FINISH_SET),
                action(R.drawable.ic_refresh, "Reset sets", TimerService.ACTION_RESET_SETS),
                action(R.drawable.ic_stop, "Stop", TimerService.ACTION_STOP_WORKOUT),
            )
            is WorkoutState.RestCountdown -> listOf(
                if (timer.paused) {
                    action(R.drawable.ic_play, "Resume", TimerService.ACTION_RESUME_TIMER)
                } else {
                    action(R.drawable.ic_pause, "Pause", TimerService.ACTION_PAUSE_TIMER)
                },
                action(R.drawable.ic_add, "+30s", TimerService.ACTION_ADD_30S),
                action(R.drawable.ic_skip_next, "Skip", TimerService.ACTION_SKIP_REST),
            )
            WorkoutState.Idle -> emptyList()
        }
        ActiveMode.Stopwatch -> if (stopwatch.isRunning) listOf(
            action(R.drawable.ic_pause, "Pause", TimerService.ACTION_STOPWATCH_PAUSE),
            action(R.drawable.ic_flag, "Lap", TimerService.ACTION_STOPWATCH_LAP),
            action(R.drawable.ic_refresh, "Reset", TimerService.ACTION_STOPWATCH_RESET),
        ) else listOf(
            action(R.drawable.ic_play, "Resume", TimerService.ACTION_STOPWATCH_RESUME),
            action(R.drawable.ic_refresh, "Reset", TimerService.ACTION_STOPWATCH_RESET),
        )
        ActiveMode.None -> emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun action(@DrawableRes iconRes: Int, label: String, serviceAction: String): RemoteAction {
        val intent = Intent(this, PipActionReceiver::class.java)
            .setAction(PipActionReceiver.ACTION_FORWARD)
            .putExtra(PipActionReceiver.EXTRA_ACTION, serviceAction)
        val pi = PendingIntent.getBroadcast(
            this,
            serviceAction.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(this, iconRes), label, label, pi)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Manual fallback for Android 8.0 – 11 (Auto-enter requires API 31+).
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.R &&
            activeMode.current() != ActiveMode.None
        ) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setActions(buildActions(activeMode.current(), workoutRepo.state.value, stopwatchRepo.state.value))
                        .build(),
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode.value = isInPictureInPictureMode
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
