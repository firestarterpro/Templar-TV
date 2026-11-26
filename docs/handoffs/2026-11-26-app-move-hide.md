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
