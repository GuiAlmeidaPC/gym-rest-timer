package com.gymresttimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gymresttimer.MainActivity
import com.gymresttimer.R
import com.gymresttimer.domain.ActiveMode
import com.gymresttimer.domain.ActiveModeHolder
import com.gymresttimer.domain.StopwatchEvent
import com.gymresttimer.domain.StopwatchState
import com.gymresttimer.domain.StopwatchStateRepository
import com.gymresttimer.domain.WorkoutEvent
import com.gymresttimer.domain.WorkoutState
import com.gymresttimer.domain.WorkoutStateRepository
import com.gymresttimer.util.formatStopwatchShort
import com.gymresttimer.util.formatTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class TimerService : LifecycleService() {

    @Inject lateinit var timerRepo: WorkoutStateRepository
    @Inject lateinit var stopwatchRepo: StopwatchStateRepository
    @Inject lateinit var activeMode: ActiveModeHolder

    private var timerTickJob: Job? = null
    private var stopwatchRefreshJob: Job? = null
    private var nextTimerTickAtMs: Long = 0L

    private var lastTickedSecond: Int = -1
    private var lastBeepSecond: Int = -1

    private val audioManager by lazy { getSystemService<AudioManager>()!! }
    private var audioFocusRequest: AudioFocusRequest? = null
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundCompat(buildNotification())

        // Drive notification updates and side effects whenever either state changes.
        lifecycleScope.launch {
            combine(
                timerRepo.state,
                stopwatchRepo.state,
                activeMode.mode,
            ) { t, s, m -> Triple(t, s, m) }
                .distinctUntilChanged()
                .collect { (t, _, _) ->
                    refreshNotification()
                    handleTimerSideEffects(t)
                    manageTimerTickLoop(t)
                    manageStopwatchRefresh()
                    maybeStopSelf()
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            // Timer
            ACTION_START_WORKOUT -> {
                val duration = intent.getIntExtra(EXTRA_REST_SECONDS, WorkoutStateRepository.DEFAULT_REST_SECONDS)
                timerRepo.dispatch(WorkoutEvent.StartWorkout(duration))
                activeMode.set(ActiveMode.Timer)
            }
            ACTION_STOP_WORKOUT -> {
                timerRepo.dispatch(WorkoutEvent.StopWorkout)
                if (activeMode.current() == ActiveMode.Timer) activeMode.set(fallbackMode())
            }
            ACTION_FINISH_SET -> timerRepo.dispatch(WorkoutEvent.FinishSet)
            ACTION_RESET_SETS -> timerRepo.dispatch(WorkoutEvent.ResetSets)
            ACTION_SET_REST_DURATION -> {
                val duration = intent.getIntExtra(EXTRA_REST_SECONDS, timerRepo.currentRestDuration())
                timerRepo.dispatch(WorkoutEvent.SetRestDuration(duration))
            }
            ACTION_SKIP_REST -> timerRepo.dispatch(WorkoutEvent.SkipRest)
            ACTION_ADD_30S -> timerRepo.dispatch(WorkoutEvent.Add30s)
            ACTION_PAUSE_TIMER -> timerRepo.dispatch(WorkoutEvent.Pause)
            ACTION_RESUME_TIMER -> timerRepo.dispatch(WorkoutEvent.Resume)

            // Stopwatch
            ACTION_STOPWATCH_START -> {
                stopwatchRepo.dispatch(StopwatchEvent.Start)
                activeMode.set(ActiveMode.Stopwatch)
            }
            ACTION_STOPWATCH_PAUSE -> stopwatchRepo.dispatch(StopwatchEvent.Pause)
            ACTION_STOPWATCH_RESUME -> {
                stopwatchRepo.dispatch(StopwatchEvent.Resume)
                activeMode.set(ActiveMode.Stopwatch)
            }
            ACTION_STOPWATCH_RESET -> {
                stopwatchRepo.dispatch(StopwatchEvent.Reset)
                if (activeMode.current() == ActiveMode.Stopwatch) activeMode.set(fallbackMode())
            }
            ACTION_STOPWATCH_LAP -> stopwatchRepo.dispatch(StopwatchEvent.Lap)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        timerTickJob?.cancel()
        stopwatchRefreshJob?.cancel()
        abandonAudioFocus()
        super.onDestroy()
    }

    private fun fallbackMode(): ActiveMode = when {
        timerRepo.state.value != WorkoutState.Idle -> ActiveMode.Timer
        !stopwatchRepo.state.value.isIdle -> ActiveMode.Stopwatch
        else -> ActiveMode.None
    }

    private fun maybeStopSelf() {
        val timerIdle = timerRepo.state.value == WorkoutState.Idle
        val stopwatchIdle = stopwatchRepo.state.value.isIdle
        if (timerIdle && stopwatchIdle) {
            activeMode.set(ActiveMode.None)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // region: timer tick loop

    private fun manageTimerTickLoop(state: WorkoutState) {
        val isCounting = state is WorkoutState.RestCountdown && !state.paused
        if (isCounting && timerTickJob?.isActive != true) {
            nextTimerTickAtMs = SystemClock.elapsedRealtime() + 1_000L
            timerTickJob = lifecycleScope.launch(Dispatchers.Default) {
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val wait = nextTimerTickAtMs - now
                    if (wait > 0) delay(wait)
                    timerRepo.dispatch(WorkoutEvent.Tick)
                    nextTimerTickAtMs += 1_000L
                }
            }
        } else if (!isCounting) {
            timerTickJob?.cancel()
            timerTickJob = null
        }
    }

    // endregion

    // region: stopwatch refresh (notification ticker; UI observes flow directly)

    private fun manageStopwatchRefresh() {
        val running = stopwatchRepo.state.value.isRunning
        if (running && stopwatchRefreshJob?.isActive != true) {
            stopwatchRefreshJob = lifecycleScope.launch(Dispatchers.Default) {
                while (true) {
                    delay(1_000L) // notification text needs seconds resolution; overlay refreshes itself
                    refreshNotification()
                }
            }
        } else if (!running) {
            stopwatchRefreshJob?.cancel()
            stopwatchRefreshJob = null
        }
    }

    // endregion

    // region: audio + haptics (timer-only)

    private fun handleTimerSideEffects(state: WorkoutState) {
        if (state !is WorkoutState.RestCountdown) {
            lastTickedSecond = -1
            lastBeepSecond = -1
            return
        }
        val s = state.secondsRemaining
        if (s == lastTickedSecond) return
        lastTickedSecond = s

        if (s in 1..3) vibrateShort()
        if (s == 0 && lastBeepSecond != 0) {
            playCompletionTone()
            lastBeepSecond = 0
        }
    }

    private fun vibrateShort() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(120)
        }
    }

    private fun playCompletionTone() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { /* no-op */ }
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)

        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
            lifecycleScope.launch {
                delay(700)
                tone.release()
                abandonAudioFocus()
            }
        }.onFailure { abandonAudioFocus() }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    // endregion

    // region: notification

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun refreshNotification() {
        val nm = getSystemService<NotificationManager>() ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val mode = activeMode.current()
        return when (mode) {
            ActiveMode.Stopwatch -> buildStopwatchNotification(stopwatchRepo.state.value)
            ActiveMode.Timer, ActiveMode.None -> buildTimerNotification(timerRepo.state.value)
        }
    }

    private fun baseBuilder(title: String): NotificationCompat.Builder {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(tap)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun buildTimerNotification(state: WorkoutState): Notification {
        val title = when (state) {
            WorkoutState.Idle -> getString(R.string.notif_idle)
            is WorkoutState.ActiveSet -> getString(R.string.notif_set, state.currentSet)
            is WorkoutState.RestCountdown -> getString(
                R.string.notif_rest,
                state.currentSet,
                formatTime(state.secondsRemaining),
            )
        }
        val builder = baseBuilder(title)
        when (state) {
            is WorkoutState.ActiveSet -> {
                builder.addAction(0, getString(R.string.action_finish_set), pendingSelf(ACTION_FINISH_SET))
                builder.addAction(0, getString(R.string.action_stop), pendingSelf(ACTION_STOP_WORKOUT))
            }
            is WorkoutState.RestCountdown -> {
                val pauseResume = if (state.paused) {
                    NotificationCompat.Action(0, getString(R.string.action_resume), pendingSelf(ACTION_RESUME_TIMER))
                } else {
                    NotificationCompat.Action(0, getString(R.string.action_pause), pendingSelf(ACTION_PAUSE_TIMER))
                }
                builder.addAction(pauseResume)
                builder.addAction(0, getString(R.string.action_add_30), pendingSelf(ACTION_ADD_30S))
                builder.addAction(0, getString(R.string.action_skip), pendingSelf(ACTION_SKIP_REST))
                builder.setProgress(
                    max(state.durationTotal, 1),
                    max(state.durationTotal - state.secondsRemaining, 0),
                    false,
                )
            }
            WorkoutState.Idle -> Unit
        }
        return builder.build()
    }

    private fun buildStopwatchNotification(state: StopwatchState): Notification {
        val now = SystemClock.elapsedRealtime()
        val elapsed = state.elapsedAt(now)
        val title = if (state.isRunning) {
            getString(R.string.notif_stopwatch, formatStopwatchShort(elapsed))
        } else {
            getString(R.string.notif_stopwatch_paused, formatStopwatchShort(elapsed))
        }
        val builder = baseBuilder(title)
        if (state.isRunning) {
            builder.addAction(0, getString(R.string.action_pause), pendingSelf(ACTION_STOPWATCH_PAUSE))
            builder.addAction(0, getString(R.string.action_lap), pendingSelf(ACTION_STOPWATCH_LAP))
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() - elapsed)
        } else {
            builder.addAction(0, getString(R.string.action_resume), pendingSelf(ACTION_STOPWATCH_RESUME))
        }
        builder.addAction(0, getString(R.string.action_reset), pendingSelf(ACTION_STOPWATCH_RESET))
        return builder.build()
    }

    private fun pendingSelf(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // endregion

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "rest_timer_channel"

        const val ACTION_START_WORKOUT = "com.gymresttimer.action.START_WORKOUT"
        const val ACTION_STOP_WORKOUT = "com.gymresttimer.action.STOP_WORKOUT"
        const val ACTION_PAUSE_TIMER = "com.gymresttimer.action.PAUSE_TIMER"
        const val ACTION_RESUME_TIMER = "com.gymresttimer.action.RESUME_TIMER"
        const val ACTION_SKIP_REST = "com.gymresttimer.action.SKIP_REST"
        const val ACTION_ADD_30S = "com.gymresttimer.action.ADD_30S"
        const val ACTION_FINISH_SET = "com.gymresttimer.action.FINISH_SET"
        const val ACTION_RESET_SETS = "com.gymresttimer.action.RESET_SETS"
        const val ACTION_SET_REST_DURATION = "com.gymresttimer.action.SET_REST_DURATION"

        const val ACTION_STOPWATCH_START = "com.gymresttimer.action.STOPWATCH_START"
        const val ACTION_STOPWATCH_PAUSE = "com.gymresttimer.action.STOPWATCH_PAUSE"
        const val ACTION_STOPWATCH_RESUME = "com.gymresttimer.action.STOPWATCH_RESUME"
        const val ACTION_STOPWATCH_RESET = "com.gymresttimer.action.STOPWATCH_RESET"
        const val ACTION_STOPWATCH_LAP = "com.gymresttimer.action.STOPWATCH_LAP"

        const val EXTRA_REST_SECONDS = "extra_rest_seconds"

        fun startTimer(context: Context, restSeconds: Int) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_START_WORKOUT)
                .putExtra(EXTRA_REST_SECONDS, restSeconds)
            ContextCompat.startForegroundService(context, intent)
        }

        fun setRestDuration(context: Context, restSeconds: Int) {
            val intent = Intent(context, TimerService::class.java)
                .setAction(ACTION_SET_REST_DURATION)
                .putExtra(EXTRA_REST_SECONDS, restSeconds)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, TimerService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
