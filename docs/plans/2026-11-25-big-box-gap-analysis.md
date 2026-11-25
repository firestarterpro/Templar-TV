# Templar TV big-box gap analysis and platform research (2026-11-25)

> **Superseded in part (2026-11-25, afternoon):** Templar voice search (4.3) is dropped, Home and star button wait on the PR #8 spike, quick settings folds into one quick menu, and home views are selectable. See "Latest decisions" in the canonical plan.

Research only. Nothing here is built yet. Companion to
[2026-11-25-templar-tv-canonical-plan.md](2026-11-25-templar-tv-canonical-plan.md).

Evidence labels:
- **[E]** checked evidence: this repo's source, the owner's local folder, or an official page read on 2026-11-25.
- **[K]** Claude's platform knowledge, not yet checked on this device.
- **[A]** assumption.
- **[V]** must be verified on the Google TV Streamer before a build depends on it.

## 1. Summary

- Templar TV already has the Vista base: per-app content rows from the TV provider, a Continue Watching row, favourite and recent apps, text search, and a quick-settings panel that opens over any app. [E]
- The biggest gaps against commercial launchers are: **editable per-provider "recently played" shelves, a separate reorderable favourite-shows list, voice search inside Templar, row editing (reorder, hide, add), a pop-up quick menu with shows and apps, and keyboard shortcuts.**
- Android TV allows all of these without root. The two hard limits are: (1) much provider "Continue Watching" data now goes to Google through private channels and is not readable by a third-party launcher, so Templar must build its own history from MediaSession, its own launches, and manual adds; (2) the Assistant button belongs to Gemini, so Templar's voice search needs its own trigger.
- The Google Home smart-home panel is opened from the system dashboard (long-press Home, or Settings at the top of the screen) or from the remote's star button. [E] Templar must never swallow long-press Home, and the quick menu must link to the panel.
- Today's feature flags are compile-time constants, so the user can't toggle anything yet. A runtime flag store with a Settings → Features screen is the first build step. [E]

## 2. What the repo and past chats already had (lost ideas recovered)

| Item | Where | State | Plan action |
| --- | --- | --- | --- |
| "Open Google Home" tile, "Return to Google TV Home" guide, graceful failure message, Templar labels in quick settings | Owner's local folder, uncommitted edits to `QuickSettingsPanel.kt` and `GoogleHomeHandoff.kt`, plus untracked handoff `2026-11-22-templar-tv-google-home-quick-access.md` [E] | Never built or committed. The diff rewrites whole files (likely a CRLF line-ending change). It targets package `com.google.android.apps.chromecast.app`, the Google Home phone app, which may not exist on the TV [V] | Carry into build thread B0. Normalize line endings, keep the owner's wording, retarget to the real panel/launcher once B1 finds them. |
| `templar-tv-status.md` build-fix breadcrumbs | Owner's local folder, untracked [E] | Superseded by the 2026-11-22 sideload handoff, which reports a working release build | Keep as history. No action. |
| Rollback commands `pm set-home com.google.android.tvlauncher` | `docs/handoffs/2026-11-22-templar-tv-v0-foundationrollback.md` [E] | `pm set-home` is not a standard command, and `com.google.android.tvlauncher` is the older Android TV launcher. Google TV uses `com.google.android.apps.tv.launcherx` [K][V] | B1 lists real HOME activities on the device; B0 fixes the rollback doc. README already uses `cmd role add-role-holder android.app.role.HOME`, which is the right mechanism [K]. |
| Accessibility service logs every key code from every app | `VistaAccessibilityService.kt` [E] | With a hardware keyboard this would write typed text, including passwords in other apps, to logcat | B0: gate the log behind the diagnostics flag and never log key codes from other apps in release builds. |
| Accessibility service consumes `KEYCODE_SETTINGS` "so the system dashboard doesn't also open" | `VistaAccessibilityService.kt` [E] | The system dashboard is where the Google Home panel lives. If the Streamer remote or a keyboard sends this key, Templar hides the panel | B0: make dashboard capture a flag, default **off**. B1 checks which key the Streamer remote sends. |
| Feature flags as `val` constants | `FeatureFlags.kt` [E] | Not user-toggleable | B0: runtime flag store (DataStore) + Settings → Features screen + diagnostics status. |
| Jellyfin server exists on the home LAN, used without Tailscale | stacks-attacks handoffs [E] | Address stays out of git; entered on the TV | Jellyfin rows use a runtime setting only. |
| REA (reverse-engineering toolset) in the stack | stacks-attacks `STACKS.md` [E] | Its rules say authorized targets only and setup is ask-first | Not run. Commercial launchers are not authorized targets. Best use later: one Templar feature at a time. |

