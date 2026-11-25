# B2: local data layer (2026-11-25)

Branch `claude/project-thread-4rx6bu`. Written in the cloud, not compiled there (no Android SDK). Build and tests run on the PC; results are recorded at the end of this file.

## What it adds
One Room database, `TemplarDatabase` (version 2, same file name `templar-tv-watchhistory` so version 1 upgrades in place). Reach everything through `TemplarData.get(context)`.

| Table | Purpose |
| --- | --- |
| `profiles`, `profile_settings` | Local profiles (archived, never deleted) and per-profile settings (home view, star action, ...) |
| `watch_history` (v1 + 3 columns) | One row per watching session: confidence, review state, evidence source |
| `watch_evidence` | Every signal behind a match (source, confidence, detail) |
| `match_candidates` | Possible titles kept instead of forcing a match |
| `shelf_entries` | Per-provider "recently played" tiles the user can add, pin, hide, mark watched, move, change provider, remove |
| `favourite_shows` | Separate ordered favourites list |
| `row_layout` | Order, visibility, name, tile size per home row, per profile (all home views read it) |
| `shortcut_bindings` | Key chord to action, per profile, in-app or global |

## Decisions (change any of them; none needed the owner)
- **Soft delete only.** Remove sets `removed_at`; "Recently removed" lists and restores. No DAO deletes user content. (`profile_settings.reset` deletes a setting override so the default applies; that is not user content.)
- **One tile per series per provider** (`DedupeKey`). Removed tiles are not revived by passive observation; a manual add or a launch from Templar revives them.
- **Confidence:** best signal per source, then noisy-OR across sources. Confirmed at 0.80 or more, otherwise `NEEDS_REVIEW`. A user confirm or correction (MANUAL) is final. DISMISSED stays dismissed. Only confirmed sessions reach shelves. Base trust per source is in `EvidenceSource`. This puts MANUAL above everything as the user's own word; the plan lists it last only as the weakest *automatic* fallback.
- **Sessions:** the same title observed within 30 minutes is one sitting; 90% completion marks watched.
- **Flags** added: `PROVIDER_RECENT_SHELVES`, `SHELF_EDITING`, `FAVOURITE_SHOWS`, `ROW_MANAGER`, `KEYBOARD_SHORTCUTS` (all on), `GLOBAL_SHORTCUTS` (off). A flag that is off makes writes return null/false and never deletes data. Existing `WATCH_HISTORY` gates observation writes. `LOW_CONFIDENCE_REVIEW` stays a UI flag (the queue is always stored).
- **Removed `Parcelable`** from `WatchHistoryEntry` (hand-rolled, unused) and replaced `LiveData` with `Flow` in the DAO.
- **Jellyfin ids** are strings: new column `jellyfin_item_ref`; the old Long column is unused.
- Default home view setting is `rows` (always works); B4 flips it when Columns ships.
- Netflix and Hulu shelves are seeded hidden. One shelf row per provider; "provider's own rows" are optional extra rows (`PROVIDER_ROWS`).

## Sync with Templar Tablet
`data/sync/SyncStore.kt` is a plain-Kotlin interface the companion server calls (real impl `TemplarSyncStore`, available as `TemplarData.sync`). Favourites, shelf entries and shortcuts have a UUID `sync_id`; rows use `profile_id + row_id`. Every row has a TV-stamped `updated_at` (ms); removed rows keep `removed_at`. `*Since(profileId, since)` returns `updated_at >= since`, removed included. Order is an explicit integer `position`; clients reorder with ordered id lists. TV is the authority, last write wins, calls are idempotent. Jellyfin pins: `provider = "jellyfin"`, item id in `provider_content_id`, poster URL in `artwork_ref`, `source = MANUAL`.

## Not done here (later threads)
- Nothing calls the repositories yet: B3 (observer) calls `history.record`, B4 (UI) reads the flows, B8 (backup) exports the tables.
- No UI, no migration from the Vista favourite-apps DataStore (separate list by design).

## Rollback
Revert the PR. A device that already opened this build keeps a version 2 file, and an older build cannot open it (Room refuses downgrades). To go back: uninstall Templar TV (the history it holds is local only), or keep the file by exporting first (B8). Back up nothing automatically; nothing is deleted by the app.

## Verification
Run on the Acer, 2026-11-25, branch `claude/project-thread-4rx6bu` at `1ce0bca`, JDK 17 (`C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot`), Gradle 8.7. Built in a separate git worktree because the main checkout had an uncommitted owner edit to `VistaAccessibilityService.kt` (left untouched).

- `:app:testDebugUnitTest`: **40 tests, 0 failures, 0 errors, 0 skipped.** ChordsTest 4, ConfidenceTest 6, DedupeKeyTest 4, MigrationTest 1, OrderingTest 4, TemplarDataTest 21.
- MIGRATION_1_2: MigrationTest builds a v1 file by hand, then Room opens it and validates every migrated table, column, foreign key and index against the entities. It passed, so the hand-written SQL matches schema 2.
- `:app:assembleRelease`: **BUILD SUCCESSFUL** (1m 34s). `app/build/outputs/apk/release/app-release.apk`, 19,288,658 bytes, SHA-256 `899E8F48CBDC96C6ACB21CD3F535A857A76377274B1D02677E4E0C74001CFAF9`. Not installed on the TV.
- Changed: committed the exported `app/schemas/com.firestarterpro.templartv.data.TemplarDatabase/2.json` (`42efcf8`). No code fixes were needed.
- Environment pitfall: the shell's default `JAVA_HOME` points at JDK 25.0.5, which makes Gradle 8.7 fail with the bare error `25.0.5`. Set `JAVA_HOME` to JDK 17 and run `gradlew --stop` first.
