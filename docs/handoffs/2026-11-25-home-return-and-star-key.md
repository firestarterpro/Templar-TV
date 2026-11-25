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

## Rollback
- Switch the flags off in Quick menu → Features (applies immediately), or `adb shell pm clear com.firestarterpro.templartv`.
- Disable the service: `adb shell settings put secure enabled_accessibility_services ""` and `adb shell settings put secure accessibility_enabled 0` (only if nothing else used accessibility before; check the old value first).
- Reassign HOME: Settings → Apps → Default apps → Home app → Google TV, or `adb shell cmd role remove-role-holder android.app.role.HOME com.firestarterpro.templartv`.
