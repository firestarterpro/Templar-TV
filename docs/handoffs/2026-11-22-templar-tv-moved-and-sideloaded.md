# Templar TV handoff: moved, built, sideloaded (2026-11-22)

## State
- Repo location: `C:\Templar Archives\Robotics Facility\Templar TV` (moved from `C:\llm\jobs\templar-tv`; old folder sent to Recycle Bin after a verified copy).
- Branch `templar-tv-v0-foundation`, remote `firestarterpro/vista`.
- Release APK builds with JDK 17 (`app\build\outputs\apk\release\app-release.apk`), signed with the debug key. Sideload only.
- Installed on Google TV Streamer over Wi-Fi adb. The app starts and works, per the user. Stock launcher still installed.

## Why the build needed fixes
- The committed build file depended on version-catalog entries that only existed in the uncommitted `gradle/libs.versions.toml`. The committed branch alone did not build.
- `WatchHistoryEntry.kt` had a broken `implied Creator` block; replaced with a standard `CREATOR`, and `describeContents()` added.
- The rebrand commit changed some imports to `com.firestarterpro.templartv` but left files declaring `dev.drewett.vista`. Aligned all 33 Kotlin files under `app/src` to the namespace `com.firestarterpro.templartv`.

## Git
- Commit `eead28e` pushed to `origin/templar-tv-v0-foundation`, rebased on the remote docs commit `88706c8`. No force push.
- Safety copies, outside git: snapshot zip `C:\Templar Archives\Robotics Facility\Templar TV snapshot 20261122-121215.zip`, and git stash `e9e2722`.

## Open
- No pull request opened yet.
- The `dev.drewett` package name survives in `README.md` and some docs; rebrand the docs in a later pass.

## Rollback
- Uninstall on TV: `adb uninstall com.firestarterpro.templartv`.
- Home screen: Settings → Apps → Default apps → Home app; pick Google TV to revert.