## 3. Gap analysis against commercial launchers

Need = build it. Nice = build later behind a flag. Skip = not needed or out of scope.

| Capability | Who does it well [K unless noted] | Templar today [E] | Verdict |
| --- | --- | --- | --- |
| Cross-app Continue Watching | Google TV, Apple TV Up Next, Roku, Fire TV | One row, from apps that publish Watch Next to the TV provider | **Need**: Templar's own history merges provider rows, MediaSession, launches, and manual adds |
| Recently played **per provider** | Fire TV and Apple TV per-app top rows, Projectivy channel rows | Per-app rows exist but are read-only and only as good as what the app publishes | **Need**: editable per-provider shelves |
| Add / remove / edit a show on a shelf | Roku Save List, Apple TV watchlist | None | **Need** |
| Separate favourite-shows list, reorderable | Roku Save List, Apple TV Up Next order, Google TV Watchlist | Favourite apps only | **Need** |
| Reorder, hide, add rows | Projectivy, FLauncher, Fire TV, Roku | Fixed rows | **Need**: row manager |
| Voice search filtered to my subscriptions | Roku, Apple TV, Fire TV, Google TV ("my services") | Text search over apps and loaded content cards only | **Need**: Templar voice search |
| Hardware keyboard and touchpad | Android TV supports HID natively; Shield users rely on it | Works for D-pad keys only by accident; no shortcuts | **Need**: shortcuts + typing in search |
| Programmable remote button | Streamer star button [E], Shield menu button, Roku shortcut keys | Not used | **Need**: integrate with the star button |
| Pop-up quick menu over any app | Fire TV long-press Home menu, Apple TV Control Center, Google TV dashboard | Quick settings over any app (system tiles only) | **Need**: extend to shows, favourites, apps, Google Home |
| Override HOME reliably | Projectivy and Kyu use an accessibility "override launcher" mode [E: PCWorld, Make Tech Easier] | HOME role via adb [E] | **Nice**: watchdog that notices when an update resets HOME and offers the re-apply command |
| Layout backup / restore | Projectivy | None | **Need** (cheap, protects customizations) |
| Profiles | Google TV, Apple TV, Projectivy [E] | Planned in architecture | Keep in plan (already canonical) |
| Wallpapers, themes, icon packs, tile sizes | Projectivy premium [E], Apple aerials | Day/night theme | **Nice**, flag-gated |
| Ambient / screensaver with art | Google TV ambient, Apple TV | Opens system screensaver settings | **Nice** |
| Up Next episode (next unwatched episode) | Apple TV, Roku | None | **Need** (follows from history + catalog) |
| New-episode badges for followed shows | Apple TV, Roku | None | **Nice** |
| HDMI input tiles | Projectivy [E] | n/a | **Skip**: the Streamer has no HDMI inputs; star button can already show inputs [E] |
| Built-in app store / sideload sources | Projectivy [E] | n/a | **Skip**: adb is enough |
| Live TV guide | Google TV, Fire TV | n/a | **Skip** for now (no live-TV subscription in plan) |
| Sports scores, weather, briefs | Google TV, Gemini | Gemini | **Skip**: stays with Gemini by design |
| Sponsored rows and ads | Google TV, Fire TV, Roku | None | **Skip** (non-goal) |
| Parental controls | Google TV kids profiles, Apple TV | None | **Skip** until profiles land |

