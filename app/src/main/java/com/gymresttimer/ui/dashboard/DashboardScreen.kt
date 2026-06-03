package com.gymresttimer.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.SystemClock
import com.gymresttimer.R
import com.gymresttimer.domain.StopwatchState
import com.gymresttimer.domain.WorkoutState
import com.gymresttimer.util.formatStopwatch
import com.gymresttimer.util.formatTime
import kotlinx.coroutines.delay

private enum class DashboardTab(@StringRes val labelRes: Int) {
    Timer(R.string.tab_timer),
    Stopwatch(R.string.tab_stopwatch),
}

/** Language options shown in the overflow menu: BCP-47 tag to display label. */
private val languageOptions = listOf(
    "en" to R.string.lang_english,
    "pt-BR" to R.string.lang_portuguese_br,
    "fr" to R.string.lang_french,
)

@Composable
fun DashboardScreen(
    hasNotificationPermission: Boolean,
    onTimerStart: (restSeconds: Int) -> Unit,
    onTimerStop: () -> Unit,
    onTimerFinishSet: () -> Unit,
    onTimerResetSets: () -> Unit,
    onTimerRestDurationChange: (restSeconds: Int) -> Unit,
    onTimerSkip: () -> Unit,
    onTimerAdd30: () -> Unit,
    onTimerPauseResume: (paused: Boolean) -> Unit,
    onStopwatchStart: () -> Unit,
    onStopwatchPause: () -> Unit,
    onStopwatchResume: () -> Unit,
    onStopwatchReset: () -> Unit,
    onStopwatchLap: () -> Unit,
    onRequestNotifications: () -> Unit,
    onSelectLanguage: (languageTag: String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Timer) }

    val bg = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = bg,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(padding),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 8.dp, bottom = 12.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                LanguageMenu(onSelectLanguage = onSelectLanguage)
            }

            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (tab.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[tab.ordinal]),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            ) {
                DashboardTab.entries.forEachIndexed { idx, t ->
                    Tab(
                        selected = tab.ordinal == idx,
                        onClick = { tab = t },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        text = { Text(stringResource(t.labelRes), fontWeight = FontWeight.SemiBold) },
                    )
                }
            }

            PermissionsCard(
                hasNotificationPermission = hasNotificationPermission,
                onRequestNotifications = onRequestNotifications,
            )

            when (tab) {
                DashboardTab.Timer -> TimerTab(
                    ui = ui,
                    canStart = hasNotificationPermission,
                    onSelectProfile = viewModel::selectProfile,
                    onStart = onTimerStart,
                    onStop = onTimerStop,
                    onFinishSet = onTimerFinishSet,
                    onResetSets = onTimerResetSets,
                    onRestDurationChange = onTimerRestDurationChange,
                    onSkip = onTimerSkip,
                    onAdd30 = onTimerAdd30,
                    onPauseResume = onTimerPauseResume,
                )
                DashboardTab.Stopwatch -> StopwatchTab(
                    state = ui.stopwatch,
                    canStart = hasNotificationPermission,
                    onStart = onStopwatchStart,
                    onPause = onStopwatchPause,
                    onResume = onStopwatchResume,
                    onReset = onStopwatchReset,
                    onLap = onStopwatchLap,
                )
            }
        }
    }
}

@Composable
private fun LanguageMenu(onSelectLanguage: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Highlight whichever language the UI is currently rendered in.
    val activeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    fun isSelected(tag: String): Boolean = when (tag) {
        "pt-BR" -> activeTag.startsWith("pt")
        "fr" -> activeTag.startsWith("fr")
        else -> !activeTag.startsWith("pt") && !activeTag.startsWith("fr")
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.menu),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            Text(
                stringResource(R.string.language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            languageOptions.forEach { (tag, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes), color = MaterialTheme.colorScheme.onSurface) },
                    trailingIcon = {
                        if (isSelected(tag)) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectLanguage(tag)
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    hasNotificationPermission: Boolean,
    onRequestNotifications: () -> Unit,
) {
    if (hasNotificationPermission) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notif_permission_title), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.notif_permission_body), color = MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onRequestNotifications) { Text(stringResource(R.string.grant_notifications), color = MaterialTheme.colorScheme.primary) }
        }
    }
}

