# 2026-11-25 Home opens Templar, and the star button

## Problem (live test, PR #6 comment 7165109551)
On the Google TV Streamer, with Templar as the HOME role holder, a short Home press still starts Google's launcher (`launcherx/.home.HomeActivity`). Long-press Home opens Google's dashboard (`DashboardHandler`, with the Google Home panel) and must keep working. The star button arrives as `KEYCODE_MACRO_1` (313) and Google's smart-home screen opens as well.

## Fix (both behind runtime flags, both OFF by default)
- **Flag `home_return` ("Home opens Templar")**: `VistaAccessibilityService` watches window changes. When Google's `HomeActivity` comes to the front it waits 300 ms and, if Templar is still the HOME holder, starts Templar's `MainActivity` (singleTask, so it resets to the top). The Home key is never consumed, so long-press Home and the dashboard are untouched.
  - Hands off when: another Google launcher window (the dashboard) appeared within 1.5 s, a Home long-press was seen (only when key filtering is on), Templar is not the HOME holder, or `HomeGuard.allowGoogleLauncherFor()` was called.
  - Circuit breaker: 4 bounces in 10 s pauses the feature for 60 s so it can never fight Google in a loop.
  - Needs no key filtering, so hold-to-repeat volume is not affected by this flag.
- **Flag `star_key_capture` ("Capture star button")**: while on, and the action isn't "Leave it to Google", the service asks for key events and consumes `KEYCODE_MACRO_1` (down and up) so Google's smart-home screen doesn't also open, then runs the action chosen in **Features → Star button action** (Templar quick menu [default], Templar home screen, Leave it to Google).
  - Key filtering is only requested while a key flag is on; the volume-hold risk from the platform findings applies to this flag only.

Logging: only window class names of Google's launcher and Templar (tag `TemplarHomeGuard`). No key codes are logged.

## Verification
No Android SDK in the cloud, so the build and device checks run on the PC. See the PR for results.

### Device test 2026-11-25 (Google TV Streamer, adb input keyevent only)
- First build compiled as-is. Short Home did **not** bounce: the log said `decision=false` with nothing else blocking. Cause: on the Streamer `cmd package resolve-activity -c HOME` still returns `launcherx/.home.HomeActivity` (priority=2) even while Templar holds `android.app.role.HOME`, so the `resolveActivity` check was always false. Fix: `templarIsHome()` now asks `RoleManager.isRoleHeld(ROLE_HOME)` first (API 29+), `resolveActivity` only as fallback. After the fix short Home from Settings lands on Templar `MainActivity` ~0.7 s after HomeActivity.
- Long-press Home (`input keyevent --longpress KEYCODE_HOME`) goes straight to `launcherx/.dashboard.DashboardHandler`; HomeActivity never shows, so no bounce. A real remote long-press was not tested.
- Star does **not** work: `KEYCODE_MACRO_1` is registered as a global key (`dumpsys window policy`: `KEYCODE_MACRO_1=launcherx/.remotecontrol.GlobalKeyListener_Receiver`). The window policy consumes global keys before the accessibility KeyboardInterceptor hands them to services, so Templar never receives it and dreamx `SmartHomeMainActivity` opens. An accessibility service cannot claim this key on this device. Options: drop the flag, or a window-based fallback (watch for SmartHomeMainActivity and open Templar's menu over it, with a flash, and an allow window for Templar's own "Open Google Home").
- Volume hold-to-repeat with key filtering on can't be checked with `input keyevent` (no real hold). That keeps `star_key_capture` off by default.
- Flags off: Home stays on Google's HomeActivity, star opens Smart Home, no `TemplarHomeGuard` lines.
- Edge case seen and fixed after the test: launcherx shows a `FrameLayout` overlay window when other apps open, which counted as an "other Google window" for 1.5 s and could skip the bounce. Now only windows whose class contains "dashboard" count; other launcherx windows are ignored. (Compiled by CI only; not re-run on the TV.)

## Decision on the star button
No accessibility-service route works on the Streamer, and the window-based fallback (react to `SmartHomeMainActivity`) can't tell the star key from a deliberate Google Home open, which would risk the Google Home path the owner wants kept. So it is not built. The flag stays off by default and is labelled as not working on the Streamer. The remaining option is Google's own star remap picker (launcherx magic button), which is a Google setting.

## Rollback
- Switch the flags off in Quick menu → Features (applies immediately), or `adb shell pm clear com.firestarterpro.templartv`.
- Disable the service: `adb shell settings put secure enabled_accessibility_services ""` and `adb shell settings put secure accessibility_enabled 0` (only if nothing else used accessibility before; check the old value first).
- Reassign HOME: Settings → Apps → Default apps → Home app → Google TV, or `adb shell cmd role remove-role-holder android.app.role.HOME com.firestarterpro.templartv`.
