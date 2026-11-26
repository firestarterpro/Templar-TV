# 2026-11-26 App menu: Move and Hide

PR #15 and #17 merged to main (#11 to #14 closed as folded into #15).

- Long-press menu on an app now has **Move** and **Hide** (Templar TV tab, big-box home only).
- **Move**: lifts the app tile in the Apps row. D-pad moves it, OK drops it, Back cancels (existing move mode). Moves are limited to the tiles shown before "See all apps".
- **Hide**: removes the app from the Apps row only (search, Google launcher and the app itself are untouched).
- **Unhide**: Row Manager shows "Show N hidden apps" when any are hidden.
- Storage: plain preferences `templar_app_layout` (order, hidden) via `AppLayoutStore`; no database change. Clearing the app's data or "Show hidden apps" restores defaults. A saved order wins over the favourites-first default.
- Not compiled/tested in the cloud (no Android SDK); the PC build is the first compile.

## Jellyfin tabs (same day)
- **My Movies** and **Music** now list movies and albums from Jellyfin (`JellyfinClient`, `JellyfinScreen`), with posters/covers. Default server `http://192.168.0.150:8096`.
- Server address and API key are entered in the tab (setup form, "Server settings" tile at the end of the grid) and stored in preferences `templar_jellyfin` on the TV only. Never in git. No key: the tab shows the setup form. Server down or key rejected: it shows the error with Try again.
- Select opens the Jellyfin app. It first tries a `jellyfin://items/ID` link; if the app can't take it, it just opens Jellyfin and a toast names the title. The deep link is untested.
- Manifest now allows cleartext HTTP (`usesCleartextTraffic`) because the LAN server is plain HTTP.
- Not compiled/tested in the cloud. TV series are not listed yet (Movie and MusicAlbum only).

## Jellyfin key from a phone or PC (same day)
Typing a 32-character key with the remote was too painful. While the Jellyfin setup form is on screen, Templar now runs a tiny web page (`JellyfinSetupServer`, port 8781, LAN addresses only) and shows `http://<TV IP>:8781` plus a 6-digit code. Open it on any phone or PC, enter the code and key, and the TV saves it and loads the library. The page stops when the form closes, and after 5 wrong codes. The key is stored only in the TV's `templar_jellyfin` preferences.

## Full library inside Templar (same day)
- Tabs: Templar TV, My Movies, My Shows (new), My Music.
- Browse in Templar: series → seasons → episodes, album → tracks (Jellyfin `ParentId` queries). Back goes up a level.
- Play in Templar: `JellyfinPlayerActivity` (media3 ExoPlayer 1.4.1, new dependency). Direct stream of the original file with the key in the `Authorization: MediaBrowser` header (never in the URL). Episodes and tracks queue the rest of the season/album. No transcoding: a file the Streamer can't decode shows the player's error. Resume position and watch-state sync with Jellyfin are not built.
- The Jellyfin app is no longer opened by Templar.

## Focus fix (same day)
Opening a show or album jumped back to the Templar TV tab: the focused tile vanished when the grid swapped, focus fell to the first tab, and tabs select on focus. The breadcrumb title now holds focus while the next level loads, then hands it to the first tile. Owner approved merging PR #18 on 2026-11-26.

## Reasoning behind today's choices
- Kept the "My" tab names to match the owner's "My Movies".
- Used plain preferences, not Room, for app order/hidden and Jellyfin settings, so there was no schema migration.
- Built playback into Templar (media3) because the owner wants the full library without the Jellyfin app. The trade-off is no transcoding yet.
- Added the phone/PC web page for the key because typing a 32-character key on the remote failed repeatedly.

## Review fixes (same day, later)
- Player: after the TV sleeps it comes back on the same item and position, paused (no autoplay on wake). Screen-on follows isPlaying only.
- Jellyfin user: saved user if still enabled, else the first administrator, else the first enabled user. Saved in `templar_jellyfin` prefs.
- Library lists come from the real libraries of the matching type; libraries named "... (preview)" are skipped, and repeated title+artist/year are shown once.
- Flags (all default on): `jellyfin` (now default on, no longer experimental), `library_tabs`, `jellyfin_web_setup` (port 8781 page), `app_move_hide`.
- CI (`.github/workflows/build.yml`) now runs unit tests before the release build. New tests: `JellyfinLogicTest`, `AppLayoutTest`.
