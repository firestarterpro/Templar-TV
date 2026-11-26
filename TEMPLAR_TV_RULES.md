# Templar TV Rules

<!-- stack-sync: rules header v1 -->
Agent rules: follow [firestarterpro/stacks-attacks `AGENTS.md`](https://github.com/firestarterpro/stacks-attacks/blob/main/AGENTS.md). Registry record: `index/sources/proj-templar-tv.md` in stacks-attacks. This file adds only what is specific to this repo.

## Goal
Build a reversible, daily-use Google TV launcher named Templar TV while retaining the stock Google launcher and Google's Gemini voice experience.

## Operating model
- Install Templar TV beside the stock Google launcher.
- Never uninstall or disable the stock launcher, Gemini/Assistant, Google Cast, Google Play services, Play Store, system updates, input services, or setup services.
- Treat real daily use as validation. Collect diagnostics locally and make experimental features independently switchable.
- Every change affecting HOME, permissions, packages, or device settings must have a documented rollback.
- Prefer Android-supported APIs, provider-published rows, MediaSession data, and explicit user permissions.
- Do not bypass DRM, protected media paths, authentication, or provider access controls.

## Initial scope
1. Rebrand the Vista fork as Templar TV without breaking the existing build.
2. Preserve the current launcher behavior and Google voice button behavior.
3. Add feature flags before experimental integrations.
4. Add a local watch-history database with evidence and confidence fields.
5. Add playback observation using MediaSession where available.
6. Add provider modules for Peacock, Paramount+, Prime Video, Max, and future Netflix/Hulu.
7. Add Jellyfin/server-media rows and exact playback events.
8. Add preview sections for new, upcoming, leaving-soon, and personal server content.
9. Add visible Open Google Home and rollback guidance.

## Data and delivery
- Watch data stays local by default.
- Store source evidence, confidence, and candidate matches rather than forcing uncertain matches.
- Never commit credentials, API keys, addresses, private watch history, screenshots, databases, or signing keys.
- Work in small, buildable commits and keep main buildable.
- Verify with a release build, install beside stock Google Home, and record dated handoffs.

## Phase 1 success criteria
- Release APK builds successfully.
- App label is Templar TV with a side-by-side-safe package identity.
- Stock launcher remains installed and selectable.
- HOME can be assigned to either launcher.
- Gemini voice still works.
- Provider rows and deep links remain functional.
- Rollback guidance is present.
