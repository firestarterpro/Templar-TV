# Templar TV big-box design review (2026-11-25)

Design notes only, no code. Reviews sections 9 (home screen), 15 (visual customization) and 17 (input, shortcuts, quick menu) of [2026-11-25-templar-tv-canonical-plan.md](2026-11-25-templar-tv-canonical-plan.md) for a 10-foot TV UI.

Evidence labels, as in the gap analysis:
- **[E]** checked in this repo's source on 2026-11-25.
- **[K]** Claude's platform and TV-design knowledge, not checked on this device.
- **[A]** assumption.
- **[V]** to verify on the Google TV Streamer.

"% benefit" means a rough estimate of how much an option moves the owner's goal (a daily-use launcher that feels like the big-box ones and never loses Google Home). It is a judgment call [A], not a measurement.

## 1. Summary

The plan is sound and buildable without root. The main design risks are about **how many presses things take** and **what happens to focus after an edit**, not about what features exist.

Top fixes, in order of impact:

1. **Too many rows.** Section 9 lists 14 rows, and each provider adds "recently played, then the provider's own rows". That is 20 or more rows, so the Apps row sits 20+ presses down. Use one shelf per provider by default, with the provider's own rows behind a "More from Peacock" tile. (~25% benefit)
2. **Row actions can't be reached.** Row titles are plain text today and can't take focus [E: `RowHeader` in `HomeTab.kt`]. "Long-press a row title" won't work with a D-pad. Put row actions in the tile menu ("Edit this row…") and in the Row Manager. Don't make titles focusable, because that doubles the presses per row. (~15%)
3. **Focus rules after edits are missing.** After Remove, Hide, Move, or a background data refresh, focus must land somewhere predictable. Rules are in section 3.4. Without them, D-pad users get "lost" focus or a jumping screen, which is the most common way TV launchers feel broken. (~15%)
4. **Too many tile actions.** Section 9 lists 8 actions per tile. Show at most 5, chosen by where the tile came from, with the rest under "More". Merge "Remove" and "Hide" into one "Remove" that does the right thing and is undoable. (~10%)
5. **One menu, not two.** Templar has an in-app quick-settings panel on the Menu key [E: `VistaRoot.kt`] and the plan adds a quick menu. Fold quick settings into the quick menu as one section so there is one menu to learn. (~10%, decided, see section 9)
7. **Choice of home views** (owner ask): Rows, Columns, Rail + grid, Spotlight, Wall and List, all reading the same shelves. See section 10. Full settings map in section 11.
6. **Google Home gets a fixed, non-hideable slot** in the quick menu and a top-bar icon on the home screen, so it is never more than 3 presses away inside Templar, plus long-press Home from anywhere. (~10%, protects the hard rule)

Decisions I made without asking, because the plan or context already answers them:
- Move mode stays inside one row. Up and Down are blocked while moving a tile. Rows move only in row move mode or the Row Manager.
- The quick menu opens as a left-side panel, so it doesn't look like Google's right-side dashboard and the two are never confused.
- Removed items go to a "Recently removed" list in the Row Manager instead of being deleted, matching the owner's never-delete-permanently rule. So no confirm dialogs are needed.
- Moves and removals save immediately on drop. Back during a move cancels and restores the old position.

## 2. D-pad paths (press counts)

Counted from Templar home with focus on the first tile of row 1. Targets are what a daily user should get [K]. "Today" is the plan as written.

| Task | Plan as written | Proposed | Target |
| --- | --- | --- | --- |
| Resume last show | OK (1) | OK (1) | 1 |
| Open a favourite show | Down, Right×n, OK | same (Favourites is row 2) | ≤ 4 |
| Reach Max shelf | ~8 to 16 Down (provider sub-rows) | 6 Down | ≤ 6 |
| Reach Apps row | 14 to 20+ Down | Back to top, then Up, Right to "Apps" tab, OK (~4) | ≤ 5 |
| Open Google Home panel from Templar home | long-press Home (1) or quick settings path (unknown) | long-press Home (1), or Up, Right to Google Home icon, OK (~3) | ≤ 3 |
| Open Google Home panel from inside a provider app | long-press Home (1) | long-press Home (1), or star/menu key then Google Home (~2) | ≤ 2 |
| Add a show to a shelf | Right to end of row (can be 20+), OK | hold OK on any tile, "Add a show here" (~3) | ≤ 4 |
| Reorder a favourite by 3 places | hold OK, Move, Right×3, OK | same (6) | ≤ 6 |
| Hide a row | long-press row title (not reachable) | hold OK on a tile, "Edit this row…", Hide (~4) | ≤ 4 |

