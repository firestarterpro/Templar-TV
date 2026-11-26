# Making the stock Google TV launcher behave like Templar TV

Dated 2026-11-26. Research only, nothing was changed on the Streamer.
Evidence labels: **[doc]** from Google/Jellyfin documentation or search results, **[known]** from my own knowledge of Android TV, **[mem]** from earlier verified device findings in this project, **[guess]** my estimate. Nothing here is verified on the TV yet.

## Goal and ceiling

Goal: when Templar TV is not the active launcher (or a family member's TV only has the stock launcher), your music, movies and recently played should still be one or two clicks away from the Google TV home screen.

**Honest ceiling: about 55-65%.** The stock launcher is Google's code. It has no plugin API for custom sections, custom tab names ("My Movies", "Music") or custom app layout. What it does accept from apps is a small set of Google-defined surfaces: the apps row, the Watch Next row, and Google's Engage/recommendation data. So the stock launcher can get you *entry points and a Continue Watching row*, but never a "Templar-style" screen. Templar TV stays the place for that.

## Rules kept
No root, no system package removal, nothing disabled. Every option below is undone in the settings screen or with one `adb` line listed under it.

## Options, ranked by benefit toward the goal

| # | Option | Est. % toward goal | Effort | Reversible |
|---|---|---|---|---|
| 1 | Jellyfin app + Tailscale on every TV/phone, apps pinned first on the stock home | 30% | Low | Yes |
| 2 | Watch Next / Continue Watching row fed by Jellyfin (and by Templar's companion) | 15-20% | Low to medium | Yes |
| 3 | Templar TV as HOME, with Google Home reachable (the current design) | 10-15% | Done | Yes |
| 4 | Apps-only mode on the stock launcher | 5% | Trivial | Yes |
| 5 | Companion "Open Templar" shortcut app visible in the stock Apps row | 5% | Low | Yes |
| 6 | adb-only tweaks (launcher package settings, wallpaper-free home) | under 3% | Low | Yes |
| - | Custom tab names, custom sections, stock "For you" replacement | 0% | Not possible without replacing the launcher | n/a |

### 1. Jellyfin + Tailscale + pinned apps (best single step)
- Jellyfin's Android TV app installs from Play Store on any Google TV and handles Music and Movies natively. [doc: jellyfin.org androidtv posts]
- Family sharing route is owned by the "Share music and videos with family" thread: **Tailscale node sharing, no public exposure**. This doc does not recommend a different route. Public options (Tailscale Funnel, Cloudflare Tunnel) are only an alternative if family cannot install Tailscale, and they expose the server publicly.
- On the stock home: Google TV lets you reorder the "Your apps" row and pick favourite apps (long-press an app, then Move). [doc: support.google.com/chromecast/answer/12390692] Put Jellyfin first. That is the closest to "My Movies / Music" you can get without a custom launcher.
- Create separate Jellyfin libraries named exactly "My Movies" and "Music" so the names match your Templar tabs everywhere.
- Rollback: unpin or uninstall the app. Nothing system-level changes.

### 2. Watch Next / Continue Watching
- Google TV's only open "feed in my content" door for ordinary apps is the **Watch Next** row (a content provider that apps write to). [doc: developer.android.com blog on Google TV app discovery] Custom preview channels, which old Android TV launchers showed, are not shown on Google TV home. [known]
- Jellyfin's Android TV app can publish Continue Watching entries to Watch Next in recent versions. [known, needs a check of the app's settings; I did not confirm the exact release]
- Caution: Google's May 2026 post says the legacy Watch Next path is being moved toward Engage SDK / Google's own data feeds. My earlier verification notes recorded loss of support in 2H 2027 and that an adb `content query` over-reports what a normal app can read. [mem] So this gets a Templar companion only if Jellyfin's own publishing proves insufficient. Do not plan on it lasting forever.
- Rollback: Settings, Apps, the app, Clear data, or the row's "Remove from Continue Watching".

### 3. Templar TV as HOME (current build)
Already built: Templar is a HOME candidate; short Home returns to Templar, long-press Home and the star button still reach Google Home. [mem] This is the only route to real custom sections, so it carries most of the "feels like Templar" value. Family TVs would install the same APK if you want it, no Google account change needed.
Rollback: Settings, Apps, Default apps, Home app, pick Google TV (path varies by version). [guess on exact menu name]

### 4. Apps-only mode
Google TV has an Apps-only mode that strips recommendations and leaves a plain app grid. [doc: support.google.com/googletv, howtogeek disable-recommendations article] It removes clutter, so your pinned Jellyfin is more visible, but it cannot add anything. Use it on family TVs where you do not install Templar. Rollback: toggle it off in the same setting.

### 5. Companion shortcut
A tiny launcher-visible activity ("Templar Music", "Templar Movies") that deep-links into the Jellyfin library or Templar sections. Shows as an app tile in the stock Apps row, so it can be pinned first. Cheap, and works even if the user keeps the stock launcher. Rollback: uninstall.

### 6. adb-only tweaks
`adb shell pm` and `settings` can change the default HOME role holder and clear Watch Next data, but nothing that rearranges stock sections. Not worth a plan beyond what option 3 already documents. All adb lines need `-s 192.168.0.166:5555` because the tablet is also attached. [mem]

## Family sharing (affects the end goal)
- Phones and TVs: Jellyfin apps exist for Android TV/Google TV, Android, iOS, and web. One account per person keeps each watch history separate.
- Security matters more than any launcher choice once other people connect: strong passwords, no shared admin account, no port-forward of raw Jellyfin. Tailscale node sharing is the chosen route.
- Music rights are yours to judge; sharing with family is within normal personal use, public hosting is not the goal.

## What I think the end goal is
Anyone in your family opens one app on any TV or phone and plays your music and movies, and on your own TV Templar TV gives the custom sections. The stock launcher only needs Jellyfin pinned first plus, if possible, a Continue Watching row.

## Recommended order (no TV test loops)
1. Sharing route: Tailscale node sharing (decided in the family-sharing thread).
2. Install Jellyfin on the Streamer and pin it first in the Apps row (hands-on, about 2 minutes).
3. Check Jellyfin's Android TV settings for the Watch Next/Continue Watching option and turn it on.
4. Keep Templar TV as HOME on your Streamer only.
5. Revisit a Templar companion only if step 3 does not give a usable Continue Watching row.

## Open facts to verify on the TV (read-only)
- Exact Jellyfin build and whether it exposes Watch Next on the Streamer.
- Exact menu names for Apps-only mode and Home app on this Streamer's Android 14 build.
