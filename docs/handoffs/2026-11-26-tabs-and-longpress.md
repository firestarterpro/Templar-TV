# 2026-11-26 Home tabs rework and long-press fix

Branch `claude/project-thread-jv7f8v`, on top of draft PR #15 (`claude/combine-full-build`).

## Decisions
- Tabs are now **Templar TV**, **My Movies**, **Music**. Apps, Library and the old Shows tab are gone (screens left in code, unused).
- **Templar TV** is the old For you screen. The Apps row is now the first (left) row: it is first in `DefaultRows`, and a one-time migration (`apps_left_v1` in the `templar_migrations` prefs) moves it to the left on existing installs. Later moves in Row Manager are kept.
- **My Movies** is the old Movies tab (provider content). **Music** is a small screen with an "Open Jellyfin music" button (launches `org.jellyfin.androidtv`, else a toast). A real in-app music library is not built.
- The Google Home icon in the top bar is unchanged and still reachable.

## Long-press bug
Cause (reasoned from the code, not reproduced on the TV): the long-press menu opens and focuses "Open" while OK is still held, so the key repeats and release hit "Open" and launch the app. Fix: `AppContextMenu` swallows OK/Enter until the first key release (or 1.5 s), so the menu stays open. The menu already has Open, favourites, App info and Uninstall. It has no move-tile option; reordering apps is done in Row Manager / the quick menu.
The tile menu for non-app tiles (`Overlay.TileMenu`) was not changed.

## Not verified
Nothing compiled or tested here (no Android SDK in the cloud session). The PC build in `install-tabs-longpress.ps1` is the first compile.

## Install / rollback
Run `docs/handoffs/install-tabs-longpress.ps1`. Rollback commands are printed by the script (previous APK, uninstall, HOME back to Google).
