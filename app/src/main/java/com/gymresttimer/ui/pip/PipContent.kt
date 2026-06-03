package com.gymresttimer.ui.pip

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymresttimer.domain.ActiveMode
import com.gymresttimer.domain.ActiveModeHolder
import com.gymresttimer.domain.StopwatchStateRepository
import com.gymresttimer.domain.WorkoutState
import com.gymresttimer.domain.WorkoutStateRepository
import com.gymresttimer.util.formatStopwatchShort
import com.gymresttimer.util.formatTime
import kotlinx.coroutines.delay

@Composable
fun PipContent(
    timerRepo: WorkoutStateRepository,
    stopwatchRepo: StopwatchStateRepository,
    activeMode: ActiveModeHolder,
) {
    val mode by activeMode.mode.collectAsStateWithLifecycle()
    val timer by timerRepo.state.collectAsStateWithLifecycle()
    val stopwatch by stopwatchRepo.state.collectAsStateWithLifecycle()

    // Optional small label above the main number — currently the set number for the timer.
    var setLabel: String? = null
    val text = when (mode) {
        ActiveMode.Timer -> when (val s = timer) {
            is WorkoutState.RestCountdown -> {
                setLabel = "SET ${s.currentSet}"
                formatTime(s.secondsRemaining)
            }
            is WorkoutState.ActiveSet -> {
                setLabel = "SET ${s.currentSet}"
                "0:00"
            }
            WorkoutState.Idle -> "—"
        }
        ActiveMode.Stopwatch -> {
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(stopwatch.isRunning) {
                while (stopwatch.isRunning) {
                    now = SystemClock.elapsedRealtime()
                    delay(500L)
                }
                now = SystemClock.elapsedRealtime()
            }
            formatStopwatchShort(stopwatch.elapsedAt(now))
        }
        ActiveMode.None -> "—"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = minOf(
            34f,
            maxWidth.value / (text.length.coerceAtLeast(1) * 0.62f),
            maxHeight.value * 0.5f,
        ).coerceAtLeast(14f)
        val labelSize = (fontSize * 0.36f).coerceAtLeast(9f).sp

        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            setLabel?.let { label ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = text,
                color = MaterialTheme.colorScheme.primary,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}
