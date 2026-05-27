# Gym Rest Timer — Technical Specification

## 1. Overview

An offline-first Android app for tracking gym rest intervals between sets. The timer runs in a foreground service so it survives the activity lifecycle, and surfaces a draggable system-wide overlay so the user can monitor and control rest without leaving their workout app of choice (music, video, notes, etc.).

Architecture: unidirectional data flow. A single source of truth (`WorkoutState`) lives in a Hilt-scoped repository, mutated only by the `TimerService`, and observed by both the in-app UI and the overlay `ComposeView`.

## 2. Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVI + Clean (domain / data / presentation) |
| Async | Coroutines + `StateFlow` |
| Persistence | Room |
| DI | Hilt |
| Background | Foreground `Service` (`specialUse`) |
| System UI | Picture-in-Picture (PIP) |
| Min / Target SDK | 26 / 34+ |

## 3. Manifest & Permissions

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
```

```xml
<service
    android:name=".service.TimerService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Long-running rest timer that must continue while the user uses other apps; UI-driven, never silent." />
</service>
```

Notes:
- `POST_NOTIFICATIONS` must be requested at runtime on Android 13+.
- The floating UI uses Android's native **Picture-in-Picture** (PIP), declared via `android:supportsPictureInPicture="true"` on the activity — no `SYSTEM_ALERT_WINDOW` overlay permission required.
- `FOREGROUND_SERVICE_SPECIAL_USE` is the correct subtype for a user-facing rest timer (none of `mediaPlayback`, `health`, `dataSync`, etc. fit). Google Play requires the property justification above.

## 4. Domain Model

### 4.1 State machine

```kotlin
sealed interface WorkoutState {
    data object Idle : WorkoutState

    data class ActiveSet(
        val currentSet: Int,
    ) : WorkoutState