Rules to keep paths short [K]:
- **Back from deep in the home list** scrolls to the top and focuses row 1, tile 1. A second Back moves focus to the top bar. Templar already has a reset signal for Home [E: `resetSignal`], so wire Back to the same behaviour.
- **Left and Right never leave a row** (Templar already does this [E: `focusProperties` exit Cancel in `HomeTab.kt`]). Keep it. Wrapping from the end of a row back to its start is not wanted on TV.
- **Up and Down keep the column.** Moving between rows should land on the tile nearest the same screen position, not on tile 1. Compose's default 2D focus search does this when rows are aligned; check it with mixed poster and landscape rows. [K][V]
- **Long rows cap at 20 tiles**, then a "See all" tile opens a grid. Long rows are where users get lost. [K]

## 3. Home screen and shelves (section 9)

### 3.1 Row structure

Proposed default order (all movable and hideable):

1. Continue Watching (merged, one tile per series, newest first)
2. Favourite Shows
3. Recently Added (server)
4. Peacock (recently played; last tile "More from Peacock")
5. Paramount+ (same)
6. Prime Video (same)
7. Personal Shows and Movies
8. Personal Music
9. Max (same)
10. New and Upcoming
11. Leaving Soon
12. Review (only when items need review; a single tile showing the count)
13. Apps

Changes from the plan:
- Provider-published rows go behind "More from …" by default. A per-row option "Show provider rows on home" brings them back for users who want them inline.
- Netflix and Hulu rows appear only when enabled, at the end of the provider block.
- **Empty rows hide themselves**, except Favourite Shows and enabled provider shelves, which show an "Add a show" tile and one line of help ("Nothing from Peacock yet. Play something, or add a show.").
- Continue Watching shows one tile per series. If a favourite show is in progress it appears in both rows; that is fine and expected.

### 3.2 Tile menu (hold OK)

Show up to 5 actions, the first being the most likely. Which ones appear depends on where the tile came from:

| Tile source | Menu (top to bottom) |
| --- | --- |
| Any shelf, Templar's own entry (launch, MediaSession, manual add) | Open · Add to Favourites (or Remove from Favourites) · Move · Remove · More… |
| Provider-published row entry | Open · Add to Favourites · Remove (hides it, the provider still owns it) · More… |
| Favourite Shows | Open · Move · Remove from Favourites · More… |
| Jellyfin item | Open · Add to Favourites · Mark watched · More… |

"More…" holds: Mark watched, Change provider (Templar entries only), Show evidence, Add a show here, Edit this row…

"Edit this row…" holds: Move row, Hide row, Rename, Tile size, Show provider rows on home (provider shelves only), Restore removed items.

Notes:
- "Show evidence" is for the owner's confidence engine. Keep it, but under More, because it is rarely used.
- Labels are plain verbs. Don't use icons alone.
- The menu opens next to the tile (the existing `AppContextMenu` pattern [E]), with focus on the first action and Back closing it.

### 3.3 Move mode

**Tiles:**
- Entering: tile menu → Move. The tile lifts (scale up, shadow, left and right chevrons on its sides). A hint bar at the bottom reads "◀ ▶ move · OK place · Back cancel".
- Left and Right swap the tile with its neighbour and the row scrolls to keep it visible. Hold to repeat.
- Up and Down do nothing (short buzz sound) while moving.
- OK saves. Back restores the original position. Pressing Home also cancels.
- Keyboard: Ctrl+Left/Right moves the focused tile without entering move mode.
- Only Templar-owned lists can be reordered (Favourites, Templar shelf entries, Apps). Provider-published rows keep the provider's order and their menu doesn't show Move.

