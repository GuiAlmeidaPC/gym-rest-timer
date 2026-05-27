# Changelog

All notable user-visible changes are recorded here.

This project uses Android `versionCode` for store upgrade ordering and `versionName` tags of the form `vMAJOR.MINOR.PATCH` for human-readable releases.

## [1.1.1] - 2026-05-27

### Changed
- Target Android 15 / API 35 for Google Play submission readiness.
- Updated Android Gradle Plugin to 8.6.1 for API 35 support.
- Added Play Store listing copy and privacy policy documentation.

### Fixed
- Replaced deprecated Compose menu anchor usage in the rest profile selector.

## [1.1.0] - 2026-05-27

### Added
- Stopwatch tab with start, pause, resume, reset, and lap controls.
- Native Android Picture-in-Picture compact display for active timer and stopwatch sessions.
- Reset sets action while a workout is active.
- Active workout rest duration updates when selecting another rest profile.

### Changed
- Picture-in-Picture timer text now scales to fit the compact window.
- Android `versionName` normalized to semantic version format.

### Fixed
- Changing rest profile during a paused rest countdown preserves the paused state.

## [1.0.0] - 2026-05-25

### Added
- Initial offline rest timer with rest profiles, set tracking, pause/resume, +30s, skip rest, foreground service notification, and notification actions.
