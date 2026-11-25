# Templar TV: Canonical Product Intent and Feature Vision

Version: 2026-11-25. This is the owner's plan, updated with the 2026-11-25 asks (big-box shelves, favourite shows, keyboard shortcuts, one quick menu, selectable home views, keeping Google Home; in-app voice was later dropped). Where the new asks conflict with the earlier text, the new asks win. Changed or new text is marked **(2026-11-25)**.

Research and platform limits behind the changes: [2026-11-25-big-box-gap-analysis.md](2026-11-25-big-box-gap-analysis.md). Platform checks: [2026-11-25-plan-verification-notes.md](2026-11-25-plan-verification-notes.md). 10-foot design: [2026-11-25-big-box-design-review.md](2026-11-25-big-box-design-review.md).

## Latest decisions (2026-11-25, afternoon). These win over anything below.

1. **Templar's own voice recognizer is dropped.** Google's voice search stays as it is, on the Assistant button. Typing in Templar search (on-screen keyboard or hardware keyboard) stays. The voice spike and voice-fallback setting are cancelled. Reason: no source confirms the remote's mic reaches a third-party app, and the owner chose to drop features that need more hands-on testing.
2. **Home and star button are pending the spike in PR #8.** On the Streamer, holding the HOME role does not win the Home key: Google's launcher still opens (device test, PR #6). PR #8 adds two flags, both off by default: `home_return` (when Google's home window appears, Templar comes back on top; the Home key is never consumed, so long-press Home and the Google Home panel are untouched) and `star_key_capture` (consumes the star key and runs Templar's star setting). If the spike fails, both are dropped: Templar stays a normally launched app, and the no-code fallback for the star button is to pick Templar in Google's own button picker.
3. **Selectable home views.** The owner picks how home looks in Settings → Home → View: Rows, Columns, Rail + grid, Spotlight, Wall, or List. All views share the same shelves, favourites, and order, so switching never loses edits. Rows can't be turned off. Details: design review section 10.
4. **One quick menu.** Today's quick-settings panel folds into the quick menu as one section. One menu, one trigger; the Menu key inside Templar opens it too. Its footer always holds Google Home, Google TV Home, and Templar Home. The full option list lives in Settings (design review section 11).

## 1. Product purpose

Templar TV is a custom home screen and media-intelligence platform for the Google TV Streamer. It replaces Google TV's cluttered, commercially prioritized organization with a practical daily-use launcher centered on:

- Active streaming subscriptions
- Personal movies, shows, videos, music, playlists, and favorites
- Durable cross-provider watch history
- Better Continue Watching and Recently Viewed
- **(2026-11-25)** A "big box" home like the commercial launchers: recently played shows per provider that the user can edit and add to, and a separate favourite-shows list the user can rearrange
- User-controlled recommendations and row ordering
- Upcoming, newly released, and leaving-soon content
- Reliable launching into installed streaming apps
- **(2026-11-25)** Search inside Templar across the user's subscriptions, by typing on screen or on a hardware keyboard (voice stays with Google; see Latest decisions)
- **(2026-11-25)** Input beyond the Google remote: a handheld keyboard with touchpad, custom shortcuts, and a pop-up quick menu over any app
- Google Gemini voice and visual capabilities where Google remains useful

It is a functional television operating layer, not merely a visual skin or debloating script. **(2026-11-25)** More features are welcome as long as each one can be turned on or off by the user and works seamlessly.

## 2. Google TV and Gemini coexistence

Templar TV must coexist with Google's stock launcher, which remains installed and selectable. Google's interface is not trusted to consistently prioritize subscribed providers, but Gemini remains useful for:

- General questions and knowledge
- Sports scores, briefs, and recaps
- Weather and topical or narrated deep dives
- Smart-home controls
- Google-native visual responses
- Voice Remote functionality

Responsibilities:

- Templar TV: home screen, streaming organization, personal media, provider preferences, viewing history, discovery, recommendations, media-specific actions, **(2026-11-25)** subscription search, shortcuts, selectable home views, and the quick menu
- Google Gemini: general assistance, sports, weather, topical briefs, visual AI answers, and Google Home functions. **(2026-11-25)** Gemini keeps the remote's Assistant button. Templar never intercepts it.
- Google TV Home (stock launcher): secondary interface for Google-native visual presentation
- **(2026-11-25)** Google Home panel (the smart-home panel in the Google TV dashboard): stays one step away at all times