**Rows:**
- Entering: "Edit this row…" → Move row. The home screen collapses to row titles only (one line each), so the whole list fits on screen and moving is fast.
- Up and Down move the row. OK saves. Back cancels.
- The Row Manager in Settings does the same thing for all rows, with on/off toggles.

### 3.4 Focus rules after edits (new; add to the plan)

| Event | Focus goes to |
| --- | --- |
| Tile removed or hidden | The next tile in the row, or the previous one if it was last. If the row is now empty and hides itself, the same column in the next row. |
| Row hidden | The same column in the next row (previous row if it was last). |
| Tile moved (OK) | Stays on the moved tile. |
| Move cancelled | Back on the tile at its original position. |
| Data refresh while the user is browsing | Focus stays on the same item (keys are stable [E: rows keyed by `section.key`, tiles by `id`]). Do not re-sort Continue Watching while it has focus; apply the new order when focus leaves the row or on the next Home press. |
| Return from a provider app | The tile that launched it. |

### 3.5 Honest labelling of progress and source

The plan says the UI must distinguish exact, estimated, unknown, low-confidence and manual. Proposed tile marks [K]:
- **Exact progress** (provider row, Jellyfin, MediaSession position): a solid progress bar.
- **Estimated** (Templar timed the session): a dashed or striped bar.
- **Unknown position**: no bar.
- **Low-confidence title**: a small "?" badge; the item goes to the Review row and never into Continue Watching as fact.
- **Manually added**: no badge (the user said so; it is confirmed).
- **Personal server**: a small server badge.
Never use colour alone for these; use shape and a text label in the focused-tile spotlight.

## 4. Big-box visuals (section 15)

- **Safe area**: keep content inside 5% overscan margins (about 48 dp left and right, 27 dp top and bottom on a 960×540 dp screen). [K] Check the hero spotlight and top bar too.
- **Text size from across a room**: tile titles at least 16 sp, row titles 20 to 24 sp, body text in menus at least 16 sp. [K] Long titles truncate with an ellipsis and show in full in the spotlight.
- **Poster shelves**: big-box style uses 2:3 posters. Show at least 5 tiles on screen plus a partial sixth, so it's obvious the row scrolls. At current card spacing, poster height around 190 to 210 dp fits that. [K][V] Today each row picks its dominant aspect ratio [E: `dominantAspectRatio()`]; keep that so landscape rows still look right.
- **Focus state** must be visible on any artwork: scale about 1.08 to 1.1 plus a solid outline, not just a glow. Templar's focus ring works today [E]; keep it in the big-box style.
- **Row dimming**: unfocused rows fade to 34% opacity today [E: `rowAlpha` in `HomeTab.kt`]. With posters and badges that becomes hard to read. Suggest about 60%, adjustable in the theme options.
- **Tile size options** per row: Small, Medium, Large. Large posters mean fewer tiles per screen, so Large is the default only for Continue Watching and Favourites.
- **Performance**: posters are bigger images. Load at display size and keep the image cache warm for the first 2 rows, so the Streamer stays smooth. [K][V]
- No other launcher's branding, art or layout copying (already a non-goal).

## 5. Keyboard and touchpad (section 17.1)

- **Type to search**: on any Templar screen, typing a letter opens search with that letter already typed. `/` also opens search.
- **Shortcut cheat sheet**: F1 (or `?`) shows a list of the current shortcuts. Make this a built-in binding the user can change but not delete.
- **Reserved keys** that can't be bound: Home, Back, Esc, the Assistant key, power, volume. The shortcut editor greys them out and says why.
- **Conflict check**: when a chord is already bound, the editor says "Already used for X. Replace?" instead of silently overwriting.
- **Touchpad**: a click focuses and opens a tile. Hovering should not move D-pad focus, so a resting thumb doesn't make focus drift. Two-finger scroll scrolls the row or list under the pointer. [K][V] Pointer hover may still show a light highlight.
- **Esc = Back, Enter = OK**, as the plan says. Arrow keys behave like the D-pad, including in move mode.
- **Default shortcuts**, all changeable:
  - F1: shortcut sheet
  - F2: quick menu
  - F3: voice search
  - F4: Favourite Shows
  - Ctrl+Alt+G: Google Home panel (or its fallback hint)
  - Ctrl+Left/Right: move focused tile
  - Over other apps, only the F-keys and Ctrl+Alt chords act (flag `globalShortcuts`, default off, as planned).

