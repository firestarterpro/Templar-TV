# B3: recently played shelves, observer layer (2026-11-25)

Branch `claude/project-thread-bkbqlj`, on top of B2 (PR #9). Data and observer layers only: no home UI (B4) and no quick menu (B5). Written in the cloud; built and tested on the PC (see Verification).

## What it adds
Everything is in `com.firestarterpro.templartv.observation` unless noted.

| Piece | What it does |
| --- | --- |
| `ProviderCatalog` | App package to provider id (Peacock, Paramount+, Prime Video, Max, Netflix, Hulu). Only listed packages are observed. Entries seen on the owner's Streamer are marked `verified`. |
| `MediaMetadataParser` (pure) | Turns an app's MediaSession metadata into title, series, season, episode and a confidence. Reads every text field for an episode marker (`S2:E5`, `Season 2, Episode 5`, `2x05`, `Episode 5`). Ignores ads, placeholders, the app's own name and items shorter than 90 s. |
| `PlaybackTracker` (pure) | Decides when a session becomes a viewing: it must play 45 s (or start 3 min or more in, i.e. a resume), so autoplay previews never reach a shelf. Then saves position every 60 s while playing, on pause or stop, when the next title starts, and when the session ends. An ad in the middle of a show does not end the show. |
| `MediaSessionObserver` | Android glue. Lists media sessions of known provider apps (needs notification access, see below), feeds the tracker, writes through `TemplarData.history.record` in order. `status` flow: `STOPPED / OFF / NO_ACCESS / WATCHING`. Follows the flags live. |
| `ProviderLaunchStore` | When a provider app is opened from Templar, remembers the time (one timestamp per package, local). Used as weaker supporting evidence and later to order shelves by most recently used provider. |
| `ShelfLauncher` | For B4: opens a tile (deep link if any app resolves it, else the provider app) and moves the tile to the front, keeping its saved progress. |
| `ObserverDiagnostics` | In-memory log of decisions: package names, field names and reason codes only. Never a title. Logcat tag `TemplarObserver`. |

Changes outside `observation/`:
- `WatchHistoryRepository`: `PlaybackObservation.corroboration` (extra weaker evidence on the same viewing). `confirm()` and `correct()` from the review queue now put the title on the shelf (before, a confirmed session never reached a shelf until another observation arrived). `correct()` also renames the series so the tile follows the corrected name.
- `VistaNotificationListenerService`: starts and stops the observer when the listener connects or disconnects.
- `AppRepository.launch`: marks a provider app launch.

## Decisions (change any; none needed the owner)
- **Evidence**: a clean reading is `MEDIA_SESSION` at 0.90 and is confirmed straight away. A vague reading (no episode marker but a distinct album field) is 0.70 and goes to the review queue; being opened from Templar adds 0.50, and the noisy-OR of the two is 0.85, which confirms it. Titles with only a title field are kept as is at 0.85.
- **Launch hint is not a title**: opening Peacock from Templar adds a weak `TEMPLAR_LAUNCH` signal to the next qualifying session within 15 minutes. It never creates a tile by itself.
- **A tile launched from a shelf keeps its progress**; a launch means "I chose this", not "I watched this".
- **Observer follows the existing flags**: `MEDIA_SESSION_OBSERVATION` and `WATCH_HISTORY` (both default on). No new flags, no schema change, no migration.
- **Logs never hold titles** (rules: no private watch history in logs or git). All test titles are invented.
- **Manual adds need no new code**: `shelves.addManual` (B2) already counts as confirmed, and a later observation of the same show updates that tile.

## What must happen on the TV (not done by this PR)
Android only shows other apps' media sessions to an app whose **notification listener** is enabled. On 2026-11-25 only the stock launcher's listener was enabled, so until this is done the observer reports `NO_ACCESS` and records nothing.

Enable (adds Templar; does not replace the stock launcher's listener):
```
adb shell cmd notification allow_listener com.firestarterpro.templartv/com.firestarterpro.templartv.service.VistaNotificationListenerService
```
Undo:
```
adb shell cmd notification disallow_listener com.firestarterpro.templartv/com.firestarterpro.templartv.service.VistaNotificationListenerService
```
Do not use `settings put secure enabled_notification_listeners ...` as the older rollback doc suggests: it replaces the whole list and would switch off Google's own listener.

## Known limits
- Provider metadata is only as good as each app publishes it. The parser is tuned from the owner's device captures (below); other providers fall back to review.
- If the user corrects a title, a later observation under the app's original title opens a new session that goes to review again (the corrected name is not remembered as an alias). Fine for now; an alias table is a small follow-up.
- Netflix often publishes no metadata at all, so it may only ever show up through manual adds.

## Rollback
Revert the PR. No schema change, so the database is untouched. To stop observing without reverting: turn off Settings → Features → Playback observation, or run the `disallow_listener` command above. Nothing is deleted either way.
