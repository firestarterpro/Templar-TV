# Templar TV companion API (v1)

Version: 2026-11-25. Status: phase 1, experimental, off by default.

The companion API lets the Templar Tablet (or another trusted client on the home network) organise
Templar TV: favourite apps, favourite shows, provider shelves, home rows, launching apps and opening
the Templar quick menu. The TV is the authority for all data.

The remote control (D-pad, keys, text entry) is **not** part of this API. The tablet uses Android TV
Remote protocol v2 pairing for that. This API carries Templar-only commands.

## Turning it on and rolling back

- Settings → Features → **Companion API** (flag `companion_api`, off by default). When off, no port is open.
- Settings → Features → **Share watch history with companion** (flag `companion_history`, off by default).
  When off, `GET /history` answers `403 history_disabled`.
- Rollback: switch **Companion API** off, then use **Unpair all devices** on the Features screen.
  Uninstalling Templar TV removes the stored token hashes too.

## Transport

- Plain HTTP/1.1 on TCP port **8780**, JSON bodies (`application/json; charset=utf-8`), UTF-8.
- LAN only: connections from addresses that are not private (RFC 1918), link-local or loopback are closed
  without an answer.
- No CORS headers are sent, so browsers on other origins cannot read responses.
- One request per connection (`Connection: close`). Request bodies are capped at 64 KiB.
- Base path: `/api/companion/v1`. A breaking change gets a new version path.

## Pairing and authentication

1. On the TV, open Settings → Features and press **Show pairing code**. The TV shows a 6-digit code for 5 minutes.
2. The client calls `POST /pair` with `{"code":"123456","deviceName":"Templar Tablet"}`.
3. The TV answers `{"token":"<64 hex chars>","deviceName":"..."}` once. The code is used up.
4. Every other call (except `GET /status`) sends `Authorization: Bearer <token>`.

- The TV stores only the SHA-256 hash of each token, never the token itself.
- The client stores the token in its own private storage (DataStore on the tablet), never in git or logs.
- Rate limit: after 5 wrong codes the pairing endpoint answers `429 too_many_attempts` for 60 seconds and
  the current code is cancelled.
- **Unpair all devices** on the TV removes every stored hash.

## Data rules (shared with the B2 data layer, see `data/sync/SyncStore.kt`)

- The TV stamps `updatedAt` (epoch ms, TV clock). Clients never send it; it is ignored if sent.
- Last write the TV receives wins. Every call is idempotent and safe to retry.
- Ids are stable strings: `syncId` (UUID; a client may choose it for a new item) or `rowId` (e.g. `shelf:peacock`).
- Nothing is hard-deleted. `DELETE` is a soft delete that sets `removedAt`; `POST .../restore` clears it.
  Removing a row means `visible=false`.
- Order is an integer `position`, lower first. Clients change order only through `.../reorder` with the
  ids in the wanted order; ids left out keep their relative order after the listed ones.
- `?since=<ms>` returns items with `updatedAt >= since`, oldest first, including removed ones. Use the
  returned `asOf` as the next `since`. Seeing an item twice is harmless: apply by id.
- A write the TV refuses (feature flag off, unknown id, blank title) answers `422 refused`.
- `profile` query parameter selects the profile; it defaults to `default`.

## Errors

