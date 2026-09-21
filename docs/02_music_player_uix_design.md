# Music-first UX specification

> Status: Proposed / partially implemented. Last verified: 2026-09-20.

Primary navigation contains only **Home**, **Search**, **Library**. Settings, Downloads, About/Diagnostics and entity details are nested. MiniPlayer remains visible with an active item and never hides due to scrolling.

Home opens mixes/recent contexts. Search federates local and YouTube into Tracks, Albums, Artists and Playlists. Library uses Playlists, Songs, Albums and Artists; Favorites and Downloaded are filters.

`Home/Search/Library → track action → play context → MiniPlayer → Player → Queue`

`Search result → artist/album/playlist detail → play/add next/add queue/save/download`

`About → Diagnostics → backend state/retry/technical details`

Now Playing keeps artwork/lyrics, identity, favorite, seek, previous/play/next, shuffle/repeat and Queue. Download, timer, equalizer and secondary actions live in overflow.

Every surface renders mutually exclusive loading/content/empty/error/offline. Offline remote content never spins forever; local/downloaded playback remains available. Recoverable playback error preserves queue and offers retry.

- 360 dp: bottom navigation, one pane.
- 600/840 dp: rail and optional Queue/Lyrics supporting pane.
- 1200/1600 dp: list-detail plus supporting pane where useful.

Resize preserves destination, query, selection, scroll and playback. Reorder has button/menu alternatives. Acceptance lives in [the roadmap](00_implementation_roadmap.md). Source: [Adaptive UI guidance](https://developer.android.com/develop/adaptive-apps/guides/adaptive-dos-and-donts).