**(2026-11-25) Hard rule: the Google Home panel must stay reachable while Templar TV is the HOME app.**
- Templar never consumes long-press Home, which opens the system dashboard and its Google Home panel.
- Templar shows a Google Home tile in quick settings and in the quick menu. It opens the panel directly where the device allows, otherwise it tells the user to long-press Home.
- Capturing the system Settings/dashboard key is a flag, default off.
- Templar also visibly exposes "Open Google TV Home" and a "Return to Google TV" guide.

## 3. Reversible installation

Templar TV and Google TV Home must be installed concurrently. The device must support:

- Templar TV as the normal HOME launcher
- Google TV Home as a secondary launcher
- Reassigning HOME to either launcher
- A visible return-to-Google action
- Documented rollback, **(2026-11-25)** with commands verified on the device (the launcher package on Google TV is expected to be `com.google.android.apps.tv.launcherx`, and HOME is assigned with `cmd role add-role-holder android.app.role.HOME`; both to be confirmed)
- Removing Templar TV without damaging Google TV

The project must not require root access, bootloader changes, factory reset, DRM modification, removal of Google system packages, or permanent uninstallation of system applications. Experimental functionality must be independently controlled through feature flags.

## 4. Streaming-provider architecture

Initial provider priority:

1. Peacock
2. Paramount+
3. Prime Video
4. Max

Potential later additions:

5. Netflix
6. Hulu

Only active providers should initially be emphasized. Provider rows must support enabling, disabling, hiding, filtering, and reordering.

Each provider is a module with declared capabilities:

