# Отчёт о реализации и сборке Materialy Music

> Дата проверки: 2026-09-20  
> Статус: первый инфраструктурный и playback-срез реализован; полный product roadmap ещё не завершён.

## 1. Итог сборки

Debug APK успешно собран командой:

```powershell
$env:GRADLE_USER_HOME='C:\code\materialy\.gradle'
$env:ANDROID_USER_HOME='C:\code\materialy\.android'
.\gradlew.bat --no-daemon assembleDebug
```

Результат:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Размер: 25 500 489 байт, приблизительно 24,32 MiB.
- SHA-256: `7196C3A59A88FEEA00A66D12D1112C0719AA545FEF08506CD8B2FF78DD9C2CE9`.
- Gradle: `BUILD SUCCESSFUL`, 41 задача, 24 выполнены, 17 взяты из cache/up-to-date.

Unit-тесты запущены командой:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest
```

Результат: `BUILD SUCCESSFUL`, 5 тестов, 0 failures, 0 errors, 0 skipped:

- `AutoMixTest`: 2 теста;
- `ContentIdTest`: 3 теста.

Instrumentation-тест миграции добавлен, но не запускался: для него необходим Android-эмулятор или устройство. Python-тесты backend не запускались, потому что в текущем Python отсутствует пакет `pytest`.

### Обход ограничения системного Android SDK

Изначально `javac` завершался с `AccessDeniedException` при чтении системного `android-35/android.jar`. Для воспроизводимой сборки были скопированы только необходимые SDK-компоненты в игнорируемую папку `.android-sdk`, а `local.properties` направлен на неё. Папка добавлена в `.gitignore` и не должна попадать в репозиторий.

После изменения потребовалось остановить старый Gradle daemon: он продолжал держать закэшированный путь к системному SDK. Чистый single-use daemon успешно использовал локальную SDK-копию.

## 2. Что исследовалось в проекте

Перед изменениями были просмотрены структура проекта, Gradle catalog, Room schema, playback service/manager, backend service, repositories, Settings, Downloads, navigation и документы `docs/01–04`.

Для поиска использовались запросы `rg` по следующим признакам:

- `fallbackToDestructiveMigration`;
- `hashCode()` и отрицательные ID;
- `192.168.*`, `10.0.2.2`, server URL и standalone mode;
- `activeQueue` и Media3 timeline;
- `PlaylistSongCrossRef.position`;
- `collectAsState()` в корневой навигации;
- `Production Ready`, Google Sans и Open Sans;
- существующие Queue, MediaLibrary, download и detail routes.

Это выявило реальные проблемы:

1. UI и Media3 поддерживали две разные очереди.
2. Онлайн-треки получали отрицательные или `hashCode()`-идентификаторы.
3. Worker и основной DI использовали destructive migration.
4. AutoMix добавлял элементы в список во время его обхода.
5. Playlist relation не гарантировал сортировку по `position`.
6. Settings и Downloads показывали порт, LAN URL и standalone/PC mode.
7. Мини-плеер скрывался при скролле.
8. Playback position обновлял корневой `NavGraph` каждые 300 мс.
9. `PlaybackService.LibraryCallback` оставался пустым.
10. В документации неподтверждённые предложения были помечены как production-ready.

## 3. Реализованные изменения

### 3.1. Стабильная идентичность контента

Добавлен `ContentId(provider, type, nativeId)` с кодированием/декодированием и независимостью от Room row ID. Добавлены `ContentProvider`, `ContentType` и временный namespaced `Long`-адаптер для старых экранов, которые пока требуют `SongEntity.songId: Long`.

Из production-кода удалены отрицательные online-ID и `String.hashCode()` для результатов Home/Search. Временные Long ID создаются через SHA-256 в выделенном положительном диапазоне. Источником истинной идентичности остаётся `ContentId`.

Файлы:

- `domain/model/ContentId.kt`;
- `domain/model/CatalogModels.kt`;
- `domain/repository/CatalogContracts.kt`;
- `OnlineSongEntity.kt`, `HomeViewModel.kt`, `SearchViewModel.kt`, `MusicRepository.kt`.

Ограничение: Long-адаптер остаётся переходным слоем. Конечное решение — не передавать Room entity в UI и playback, а использовать только доменный `Track`.

### 3.2. Zero-configuration backend

Добавлен singleton `BackendCoordinator` со состояниями `Starting`, `Ready`, `Degraded`, `Unavailable`, повторным запуском и exponential backoff. `MusicApp` запускает coordinator автоматически. `DownloadRepository` всегда выдаёт адрес встроенного loopback backend и больше не требует выбора PC/standalone режима.

Foreground notification больше не показывает внутренний порт и термин «backend». Добавлен вложенный Diagnostics screen с состоянием и Retry.

Технические карточки Settings и Downloads скрыты из обычного пути. Hardcoded LAN-адреса и LAN cleartext domains удалены.

Ограничения:

- health сейчас проверяет факт запуска `LocalHttpServer`, а не полный HTTP `/health` с end-to-end проверкой extractor;
- старые server-setting composables физически остаются в исходниках за недостижимой веткой и должны быть удалены после очистки старых ViewModel API;
- backend provider всё ещё напрямую связан с текущим Innertube implementation; полноценная реализация `YouTubeCatalogRepository` остаётся следующей задачей.

### 3.3. Room migrations и playlist ordering

Удалены оба вызова `fallbackToDestructiveMigration()`. Добавлена явная миграция 1→2, создающая `online_songs` и три индекса в точном соответствии с экспортированной schema 2.

Включён `exportSchema`, создан `2.json` и добавлен `MigrationTestHelper` instrumentation test. После удаления трека playlist positions уплотняются в транзакции. Для выбранного playlist добавлен явный запрос с `ORDER BY position, addedAt, songId`.

Ограничения:

- migration test ещё нужно выполнить на эмуляторе;
- reorder playlist и конкурентные вставки требуют отдельных транзакционных тестов;
- список всех playlists всё ещё использует Room relation и должен быть переведён на гарантированно упорядоченную проекцию.

### 3.4. AutoMix

Исправлено изменение `mutable` во время `for`-обхода. Новый алгоритм работает с отдельным `remaining`, удаляет выбранный элемент и поддерживает окно недавних исполнителей. Добавлены тесты сохранения всех элементов и anti-repeat поведения.

Ограничение: используемая сортировка всё ещё включает randomness; для полностью воспроизводимых тестов следует внедрить `Random` как зависимость/seed.

### 3.5. Playback contract и очередь

Добавлены:

- `PlaybackCoordinator`;
- `PlaybackState`;
- `PlaybackContext`;
- `QueueItem`;
- `QueueSnapshot`.

`PlayerManager` реализует contract. Источником порядка теперь является Media3 timeline; отдельный `activeQueue` удалён. Из timeline публикуются queue items, current index, position и revision. Реализованы move, remove, play-next, add-to-queue, clear и retry.

Каждый экземпляр трека в очереди имеет отдельный UUID queue item ID, поэтому дубликаты одного ContentId могут существовать независимо.

Очередь, media URI, metadata, current index и position сохраняются в `SharedPreferences`. Во время подключения `MediaController` пустой timeline восстанавливается и seek выполняется к сохранённой позиции.

Добавлен Queue screen с:

- текущим элементом;
- кнопками перемещения вверх/вниз как доступной альтернативой drag;
- remove;
- clear;
- стабильными Lazy keys и `contentType`;
- touch targets 48 dp и content descriptions.

Ограничения:

- очередь пока не разделена на `Now Playing`, ручной `Next` и AutoMix;
- `playNext(ContentId)` и `addToQueue(ContentId)` умеют клонировать уже известный timeline item, но ещё не вызывают catalog resolver для произвольного ID;
- сохранение position реализовано, но допуск ≤5 секунд не проверен process-death integration test;
- repeat/shuffle/context metadata пока сохраняются не полностью;
- настоящий Media3 `onPlaybackResumption()` ещё не реализован.

### 3.6. Navigation и mini-player

Сохранены три primary destination: Home, Search, Library. Queue и Diagnostics добавлены как вложенные routes. MiniPlayer больше не скрывается из-за nested scroll.

Playback flows собираются через `collectAsStateWithLifecycle`. Частый position/bass state вынесен в отдельный `PlayerDock`, поэтому он больше не заставляет пересобирать весь `NavHost`.

Ограничения: responsive rail и multi-pane layout ещё отсутствуют.

### 3.7. Material 3 и build configuration

- Material 3 обновлён до стабильной `1.4.0`.
- Версия Material 3 Adaptive `1.3.0` закреплена в version catalog, но artifact не подключён.
- Release R8 включён.
- `GoogleSansFont = GoogleFont("Open Sans")` исправлен на корректное имя Open Sans.
- Kotlin compiler переключён на in-process execution, чтобы не писать daemon markers в закрытую пользовательскую директорию.

Adaptive `1.3.0` в текущем dependency metadata требует compileSdk 37 и AGP 9.1, а проект использует compileSdk 35 и AGP 8.7.3. Поэтому подключение библиотеки сейчас намеренно отложено: иначе проект не собирается.

Roboto Flex всё ещё загружается через Fonts Provider. Локальный лицензированный font binary в проект не добавлялся.

### 3.8. Документация

Добавлен `00_implementation_roadmap.md`. Документы 01–04 переписаны и разделяют:

- фактически реализованное;
- целевое решение;
- оставшиеся gaps;
- acceptance criteria;
- первичные источники и дату проверки.

Статус `Production Ready` удалён. Зафиксированы music-only, local-first, no-account и YouTube-only assumptions, а также отдельный policy risk для YouTube/offline.

## 4. Что осталось сделать

### P0 — завершить playback foundation

1. Реализовать queue reducer с отдельными сегментами Now Playing / manual Next / AutoMix.
2. Научить queue commands разрешать любой `ContentId` через catalog repository.
3. Реализовать `MediaLibrarySession.Callback`: root, children, item, search, search results.
4. Реализовать `onPlaybackResumption()` и manifest media button receiver.
5. Сохранять repeat, shuffle, context и queue section metadata.
6. Добавить Media3 integration tests: timeline, duplicates, reorder, retry, process death/resumption.

### P1 — music-first information architecture

1. Search sections: Tracks, Albums, Artists, Playlists.
2. Artist/album/playlist details с pagination.
3. Library sections: playlists, songs, albums, artists; Favorites/Downloaded filters.
4. Loading/content/empty/error/offline как отдельные состояния.
5. Единое track menu на всех экранах.
6. Удалить переходный Online screen и старые server-setting UI/API.

### P1 — настоящий offline

1. Заменить WorkManager/file download на Media3 `DownloadManager`, `DownloadIndex` и `DownloadService`.
2. Не считать streaming cache скачанным треком.
3. Реализовать queued/downloading/paused/failed/completed во всех экранах.
4. Загружать album/playlist как атомарный пользовательский context и проверять полноту.
5. Добавить airplane-mode и failed-download retry tests.

Важно: до реализации YouTube download необходимо отдельно решить правовой/policy вопрос. Текущая официальная YouTube policy запрещает API clients скачивать видео для offline playback вне YouTube Premium и отделять audio track.

### P2 — adaptive UI и design system

1. Обновить toolchain до совместимых compileSdk 37/AGP 9.1 после проверки migration notes.
2. Подключить Material 3 Adaptive 1.3.0.
3. Внедрить `NavigationSuiteScaffold`, list-detail и supporting panes.
4. Протестировать 360/600/840/1200/1600 dp, landscape и resize continuity.
5. Положить локальный Roboto Flex с лицензией и source record.
6. Удалить hardcoded colors/shapes/durations и обычный bounce.
7. Добавить reduced-motion обработку, stateDescription и TalkBack traversal tests.

### P2 — библиотека, lyrics и рекомендации

1. Сохранённые albums/artists/playlists и recently played.
2. Playlist reorder/description/cover/duplicate policy/undo/import/export.
3. Room cache для synced/plain lyrics, source и rematch.
4. Playback events START/COMPLETE/SKIP/LIKE/SAVE/SEARCH_CLICK.
5. Recently played, mixes, radio и autoplay поверх этих событий.
6. Android Auto/Bluetooth browse; Cast — после стабильного session layer.

### P3 — performance и quality gates

1. Room FTS и Paging.
2. Coil thumbnail sizing и cache policy.
3. Отключение visualizer/infinite animation вне экрана и на pause.
4. Baseline Profile module.
5. Macrobenchmark для cold start, Home scroll, Search, Player и Queue reorder.
6. Фиксированное release-устройство и сохранённый baseline; gate ≤5% regression.
7. Compose LCE/navigation/semantics/font-scale tests.
8. Запуск migration test на устройстве и установка `pytest` для backend tests.

## 5. Где изучать дальнейшую реализацию

Ниже перечислены первичные источники, проверенные 2026-09-20.

### Playback, session и Android Auto

- [Media3 background playback](https://developer.android.com/media/media3/session/background-playback) — размещение Player/Session в service, controller и playback resumption.
- [MediaSession.Callback API](https://developer.android.com/reference/androidx/media3/session/MediaSession.Callback) — `onPlaybackResumption`, add/set media item flow.
- [MediaLibrarySession.Callback API](https://developer.android.com/reference/androidx/media3/session/MediaLibraryService.MediaLibrarySession.Callback) — root/children/item/search contracts; browsable/playable metadata requirements.
- [Control playback with MediaSession](https://developer.android.com/media/media3/session/control-playback) — автоматическая синхронизация Player state и system session.

### Offline downloads

- [Media3 downloading media](https://developer.android.com/media/media3/exoplayer/downloading-media) — `DownloadService`, singleton `DownloadManager`, `DownloadIndex`, cache и playback downloaded media.

### Material 3 и adaptive layouts

- [Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3) — стабильная версия 1.4.0 и API/release notes.
- [Adaptive do's and don'ts](https://developer.android.com/develop/adaptive-apps/guides/adaptive-dos-and-donts) — `NavigationSuiteScaffold`, list-detail и supporting pane.
- [Build a list-detail layout](https://developer.android.com/develop/adaptive-apps/guides/list-detail) — navigator, pane roles, state continuity и predictive back.

### Accessibility

- [Compose accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) — semantics и minimum 48 dp touch targets.
- [Testing for accessibility](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-accessibility) — Compose accessibility tests и content descriptions.

### Room

- [Migrate Room database versions](https://developer.android.com/training/data-storage/room/migrating-db-versions) — почему destructive fallback удаляет данные и как задавать migration paths.
- [MigrationTestHelper](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper) — schema export, создание старой БД и validation после migration.

### Производительность

- [Compose performance](https://developer.android.com/develop/ui/compose/performance) — release/R8, stable Lazy keys, deferred reads и контроль recomposition.
- [Use a Baseline Profile](https://developer.android.com/develop/ui/compose/performance/baseline-profiles) — app-specific critical journeys.
- [Measure Baseline Profiles with Macrobenchmark](https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile) — сравнение compilation modes и измерение реального эффекта.

### YouTube policy

- [YouTube Developer Policies](https://developers.google.com/youtube/terms/developer-policies-guide) — ограничения background/offline playback и отделения audio. Этот документ необходимо проверить с юристом/специалистом по policy до публичного релиза.

## 6. Рекомендуемый следующий шаг

Следующий bounded milestone: завершить Media3 session layer — полноценный `MediaLibrarySession.Callback`, `onPlaybackResumption`, queue sections и integration tests. Только после этого рационально строить детали сущностей, Android Auto и гарантированные offline downloads: все они зависят от стабильных ContentId, catalog resolution и Media3 timeline.
