# 2026-11-25 B4 big-box home UI

## What changed
A new home screen (`com.firestarterpro.templartv.home`) replaces the "For you" tab content when the flag **Big-box home** is on (default on). Flag off, or a data-layer failure, brings back the classic For you screen unchanged.

- **Six views, one model.** Columns (default), Rail + grid, Spotlight, Wall, List, and Rows (always works, no flag). Each non-Rows view has its own flag (`Home view: ... (Beta)` in Features). The saved view (`home_view` profile setting) falls back to Rows if its flag is off. All views read the same rows, so switching never loses an edit and focus stays on the same show.
- **Same shelves everywhere.** `HomeModelBuilder` turns `row_layout` + shelves + favourites + channel content + apps into rows. Empty rows hide, except Favourite Shows and provider shelves (they show help and an "Add a show" tile). Rows cap at the "shelf length" setting (20) with a "See all" tile; provider rows sit behind "More from X" unless the row option is on.
- **Hold OK on a tile** opens the tile menu (max 5 actions, chosen by where the tile came from, "More…" for the rest), per design review 3.2. Apps keep the existing app menu.
- **Move mode** (Move in the tile menu): the tile lifts, the D-pad moves it along the view's axis (Rows/Spotlight left-right, Columns/List up-down, grids all four), OK saves, Back or Home restores. Only Templar-owned tiles move (favourites, shelf tiles not from a provider row).
- **Edit this row…** (More… on any tile): Move row (collapsed list), Hide row, Rename, Tile size, Show provider rows on home, Restore removed items, Row Manager, Change home view. Row Manager lists every row (hidden too) with Shown/Hidden and Move.
- **Focus rules after edits** (design review 3.4) are `HomeRules.focusAfterTileGone / focusAfterRowHidden`, applied before the edit is written. Focus is requested by tile key through `FocusCoordinator`, which scrolls to the tile if needed. Back from deep in a view goes to the first tile, then the top bar.
- **Home view picker** previews each view live behind a left panel; OK keeps it, Back keeps the old one.
- **Google Home icon** in the top bar (every view, and the classic home). It opens the Google Home app if installed; otherwise it tells you to hold the Home button for the Google Home panel (the panel has no public entry point).
- **Seeds the default rows.** Nothing in the app called `ProfileRepository.ensureDefault()` before; the new home view model does it once on start (safe to repeat, never overwrites edits).

## Flags
`BIG_BOX_HOME`, `HOME_VIEW_COLUMNS`, `HOME_VIEW_RAIL_GRID`, `HOME_VIEW_SPOTLIGHT`, `HOME_VIEW_WALL`, `HOME_VIEW_LIST`. Existing flags still gate the edits: `SHELF_EDITING` (tile edits), `FAVOURITE_SHOWS`, `ROW_MANAGER` (row edits), `PROVIDER_ROWS`, `PREVIEW_AND_UPCOMING`. A flag that is off hides the matching action; nothing stored is deleted.

## Settings
`home_view` default changed from `rows` to `columns` (Columns is the plan's default; Rows is the fallback). New `columns_count` (3 to 6, default 4, read by the Columns view; no UI to change it yet).

## Hooks for B5 (quick menu) and B3 (recently played)
- `HomeActions.openRowManager()`, `openViewPicker()`, `cycleView(+1/-1)` ask the home screen to open those editors. They do nothing off the home screen.
- B3 only needs to write shelf entries through `ShelfRepository.record(...)`; the home reads `observeShelf` / `observeContinue`, so new entries appear live. Launching from a Templar tile does not call `record` yet (B3's job).
- Touched shared files: `FeatureFlags.kt` (flags added after `ROW_MANAGER`), `ProfileSetting.kt` (default view, `COLUMNS_COUNT`), `HomeScreen.kt`, `VistaTopBar.kt`, one new drawable. Everything else is new under `home/`.

## Decisions (no owner input needed)
- Columns is the default view; Rows is the fallback; the classic home stays behind the Big-box flag.
- Channel (provider-published) tiles cannot be removed from the home yet (no table for them); their menu is Open, Add to Favourites. A shelf tile that came from a provider row can be removed (it goes to Recently removed) but not moved.
- Row move mode lists only rows that are showing; the move is mapped onto the full order so hidden rows keep their places.
- Row Manager and the view picker are reached from "Edit this row…", the empty-home button, or `HomeActions` (B5 will add the quick-menu entries). No extra top-bar icon.
- Provider package names in `ProviderCatalog` are unverified; matching falls back to the app label ("Peacock", "Paramount+", "Prime Video", "Max", "Netflix", "Hulu").

## Not done / needs the TV
- Not installed on the TV (the PC's safety check blocks relayed installs). Install command is in the PR.
- Unverified on the Streamer: smoothness of Columns and Wall with many posters, whether the on-screen keyboard opens from "Add a show" (press OK on the box), long-press timing of the hold-OK menu, and hold-to-repeat while moving a tile.
- Not built yet: per-view options other than column count, "?" badge for low-confidence titles (needs the review state from B-later), exact vs estimated is shown by solid vs dashed bar plus words in Spotlight/List, Jellyfin and review rows (no data source yet), moving app tiles (apps keep their existing order and favourites).
- Return from a provider app: the Activity stays alive, so focus stays on the same tile; not yet checked on the device.

## Rollback
Turn off **Big-box home** in Quick menu → Features to get the classic For you screen back immediately. The saved layout stays. Reverting the PR removes the code; the `row_layout` rows it seeded are harmless to the old build.

## Verification
Run on the Acer (TemplarArchives), 2026-11-25, in a separate worktree (`Templar TV-worktrees\b4-home`) at `65f7044`, JDK 17, Gradle 8.7. The main checkout and the TV were not touched.
- `:app:testDebugUnitTest`: **73 tests, 0 failures, 0 skipped.** New: HomeModelBuilderTest 12, HomeRulesTest 9, TileMenuTest 6, HomeViewTest 3, RowOptionsTest 2, ProviderCatalogTest 1. Existing data tests all pass (the `home_view` default test now expects `columns`).
- `:app:assembleRelease`: **BUILD SUCCESSFUL**, no compile fixes needed. `app-release.apk`, 19,756,118 bytes, SHA-256 `6F7DE9E1EFD797A40ACE5D37A981B5256B588865FE0F8DD8EB2D54870795D87C`. Not installed on the TV.
- The views and menus are Compose UI with no automated UI tests; they are checked only by compiling. Real use on the TV is the validation.

## For B3
B3's `ShelfLauncher.launch(entry)` (PR #11) should replace `BigBoxViewModel.open` for SHELF tiles once it merges. Until then SHELF tiles open through their deep link or provider package.
