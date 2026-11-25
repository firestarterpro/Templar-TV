# Templar TV big-box plan: platform verification notes (2026-11-25)

Review notes only. No code changed. This checks the [K] and [V] claims in
[2026-11-25-big-box-gap-analysis.md](2026-11-25-big-box-gap-analysis.md) against Android and Google TV sources.

Evidence labels used here:
- **[E]** read on 2026-11-25 (source linked in section 6).
- **[E-weak]** read on 2026-11-25, but the source is a community report or a single user.
- **[K]** Claude's platform knowledge, not confirmed by a page read today.
- **[V]** only the owner's Streamer can settle it.

## 1. Summary

The plan holds up. Nothing in it needs root or breaks a platform rule, and its two central design choices are confirmed: build Templar's own watch history, and never disable Google's launcher.

Five things in the plan are promised more firmly than the platform supports. None is a dead end, but each needs a fallback written into the plan before a build thread depends on it.

| # | Plan promise | Verdict | What to change |
| --- | --- | --- | --- |
| 1 | Voice search inside Templar using the remote's mic | **At risk.** No source confirms the remote mic feeds a third-party app. A long-running report shows in-app voice on Chromecast with Google TV failing and recovering with Google-side updates. | Make voice conditional on a real spike test. Name the fallback now (question 1). |
| 2 | Templar reads other apps' Watch Next rows | **Holds today, with an end date.** Google says the legacy Watch Next API loses support in the second half of 2027. The B1 probe over-reports what Templar can read. | Treat provider rows as a declining source. Measure from inside Templar, not from adb shell. |
| 3 | Star button opens a second "Templar Menu" entry | **Unproven.** Google only says "select an app on the list". A second entry in the same app is probably not listed separately. | Plan a tiny companion app as the fallback (question 2). |
| 4 | Accessibility key filter for global shortcuts | **Works, with a cost.** A report on the Streamer's current firmware says any key-filtering accessibility service breaks hold-to-repeat volume. | Key filter off by default. Test volume hold on the physical remote first (question 3). |
| 5 | Google Home tile opens the panel directly | **No public component.** The panel and the star button are served by Google's home app. | Keep the "Long-press Home" hint as the expected outcome, not the fallback. |

## 2. The five flagged areas in detail

### 2.1 SpeechRecognizer on Google TV

- Google's help page says the remote mic opens when you press and hold the Assistant button. It says nothing about third-party apps. [E]
- The plan line "the remote's microphone feeds it while Templar is in front" is therefore unverified. The Streamer has no built-in mic for apps to fall back on. [K]
- SmartTube issue 3507: on Chromecast with Google TV, in-app voice search showed the green mic indicator and then nothing happened. It began working again after an apparent Google-side update, broke for others later, and was still being reported in December 2025. The issue was closed without a fix. [E-weak]
- SmartTube's own documented voice path avoids the recognizer entirely. It catches the system's "open YouTube" voice intent through a bridge app. [E-weak] That route is not open to Templar without replacing a Google app.
- The API path itself is sound. Leanback's search screen uses `SpeechRecognizer` with `RECORD_AUDIO`. [K] The reference page did not load well enough today to confirm the wording.
- The Assistant button will keep going to Gemini. Pressing it inside Templar will not start Templar's search. The owner should expect a separate Templar mic control. [K]
- Gaps in the plan and probe:
  - The manifest has no `RECORD_AUDIO` permission yet. [E: manifest]
  - The B1 probe only lists recognition services. That proves a service exists, not that remote audio reaches Templar. A small spike build with one mic button is the only real test. [V]
  - The spike should record three things: whether results return, whether the user must hold any button, and whether focus leaves Templar.

### 2.2 Reading Watch Next versus Engage

- `READ_TV_LISTINGS` is a `dangerous` permission in the AOSP TV provider manifest, so `pm grant` works as the README says. `ACCESS_ALL_EPG_DATA` is `signature|privileged`. [E]
- The provider has no read permission attribute, so row filtering happens in code. Whether `READ_TV_LISTINGS` alone returns other apps' Watch Next and preview rows on Android 14 was not confirmed from source today. The Vista base depends on it, and the app is reported working, but nobody has recorded which packages' rows appear. [K][V]
- **Probe flaw:** B1 runs `content query` as the adb shell user, which has wider access than Templar. It can show rows Templar cannot read. Add a per-package row count to Templar's diagnostics screen and trust that number.
- Engage: the gap analysis is right. Engage continuation data goes to Google TV (servers when cross-device sync is on, otherwise held on the device for up to 60 days). Google documents no way for another app to read it. [E]
- New fact: Google's May 2026 developer post says "the legacy Watch Next API ... will lose support in the 2nd half of 2027", and the FAQ says all new Continue Watching integrations must use Engage. [E] Provider rows will thin out over the next year.
- Effect on the plan: MediaSession, Templar launches, and manual adds are the long-term sources. Provider rows are a bonus. The per-provider shelves in B4 must look right with zero provider rows.