- App launching
- Catalog and genre browsing
- Published Android TV rows
- Continue Watching information
- Title and episode deep links
- **(2026-11-25)** Search intent (open the provider's own search with a query)
- MediaSession playback state and metadata
- Focused-tile information
- External catalog matching
- Local session tracking

Providers must degrade gracefully when information is unavailable. Templar TV must never imply that unsupported data is known. **(2026-11-25)** Each provider's real coverage (rows, MediaSession fields, deep links) is measured on the device and recorded in its module before the UI relies on it.

## 5. Personal media and Jellyfin

Personal server media must appear as a native part of the launcher rather than an isolated application. Jellyfin is the media-server layer for movies, television, personal videos, music, playlists, and favorites.

Jellyfin provides authoritative data for libraries, Continue Watching and Next Up, Recently Added, favorites and playlists, music and personal-video collections, playback position and resume state, episode details and artwork, and server availability.

Jellyfin addresses, credentials, tokens, and private library details must never be stored in Git. They are entered on the TV and stored there.

## 6. Durable watch-history database

A local database covers personal and commercial media:

- User or local profile
- Provider and application package
- Movie, series, season, episode, and episode title
- Start and stop times, duration, playback position, and runtime
- Completion percentage
- Watched, abandoned, resumed, and rewatched states
- Provider content IDs
- TMDB, IMDb, TVDB, and Jellyfin IDs
- Artwork and metadata references
- Candidate matches, confidence scores, and evidence sources
- Manual correction
- Synchronization state
- Created and updated timestamps

**(2026-11-25)** Alongside history, the database holds:
- **Shelf entries**: the per-provider "recently played" items. Source is provider row, MediaSession, Templar launch, or manual add. The user can add, remove, hide, pin, mark watched, change provider, and reorder.
- **Favourite shows**: a separate ordered list, independent of favourite apps and of history.
- **Row layout**: order, visibility, and options for every row, per profile.
- **Shortcuts**: key chord → action bindings, per profile.

Uncertain observations stay unresolved or keep candidate matches instead of being forced into false history. Private viewing data remains local by default.

## 7. Title-identification and confidence system

Preferred evidence order:

1. Exact Jellyfin playback events
2. Active Android MediaSession metadata
3. Provider-published Android TV rows
4. **(2026-11-25)** Templar's own launch of a known title
5. Focused-tile labels before selection
6. Visible playback controls or title overlays
7. Consent-based occasional OCR
8. Provider, duration, catalog, and viewing-pattern matching
9. Manual confirmation or correction

The confidence engine combines signals rather than trusting one OCR result or provider session.

Strong evidence: exact MediaSession title plus provider; focused title plus matching provider and playback start; Jellyfin event with an item ID; **(2026-11-25)** a Templar launch of a known title followed by a matching provider session; OCR title supported by matching artwork and current availability.

Weaker evidence: provider activity duration; partial title text; runtime similarity; a recent title available on that provider.

Low-confidence sessions enter a review queue rather than become verified history. **(2026-11-25)** A manual shelf add is the user's own statement and counts as confirmed.

## 8. Privacy and screen observation

Templar TV must not continuously record the television screen. Screen observation must be explicitly enabled, independently switchable, used only when stronger metadata is unavailable, limited to occasional frames or visible interface text, stored only when necessary, cleared under a documented retention policy, and never used to bypass protected media surfaces.

Templar TV must not circumvent DRM, capture protected video, extract credentials, automate account logins, intercept provider tokens, or bypass access controls.

**(2026-11-25)** Key handling rules:
- The accessibility key filter never logs keys from other apps in release builds (today it logs every key code, which a hardware keyboard would turn into a log of typed text).
- Global shortcuts act only on modifier chords or unused keys and pass every other key through.
- Search terms sent to an external catalog (TMDB) are covered by that source's own toggle.

OCR and focused-tile observation remain experimental until validated on the physical device.

## 9. Home-screen experience

**(2026-11-25)** Default row order (every row can be moved, hidden, or removed, and new rows can be added):

1. Continue Watching (merged across providers and Jellyfin)
2. Favourite Shows (the separate, user-ordered list)
3. Recently Added from the personal server
4. Peacock: recently played, then the provider's own rows
5. Paramount+: recently played, then the provider's own rows
6. Prime Video: recently played, then the provider's own rows
7. Personal Shows and Movies
8. Personal Music
9. Max: recently played, then the provider's own rows
10. Netflix and Hulu when enabled
11. New and Upcoming
12. Leaving Soon
13. Low-Confidence Review when needed
14. Apps (favourite and recent apps, as in Vista)

**(2026-11-25) Edit mode** (long-press OK on a tile or row title, or a keyboard shortcut):
- On a tile: Open, Add to favourites, Move, Remove from this shelf, Hide, Change provider, Mark watched, Show evidence.
- On a row: Move row, Hide row, Rename, Tile size, Show only this provider.
- "Add a show" tile at the end of each provider shelf and of Favourites: search by typing (on screen or keyboard), pick the show and provider.
- A Row Manager screen in Settings lists all rows with toggles and order.

Rows also support provider filtering, profile assignment, artwork and tile-size options, personalized recommendations, hiding unavailable services, and experimental-feature status indicators.

The interface must remain readable across a room, predictable with a D-pad, and responsive on Google TV Streamer hardware.

## 10. Viewing-history experiences

Templar TV creates its own views because Google's Continue Watching coverage is not dependable, **(2026-11-25)** and because many providers now report Continue Watching only to Google, where a third-party launcher cannot read it.

Desired views: Continue Watching, Recently Viewed, **(2026-11-25)** Recently Played per provider, Recently Completed, Recently Abandoned, Next Episode, New Episodes of Watched Shows, Rewatch, Favourites, Unfinished Movies, Personal Server Continue Watching.

The interface distinguishes exact provider-reported progress, Templar-estimated progress, unknown position, low-confidence title matches, **(2026-11-25)** manually added items, personal server content, and commercial streaming content.

## 11. Discovery and recommendations

Desired sections: Coming Soon, New This Week, Recently Released, New Episodes, Leaving Soon, Because You Watched, Hidden Gems, From Your Server, Watchlist Updates, Provider-Specific Premieres.

The system must not use one giant, equally weighted database for every match. It should maintain a broad provider and release catalog, build a smaller personalized candidate index, prioritize subscribed providers and watched series, match observations against likely candidates, refresh or archive stale records, and retain external IDs to prevent duplicates.

Personal relevance considers watch history, **(2026-11-25)** favourite shows and shelf edits, watchlists, searches, frequently previewed titles, new episodes of watched shows, provider availability, recent and upcoming releases, related series, and Jellyfin content.

## 12. Unified search and deep linking

Search spans installed providers, Jellyfin libraries, watch history, **(2026-11-25)** favourite shows and shelves, upcoming releases, watchlists, and external metadata catalogs.

**(2026-11-25)** Search accepts the on-screen keyboard and a hardware keyboard. Voice search stays with Google (section 13).

Where supported, results open the correct provider, title, season, episode, Jellyfin item, or resume position. If exact deep linking is unavailable, Templar TV opens the provider (and the provider's search with the title, where the app supports it) and keeps the title on screen. The result shows which kind of link it will use.

Provider preferences affect ranking. Content included with an active subscription is favored over rental, purchase, or alternate-provider options.

## 13. Voice behavior

**(2026-11-25, latest) Templar's own voice recognizer is dropped.** Google's voice search and Gemini stay on the Assistant button, exactly as they are. Templar does not intercept or replace them.

- Templar search is typed: on-screen keyboard, or a hardware keyboard (section 17.1).
- Routing: general knowledge, sports, weather, visual briefs, smart home, and voice search → Google (Assistant button). Personal media, watch history, favourites, and subscription search → Templar (typed search, shelves, quick menu).
- Revisit only if a later platform change confirms the remote's mic reaches a third-party app. It would come back behind its own flag, default off.

## 14. Profiles

The architecture supports multiple local profiles even if the first release is single-user.

Profile-level settings may include active providers, row order, favourites, watch and Continue Watching history, recommendation preferences, hidden genres or content, Jellyfin profile, playback settings, data retention, **(2026-11-25)** shelves, shortcuts, quick-menu contents, and feature toggles.

Profiles must never silently mix viewing histories.

## 15. Visual customization

Customization includes themes, colors, and backgrounds; tile sizes and row styles; dock size and order; column count; hero artwork; clock and date; system status; provider visibility; personal-media placement; **(2026-11-25)** selectable home views (Rows, Columns, Rail + grid, Spotlight, Wall, List; design review section 10) with large poster shelves per provider; drag-style move mode for tiles and rows; and quick-menu layout.

The project must preserve Vista's working Compose for TV focus and navigation behavior. Customization must not reduce reliability.

## 16. Diagnostics and review

History records explain the selected title, provider, confidence, evidence sources, alternative candidates, playback duration, matching catalog record, and manual-correction status.

The user can confirm a match, choose another candidate, enter a title manually, mark a session irrelevant, merge duplicates, correct season or episode, delete private history locally, and disable an observer.

**(2026-11-25)** Diagnostics also show every feature flag and its state, each provider's measured capabilities, when the Home return last fired, and which key the last shortcut matched. Diagnostics never expose credentials or other apps' keystrokes.

## 17. Input devices, shortcuts, and quick menu (2026-11-25, new)

### 17.1 Keyboard and touchpad
- Support USB and Bluetooth HID keyboards and touchpads, including handheld 92-key models with a touchpad. **(owner, 2026-11-25)** The owner's keyboard may have both Bluetooth and a USB dongle, so both paths are supported. Bluetooth is the default recommendation because the Streamer's only USB-C port is mainly for power; the device probe checks whether it carries data through a hub.
- Typing goes straight into Templar search. Arrow keys, Enter, and Esc behave like D-pad, Select, and Back. The touchpad pointer can click tiles and scroll rows.
- **Shortcut editor** (Settings → Shortcuts): record a key or chord, then pick an action: open app, open a shelf, open favourites, Google voice search, quick menu, Google Home panel, Google TV Home, play/pause, back to Templar, toggle a feature.
- Inside Templar any key can be bound. Over other apps, only modifier chords or unused keys (for example F1–F12) act; everything else passes through untouched.

### 17.2 Quick menu
- A pop-up menu that opens over any app, scrolls with the D-pad, and launches without returning to the home screen. Back closes it and returns to the app.
- **(latest)** This is the only menu: today's quick-settings panel folds into it as the Quick settings section, and the Menu key inside Templar opens it.
- Default sections (each can be hidden or reordered): Resume, Continue Watching, Favourite Shows, pinned apps, Quick settings, Change view. Pinned footer that can't be hidden: Google Home, Google TV Home, Templar Home.
- Triggers: a keyboard shortcut, the Menu key inside Templar, the companion API's open-menu command, and optionally the Settings key captured by the accessibility service (`settings_key_capture`, off). **Dropped 2026-11-25 (combined build):** the remote's star button is not a trigger (see below). Long-press Home and long-press star are never used.
- **Dropped 2026-11-25 (combined build):** star button capture and the Star button setting. On the Streamer `KEYCODE_MACRO_1` (313) is a global key owned by launcherx (`dumpsys window policy`), so no accessibility service receives it (device test, `docs/handoffs/2026-11-25-home-return-and-star-key.md`). The `star_key_capture` flag, the Star button action setting, the `star_action` profile setting and the service code are removed. The "Templar Menu" companion app is not planned. The owner can still point the star at Templar in Google's own button picker (no Templar code needed).
- **(combined build, 2026-11-25)** The quick menu now has the sections Resume, Continue watching, Favourite shows, Apps, Change view, Rows (Row Manager), Quick settings and Templar, each hideable and movable and each behind its feature flag. The pinned footer is unchanged.
- v1 opens as a translucent screen. Keeping a video playing underneath with a system overlay is a later, separate flag.

## 18. Feature flags and user control (2026-11-25, new)

Every feature is user-toggleable. Flags move from compile-time constants to a runtime store (DataStore), shown in Settings → Features with a short description and status (Stable, Beta, Experimental). Defaults below; any of them can be changed by the user.

| Flag | Default |
| --- | --- |
| watchHistory | on |
| mediaSessionObservation | on |
| providerRows | on |
| providerRecentShelves | on |
| shelfEditing | on |
| favouriteShows | on |
| rowManager | on |
| tmdbCatalog | on once a key is entered (owner approved TMDB, 2026-11-25) |
| ~~starButtonAction~~ | dropped 2026-11-25 (star key cannot be captured on the Streamer) |
| unifiedSearch | on |
| quickMenu (includes quick settings) | on |
| homeViews (each view is Beta; Rows always on) | Columns (falls back to Rows when its flag is off) |
| home_return (PR #8 spike) | off |
| ~~star_key_capture~~ | dropped 2026-11-25, code removed |
| quickMenuOverlayMode | off |
| keyboardShortcuts | on |
| globalShortcuts (over other apps) | off |
| captureDashboardKey | off |
| googleHomeHandoff | on (cannot be turned off while Templar is HOME) |
| jellyfin | off until configured |
| previewAndUpcoming | on |
| lowConfidenceReview | off |
| focusedTileObservation | off |
| ocrObservation | off |
| ~~customVoiceRouting~~ | dropped 2026-11-25, flag removed with Templar voice |
| layoutBackup | on |
| homeWatchdog | off |
| diagnostics | on |

## 19. Technical foundation

Templar TV is based on the Vista Android TV launcher and uses Kotlin, Jetpack Compose for TV, Android TV HOME intents, Android TV provider rows, deep links, local launch history, Room persistence, KSP, runtime feature flags, provider capability contracts, Jellyfin integration boundaries, observation contracts, the Google Home handoff, **(2026-11-25)** the accessibility window watcher and key filter (both flag-gated), and layout backup and restore (export and import of rows, shelves, favourites, shortcuts, and flags to a local file).

It retains Vista's useful behavior while replacing its identity and extending its architecture.

## 20. Validation model

The real product is validated through normal use rather than a separate disposable project:

- Templar TV installs beside Google TV Home.
- Experimental modules use feature flags.
- Real use supplies validation.
- Failures are logged locally.
- Individual features can be disabled.
- HOME can quickly return to Google.
- The stock launcher is never removed.

Before physical installation, the project requires successful compilation, passing available tests, a release APK, verified application ID and label, manifest inspection, the APK SHA-256, documented permissions, and a verified rollback plan. **(2026-11-25)** Each build that touches keys or the quick menu also checks that long-press Home still opens the dashboard with the Google Home panel, and that the Assistant button still opens Gemini.

## 21. Explicit non-goals

Templar TV will not:

- Pirate or redistribute content
- Bypass subscriptions, DRM, or access controls
- Extract credentials or provider tokens
- Remove Google services completely
- Root the Google TV Streamer
- Become an uncontrolled background recorder or keylogger
- Depend on one provider exposing private APIs
- Present guesses as verified history
- Permanently lock the user into the custom launcher
- **(2026-11-25)** Take over the Assistant button or the long-press Home dashboard
- **(2026-11-25)** Copy another launcher's branding, art, or proprietary assets

## 22. Final product vision

Templar TV is a personal television operating layer where commercial providers, personal media, viewing history, discovery, recommendations, and voice assistance work together without letting Google or any single provider control the whole experience.

The final product provides:

- A home screen based on actual subscriptions
- **(2026-11-25)** Big-box shelves of recently played shows per provider, editable, with add
- **(2026-11-25)** A separate favourite-shows list in the user's own order
- **(2026-11-25)** Typed and keyboard search inside Templar across subscriptions, with Google voice search kept as it is
- **(2026-11-25)** Home views the owner can switch between
- **(2026-11-25)** Keyboard shortcuts and a quick menu over any app
- Native access to personal server media
- Durable cross-provider watch history
- Better Continue Watching
- Better upcoming and newly released discovery
- Clear provider preferences
- Honest confidence and review of uncertainty
- Gemini where Google's AI is useful, and the Google Home panel always one step away
- Simple rollback when Templar TV is not wanted
- User control over layout, privacy, profiles, and every feature
