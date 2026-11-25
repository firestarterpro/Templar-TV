# Templar TV big-box plan handoff (2026-11-25)

## State
- Research and planning only. No app code changed in this PR.
- New canonical plan: [docs/plans/2026-11-25-templar-tv-canonical-plan.md](../plans/2026-11-25-templar-tv-canonical-plan.md). It supersedes the plan text in the owner's 2026-11-25 message wherever they differ, and the new asks win over the older text.
- Research and gap analysis: [docs/plans/2026-11-25-big-box-gap-analysis.md](../plans/2026-11-25-big-box-gap-analysis.md).
- Owner's local folder (`C:\Templar Archives\Robotics Facility\Templar TV`) is on branch `templar-tv-v0-foundation` with **uncommitted** Google Home quick-access edits to `QuickSettingsPanel.kt` and `GoogleHomeHandoff.kt`, and two untracked handoffs. They were read, not changed.

## Decisions and why
1. **Keep both Google surfaces reachable.** "Google Home menu" can mean the Google Home smart-home panel or the stock Google TV home screen. The plan protects both, because protecting only one risks losing what the owner meant. The panel opens from long-press Home or the star button (Google support page), so Templar must never consume long-press Home.
2. **Do not hijack the Assistant button for Templar voice.** Gemini stays the general assistant per the owner's plan. Templar gets its own mic in search and the quick menu. This satisfies "search on my app without bouncing to the Google TV launcher" without breaking Gemini.
3. **Templar builds its own "recently played".** Provider Continue Watching often goes only to Google now, so per-provider shelves are built from MediaSession, Templar launches, provider rows where available, and manual adds. Manual adds count as confirmed.
4. **Runtime feature flags first.** The owner wants every feature toggleable; today's flags are compile-time constants. Building the flag store first means every later feature lands toggleable from day one.
5. **Measure the device before building UI on it.** Watch Next coverage, MediaSession fields, recognizer availability, remote key codes, the Google Home panel component, and the real launcher package are all unverified. A short probe thread (B1) settles them, cheaply, before the expensive threads.
6. **Fix two safety issues found in review.** The accessibility service logs every key code from every app (a keylogger once a keyboard is attached), and it swallows the Settings key, which may hide the dashboard and its Google Home panel. Both are fixed in B0.
7. **Bluetooth keyboard preferred.** Most 92-key handhelds use a USB-A dongle; the Streamer's one USB-C port is mainly power. To be verified.
8. **REA not run.** Its stack rules require authorized targets and ask-first setup; commercial launchers are not authorized targets. Web research was used instead.

## Proposed threads

Verification (start these first):
- **V1 Fable: verify the plan works as stated.** Check every [K]/[V] claim in the gap analysis against Android docs and the B1 probe output; flag anything the plan promises that the platform can't do.
- **V2 Design review.** Review sections 9, 15, and 17 for a 10-foot UI: D-pad paths, edit mode, move mode, quick menu, readability across a room. Output: annotated wireframe notes, not code.

Build (short, one PR each, in order; each lands behind its flag):

| # | Thread | Model / effort | Depends on |
| --- | --- | --- | --- |
| B0 | Foundation fixes: runtime flag store + Settings → Features; commit the owner's Google Home quick-access edits (normalized line endings); stop logging other apps' keys; Settings-key capture behind a flag (off); fix rollback doc | Sonnet, medium | none |
| B1 | Device probe on the owner's PC (Remote Control + adb, read-only): run the probe below, record results in a dated handoff | Sonnet, low | none |
| B2 | Data layer: Room tables for shelf entries, favourite shows (ordered), row layout, shortcuts; migrations and tests | Opus, medium | B0 |
| B3 | MediaSession observer → per-provider recently played; Templar-launch evidence; confidence wiring | Opus, high | B1, B2 |
| B4 | Big-box home UI: provider shelves, Favourite Shows row, edit mode, move mode, Add-a-show tile, Row Manager | Opus, medium | B2 (B3 for live data) |
| B5 | Quick menu: extend the quick-settings activity, "Templar Menu" launcher entry for the star button, Google Home tile | Sonnet, high | B0, B1 |
| B6 | Voice + unified search: recognizer, TMDB source with its own toggle, subscription ranking, deep-link templates | Opus, high | B1, B2 |
| B7 | Keyboard: shortcut editor, chord rules over other apps, touchpad polish | Sonnet, medium | B0, B5 |
| B8 | Layout backup and restore to a local file | Sonnet, low | B2 |

After B4, B5, and B6: a short design-review pass and a Fable check each.