`{"error":"<code>","message":"<text>"}` with status 400 `bad_request`, 401 `unauthorized`,
403 `history_disabled`, 404 `not_found`, 405 `method_not_allowed`, 410 `no_pairing_code`,
422 `refused`, 429 `too_many_attempts`, 500 `internal`.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/status` | no | `{app, apiVersion, appVersion, authorized, pairingOpen, historyEnabled}` |
| POST | `/pair` | no | `{code, deviceName}` → `{token, deviceName}` |
| GET | `/profiles` | yes | `{items:[{id,name,archived}]}` |
| GET | `/apps` | yes | `{items:[{packageName,label,favourite,isLeanback}]}` |
| PUT | `/apps/favourites/{packageName}` | yes | `{favourite:true\|false}` → `{packageName,favourite}` |
| POST | `/launch` | yes | `{packageName}` → `{ok}` |
| POST | `/quick-menu` | yes | Opens the Templar quick menu on the TV → `{ok}` |
| GET | `/history?limit=50` | yes + flag | Read-only watch history, newest first, max 200 |
| GET | `/favourites?since=` | yes | `{items:[Favourite], asOf}` |
| PUT | `/favourites/{syncId}` | yes | Create or update a favourite show → Favourite |
| POST | `/favourites/reorder` | yes | `{ids:[syncId...]}` → `{ok}` |
| DELETE | `/favourites/{syncId}` | yes | Soft delete → `{ok}` |
| POST | `/favourites/{syncId}/restore` | yes | Restore → `{ok}` |
| GET | `/shelf-entries?since=` | yes | `{items:[ShelfEntry], asOf}` |
| PUT | `/shelf-entries/{syncId}` | yes | Create or update a shelf tile → ShelfEntry |
| POST | `/shelf-entries/reorder` | yes | `{provider, ids:[syncId...]}` → `{ok}` |
| DELETE | `/shelf-entries/{syncId}` | yes | Soft delete → `{ok}` |
| POST | `/shelf-entries/{syncId}/restore` | yes | Restore → `{ok}` |
| GET | `/rows?since=` | yes | `{items:[Row], asOf}` |
| PUT | `/rows/{rowId}` | yes | Change `visible`, `titleOverride`, `tileSize` of an existing row → Row |
| POST | `/rows/reorder` | yes | `{ids:[rowId...]}` → `{ok}` |
| GET | `/changes?since=` | yes | `{favourites:[...], shelfEntries:[...], rows:[...], asOf}` |

### Favourite

```json
{"syncId":"uuid","profileId":"default","title":"Severance","tmdbId":95396,"imdbId":"","tvdbId":"",
 "preferredProvider":"","artworkRef":"https://.../poster.jpg","position":0,"updatedAt":1764080000000,"removedAt":null}
```

### ShelfEntry

`provider` is `peacock`, `paramount_plus`, `prime_video`, `max`, `netflix`, `hulu` or `jellyfin`.
For Jellyfin, `externalId` is the Jellyfin item id. `source` is `MANUAL` for a client pin; the TV sets
`PROVIDER_ROW`, `MEDIA_SESSION` or `TEMPLAR_LAUNCH` for what it observed. `artworkRef` is a URL or
reference only; the TV never copies media.

```json
{"syncId":"uuid","profileId":"default","provider":"jellyfin","title":"The Expanse","seriesTitle":"",
 "contentType":"series","externalId":"a1b2c3","tmdbId":0,"imdbId":"","deepLink":"","artworkRef":"http://server:8096/Items/a1b2c3/Images/Primary",
 "source":"MANUAL","pinned":false,"hidden":false,"watched":false,"position":0,"updatedAt":0,"removedAt":null}
```

### Row

```json
{"profileId":"default","rowId":"shelf:peacock","kind":"PROVIDER_SHELF","provider":"peacock",
 "titleOverride":"","visible":true,"tileSize":"MEDIUM","position":3,"updatedAt":0}
```

### History item (only with `companion_history` on)

```json
{"id":12,"profileId":"default","provider":"peacock","title":"...","seriesTitle":"...","seasonNumber":1,
 "episodeNumber":2,"positionMs":0,"runtimeMs":0,"completion":0,"watched":false,"confidence":0.9,
 "evidence":"MEDIA_SESSION","startedAt":0,"stoppedAt":0}
```

## Phase 1 limits

- Favourite shows, shelves and rows are served from an in-memory store until the B2 data layer
  (`TemplarSyncStore`) lands. They reset when Templar TV restarts. The API shape does not change when the
  real store is swapped in.
- Launching an app or opening the quick menu from the companion relies on Templar TV being allowed to
  start activities from the background (it already holds the overlay permission granted at setup). If
  Android blocks it, the call still answers `{ok:true,"mayBeBlocked":true}`.
- Jellyfin browsing happens on the tablet, straight against the Jellyfin server. Pins reach the TV as
  shelf entries or favourites with `provider:"jellyfin"` and the Jellyfin item id.
