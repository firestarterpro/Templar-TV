# B5: quick menu (2026-11-25)

Branch `claude/quick-menu`, from `origin/main` at `e634469` (contains PR #8 home_return / star_key_capture and PR #9 TemplarDatabase / SyncStore). Built in a separate worktree (`..\Templar-TV-b5`) so the owner's uncommitted edits in the main checkout were not touched.

## What it adds
One quick menu behind a new runtime flag **`quick_menu`** (Settings → Features → "Quick menu", experimental, **default off**).

- **Panel:** slides in from the **left** (Google's dashboard is on the right) over whatever is showing, about 460 dp wide, dimmed backdrop. D-pad scrolls and selects, Back closes, the trigger key pressed again closes. No auto-dismiss.
- **Sections** (order and visibility saved per profile): **Resume** (first; the last app opened from Templar), **Apps** (the existing pinned/favourite apps, up to 8), **Quick settings** (every action of the old panel, see below), **Templar** (Features, how to turn the menu off).
- **Pinned footer** (not in the reorderable list, can't be hidden or moved): **Google Home** (`GoogleHomeHandoff.openGoogleHome`, else "Hold the Home button to open Google Home."), **Google TV Home** (opens `com.google.android.apps.tv.launcherx` HOME after `HomeGuard.allowGoogleLauncherFor()` so Home return doesn't bounce it back; else a one-line guide), **Templar Home**.
- **Focus:** starts at the top. Focus can't leave the panel; Up from the top wraps to Google Home (2 presses from opening), Down from the footer wraps to the top. Inside Templar, closing the menu returns focus to the tile that had it (`focusRestorer` on the home screen group).
- **Edit this menu:** a tile below the sections (outside the layout, so it stays reachable even with every section hidden). OK shows/hides a section, ▲ ▼ move it, "Reset to default" removes the override.

## Decisions and reasoning
- **Quick settings folded in, nothing lost.** The old panel body was extracted unchanged into `QuickSettingsBody` (`QuickSettingsPanel.kt`) and is used by both the classic panel and the menu's Quick settings section. The only difference inside the menu: the "Open Google Home" tile is skipped (`showGoogleHome = false`) because the pinned footer already has it. Theme, Wi-Fi, Bluetooth, Audio output, Display, Accessibility, Screensaver, Date & time, Return-to-Google-TV guide, Features, Reload, All settings, notifications and version are all there.
- **Storage:** the B2 `profile_settings` table, key `quick_menu_layout`, value like `templar_shortcuts,resume,apps,-quick_settings` (`-` = hidden). No new table, no migration, no schema change. Hide is a flag; every section is always present in the stored layout. Decoding is tolerant: unknown ids and footer ids are ignored, sections added later are appended shown. The footer is never stored.
- **Resume = last launched app** (Templar's own `LaunchHistoryRepository`), because watch history is not written yet (B3). Continue Watching / Favourite shows / Change view sections from the design review are left for when B3/B4 provide the data; adding one is a new `QuickMenuSection` entry (old stored layouts pick it up automatically).
- **Star button dropped** (can't be captured on the Streamer). **Combined build 2026-11-25:** `StarAction` and `star_key_capture` are now removed; `SETTINGS_KEY_CAPTURE` stays. Original note: the existing `StarAction.QUICK_MENU` and `SETTINGS_KEY_CAPTURE` paths still start `QuickSettingsActivity`, which now shows the quick menu when the flag is on and the old panel when off. `VistaAccessibilityService` is unchanged.
- **Width 460 dp** (a third of 1920 px ≈ 320 dp is too narrow for 10-foot labels); left side per design review section 2.

## Entry points
1. **Templar key, inside Templar:** Menu, Settings or F2 in `VistaRoot`'s own key handling (Compose `onPreviewKeyEvent`). Not the accessibility service. Home and long-press Home are never touched.
2. **`QuickMenuController.open(context)`** (`quickmenu/QuickMenuController.kt`): if Templar's home is in front it opens the in-app panel; otherwise it starts `QuickSettingsActivity` with action `com.firestarterpro.templartv.action.OPEN_QUICK_MENU`. Returns false (does nothing) when the flag is off. The activity stays `exported="false"`, so only Templar's process can send it.
3. **Top-bar quick-settings button** on the home screen opens the quick menu when the flag is on (no change to home UI files; `VistaRoot` routes the existing callback).

### Companion API hook (PR #10, `claude/companion-api`)
`CompanionController.openQuickMenu()` currently starts `QuickSettingsActivity` directly. That already shows the quick menu when the flag is on. When PR #10 is rebased onto main, switch it to:
```kotlin
override fun openQuickMenu(): Boolean = QuickMenuController.open(context) ||
    runCatching { context.startActivity(Intent(context, QuickSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
```
so it uses the in-app panel when Templar is in front and still falls back to the old panel when the flag is off. A separate-package companion (the "Templar Menu" star app idea) would need a signature-level permission on an exported alias; not added, since the star button is dropped.

## Shared files touched (kept minimal)
- `FeatureFlags.kt`: one new enum entry `QUICK_MENU` (Features screen lists all flags, so no screen change).
- `QuickSettingsPanel.kt`: body extracted to `QuickSettingsBody`; `Tile`, `SubHeader`, `Hint`, `dateLine`, `timeLine` made `internal`. Re-indented (whitespace-only diff for most lines).
- `VistaRoot.kt`: flag branch; flag-off branch is the original code.
- `QuickSettingsActivity.kt`, `AndroidManifest.xml` (intent-filter on the non-exported activity).
- No home-screen or recently-played files changed (B3/B4 are working there).

## Flag off = old behaviour
`VistaRoot` renders exactly the old `HomeScreen` + `QuickSettingsPanel` and old key handling. `QuickSettingsActivity` shows the old panel; the new action finishes immediately; no new `onNewIntent`/key behaviour. The extracted body renders the same tiles in the same order with the same modifiers.

## Rollback
- Fastest: Settings → Features → Quick menu → Off. Saved layout stays in `profile_settings` (harmless).
- Code: revert the PR. No database or schema change, no permissions, no HOME/package/device-setting change.

## Verification (PC, 2026-11-25, JDK 17.0.21, Gradle 8.7)
- `:app:testDebugUnitTest`: **51 tests, 0 failures, 0 errors, 0 skipped** (QuickMenuModelTest 11 new; ChordsTest 4, ConfidenceTest 6, DedupeKeyTest 4, MigrationTest 1, OrderingTest 4, TemplarDataTest 21).
- `:app:assembleRelease`: **BUILD SUCCESSFUL**. `app-release.apk` 19,391,114 bytes, SHA-256 `9611E0C635C4079F4E69E0B5AB950BCA39AD41986BC2C639BFCFEF44D2FD9675`. **Not installed on the TV.**
- Not verified on device: Compose focus behaviour (wrap, focus restore, focus not leaving the panel) is from the Compose 1.7 API, untested on the Streamer.

## Test plan on the TV (owner)
1. Install beside stock. With the flag **off**: Menu/Settings key and top-bar button open the old right-side quick settings exactly as before.
2. Features → Quick menu → On. Back to home, press Menu: panel slides in from the left, focus on Resume (or first tile).
3. Up from the top lands on Google Home; OK opens Google Home (or shows the hold-Home line). Down from the footer wraps to the top.
4. Google TV Home opens Google's launcher and Templar does not bounce back (also with Home opens Templar on). Templar Home returns to Templar's home.
5. Open an app from Apps, come back: Resume shows it. Every quick setting works as before.
6. Edit this menu: hide Apps, move Quick settings up, reopen the menu, layout kept; Reset to default restores it. Footer is always shown.
7. Back and Menu close the menu; focus returns to the tile you were on.
8. If Settings key capture is on, press Settings inside another app: the menu opens over it; Back returns to the app. Note whether video pauses (Peacock, Paramount+, Prime Video, Max).
9. Every build touching keys: long-press Home still opens the dashboard with Google Home; the Assistant button still opens Gemini.
