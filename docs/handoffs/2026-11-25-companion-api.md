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
