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

## Not done here
- The owner's uncommitted Google Home quick-access edits (`QuickSettingsPanel.kt`, `GoogleHomeHandoff.kt`, plus untracked handoffs) live only on the owner's PC and were not reachable from the cloud. Commit them on top of this branch. This branch adds a "Features" tile to `QuickSettingsPanel.kt`, so expect a small merge there.
- No Android SDK in the cloud container: not compiled locally; CI build is the check.

## Rollback
Revert this PR. Flag values live in `shared_prefs/templar_feature_flags.xml` and can be cleared with `adb shell pm clear com.firestarterpro.templartv` (resets launcher data too).
