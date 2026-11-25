# 2026-11-25 Combined build: B3 + companion fixes + B5 + B4, wired, star and voice removed

Branch `claude/combine-full-build`, from `origin/main` at `bc4ed54` (PR #10 merged). Built in the worktree
`C:\Templar Archives\Robotics Facility\Templar-TV-combine` (the main checkout was not touched).
Nothing was merged into `main`; the owner merges. Nothing was installed on the TV.

## 1. Merge order

All with `git merge --no-ff` from `refs/pull/N/head`, history kept. Unit tests ran after each step.

| Step | Source | Head merged | Conflicts | Check |
| --- | --- | --- | --- | --- |
| 1 | PR #11 B3 recently played + ShelfLauncher | `b511e55` | none | tests pass (run with #13) |
| 2 | PR #13 companion fixes | `463ed7a` | none | `testDebugUnitTest` pass |
| 3 | PR #12 B5 quick menu | `07e2652` | none | `testDebugUnitTest` pass |
| 4 | PR #14 B4 big-box home | `f36924b` | none (FeatureFlags and HomeScreen auto-merged; every flag from both sides kept) | `testDebugUnitTest` pass |
| 5 | PR #11 again (debug metadata switch) | `e006074` | none (FeatureFlags auto-merged; `DEBUG_METADATA_VALUES` kept) | pass |
| 6 | PR #11 again (parser and stop-position fixes) | `b164d9b` | none | pass, then release build |

`home_view` default is Columns: `SettingKeys.defaults[HOME_VIEW] = "columns"` (from #14), falling back to Rows when the
Columns flag is off. Now covered by `HomeWiringTest.defaultHomeViewIsColumns`.

## 2. Wiring

| Wire | Where | Flag | Test |
| --- | --- | --- | --- |
| B4 shelf tiles open through `ShelfLauncher.launch` from `viewModelScope.launch` (tile moves to the front, progress kept). Falls back to the old direct open when the flag is off, the entry is gone or nothing launched. Favourite tiles unchanged. | `home/ShelfOpen.kt`, `BigBoxViewModel.openShelf`, `BigBoxHome.open` | `provider_recent_shelves` | `HomeWiringTest` (2 tests) |
| Quick menu sections: Continue watching (recently played shelf entries, opened with `ShelfLauncher`), Favourite shows (opens the show's provider app, else Templar home), Change view (Previous / Next = `HomeActions.cycleView(-1/+1)`, Pick a view = `openViewPicker()`), Rows (`openRowManager()`). Footer unchanged and pinned: Google Home, Google TV Home, Templar Home. | `quickmenu/QuickMenuModel.kt`, `ui/quickmenu/QuickMenuPanel.kt` | Continue: `provider_recent_shelves`; Favourites: `favourite_shows`; Change view: `big_box_home`; Rows: `big_box_home` + `row_manager`; whole menu: `quick_menu` | `QuickMenuModelTest` (flag gating, old stored layouts gain the new sections, order, encode) |
| Home requests from the quick menu reach the home screen even when it is not composed yet: `HomeActions` keeps one pending request, `BigBoxHome` consumes it once on start. | `home/HomeActions.kt`, `BigBoxHome.kt` | (follows the section flags) | `HomeWiringTest.homeRequestsWaitForTheHomeScreenWhenNobodyListens` |
| Companion API `open-menu` calls `QuickMenuController.open()` (via `openOrLegacy`, so with `quick_menu` off the old quick-settings panel still opens, as before). | `companion/CompanionController.kt`, `QuickMenuController.openOrLegacy` | `companion_api`, `quick_menu` | `QuickMenuModelTest.companionOpenMenuUsesQuickMenuThenLegacyPanel`, `openRoutesToInAppPanelThenActivity` |
| Settings-key capture in the accessibility service also uses `QuickMenuController.openOrLegacy`. | `VistaAccessibilityService.openQuickMenu` | `settings_key_capture` (off) | routing tests above |

Not changed: long-press Home is still Google's (the service never consumes `KEYCODE_HOME`); the Google Home footer tile
and "Hold the Home button" hint stay; Google's mic / Assistant button untouched.

## 3. Features removed (ordinary code removal, history kept)

1. `Flag.STAR_KEY_CAPTURE` (`star_key_capture`). Reason: device test, `KEYCODE_MACRO_1` (313) is a launcherx global key and never reaches the service.
2. `enum StarAction` and `FeatureFlags.starAction / setStarAction` (SharedPreferences key `star_action`).
3. The "Star button action" row in Settings → Features (`FeaturesActivity`).
4. Star handling in `VistaAccessibilityService`: `starCaptureWanted()`, the `MACRO_1` branch of `onKeyEvent`, `runStarAction()`, `STAR_KEYCODE`.
5. Key filtering that existed only for the star: the key filter is now requested only while `settings_key_capture` is on. Kept: Home-return window watch (`home_return`), Home long-press observe-only, no logging of key codes, `KEYCODE_SETTINGS` only consumed while its flag is on (off by default).
6. `Flag.CUSTOM_VOICE_ROUTING` (`custom_voice_routing`, "Templar voice search"). It had no code behind it.
7. Profile settings `SettingKeys.STAR_ACTION` (`star_action`) and `SettingKeys.VOICE_FALLBACK` (`voice_fallback`) and their defaults.
8. In-app voice recognition: there was no `SpeechRecognizer` code, no `RECORD_AUDIO` permission and no voice UI in the tree, so nothing else to remove. `ShortcutAction.VOICE_SEARCH` is kept: it is the "Google voice search" shortcut target in the plan (Google's own voice), and removing an enum value stored in Room would break existing rows.
9. Companion: no star-specific code existed (checked), nothing removed.
10. Docs amended with dated "Dropped 2026-11-25" notes instead of stale instructions: canonical plan 17.2 and the flag table (section 18), verification notes (banner, 2.1, 2.3, 4a, 5, 7, 8), design review 6.1 and decision 1, gap analysis 4.3, B5 and B2 handoffs, home-return/star handoff.

Kept on purpose (planned, not ruled out by device tests, no code yet): `jellyfin`, `ocr_observation`, `focused_tile_observation`,
`low_confidence_review`, `google_home_handoff` flags. They are listed in the canonical plan's flag table.

Leftover on devices (harmless): old SharedPreferences `star_key_capture` / `star_action` and any stored
`profile_settings` rows for `star_action` / `voice_fallback` are ignored. A stored quick-menu layout from #12 gains the new sections, shown.

## 4. Test and build results (PC, JDK 17 `C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot`)

- `:app:testDebugUnitTest`: **147 tests, 0 failures, 0 errors, 0 skipped** (19 suites).
- `:app:assembleRelease`: **BUILD SUCCESSFUL**.
- APK: `C:\Templar Archives\Robotics Facility\Templar-TV-combine\app\build\outputs\apk\release\app-release.apk`, 20,030,602 bytes.
- SHA-256: `94623C86DAEAE2E7C3160C1C8767D14C266225E66FD1C65D367E59C325E29326`
- applicationId `com.firestarterpro.templartv`, versionName `0.1.0`, versionCode `1` (same as installed, so `install -r` keeps data).
- Signing: debug key (`CN=Android Debug`), cert SHA-256 `ea35fcd6616326a1d8fdb3b22f2c27c4839e3a369750cacfd157aa6e6037868e`.
  The APK on the Streamer (pulled read-only with `adb pull` of `pm path`) has the **same cert**, so `install -r` will not hit a signature mismatch.
  Installed today: 19,374,674 bytes (the B3 build), lastUpdateTime 2026-11-25 09:36.
- A copy of the installed APK is kept at `Templar-TV-combine\rollback\templar-before-combined.apk` (SHA-256 `5772FAC1F0378853999D240EE0FF0C5EB2DA31E0C1D6D72A644EDD3CF01ECA37`, git-excluded) for rollback.

## 5. Install

Run `docs/handoffs/install-combined.ps1` (one `& { }` block, Windows PowerShell 5.1). It connects to `192.168.0.166:5555`,
uses `-s 192.168.0.166:5555` on every command (the tablet is also connected), checks the SHA-256 before installing,
runs `install -r`, enables the notification listener
`com.firestarterpro.templartv/com.firestarterpro.templartv.service.VistaNotificationListenerService`, prints the
SHA-256 and rollback lines, and copies all output to the clipboard. It does not change HOME, Google's launcher or any other setting.

## 6. Rollback

- Previous Templar build: `adb -s 192.168.0.166:5555 install -r "...\Templar-TV-combine\rollback\templar-before-combined.apk"`.
- Remove Templar: `adb -s 192.168.0.166:5555 uninstall com.firestarterpro.templartv` (HOME falls back to Google).
- Listener off: `adb -s 192.168.0.166:5555 shell cmd notification disallow_listener <component above>`.
- HOME role is reassignable: Settings → Apps → Default apps → Home app → Google TV, or `cmd role remove-role-holder android.app.role.HOME com.firestarterpro.templartv`.
- Google's launcher (`com.google.android.apps.tv.launcherx`) is untouched. Every new behaviour is behind a flag in Settings → Features.

## 7. Not verified on the Streamer

- Nothing from this branch has run on the TV. The wiring is unit tested at the routing level; the Compose UI is compile-checked only.
- Quick menu: new sections' look and D-pad flow, and Change view / Rows when the menu is opened over another app (Templar home must come to the front and then open the editor via the pending request).
- Shelf tile launch through `ShelfLauncher` on real provider deep links, and the tile moving to the front.
- Companion `open-menu` with `quick_menu` on (from the tablet).
- `home_return` and long-press Home after this build (code unchanged, but the key filter is now requested only for Settings capture, so a Home long-press is only observed while that flag is on; the window-based dashboard check still guards the bounce).
- Volume hold-to-repeat with a key filter on (still untestable by injection).
