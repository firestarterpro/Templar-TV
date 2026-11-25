# 2026-11-25 B0 foundation fixes

## What changed
- **Runtime feature flags.** `FeatureFlags` is now a runtime store (SharedPreferences) over a `Flag` enum with label, description, default and an experimental marker. Flags are readable synchronously (services) and observable as a Flow (UI). New screen **Quick menu → Features** (`FeaturesActivity`) toggles each one with no rebuild.
- **Key logging removed.** `VistaAccessibilityService` no longer logs any key code. It acts on only the Settings key.
- **Settings key no longer blocked by default.** New flag `SETTINGS_KEY_CAPTURE`, default **off**. While off, the service does not request key events (the filter flag is removed from the service config and added at runtime only when the flag is on), never consumes a key, and the system dashboard with its Google Home panel behaves as stock. Turning the flag on restores the old behavior (open Templar's quick menu).
- **Rollback doc corrected.** `pm set-home` and `com.google.android.tvlauncher` are unverified; the rollback now lists the Home chooser, Settings → Default apps → Home app, and `adb uninstall` as the last resort.

## Decisions (no owner input needed)
- SharedPreferences, not DataStore, for flags: the accessibility service needs a synchronous read.
- Capture default off, per the platform findings (filtering reportedly breaks hold-to-repeat volume).
- Google Home stays reachable: long-press Home and the dashboard are untouched while capture is off.

## Owner's local Google Home edits (carried in)
Committed from the owner's PC folder, line endings normalized to LF: `GoogleHomeHandoff.kt` (open Google Home by launch intent, returns success), `QuickSettingsPanel.kt` ("Launcher & rollback" section with Open Google Home and a Return to Google TV Home guide, Templar TV naming), plus the two untracked handoffs `2026-11-22-templar-tv-google-home-quick-access.md` and `templar-tv-status.md`. The cloud could not stage the two deeply nested source files, so their diffs were re-applied by hand; the diff size matches the local one (21 and 49 lines). Nothing on the PC was changed. The Features tile sits just above "Reload Templar TV".

## Not compiled here
No Android SDK in the cloud container: CI is the check.

## Rollback
Revert this PR. Flag values live in `shared_prefs/templar_feature_flags.xml` and can be cleared with `adb shell pm clear com.firestarterpro.templartv` (resets launcher data too).