## 6. Quick menu (section 17.2)

### Layout
- A **left-side panel**, about one third of the screen wide, over a dimmed app underneath. One vertical list; no nested menus beyond one level.
- Order (user can hide or reorder all sections except the pinned footer):
  1. **Resume**: the current or last show, one large item. Focus starts here.
  2. Continue Watching (next 4, compact)
  3. Favourite Shows (first 5, then "All favourites")
  4. Pinned apps
  5. Voice search
  6. Quick settings (folded in from today's panel, see section 9)
  7. **Pinned footer, can't be hidden**: Google Home · Google TV Home · Templar Home
- The footer is always the last items, so "Up from the top" or "Down to the bottom" reaches Google Home in a fixed number of presses. Up from item 1 wraps to the footer (the one place wrapping helps: it makes Google Home 2 presses away).

### 6.1 Star button setting (owner answer, 2026-11-25)

Matches canonical plan section 17.2 (PR #4, commit 3adf1da).

Settings → Star button, choices: **Quick menu (default)**, Voice search, Favourite Shows, Continue Watching, a chosen app, or Google Home panel.

How it works:
- The owner maps the star button once in Google's shortcut editor (hold star for 1–2 seconds) to a small companion app, "Templar Menu", with its own package. [V: sideloaded apps may not appear in Google's picker]
- Each star press opens the companion, which reads Templar's setting and runs that action. Later changes happen only in Templar.
- "Google Home panel" can't be opened by an app, so picking it shows the steps to point the star button back at Google Home in Google's editor.
- Google Home stays a pinned tile in the quick menu footer either way, so with the default it is star → Google Home (~2 presses). Long-press Home still opens the dashboard with Google Home in 1.
- Fallback if the companion can't be picked: accessibility capture of the star key, behind `starKeyCapture` (default off).

Design notes for the companion:
- It opens straight into the chosen action with no screen of its own, so a star press feels instant.
- Templar's Star button setting shows what a press does right now and has a "Set up star button" guide for the one-time mapping.

### Behaviour
- Pressing the trigger again closes it, as does Back. It returns to the app underneath.
- Opening a translucent activity over a playing video may pause it. Most players pause on `onPause` and some stop playback. Measure with Peacock, Paramount+, Prime Video and Max before calling the menu seamless. [K][V] The later overlay mode (`quickMenuOverlayMode`) is the fix if pausing bothers the owner.
- No auto-dismiss timer; TV users often step away mid-menu.

### Google Home in the quick menu
- The Google Home item opens the panel directly if the device probe finds a launchable component. Otherwise it shows one line: "Hold the Home button to open Google Home." [V]
- The item is part of the pinned footer and the `googleHomeHandoff` flag can't be turned off while Templar is HOME, as the plan already says.

## 7. Keeping Google Home reachable (check against the hard rule)

| Path | Where | Presses | Status |
| --- | --- | --- | --- |
| Long-press Home | Anywhere | 1 | Must never be consumed by Templar (plan rule). Test on every build. |
| Star button → quick menu footer | Anywhere | ~2 | Default setting (quick menu). 1 press if the owner points the star back at Google Home. See 6.1. |
| Top-bar Google Home icon | Templar home | ~3 | **New in this review**; add to the plan. |
| Quick menu footer | Anywhere the quick menu opens | ~2 | Pinned, can't be hidden. |
| Quick settings tile | Templar | ~3 | Plan already has it; now part of the quick menu (section 9). |

Also from the code [E]: the accessibility service consumes `KEYCODE_SETTINGS` today, and `VistaRoot.kt` uses Menu/Settings to toggle the in-app panel. If the Streamer remote sends Settings for its dashboard, Templar could hide the dashboard. The plan already moves this behind `captureDashboardKey` (default off). Keep that; this review adds nothing new there.

## 8. Additions to the plan (proposed text)

Add to section 9:
- "One shelf per provider by default; the provider's own rows open from a 'More from …' tile, or can be shown inline per row."
- "Row actions are reached from the tile menu ('Edit this row…') and the Row Manager. Row titles are not focus stops."
- "Focus rules after edits" (table in 3.4 above).
- "Removed items go to 'Recently removed' and can be restored."

Add to section 15:
- Safe-area, text-size, focus-state, and row-dimming notes from section 4.

Add to section 17.2:
- Left-side panel, Resume first, pinned Google Home footer, toggle-to-close, and the pause-on-open check.

Add to section 20 (validation), per build that touches the home screen:
- Press-count check for the table in section 2.
- Focus never lost after remove, hide, move, or refresh.

## 9. Owner answers (2026-11-25)

1. **Star button:** chosen and changed in Templar. Default is the quick menu. See section 6.1.
2. **Quick settings vs quick menu:** owner said "you choose, and explore more settings". Decision: **fold quick settings into the quick menu** as one section, so there is one menu and one trigger. The Menu key inside Templar opens the same quick menu. The full set of options lives in a proper Settings screen, mapped in section 11.
3. **Provider rows:** owner wants something "totally different" from left-to-right rows, with a choice of views on the TV. See section 10. Inside any view, each provider still shows one "recently played" shelf by default, with the provider's own rows behind "More from …" (a per-shelf option can show them inline).

## 10. Home views the owner can switch between (new)

The owner picks a view in Settings → Home → View, or from the quick menu ("Change view"). All views read the **same** shelves, favourites, row order and hidden rows, so switching never loses an edit. Each view is its own feature flag (Beta until used for a while), and the classic Rows view can't be turned off, so there is always a working home. Views are original designs, not copies of another launcher.

### 10.1 The views

| View | What it looks like | D-pad | Best for |
| --- | --- | --- | --- |
| **Rows** (today) | Horizontal shelves stacked top to bottom | Up/Down = shelf, Left/Right = tile | Familiar; lots of shelves |
| **Columns** | Each shelf is a vertical column of posters, 4 to 5 columns side by side (Continue, Favourites, Peacock, Paramount+, …). Columns scroll on their own | Left/Right = column, Up/Down = tile in column | Seeing every provider at once without scrolling the page |
| **Rail + grid** | A slim list of shelf names on the left; the chosen shelf fills the right as a poster grid (3 rows of 5 to 6) | Left rail Up/Down picks a shelf (the grid updates as you go); Right enters the grid | Fastest jump to any shelf: 1 to 3 presses |
| **Spotlight** | Full-screen artwork of the focused show with title, progress and provider; one compact strip at the bottom | Left/Right = show, Up/Down = switch shelf (the strip slides) | Living-room "big box" feel, one show at a time |
| **Wall** | One big poster grid of everything, with filter chips across the top (All, Continue, Favourites, Peacock, …, Server) | Up from row 1 = chips; chips Left/Right filter; Down = grid | Browsing by filter instead of by shelf |
| **List** | Text-first list, two columns: title, provider, episode, progress bar, small thumbnail | Up/Down = item, Left/Right = jump a shelf | Speed, small text sizes, keyboard users |

### 10.2 Press counts by view (from first focused item)

| Task | Rows | Columns | Rail + grid | Spotlight | Wall | List |
| --- | --- | --- | --- | --- | --- | --- |
| Resume last show | 1 | 1 | 2 (Right, OK) | 1 | 1 | 1 |
| Open 3rd favourite | ~4 | ~4 | ~4 | ~4 | ~4 (chip) | ~3 |
| Reach Max shelf | ~6 | ~6 Right | ~7 Down in rail, or type "M" on a keyboard | ~6 | ~8 (chip) | ~6 |
| Reach Apps | ~4 (top bar) | ~4 | ~3 | ~4 | ~3 (chip) | ~4 |
| Google Home | ~3 (top bar icon) | ~3 | ~3 | ~3 | ~3 | ~3 |

Rail + grid wins on jumping between shelves. Columns wins on seeing everything at once. Spotlight is the most cinematic but the slowest to browse. Recommended default: **Columns** for "totally different" with short paths, with Rows one tap away. [A]

### 10.3 Rules every view must follow

- Same tile menu, same move mode and same focus rules (section 3.4). Move mode follows the view's main axis: in Columns a tile moves Up/Down within its column, and a whole column moves Left/Right in "Move column".
- The top bar (tabs, Google Home icon, clock) stays the same in every view, so Google Home is always in the same place.
- Back from deep in any view returns to its first item; a second Back goes to the top bar.
- Switching views keeps focus on the same show where that show is visible in the new view.
- **Preview before switching:** Settings → Home → View shows a live preview of each view; OK applies, Back keeps the current one. Quick-menu "Change view" cycles views with Left/Right and applies on OK.
- Per profile: each profile can have its own view.
- Performance: Columns and Wall load more posters at once. Load the visible ones at display size first and cap each column at 20 tiles plus "See all". Check smoothness on the Streamer before a view leaves Beta. [V]

### 10.4 Per-view options

- Columns: number of columns (3 to 6), poster or landscape tiles, column width.
- Rail + grid: rail on the left or right, grid density (3×5 or 2×4), show counts next to shelf names.
- Spotlight: background blur and dimming, auto-advance on or off (off by default), show trailer artwork only (no autoplay video in v1).
- Wall: chips to show, sort by recent, A–Z or provider.
- List: show thumbnails on or off, text size.

## 11. Settings map (new)

Layout of the Settings screen for a TV: categories on the left, options on the right, each option with a one-line description. Changes apply live. Every category has "Reset this section". Every reset or clear goes to a restorable list for 30 days rather than deleting outright.

| Category | Settings |
| --- | --- |
| **Home** | View (section 10) and per-view options; Row Manager (order, show or hide, rename); tile size; row dimming level; poster or landscape style; clock and date; spotlight panel on or off; start screen (home, last view, favourites) |
| **Shelves and favourites** | Auto-add from playback on or off; minimum watch time before a show counts (default 2 minutes); one tile per series; shelf length; Recently removed (restore) |
| **Providers** | Enable, disable and order providers; show provider rows inline per provider; measured capabilities per provider; test deep link |
| **Remote and keyboard** | Star button action (6.1) with set-up guide; Menu key action (default: quick menu); hold-OK time for the tile menu (short, normal, long); shortcut editor; global shortcuts over other apps (off); type to search; touchpad hover moves focus (off); focus sounds |
| **Quick menu** | Sections to show and their order; side (left or right); width; open over video (overlay mode, off); show what's playing at the top |
| **Search and voice** | Sources (history, shelves, Jellyfin, provider rows, TMDB); ranking (my subscriptions first, then server, then rent or buy); hide rent or buy results; voice language; send search text to TMDB (follows TMDB toggle) |
| **Google** | Google Home tile (can't be turned off while Templar is HOME); Open Google TV Home; Return to Google TV guide; HOME watchdog (off) |
| **Personal media** | Jellyfin server (entered on the TV, never in git); libraries to show; music on home on or off |
| **Privacy and history** | Watch history on or off; how long to keep history; observers (MediaSession, focused tile, OCR) each on or off; review queue; clear history (restorable for 30 days) |
| **Appearance** | Theme, accent colour, background; text size (Normal, Large, Extra large); reduce motion; focus style (outline, scale, both) |
| **Profiles** | Add, switch, and edit profiles; what each profile shares |
| **Features** | Every feature flag with status (Stable, Beta, Experimental) |
| **Backup and restore** | Export or import layout, shelves, favourites, shortcuts, views and settings to a local file |
| **Diagnostics** | Flag states, provider capabilities, recognizer status, last shortcut matched; never other apps' keys |
| **About and rollback** | Version, checksums, how to make Google TV the home screen again |

Quick menu "Quick settings" section shows a short pick of these: View, Theme, Text size, Focus sounds, and "All settings".

## 12. Still open

Nothing blocks the design. To confirm on the TV with the device probe: Google's star-button picker lists the "Templar Menu" companion; Columns and Wall views scroll smoothly on the Streamer; the translucent quick menu doesn't stop playback in the four providers.

## 13. Rollback

Docs only. Revert the commit or close the PR.