// --- Timer tab -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerTab(
    ui: DashboardUiState,
    canStart: Boolean,
    onSelectProfile: (com.gymresttimer.data.RestProfile) -> Unit,
    onStart: (Int) -> Unit,
    onStop: () -> Unit,
    onFinishSet: () -> Unit,
    onResetSets: () -> Unit,
    onRestDurationChange: (Int) -> Unit,
    onSkip: () -> Unit,
    onAdd30: () -> Unit,
    onPauseResume: (paused: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ProfileSelector(
            profiles = ui.profiles.map { it.profileName to it.restDurationSeconds },
            selectedLabel = ui.selectedProfile?.let { "${it.profileName} (${it.restDurationSeconds}s)" } ?: "—",
            onSelect = { idx ->
                ui.profiles.getOrNull(idx)?.let { profile ->
                    onSelectProfile(profile)
                    if (ui.workout !is WorkoutState.Idle) {
                        onRestDurationChange(profile.restDurationSeconds)
                    }
                }
            },
        )

        WorkoutToggle(
            isOn = ui.workout !is WorkoutState.Idle,
            enabled = canStart && ui.selectedProfile != null,
            onToggle = { on ->
                if (on) ui.selectedProfile?.let { onStart(it.restDurationSeconds) }
                else onStop()
            },
        )

        if (ui.workout !is WorkoutState.Idle) {
            TextButton(onClick = onResetSets, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.reset_sets), color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(4.dp))

        when (val s = ui.workout) {
            WorkoutState.Idle -> Text(
                stringResource(R.string.toggle_hint),
                color = MaterialTheme.colorScheme.onSurface,
            )
            is WorkoutState.ActiveSet -> ActiveSetPanel(setNumber = s.currentSet, onFinishSet = onFinishSet)
            is WorkoutState.RestCountdown -> RestCountdownPanel(
                state = s,
                onSkip = onSkip,
                onAdd30 = onAdd30,
                onPauseResume = { onPauseResume(s.paused) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSelector(
    profiles: List<Pair<String, Int>>,
    selectedLabel: String,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.profile_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            profiles.forEachIndexed { idx, (name, secs) ->
                DropdownMenuItem(
                    text = { Text("$name (${secs}s)", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { onSelect(idx); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun WorkoutToggle(isOn: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(if (isOn) R.string.workout_on else R.string.workout_off),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 16.dp),
        )
        Switch(
            checked = isOn,
            enabled = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Composable
private fun ActiveSetPanel(setNumber: Int, onFinishSet: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.set_label, setNumber), color = MaterialTheme.colorScheme.primary, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = onFinishSet,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) { Text(stringResource(R.string.action_finish_set), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun RestCountdownPanel(
    state: WorkoutState.RestCountdown,
    onSkip: () -> Unit,
    onAdd30: () -> Unit,
    onPauseResume: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.set_rest_label, state.currentSet),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Text(
                formatTime(state.secondsRemaining),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 88.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.paused) Text(stringResource(R.string.paused), color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onAdd30,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(stringResource(R.string.action_add_30), fontWeight = FontWeight.Bold) }
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(stringResource(R.string.skip_rest), fontWeight = FontWeight.Bold) }
        }
        TextButton(onClick = onPauseResume) {
            Text(stringResource(if (state.paused) R.string.action_resume else R.string.action_pause), color = MaterialTheme.colorScheme.primary)
        }
    }
}

// --- Stopwatch tab ---------------------------------------------------------

@Composable
private fun StopwatchTab(
    state: StopwatchState,
    canStart: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onLap: () -> Unit,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            now = SystemClock.elapsedRealtime()
            delay(33L) // ~30 fps
        }
        now = SystemClock.elapsedRealtime()
    }
    val elapsed = state.elapsedAt(now)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            formatStopwatch(elapsed),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val primaryLabel = stringResource(
                when {
                    !state.isRunning && state.isIdle -> R.string.action_start
                    !state.isRunning -> R.string.action_resume
                    else -> R.string.action_pause
                }
            )
            Button(
                onClick = {
                    when {
                        !state.isRunning && state.isIdle -> onStart()
                        !state.isRunning -> onResume()
                        else -> onPause()
                    }
                },
                enabled = canStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text(primaryLabel, fontSize = 18.sp, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { if (state.isRunning) onLap() else onReset() },
                enabled = !state.isIdle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Text(
                    stringResource(if (state.isRunning) R.string.action_lap else R.string.action_reset),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (state.laps.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.laps.withIndex().toList()) { (idx, lapMs) ->
                    val lapNumber = state.laps.size - idx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.lap_number, lapNumber),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            formatStopwatch(lapMs),
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