    data class RestCountdown(
        val currentSet: Int,
        val secondsRemaining: Int,
        val durationTotal: Int,
        val paused: Boolean = false,
    ) : WorkoutState
}
```

Transitions:

| From | Event | To |
|---|---|---|
| `Idle` | `START_WORKOUT` | `ActiveSet(1)` |
| `ActiveSet(n)` | set finished (user taps "Done") | `RestCountdown(n, duration, duration)` |
| `RestCountdown` | tick (every 1 s, not paused) | `secondsRemaining - 1` |
| `RestCountdown` | `secondsRemaining == 0` | `ActiveSet(n + 1)` |
| `RestCountdown` | `+30s` | `secondsRemaining + 30`, `durationTotal + 30` |
| `RestCountdown` | `SKIP` | `ActiveSet(n + 1)` |
| `RestCountdown` | `PAUSE` / `RESUME` | toggle `paused` |
| any | `STOP_WORKOUT` | `Idle` |

`paused` was added to the original spec so pause/resume from the overlay doesn't require a separate state class — the FSM stays flat and the UI just renders a paused indicator.

### 4.1.1 Stopwatch

The stopwatch is a separate, independent feature exposed under its own dashboard tab. It is *not* part of the rest-timer FSM — they can be in any combination of states, but only one drives the foreground notification and overlay at a time (the most recently started).

```kotlin
data class StopwatchState(
    val accumulatedMs: Long = 0L,
    val runningSinceElapsedMs: Long? = null,  // SystemClock.elapsedRealtime() anchor
    val laps: List<Long> = emptyList(),
)
```

Elapsed time is computed lazily as `accumulatedMs + (now - runningSinceElapsedMs)` so there is no per-frame state mutation: the UI re-reads `SystemClock.elapsedRealtime()` on its own refresh cadence (~30 fps in-app, ~20 fps in the overlay), and the notification re-renders once per second. Pause folds the live delta back into `accumulatedMs` and clears the anchor.

Events: `Start`, `Pause`, `Resume`, `Reset`, `Lap`.

### 4.1.2 Active mode

```kotlin
enum class ActiveMode { None, Timer, Stopwatch }
```

`ActiveModeHolder` is a singleton `StateFlow` set by the service when the user starts a timer/stopwatch session. The notification and overlay render whichever mode is currently active. Stopping one feature falls back to the other if it is still running, otherwise the service self-stops.

### 4.2 State holder

A `@Singleton` `WorkoutStateRepository` exposes `val state: StateFlow<WorkoutState>` and `suspend fun dispatch(event: WorkoutEvent)`. Both `TimerService` and the dashboard `ViewModel` inject it. The service owns the ticking coroutine; the ViewModel is read-only plus event dispatch.

## 5. Data Layer (Room)

```kotlin
@Entity(tableName = "rest_profiles")
data class RestProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileName: String,
    val restDurationSeconds: Int,
)
```

DAO: `getProfiles(): Flow<List<RestProfile>>`, `insert(profile)`, `delete(profile)`, `update(profile)`.

Prepopulate on first creation via `RoomDatabase.Callback`:

- `Hypertrophy` — 90 s
- `Strength` — 180 s
- `Power` — 240 s

`RestProfileRepository` interface decouples Room from ViewModels for testability.

## 6. TimerService

Core engine. Runs as a foreground service so AOSP/OEM background limits don't kill the countdown.

### Responsibilities

1. **Tick loop.** On entering `RestCountdown`, launch a coroutine on the service scope:
   ```kotlin
   while (state is RestCountdown && !state.paused) {
       delay(1_000)
       repo.dispatch(Tick)
   }
   ```
   Use `SystemClock.elapsedRealtime()` to correct for drift — `delay(1000)` accumulates skew over a long rest.
2. **Foreground notification.** Created in `onCreate`, updated on every state change. Channel importance `HIGH` (not `MAX` — `MAX` triggers a heads-up notification on every update, which is noisy). Notification shows: current set, time remaining, and action buttons for Pause/Resume, +30 s, and Stop.
3. **Audio cues.**
   - At `secondsRemaining == 3, 2, 1`: short `Vibrator` pulses (`VibrationEffect.createOneShot(100, DEFAULT_AMPLITUDE)`).
   - At `secondsRemaining == 0`: request transient `AudioFocusRequest` with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, play a short completion tone (`SoundPool` or `MediaPlayer`), then `abandonAudioFocusRequest`.
4. **No window binding.** The floating UI is the activity itself, via Picture-in-Picture (see §8.3). The service is purely state + notification.

### Intent actions

| Action | Effect |
|---|---|
| `ACTION_START_WORKOUT` | `Idle → ActiveSet(1)`, start foreground |
| `ACTION_STOP_WORKOUT` | Any → `Idle`, stop foreground |
| `ACTION_PAUSE_TIMER` | Sets `paused = true`, cancels tick loop |
| `ACTION_RESUME_TIMER` | Sets `paused = false`, relaunches tick loop |
| `ACTION_SKIP_REST` | `RestCountdown → ActiveSet(n + 1)` |
| `ACTION_ADD_30S` | Extends current countdown |
| `ACTION_FINISH_SET` | `ActiveSet → RestCountdown` |

Started via `ContextCompat.startForegroundService(context, intent)`.

## 7. Theme

Single dark theme — no light variant.

| Role | Color | Usage |
|---|---|---|
| `background` | `#000000` | App background, scaffold surface |
| `surface` | `#0A0A0A` | Cards, dropdown sheets, dialogs |
| `surfaceVariant` | `#1A1A1A` | Elevated containers, profile rows |
| `primary` | `#FF6A00` | Accent — primary buttons, active toggle, timer text, FAB |
| `onPrimary` | `#000000` | Text/icons on orange fills |
| `secondary` | `#FF8A33` | Hover/pressed accent, secondary actions |
| `error` / `errorContainer` | `#FF3D00` / `#3D0F00` | Countdown background tint during rest |
| `onBackground` / `onSurface` | `#FFFFFF` | Primary text |
| `outline` | `#2A2A2A` | Dividers, card borders |

Implementation:

- Define a single `darkColorScheme(...)` in `ui/theme/Color.kt` with the values above; do not generate or use a light scheme. Force `MaterialTheme` to it regardless of system setting.
- Disable Material 3 dynamic color (`dynamicColor = false`) — the brand is fixed black/orange, not wallpaper-derived.
- Set the system status bar and navigation bar to `#000000` with light icons via `WindowCompat` + `WindowInsetsControllerCompat.isAppearanceLightStatusBars = false`.
- Overlay card background changes from the previously-specified `#EE1E1E1E` to **`#EE000000`** (black, ~93% opaque). The timer text inside uses `primary` (`#FF6A00`).
- During `RestCountdown`, the dashboard background animates from `background` (`#000000`) to `errorContainer` (`#3D0F00`) — a dark, orange-tinted black, not a bright amber. Keeps the black-dominant feel while signalling active rest.

## 8. Presentation Layer

### 8.0 Tabs

`DashboardScreen` hosts a Material 3 `TabRow` with two tabs — **Timer** and **Stopwatch** — selected state persisted via `rememberSaveable`. The orange-on-black `errorContainer` background tint that signals active rest only applies on the Timer tab (switching tabs during rest doesn't recolor the screen).

### 8.1 Dashboard (`MainActivity`)

- Observes `WorkoutStateRepository.state` via a `ViewModel`.
- Components:
  - **Profile selector** — dropdown sourced from Room.
  - **Workout toggle** — master `Switch`. On enable:
    1. If `!Settings.canDrawOverlays(this)` → launch `Intent(ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))` and revert the toggle until permission returns.
    2. If `Build.VERSION.SDK_INT >= 33 && !POST_NOTIFICATIONS granted` → request runtime permission.
    3. Otherwise fire `ACTION_START_WORKOUT` to the service.
  - **"Finish set"** button when state is `ActiveSet`.
  - **Countdown screen** when state is `RestCountdown`: large timer, animated background tint (Material `errorContainer` while rest is active, returning to `surface` on completion), `+30s` and `Skip Rest` actions.

### 8.2 Stopwatch tab

- Large monospace `mm:ss.cs` display, refreshed at ~30 fps via a `LaunchedEffect` polling `SystemClock.elapsedRealtime()` while running.
- Primary button: **Start** → **Pause** → **Resume** (label adapts to state).
- Secondary button: **Lap** while running, **Reset** when paused.
- Lap list below: newest at top, monospace alignment, lap number on the left.

### 8.3 Picture-in-Picture (`PipContent`)

Instead of a `SYSTEM_ALERT_WINDOW` overlay, the activity itself enters Android's native PIP mode when the user navigates away from it while a session is running.

- Manifest: `android:supportsPictureInPicture="true"` and `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboardHidden"` on the activity (no Compose recreation on config change).
- Auto-enter is configured via `PictureInPictureParams.Builder().setAutoEnterEnabled(true)` on API 31+. The activity keeps the params in sync with a `combine(activeMode, timer, stopwatch)` flow, enabling auto-enter only while at least one mode is active.
- API 26–30 fallback: manually call `enterPictureInPictureMode(...)` from `onUserLeaveHint()` when a session is running.
- Aspect ratio is `1:1` (Rational(1, 1)).
- `onPictureInPictureModeChanged()` flips a `MutableState<Boolean>` that the root composable observes; when true it renders `PipContent` (compact text-only view) instead of the full dashboard.
- `PipContent` observes the same `StateFlow`s — same source of truth, just a different layout. No buttons (the PIP frame supplies expand/close affordances).

Notes:
- Compose `setContent` survives the PIP transition because we added `keyboardHidden|screenLayout|screenSize|smallestScreenSize|orientation` to `configChanges`.
- PIP requires API 26+; below that the user simply sees the persistent notification.

## 9. Implementation Order

1. **Scaffold** — Gradle KTS, Compose BOM, Hilt, Room, KSP.
2. **Domain** — `WorkoutState`, `WorkoutEvent`, `WorkoutStateRepository`.
3. **Data** — Room DB, DAO, repository, prepopulate callback.
4. **Service** — foreground notification + channel, tick loop, audio/haptics, intent action routing.
5. **PIP** — manifest flags, `setAutoEnterEnabled` wiring, compact `PipContent` composable.
6. **UI** — dashboard, profile dropdown, permission flow (notifications only), countdown screen.
7. **Polish** — drift-corrected timer, edge snap, settings (sound on/off, vibration intensity).

## 10. Build & Deploy

### Toolchain
- **JDK 17** required (AGP 8.5 is incompatible with JDK 18+). Local copy: `/tmp/jdk-17.0.2`.
- **Android SDK** at `/home/gui/Android/Sdk` with platform-34 + build-tools-34 installed.
- Always invoke Gradle with `JAVA_HOME=/tmp/jdk-17.0.2 ANDROID_HOME=/home/gui/Android/Sdk`.

### Release signing
- Keystore: `keystore/release.jks` (RSA 2048, validity 10000 days).
- Credentials in `keystore.properties` at the repo root (gitignored).
- `app/build.gradle.kts` loads the properties file and wires the signing config into the `release` build type only when the file is present; debug builds keep using the auto-generated debug keystore.
- **Back up `keystore/release.jks` and `keystore.properties`.** Losing them means future updates cannot replace the installed app (Android rejects APKs signed with a different key; users would have to uninstall and lose their local DB).

### Build commands
```
# Debug APK (fast, debuggable, no minification)
JAVA_HOME=/tmp/jdk-17.0.2 ANDROID_HOME=/home/gui/Android/Sdk \
  ./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (R8 minified, shrunk resources, signed)
JAVA_HOME=/tmp/jdk-17.0.2 ANDROID_HOME=/home/gui/Android/Sdk \
  ./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Before any user-facing release, bump `versionCode` (integer, monotonic) and `versionName` in `app/build.gradle.kts`. `adb install -r` works without a bump but Play Store / proper update flows require it.

### Release management
- Releases are cut from `main` using annotated Git tags and GitHub Releases.
- `versionName` uses semantic format (`MAJOR.MINOR.PATCH`); release tags use the matching `vMAJOR.MINOR.PATCH` format.
- `versionCode` must increase by 1 for every release artifact uploaded to a device, GitHub Release, or app store.
- User-visible changes are tracked in `CHANGELOG.md`.
- The operational checklist, signing-secret names, and GitHub Actions release flow are documented in `docs/RELEASE.md`.
- Before tagging a release, run:

```
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleRelease :app:bundleRelease :app:lintRelease
```

Tag and push the release after the release-prep commit:

```
git tag -a vX.Y.Z -m "Release vX.Y.Z"
git push origin main
git push origin vX.Y.Z
```

### ProGuard / R8 keep rules
`app/proguard-rules.pro` keeps Hilt-generated classes, Room entities/DAOs, and everything under `com.gymresttimer.data.*` (Room accesses them reflectively). Add new keep rules if introducing additional reflection-based libraries.

### Install to a phone
1. **USB sideload (recommended)**
   - Enable Developer Options: Settings → About phone → tap "Build number" 7 times.
   - Enable USB debugging in Developer Options, plug in, accept the RSA prompt.
   - `/home/gui/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk`
2. **Wireless ADB** — Developer Options → Wireless debugging → pair → `adb pair`/`adb connect`/`adb install -r`.
3. **Manual** — copy the APK to the phone (Drive, Syncthing, email), tap it in the file manager, allow "Install unknown apps" for that file manager.

### Emulator testing (Pixel 5 AVD, API 34)
```
/home/gui/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/home/gui/Android/Sdk/platform-tools/adb shell am force-stop com.gymresttimer
/home/gui/Android/Sdk/platform-tools/adb shell monkey -p com.gymresttimer -c android.intent.category.LAUNCHER 1
/home/gui/Android/Sdk/platform-tools/adb exec-out screencap -p > /tmp/shot.png
```
Exercise PIP by pressing HOME (`adb shell input keyevent KEYCODE_HOME`) while a timer or stopwatch is running — `setAutoEnterEnabled(true)` slides the activity into the PIP frame.

### Room migrations
When entity schema changes (or seed data must be added for existing installs), bump the `version` on `@Database` and register a `Migration` in `AppDatabase.kt`. `MIGRATION_1_2` is the reference pattern: an idempotent `INSERT … WHERE NOT EXISTS` so re-running it on a partially-migrated DB is safe.

## 11. Out of Scope (v1)

- Workout history / analytics
- Cloud sync
- Wearable companion
- Multi-exercise programs (only single rest duration per session)
