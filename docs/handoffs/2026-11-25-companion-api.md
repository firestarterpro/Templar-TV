# Handoff: companion API v1 (2026-11-25)

Branch `claude/companion-api`, stacked on the B2 data layer (`claude/project-thread-4rx6bu`, PR #9).
Tablet side: Templar-Tablet branch `claude/companion-client` (v0.5.0).

## Done
- Contract: `docs/companion-api.md` (HTTP/JSON on LAN port 8780, `/api/companion/v1`).
- `companion/` package: `CompanionPairing` (6-digit code, 5 min, 5 misses → 60 s lock, SHA-256 token hashes only),
  `CompanionRouter` (pure, codes against B2's `SyncStore`), `CompanionServer` (ServerSocket, LAN/loopback peers only,
  64 KiB body cap, no CORS), `CompanionController` (runs only while `companion_api` is on; real store via `TemplarData.get(context).sync`).
- Flags (both off): `companion_api`, `companion_history`. Settings → Features shows address, pairing code, Unpair all.
- Tests: 17 JVM tests (router + real sockets) pass. `:app:assembleRelease` builds.

## Decisions (owner, via coordinator)
- No B2 tables here; B2 owns entities. Remote D-pad stays on Android TV Remote v2 (tablet), not this API.
- History is read-only and behind its own toggle. Tokens: hashed on TV, DataStore on tablet.
- Jellyfin browsing is tablet-side; pins arrive as shelf entries with `provider:"jellyfin"`, `source:"MANUAL"`.

## Not done / next
- Not installed on the TV (needs the owner's go). To try: install, Settings → Features → Companion API on,
  Show pairing code, then pair from the tablet (TV & Media → Organize).
- Launch / quick menu from the background depend on the overlay permission already granted at setup; the API
  reports `mayBeBlocked` when it is missing. Verify on the Streamer.
- Shortcuts have no sync methods yet (skip for v1). `app/schemas/` from B2's KSP config is left uncommitted here.
- Rebase onto `main` once #9 merges.

## Rollback
Turn Companion API off (port closes), press Unpair all devices, or uninstall Templar TV.

## Streamer install (2026-11-25, owner approved)
Before install (Google TV Streamer, Wi-Fi adb):
- Installed Templar TV: 0.1.0, versionCode 1, first installed 2026-11-22, last updated 2026-11-25 08:30.
  APK pulled to the installing PC as a backup (SHA-256 7929af47…1842).
- HOME role holder: `com.google.android.apps.tv.launcherx` (stock). Not changed.
- Accessibility services enabled: none. Not changed.
- Overlay (`SYSTEM_ALERT_WINDOW`) for Templar TV: `default` (not granted). Not changed.
- Port 8780 not listening.

Installed build: this branch's release APK (includes B2 data layer, SHA-256 8f92f3f4…ef19), `adb install -r`, same versionCode.
Database: B2 keeps the file name `templar-tv-watchhistory` and migrates v1 → v2 with `ALTER TABLE ... ADD COLUMN`
and `CREATE TABLE IF NOT EXISTS` only; no destructive fallback is configured, so existing rows are kept.

Rollback commands (replace the address with the Streamer's adb address):
- Remove Templar TV and its data; the stock launcher stays HOME: `adb -s <streamer> uninstall com.firestarterpro.templartv`
- Go back to the previous APK instead (a v2 database cannot be opened by v1, so its data is cleared first):
  `adb -s <streamer> shell pm clear com.firestarterpro.templartv` then
  `adb -s <streamer> install -r -d templartv-0.1.0-before-companion.apk`

Install and overlay were not run by the agent (the PC's permission check blocks device changes relayed through
the project assistant); the owner runs them. Owner approved granting the overlay permission so companion
launch and quick-menu calls are not blocked by Android's background-start rules.

Install (Windows PowerShell 5.1):
`& { adb -s <streamer> install -r "<path>\app-release.apk" 2>&1 | Set-Clipboard }`

Grant overlay:
`& { adb -s <streamer> shell appops set com.firestarterpro.templartv SYSTEM_ALERT_WINDOW allow; adb -s <streamer> shell appops get com.firestarterpro.templartv SYSTEM_ALERT_WINDOW } 2>&1 | Set-Clipboard`

Revoke overlay (back to the state before install):
`& { adb -s <streamer> shell appops set com.firestarterpro.templartv SYSTEM_ALERT_WINDOW default } 2>&1 | Set-Clipboard`

## On-device verification (2026-11-25 ~15:20, owner installed and paired)
Read-only checks from the PC plus taps on the tablet's own UI:
- PASS: Templar TV running, no crash in logcat. HOME still the stock launcher. Accessibility services: none.
- PASS: port 8780 listening with Companion API on; `GET /status` answers from the LAN, `authorized:false` without a token.
- PASS: tablet shows "Paired with Templar TV 0.1.0 at 192.168.0.166".
- PASS: Open app from the tablet (Paramount+ came to the front on the TV).
- PASS: Open Templar menu from the tablet (QuickSettingsActivity came to the front over Paramount+).
- PASS: Pin a favourite app (DIRECTV) from the tablet.
- FAIL → fixed: Unpin timed out on the tablet. Cause: the companion host called `AppRepository.loadApps()`, which
  decodes every app's banner and icon (seconds on the Streamer), twice per call. Fixed on `claude/companion-fixes`
  with an icon-free listing and direct launch intents; tablet read timeout raised to 15 s.
- FAIL → fixed: Home rows list was empty. Nothing seeds the default profile's rows yet (B4 UI will); the companion
  now calls `profiles.ensureDefault()` (idempotent) when it starts.
- Observation: `appops get` shows `SYSTEM_ALERT_WINDOW: default` after the owner's grant, yet app launch and the quick
  menu still came to the front. So `mayBeBlocked` (based on `canDrawOverlays`) is pessimistic here; the starts work.
- Observation: `historyEnabled:true`, so "Share watch history with companion" is on.
- NOT RUN on device: favourite shows / shelf pin / restore. The tablet adds those from Jellyfin search, and no
  Jellyfin URL/key is set on the tablet; the PC has no token. Covered by the JVM tests.
- Still open: DIRECTV is left pinned as a favourite app (the unpin call timed out before the fix). Unpin from the
  tablet after the fixed TV build is installed.
