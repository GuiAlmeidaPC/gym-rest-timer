# Changelog

All notable user-visible changes are recorded here.

This project uses Android `versionCode` for store upgrade ordering and `versionName` tags of the form `vMAJOR.MINOR.PATCH` for human-readable releases.

## [1.2.0] - 2026-06-03

### Added
- Language selection (English, Português (BR), Français) from a new overflow menu in the top-right of the dashboard.
- Reset sets action in the Picture-in-Picture controls during an active set.
- Set number shown in the Picture-in-Picture display.

### Changed
- Smaller Picture-in-Picture window and timer numbers for a more compact overlay.
- Added padding to the launcher icon so it sits better within the adaptive-icon safe zone.

### Fixed
- Foreground notification is now dismissed when the workout toggle is turned off.

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
