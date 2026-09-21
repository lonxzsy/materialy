# Music feature roadmap

> Status: Proposed / partially implemented. Last verified: 2026-09-20.

Version one is music-only, local-first and account-free, with YouTube as the only online provider. Podcasts, audiobooks, social features and cloud-authoritative handoff are out of scope.

| Capability | Current | Gap |
|---|---|---|
| Playback | Media3 service/controls | persisted context/position, recovery tests |
| Queue | timeline snapshot contract | Queue UI, manual/AutoMix sections, restore |
| Library | tracks/favorites/playlists | albums/artists/recent activity/filters |
| Playlists | create/add/remove | reorder, metadata, duplicates, undo, import/export |
| Search | local + online tracks | typed sections, paging, offline/empty separation |
| Details | playlist | artist/album routes, pagination |
| Downloads | worker/cache paths | Media3 DownloadManager, pause/retry/progress |
| Lyrics | LRC parsing/fetch | Room cache, source, rematch |
| Devices | session shell | library browse, Auto/Bluetooth, then Cast |

Stable `ContentId`, shared entity models and catalog/offline/playback contracts are provider/UI boundaries. UI must not call Innertube, yt-dlp or backend URLs directly.

Record START, COMPLETE, SKIP, LIKE, SAVE and SEARCH_CLICK locally. Streaming cache is never a downloaded track; guaranteed downloads use Media3 DownloadManager/DownloadService. [Media3 downloads](https://developer.android.com/media/media3/exoplayer/downloading-media).

The YouTube provider is protocol-dependent. Public distribution/offline download requires policy review: [YouTube policies](https://developers.google.com/youtube/terms/developer-policies-guide).