## 4. What Android TV actually allows, per requested feature

### 4.1 Recently played per provider, editable, with add

- **Provider rows.** Templar can read other apps' preview channels and Watch Next rows from the TV provider with `READ_TV_LISTINGS` (granted by adb). The system-wide aggregated Watch Next table needs a privileged permission and is not available. [E: README]
- **Coverage gap.** Many large streaming apps now send Continue Watching to Google TV through Google's Engage service instead of the on-device TV provider. That data is not readable by a third-party launcher. [K][V] Expect Peacock, Paramount+, Prime Video, and Max coverage to vary. B1 measures it per app with `adb shell content query --uri content://android.media.tv/watch_next_program`.
- **MediaSession.** Because Templar already has a notification listener, it may call `MediaSessionManager.getActiveSessions()` and read the title, subtitle, duration, and position that a provider's player publishes. [K][V] This is the main source for "recently played" when provider rows are empty. Some apps publish only the app name or nothing. [K]
- **Templar launches.** Templar knows which tile it launched and when, which gives strong evidence when the user starts a show from Templar. [E: launch history exists]
- **Manual add.** The user adds a show by searching a catalog (TMDB) and picking the provider. Stored locally in Room. Needs a TMDB API key entered on the TV, never in git. [K]
- **Editing.** Shelf entries are Templar's own records, so remove, pin, mark watched, change provider, and reorder are all local operations. Entries that came from a provider row can be hidden but not deleted at the source.
- **Opening a show.** Provider rows carry their own deep links. For manual and MediaSession entries, Templar uses a per-provider deep-link template where one is known, otherwise opens the provider app (and its search, when the app supports a search intent). [K][V] The UI must show which kind of link it is.

### 4.2 Separate favourite-shows list you can rearrange

- Fully local: a Room table with an explicit position column, separate from favourite apps and from history.
- D-pad move mode: long-press OK on a tile → "Move", arrows move it, OK drops, Back cancels. This matches how Google TV moves apps. [K]
- Keyboard: Ctrl+arrow moves a focused tile.
- Jellyfin favourites can be shown in the same list with a "server" badge, but stay owned by Jellyfin.

### 4.3 Voice search inside Templar across subscriptions

- **Do not hijack the Assistant button.** It is Gemini's, and the plan keeps Gemini as the general assistant. Intercepting it would also be fragile. [K]
- **Templar's own mic button** on the search screen uses `SpeechRecognizer` (or `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`). On Google TV the recognition service is usually provided by the Google app, and the remote's microphone feeds it while Templar is in front. [K][V] Needs the `RECORD_AUDIO` runtime permission, which Android TV can request with a normal dialog. [K] B1 checks `SpeechRecognizer.isRecognitionAvailable()` and whether results come back without leaving Templar.
- **Fallback:** if the recognizer is unavailable or bounces to Google, Templar keeps text search and keyboard typing, and the voice button explains why.
- **Triggers for Templar voice:** the mic button in Templar search, the quick menu, a keyboard shortcut, and the star button if the user maps it to Templar.
- **Search sources:** Templar history, favourites, shelves, provider rows on the device, Jellyfin, and TMDB with its watch-provider data (sourced from JustWatch, attribution required). [K]
- **Ranking:** included-with-my-subscriptions first, then Jellyfin, then rent/buy. Search terms sent to TMDB leave the device, so the TMDB source has its own toggle.
- **Open result:** deep link where known, otherwise open the provider app and keep the title on screen.

### 4.4 Wireless handheld keyboard with touchpad (92-key)

