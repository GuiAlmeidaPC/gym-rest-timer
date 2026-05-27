# Gym Rest Timer

Android app implementing [SPEC.md](SPEC.md). Standalone, offline-first, foreground-service backed rest timer with native Android Picture-in-Picture. Theme: black background, orange (`#FF6A00`) accents.

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/gymresttimer/
│   ├── GymRestTimerApp.kt          @HiltAndroidApp
│   ├── MainActivity.kt             Compose host, permission flow, PIP params
│   ├── di/AppModule.kt             Hilt modules (DB, scope, repo bindings)
│   ├── domain/
│   │   ├── ActiveMode.kt           Active foreground/PIP mode selector
│   │   ├── WorkoutState.kt         Sealed FSM (Idle / ActiveSet / RestCountdown)
│   │   ├── WorkoutEvent.kt
│   │   ├── WorkoutStateRepository.kt  Singleton timer state holder + reducer
│   │   ├── StopwatchState.kt
│   │   └── StopwatchStateRepository.kt
│   ├── data/
│   │   ├── RestProfile.kt          Room entity
│   │   ├── RestProfileDao.kt
│   │   ├── AppDatabase.kt          Prepopulates Hypertrophy/Strength/Power
│   │   └── RestProfileRepository.kt
│   ├── service/
│   │   ├── TimerService.kt         Foreground service, tick loop, notification, audio/haptics
│   │   └── PipActionReceiver.kt    Forwards PIP actions to the service
│   ├── ui/
│   │   ├── theme/                  Black/orange Material3 scheme (no dynamic color)
│   │   ├── dashboard/              Main dashboard tabs and ViewModel
│   │   └── pip/                    Compact Picture-in-Picture content
│   └── util/TimeFormat.kt
└── res/                            strings, themes, icons
```

## Open & build

1. Open the root directory in Android Studio (Iguana+ / AGP 8.5).
2. Let Gradle sync — it'll fetch Compose BOM, Hilt, Room, KSP.
3. Run on a physical device for foreground service, notification, and Picture-in-Picture behavior.

## Release management

Releases are tracked with annotated Git tags and GitHub Releases.

- Current Android version is defined in `app/build.gradle.kts`.
- `versionCode` must increase for every release.
- `versionName` uses semantic format, for example `1.1.0`.
- Git tags use a `v` prefix, for example `v1.1.0`.
- User-visible changes are recorded in [CHANGELOG.md](CHANGELOG.md).
- The full release checklist is in [docs/RELEASE.md](docs/RELEASE.md).

## Runtime notes

- On Android 13+, notification permission is requested at runtime. The dashboard surfaces a card if it is missing.
- The foreground service uses type `specialUse` (no other Android 14 type fits a user-rest timer). Play Store submissions need the property tag justification already in the manifest.
- The timer tick loop uses `SystemClock.elapsedRealtime()` for drift correction — `delay(1000)` alone accumulates skew over long rests.
- Native Android Picture-in-Picture is used for compact timer/stopwatch display when the user leaves the app during an active session.

## v1 scope (per SPEC)

In: single rest profile selection, set tracking, pause/resume/+30s/skip, stopwatch, native Picture-in-Picture, foreground notification with actions.
Out: workout history, multi-exercise programs, cloud sync, wearable companion.