### 2.3 Star button launching an app entry

- The star button sends key code `MACRO_1` (313) on firmware UTTK.260317.003, Android 14. Netflix is `BUTTON_4`, YouTube is `BUTTON_3`. [E-weak: two merged community PRs, October 2026]
- Google's help page lists three shortcut types: Google Home panel, a favourite app, or device inputs. For apps it says only "Select an app on the list". It does not say whether sideloaded apps appear or whether an app can appear twice. [E]
- A second launcher entry inside the same package is unlikely to show as a separate choice, because TV launchers list one entry per app. This is a guess. [K]
- Templar's main activity is also the HOME activity. If the picker does list Templar, the star button would open Templar's home, not the quick menu.
- The star and YouTube buttons are "routed through Google's home app, so they stop working when that home app is disabled". [E-weak] This supports the existing rule to never disable the stock launcher.
- Long-pressing the star button opens Google's shortcut editor. Templar should never consume it. [E]
- Fallbacks, best first:
  1. A tiny separate "Templar Menu" app (its own package, no UI) that opens the quick menu. Works with the stock picker if sideloaded apps are listed. [V]
  2. Capture `MACRO_1` in the accessibility service. Works without the picker, but carries the costs in 2.4, and the stock action may fire as well. [V]
  3. Leave the star on Google Home and open the quick menu from the keyboard only.

### 2.4 Accessibility key filtering

- The mechanism is real. A service with `flagRequestFilterKeyEvents` sees physical key events before apps do, including from a keyboard. Templar already requests it. [E: repo][K]
- **Volume regression.** On the Streamer (Android 14, UTTK.260317.003), a reporter found that an enabled key-filtering accessibility service stops hold-to-repeat volume after about six steps, even when the service passes volume keys through. Disabling the service fixed it. [E-weak: one report, October 2026] Templar's current build may already have this problem. [V]
- Keys injected with adb skip the filter. Shortcut tests must use the physical remote or keyboard. [E-weak]
- A sideloaded app's accessibility service is enabled through adb. Android 13 and later restrict enabling it from Settings for sideloaded apps. [K] The rollback doc should list the exact enable and disable commands.
- The plan's chord rules (modifier chords or F-keys only, pass everything else, log nothing from other apps) are correct and should stay.
- Recommended changes:
  - Put the whole key filter behind one flag, default off, separate from the quick-menu flag.
  - Shortcuts inside Templar's own screens need no accessibility service. Ship those first.
  - Add "hold volume up for three seconds with the service on, then off" to the pre-install checks.

### 2.5 Google Home panel component

- Google documents three ways in: the quick settings dashboard, long-pressing Home, and the star button shortcut. Requirements are standard mode, opt-in, a non-kids profile, and at least one smart device. [E]
- No public activity or intent for the panel is documented. The owner's lost edit targets `com.google.android.apps.chromecast.app`, which is the phone app's package. The gap analysis is right to doubt it. [K]
- Nothing Google publishes says the panel needs the Google TV launcher to hold the HOME role. One user report says the panel stopped working when the stock launcher was disabled and returned when it was left enabled. [E-weak, April 2025, before Android 14]
- Disabling `setupwraith` (a setup service some launcher guides disable) breaks Google Cast after a cold boot. [E-weak] `TEMPLAR_TV_RULES.md` already forbids disabling it. Keep that rule.
- **Most important device check:** with Templar holding HOME, does long-press Home still open the dashboard with the panel? This is the owner's hard constraint and it is unverified. Move it to the top of B1. [V]
- If B1 finds a launchable component, treat it as fragile. Google can rename it in any update. The tile needs the text hint as a permanent fallback.

## 3. Other claims checked

