# Materialy Music — implementation roadmap

> Status: Proposed / in progress  
> Last verified: 2026-09-20

This file is the source of truth for sequencing. The product is music-only, local-first, account-free, and uses YouTube through the embedded Innertube/yt-dlp path as its only online provider. That provider is an unstable external protocol and requires a separate YouTube policy review before public distribution or offline-download release.

| Stage | Scope | Depends on | Done when | Status |
|---|---|---|---|---|
| 1 | Stable IDs, backend coordinator, migrations, playlist ordering | — | no URL/setup path; explicit migrations; deterministic IDs | In progress |
| 2 | Media3-owned queue, restore, queue UI, library callback | 1 | timeline mutations are immediate and queue resumes within 5 s | In progress |
| 3 | Home/Search/Library, persistent mini-player, adaptive layout, tokens | 2 | compact bar and expanded rail; LCE/offline states; 200% font scale | In progress |
| 4 | Details, downloads, lyrics cache, events, Auto/BT browse | 2–3 | entity routes and guaranteed offline albums/playlists | Proposed |
| 5 | measurement, accessibility, Baseline Profile, Macrobenchmark | stable flows | no >5% regression against stored release baseline | Proposed |

## Implemented baseline

- Three primary destinations: Home, Search, Library, with a persistent mini-player.
- Embedded backend auto-start through `BackendCoordinator`; external-server setup is absent from the normal UI.
- Stable `ContentId` and initial catalog/offline/playback contracts.
- Media3 timeline queue snapshot; AutoMix no longer mutates its input during traversal.
- Explicit Room 1→2 migration; playlist removal compacts positions.

## Open acceptance gaps

- Full process-death restoration and MediaLibrary browse/search/resumption.
- Queue sheet with manual Next versus AutoMix sections and accessible reorder alternatives.
- Artist/album/playlist details, pagination and typed federated search.
- Media3 downloads, unified download state and complete offline contexts.
- Local Roboto Flex binary, FTS, Paging, Baseline Profile and Macrobenchmark module.
- Adaptive `1.3.0` integration requires the project toolchain to move from compileSdk 35/AGP 8.7.3 to compileSdk 37/AGP 9.1; the version is pinned but the artifact is deliberately not linked until that upgrade.
- UI/accessibility/Media3 integration coverage and fixed-device performance baseline.

Every stage moves to Done only after its tests and acceptance checks exist; documentation alone never changes implementation status.