## Owner answers (2026-11-25, 3:41 AM Central)
1. **Star button: "let me choose in the app and change it."** Decision: a companion "Templar Menu" app is mapped once in Google's shortcut editor; Templar's Settings → Star button picks what each press does, and changing it never needs Google's editor again. Google Home panel stays one tile away in the quick menu, and choosing it in the setting shows how to map the star back to Google. Why: it's the only design that gives an in-app choice without the accessibility key filter's volume risk. Goes into B5.
2. **Keyboard: "I'll have to find it, but I think it's both."** Decision: support Bluetooth and USB dongle; B1 checks USB-C data on the Streamer. Recommend pairing over Bluetooth when the keyboard offers both.
3. **TMDB: yes.** Decision: TMDB is the catalog for adding shows and checking which services carry them. The owner creates a free TMDB account and API key, enters it on the TV only, and saves it in Spawning Pool. Never in git. Goes into B6.

## Device probe (B1), Windows PowerShell 5.1

Read-only. It runs adb queries, saves a report to `%TEMP%`, and copies it to the clipboard. It prompts at three points: play a show, open the Google Home panel, press remote keys. The report includes installed package names and what is playing, so share it only if you are comfortable.

```powershell
& {
  $ErrorActionPreference = 'Continue'
  $out = Join-Path $env:TEMP ('templar-probe-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
  $log = New-Object System.Collections.Generic.List[string]
  function Run([string]$label, [string[]]$a, [int]$max = 120) {
    $log.Add("===== $label =====")
    try { $r = (& adb @a 2>&1 | Out-String) -split "`r?`n" | Select-Object -First $max; $log.Add(($r -join "`n").TrimEnd()) }
    catch { $log.Add("ERROR: $_") }
  }
  if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { $m = 'adb not found on PATH'; Set-Clipboard -Value $m; Write-Host $m -ForegroundColor Red; return }
  $addr = Read-Host 'TV adb address (ip:port), or press Enter if already connected'
  if ($addr) { Run 'connect' @('connect', $addr) 5 }
  Run 'devices' @('devices', '-l') 10
  Run 'model and build' @('shell', 'getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.fingerprint') 5
  Run 'HOME activities' @('shell', 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME') 30
  Run 'HOME role holder' @('shell', 'cmd role get-role-holders android.app.role.HOME') 5
  $log.Add('===== relevant packages =====')
  $pk = & adb shell pm list packages 2>&1
  $log.Add((($pk | Select-String -Pattern 'launcher|home|chromecast|katniss|peacock|paramount|cbs|amazon|hbo|wbd|max|netflix|hulu|jellyfin|templar' | ForEach-Object { $_.Line }) -join "`n"))
  Run 'speech recognition services' @('shell', 'cmd package query-services --brief -a android.speech.RecognitionService') 20
  Run 'voice settings' @('shell', 'settings get secure voice_recognition_service; settings get secure assistant; settings get secure voice_interaction_service') 5
  Run 'watch next rows' @('shell', 'content query --uri content://android.media.tv/watch_next_program --projection package_name:title:episode_title') 80
  Run 'preview programs' @('shell', 'content query --uri content://android.media.tv/preview_program --projection package_name:title') 80
  Run 'input devices' @('shell', 'dumpsys input | grep -E "^ +[0-9]+: |Name:|Classes:|KeyLayoutFile:"') 80
  Run 'usb' @('shell', 'dumpsys usb | grep -iE "mode|data|power|connected"') 30
  [void](Read-Host 'Start a show in Peacock, Paramount+, Prime Video or Max, let it play, then press Enter')
  Run 'media sessions while playing' @('shell', 'dumpsys media_session') 150
  [void](Read-Host 'Long-press Home on the remote, open the Google Home panel, then press Enter')
  Run 'Google Home panel activity' @('shell', 'dumpsys activity activities | grep -E "ResumedActivity|mFocusedApp"; dumpsys window | grep -E "mCurrentFocus|mFocusedApp"') 20
  [void](Read-Host 'Press Enter, then within 20 seconds press: star button, long-press Home, and a few keyboard keys if one is paired')
  Run 'key events (20 s)' @('shell', 'timeout 20 getevent -l | grep EV_KEY') 200
  $text = $log -join "`n"
  $text | Set-Content -Path $out -Encoding UTF8
  Set-Clipboard -Value $text
  Write-Host "Saved $out and copied the report to the clipboard." -ForegroundColor Green
}
```

## Rollback
This PR adds documentation only. Revert the PR to undo it.

## Next action
Owner answers the questions on the PR. Coordinator starts V1 and V2, then B0 and B1 in parallel.
