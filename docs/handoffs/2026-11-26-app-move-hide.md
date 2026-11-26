# 2026-11-26 App menu: Move and Hide

PR #15 and #17 merged to main (#11 to #14 closed as folded into #15).

- Long-press menu on an app now has **Move** and **Hide** (Templar TV tab, big-box home only).
- **Move**: lifts the app tile in the Apps row. D-pad moves it, OK drops it, Back cancels (existing move mode). Moves are limited to the tiles shown before "See all apps".
- **Hide**: removes the app from the Apps row only (search, Google launcher and the app itself are untouched).
- **Unhide**: Row Manager shows "Show N hidden apps" when any are hidden.
- Storage: plain preferences `templar_app_layout` (order, hidden) via `AppLayoutStore`; no database change. Clearing the app's data or "Show hidden apps" restores defaults. A saved order wins over the favourites-first default.
- Not compiled/tested in the cloud (no Android SDK); the PC build is the first compile.
