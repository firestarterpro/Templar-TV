# Templar TV bootstrap handoff — 2026-11-22

## Decision
Fork Vista and evolve it into Templar TV rather than creating a launcher from scratch.

## User intent
- Use Templar TV as the everyday launcher.
- Keep stock Google Home installed for Gemini visual results, sports briefs, and Google-native experiences.
- Keep Google's remote voice button and voice service.
- Prioritize Peacock, Paramount+, Prime Video, and Max; possibly add Netflix and Hulu later.
- Include personal shows, videos, and music from the user's server.
- Build directly and validate through normal use.
- Revert by switching HOME back to Google.

## Architecture direction
- Launcher: Vista fork, incrementally rebranded and extended.
- Coexistence: Templar TV and stock Google launcher installed together.
- Observation: on-device MediaSession first; provider-published rows and focused metadata where available.
- History: local database with provider, title candidates, season/episode, timestamps, position, duration, completion, evidence, and confidence.
- Personal media: Jellyfin for exact metadata and playback progress.
- Discovery: broad catalog cache narrowed into a personalized candidate index.
- Voice: Google/Gemini remains the general assistant; Templar-specific media commands may be added without replacing Google.

## Guardrails
- No root or DRM bypass.
- Do not remove or disable Google Home, Gemini/Assistant, Cast, Play services, Play Store, updates, input, or setup components.
- Do not commit secrets, device addresses, account data, private history, signing keys, screenshots, or local databases.
- Put experimental modules behind feature flags.
- Give each device mutation a rollback.

## First implementation sequence
1. Confirm upstream build and CI.
2. Choose a side-by-side-safe Templar package identity.
3. Rebrand visible strings/assets without changing behavior.
4. Add feature flags and a diagnostics boundary.
5. Add local watch-history schema and repository.
6. Add MediaSession observation.
7. Add Google Home launch/rollback affordances.
8. Add Jellyfin.
9. Add provider and preview modules incrementally.

## Verification
A feature is complete only after release build, side-by-side installation, remote navigation, provider deep links, Gemini microphone behavior, and HOME rollback are exercised on the Google TV Streamer.
