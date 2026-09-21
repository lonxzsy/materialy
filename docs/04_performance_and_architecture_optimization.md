# Performance and quality process

> Status: Proposed. Last verified: 2026-09-20.

Use one loop: **record release baseline → make one scoped change → run Macrobenchmark on the fixed device → compare and keep/revert**. Unmeasured percentage claims are not accepted.

Measure cold start, Home scroll, Search results, Player open and Queue reorder. Store median/p95 startup, slow/frozen-frame ratio and frame distributions with device/build metadata. Reject changes regressing cold start or slow-frame ratio by more than 5%.

Candidates after profiling: lifecycle-aware collection; isolate the position ticker from NavGraph; stable Lazy keys/content types; move sorting out of composables; stop off-screen visualizers; size Coil requests; Room FTS/Paging; release R8; app-specific Baseline Profile.

Tests: unit IDs/queue/AutoMix/migrations/positions/LRC; Compose LCE/offline/restoration/200%-font/semantics; Media3 timeline/retry/restoration/browse. Macrobenchmark runs against release, never debug.

CI sets writable `GRADLE_USER_HOME` and `ANDROID_USER_HOME`. `NO-SOURCE` is not coverage; Python tests require pytest and an explicit run.

Source: [Compose performance](https://developer.android.com/develop/ui/compose/performance).