| Claim in the gap analysis | Verdict |
| --- | --- |
| `pm set-home` is not a standard command | Correct as written. The real commands are `cmd package set-home-activity` and `cmd role add-role-holder android.app.role.HOME`. [K] |
| Launcher package is `com.google.android.apps.tv.launcherx` | Confirmed by Streamer community sources. [E-weak] |
| HOME role stays with Templar | **New risk.** March 2026 reports say a Google TV update stopped Projectivy's home override for some users. Mechanism and devices are unclear. [E-weak] Move the HOME watchdog from Nice to Need. |
| "Settings → Apps → Default apps → Home app" rollback path (2026-11-22 handoff) | Unverified on the Streamer. B1 should confirm the menu exists. [V] |
| MediaSession reading through the notification listener | Correct API path. [K] Field quality per provider is still [V]. |
| Android TV handles HID keyboards and touchpads | Correct. [K] Google's May 2026 post also tells TV developers to support pointer hover, scroll, and click, so more apps should work with a touchpad. [E] |
| Streamer USB-C may not carry data | Likely too cautious. Earlier Google streamers accept powered USB-C hubs. [K][V] Bluetooth is still the simpler purchase. |
| Activity start from the accessibility service over another app | Allowed. Accessibility services are exempt from background-launch limits. [K] |
| TMDB watch-provider data needs JustWatch attribution | Correct. [K] |

## 4. Suggested edits to the plan and threads

1. Canonical plan section 13: change voice from "core feature" to "core feature, gated on the spike result", and name the fallback chosen in question 1.
2. B1: add the long-press Home check first, a physical-remote volume-hold check, and the Home app settings check. Drop reliance on the shell `content query` for coverage.
3. B0: add the per-package provider-row count and recognizer status to diagnostics, and the `RECORD_AUDIO` permission only when the voice flag ships.
4. B5: write the companion-app fallback into scope. Never consume star long-press.
5. B7: split into "shortcuts inside Templar" (no accessibility) and "global shortcuts" (flag, default off).
6. Add a new short thread, **S1 voice spike** (Sonnet, low): one mic button, three recorded outcomes, before B6 starts.
7. Gap analysis table: "Override HOME reliably" moves from Nice to Need.

## 5. Questions for the owner

Percentages are Claude's estimates of benefit toward "seamless, toggleable features".

1. **If the remote mic does not feed Templar's own recognizer, which voice fallback do you want?** (ranked)
   - a. Google's speech pop-up that returns the text to Templar, so results still show in Templar. About 70%.
   - b. Typing only, on the handheld keyboard or on-screen keyboard. About 40%.
   - c. Drop Templar voice and let Gemini handle spoken search. About 15%.
2. **The star button: what should it do?** (ranked)
   - a. Open the Templar quick menu through a small companion app, with Google Home one tile away inside the menu. About 85%.
   - b. Stay on Google Home, and the quick menu opens from the keyboard only. About 45%.
   - c. Open the quick menu through accessibility key capture. About 40%, because of the volume risk.
3. **Global keyboard shortcuts inside other apps need the key filter that may break hold-to-repeat volume. Ship them? (yes / no / test first)** Recommendation: test first, default off. About 60% if the volume test passes, near 0% if it fails.

## 6. Sources read on 2026-11-25

- [AOSP TvProvider manifest](https://android.googlesource.com/platform/packages/providers/TvProvider/+/93b0278/AndroidManifest.xml)
- [Android Developers: Integrate Continue Watching on Android TV (Engage)](https://developer.android.com/guide/playcore/engage/tv/continue-watching/client)
- [Android Developers: Engage SDK FAQ](https://developer.android.com/guide/playcore/engage/faq)
- [Android Developers Blog, May 2026: Increasing app discovery and engagement on Google TV](https://android-developers.googleblog.com/2026/05/increase-google-tv-app-discovery.html)
- [Google: Set up the Voice Remote customizable button](https://support.google.com/chromecast/answer/15157668)
- [Google: Control smart home devices on your Google TV](https://support.google.com/chromecast/answer/15158065)
- [Google: Use Assistant on the Voice Remote](https://support.google.com/chromecast/answer/10115165)
- [9to5Google: Streamer remote shortcut button](https://9to5google.com/2024/09/26/google-tv-streamer-remote-shortcut-button/)
- [SmartTube issue 3507: Voice Search not working](https://github.com/yuliskov/SmartTube/issues/3507)
- [SmartTube docs: voice search](https://www.mintlify.com/yuliskov/SmartTube/configuration/voice-search)
- [cleanyourtv PR 975: Streamer remote key codes](https://github.com/Gathin23/cleanyourtv/pull/975)
- [cleanyourtv issue 796: Streamer Android 14 volume repeat and Cast](https://github.com/Gathin23/cleanyourtv/issues/796)
- [free-my-tv PR 387: Streamer remote remapping](https://github.com/simbasjungles/free-my-tv/pull/387)
- [Android Authority: Google TV update blocks Projectivy override](https://www.androidauthority.com/google-tv-update-blocks-projectivy-override-3649445)
- [Reddit mirror: Google TV Streamer Projectivy question](https://axebps-redlib.hf.space/r/AndroidTV/comments/1jrrjxe/google_tv_streamer_projectivy_question)

Not done: nothing was tested on a device, and REA was not run.