- Android TV supports USB and Bluetooth HID keyboards and touchpads natively. A touchpad shows a mouse pointer. Arrow keys act like the D-pad, Enter selects, Esc acts as Back, and media keys work. [K]
- **Connection:** most 92-key handhelds use a 2.4 GHz USB-A dongle. The Streamer has a single USB-C port, mainly for power, so a dongle would need a USB-C hub with power pass-through, if the port carries data at all. [K][V] A **Bluetooth** model avoids this and is the safer purchase. [A]
- **In Templar:** every key reaches Templar's own screens, so shortcuts and typing in search are easy.
- **In other apps (global shortcuts):** need the existing accessibility service's key filter. Rules: only act on chords that include a modifier (for example Ctrl+Alt+letter) or on keys apps don't use (F1–F12), and pass everything else through so typing in other apps is not broken. Never log keys from other apps.
- **Shortcut editor:** Settings → Shortcuts: "Press a key combination", then pick an action (open app, open shelf, open favourites, voice search, quick menu, Google Home panel, play/pause, back to Templar). Stored per profile and included in backup.
- Keyboard keys such as Home, Menu, and Search are handled by the system first. Templar does not remap those by default. [K]

### 4.5 Pop-up shortcut menu from the remote

- **What already exists:** `QuickSettingsActivity`, a translucent activity the accessibility service opens over any app. [E] Templar's quick menu extends it.
- **Contents (all user-editable, each section toggleable):** Continue watching (top 5), favourite shows, pinned apps, voice search, Google Home panel, Google TV Home, inputs, quick settings.
- **Triggers, best first:**
  1. **Star button.** The Streamer remote's customizable button can open the Google Home panel, a favourite app, or the input list. [E: Google support] Templar can expose a second launcher entry, "Templar Menu", so the star button can launch the quick menu directly with no accessibility key capture. [K][V] Trade-off: if the star button currently opens the Google Home panel, remapping it moves the panel one step away (long-press Home still opens it). See question 1.
  2. **Keyboard shortcut** (any chord the user records).
  3. **Accessibility key capture** of a remote key that apps don't use. B1 logs which keys the Streamer remote sends; long-press Home is excluded because it opens the system dashboard.
- **Over a playing video:** opening an activity pauses some players. A `SYSTEM_ALERT_WINDOW` overlay keeps the app underneath running but is harder to make focusable. [K] v1 uses the activity; the overlay is a later flag.

### 4.6 Keep Google Home reachable (hard constraint)

Two different Google surfaces matter, and Templar keeps both:
- **Google Home panel** (smart-home lights, cameras, thermostats): opened from the system dashboard, reached by long-pressing Home or from Settings at the top of the Google TV screen, or directly by the star button. Needs standard mode, opt-in, and at least one smart device; kids profiles are not supported. [E: Google support] Templar never consumes long-press Home and shows a "Google Home" tile in the quick menu and quick settings. If B1 finds a launchable component for the panel, the tile opens it; otherwise the tile shows "Long-press Home to open Google Home". [V]
- **Google TV Home** (stock launcher): stays installed. Templar shows "Open Google TV Home" and the "Return to Google TV" guide from the owner's lost edits.

## 5. Sources read on 2026-11-25

- [Google: Set up the Google TV Streamer Voice Remote customizable button](https://support.google.com/chromecast/answer/15157668)
- [Google: Control smart home devices on your Google TV](https://support.google.com/chromecast/answer/15158065)
- [9to5Google: Google TV Home panel](https://9to5google.com/2024/08/14/google-tv-home-panel)
- [Android Authority: Projectivy Launcher](https://www.androidauthority.com/projectivy-launcher-android-tv-3684205/)
- [Make Tech Easier: Projectivy Launcher](https://maketecheasier.com/use-projectivy-launcher-android-tv)
- [PCWorld: Make Google TV look like Roku](https://www.pcworld.com/article/3197435/cant-stand-google-tv-make-it-look-like-roku-instead.html)
