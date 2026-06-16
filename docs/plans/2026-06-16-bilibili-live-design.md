# Bilibili Live (TV) — Design

**Date:** 2026-06-16
**Status:** Design approved — awaiting implementation plan
**Branch:** `develop-lite`
**Companion:** `2026-06-16-bilibili-live-investigation.md`

## Decisions (from brainstorming)

| Dimension | Decision |
|---|---|
| Scope | **Full watchable live** — area list → tap → live playback + live danmaku chat |
| Platform | **TV only** (`app/src/tv`) |
| Discovery | **Area directory** — area tree + paged rooms per sub-area |
| Playback | **Minimal** — resolve playurl, prefer HLS-ts → fmp4, best quality, into `ExoMediaPlayer` |
| Danmaku | **Basic chat overlay** — `DANMU_MSG` only, reusing `AkDanmakuPlayer` |
| Architecture | **Approach A** — dedicated live player (separate Activity/Screen/ViewModel), delivered in phases (C) |

## Key findings that shaped the design

1. **v1 `getLiveRoomPlayInfo` does not give a usable stream URL.** Its `RoomPlayInfoData.playUrl` is `null` in practice (the entity comment shows `"play_url": null`). v1 returns room status/metadata only. The actual playable URLs come from the **v2** endpoint `/xlive/web-room/v2/index/getRoomPlayInfo` → structured `playurl_info` (stream → format → codec → url, with `qn` and protocols `http_hls`/`http_flv`). Playback therefore requires adding the v2 endpoint + a resolver.
2. **The existing TV player (`VideoPlayerV3Activity` → `VideoPlayerV3Screen` → `BvPlayer`) is deeply VOD-shaped** — seekbar/duration, resolution/codec/audio switchers, heartbeat-to-history, back-to-history, next-video, danmaku mask, subtitles, and position-synced danmaku. Almost none applies to live. Branching it for live would be messy and risky. → A dedicated live player is cleaner.
3. **`ExoMediaPlayer.playUrl` hardcodes `ProgressiveMediaSource`** (`ExoMediaPlayer.kt:135-144`). It cannot play live HLS as-is; bilibili `http_hls` (`.m3u8`) URLs need an `HlsMediaSource`. → A separate `playLiveUrl` keeps the VOD path untouched.
4. **`AkDanmakuPlayer` is a thin Compose wrapper** over Kuaishou's `DanmakuPlayer`/`DanmakuView`; data is fed via `send()` on the `DanmakuPlayer` instance. The renderer is reusable; only the data source differs (WebSocket vs VOD XML).
5. **`LiveDataWebSocket` has no event emitter (it prints) and brotli (protover 3) is a TODO.** → A `Flow<LiveEvent>` emitter and brotli decompression are required.

## Architecture & module map

```
bili-api (shared)                      app/src/tv                              player/core + shared
─────────────────                      ───────────                              ──────────────────────
BiliLiveHttpApi  (+3 endpoints)   →    DrawerItem.直播 (Live) in DrawerContent   ExoMediaPlayer.playLiveUrl()
LiveAreaRepository                →    LiveScreen + LiveViewModel (area grid)   (new HlsMediaSource path)
LivePlayUrlResolver               →    LivePlayerActivity + Screen + ViewModel
LiveDataWebSocket (+Flow +brotli) →    BvLivePlayer composable (reuses engine
Live entities (area, room, v2url)       + AkDanmakuPlayer renderer)
```

**Module placement** (follows existing conventions):
- `bili-api`: endpoints on `BiliLiveHttpApi` (host already `api.live.bilibili.com`), entities under `http/entity/live/`, two new `@Single` repositories under `repositories/`, the playurl resolver, and the WebSocket `Flow` emitter + brotli fix.
- `app/src/tv`: `LiveScreen`/`LiveViewModel` (discovery), `LivePlayerActivity`/`LivePlayerScreen`/`LivePlayerViewModel` + `BvLivePlayer` + `LivePlayerController` (watch), and the `DrawerItem.直播` entry.
- `player/core`: `playLiveUrl(url)` on `AbstractVideoPlayer` + `ExoMediaPlayer`.
- `player/shared`: `AkDanmakuPlayer` reused as-is.

DI follows the existing Koin convention (`@Single` repos, `@KoinViewModel`).

## Data layer (`bili-api`)

**Three new endpoints** on the existing `BiliLiveHttpApi` (no new client — same host, same `BiliResponse<T>` wrapper). Optional `SESSDATA` cookie added for CDN/rate-limit parity with `BiliHttpApi`.

| Method | Path | Returns |
|---|---|---|
| `getLiveAreaList(sessData)` | `/xlive/web-interface/v1/index/getList` | area tree + recommended rooms |
| `getLiveRoomList(parentAreaId, areaId, page, pageSize, sessData)` | `/xlive/web-interface/v1/second/getList` | paged rooms in a sub-area |
| `getLiveRoomPlayInfoV2(roomId, qn, sessData)` | `/xlive/web-room/v2/index/getRoomPlayInfo` | structured playurl (v1 `playUrl` is null) |

**New entities** (`http/entity/live/`, `@Serializable`, `ignoreUnknownKeys` — model only what is used):
- `LiveAreaListData { parentAreas: List<LiveParentArea>, recommendedRooms: List<LiveRoomItem> }`; `LiveParentArea { id, name, subAreas: List<LiveArea> }`; `LiveArea { id, parentId, name, pic }`.
- `LiveRoomItem { roomId, uid, uname, title, cover, online, areaName, parentAreaId, areaId }` — the list-domain model (mirrors `UgcItem`'s role).
- `LivePlayUrlV2Data` — the nested `playurl_info.playurl.stream[]→format[]→codec[]{qn,url}` structure, plus `g_qn_desc`.

**`LivePlayUrlResolver`** — pure, unit-tested: given `LivePlayUrlV2Data`, pick **one** playable URL → `{ url, qn, formatName }`, or `null` when unplayable. Selection order: protocol `http_hls` > `http_stream`; format `ts`/`fmp4` > `flv`; highest `qn`; first `base_url`.

**Repositories** (`@Single`):
- `LiveAreaRepository.getAreas()` → area tree; `getRooms(area, page)` → `{ list: List<LiveRoomItem>, nextPage, noMore }` (mirrors `RecommendVideoRepository.getPopularVideos`).
- `LiveRoomRepository.getPlayUrl(roomId)` → resolves v2 → returns a ready `ResolvedLivePlayUrl` (or null).

**WebSocket** (`LiveDataWebSocket`): add an `events: SharedFlow<LiveEvent>` (or listener) emitting parsed `DanmakuEvent`s, and implement brotli (protover 3) decompression (currently a TODO). `DanmakuEvent` already exists.

## TV discovery UI

- **Drawer entry**: add `Live("直播")` to the `DrawerItem` enum + the `buildList` in `app/src/tv/.../screens/main/DrawerContent.kt`. Selecting it shows `LiveScreen`.
- **`LiveScreen`** (mirrors `PopularPage`'s grid, with a category rail):
  - Left rail: parent areas (大类) + sub-areas, with a **"热门推荐"** entry pinned at top (renders `recommendedRooms` from `getList`). Default selection = 热门推荐.
  - Right: `LazyVerticalGrid` of room cards for the selected area; `OnBottomReached` → `loadMore`; pull-to-refresh.
  - Room card (`LiveRoomCard`): cover (16:9), title, host (`uname`), formatted **online** count, area badge, a red **●LIVE** dot. D-pad focus matches existing TV cards.
- **`LiveViewModel`** (`@KoinViewModel`): `selectedArea` state + `mutableStateListOf<LiveRoomItem>` + `nextPage`, mirroring `PopularViewModel` (`loadData`/`loadMore`/`clearData`/`resetPage`). Switching area calls `clearData()` + `loadData()`.
- **Tap**: `LivePlayerActivity.actionStart(context, roomItem)` (Intent-based, like `VideoPlayerV3Activity.actionStart`).

## Live player (watch)

- **`LivePlayerActivity`** (`app/src/tv/.../activities/live/`): builds `ExoMediaPlayer` via `ExoPlayerFactory().create(...)` (same as `VideoPlayerV3Activity`), `FLAG_KEEP_SCREEN_ON`, hosts `LivePlayerScreen`. `actionStart` carries `roomId/title/uname/cover`.
- **`LivePlayerViewModel`** (`@KoinViewModel`):
  1. `loadPlayUrl(roomId)` → `LiveRoomRepository.getPlayUrl(roomId)` → `ExoMediaPlayer.playLiveUrl(resolved.url)` + `prepare()` + `start()`.
  2. `startDanmaku(roomId)` → `BiliLiveHttpApi.getLiveDanmuInfo(roomId)` for token/host → open `LiveDataWebSocket`, collect `events` → `danmakuPlayer.send(DanmakuEvent→DanmakuItemData)` on wall-clock. Cancel on exit/destroy.
- **`BvLivePlayer`** composable: the **video surface** (reuse `BvVideoPlayer`) + **`AkDanmakuPlayer`** overlay + a **`LivePlayerController`**. Reuses engine and danmaku renderer; nothing else from VOD's `BvPlayer`.
- **`LivePlayerController`** — deliberately minimal (no seekbar, no history, no next-video): **play/pause, exit, danmaku on/off, danmaku opacity**, a non-seekable **"● LIVE"** badge + room title/host, and a buffering spinner. D-pad focus on the few controls.

## Engine change (player/core)

- Add **`fun playLiveUrl(videoUrl: String)`** to `AbstractVideoPlayer` (default: no-op/`TODO`) + `ExoMediaPlayer` impl using **`HlsMediaSource.Factory(dataSourceFactory)`** + the existing `customLoadErrorHandlingPolicy`, `MediaItem.fromUri(url)`.
- **VOD `playUrl` is left untouched** → zero regression risk. The malformed-container recovery (`ExoMediaPlayer.kt:286`) stays as-is; for clean live HLS it rarely triggers, and the existing cap prevents runaway retries.

## Error handling & edge cases

- **Discovery**: same shape as `PopularViewModel` — `try/catch` per load, toast on error, keep partial list; area switch resets. Empty-area / `noMore` → end-of-list sentinel (no infinite spinner).
- **Room not live / playurl missing**: gate playback on v1 `getLiveRoomPlayInfo`'s `liveStatus == 1`; if v2 resolver returns `null`, show a "未开播 / 无法播放" state with **back** (not a crash).
- **WebSocket failure**: connect with retry/backoff; on persistent failure keep video playing and show a subtle "弹幕连接失败" hint (chat is non-blocking). Heartbeat continues per the existing 30s loop; on `STOP_LIVE_ROOM_LIST` containing this room, surface "直播已结束".
- **Player errors**: existing `onError` path surfaces the diagnostics dump; live HLS errors fall through to it. Buffering under-run handled by the standard `STATE_BUFFERING` path.

## Testing

- **`LivePlayUrlResolverTest`** (unit, pure logic): fixtures for hls-ts, hls-fmp4, flv-only, multi-qn, and empty → assert correct selection + `null` when unplayable. Highest-value test.
- **`BiliLiveHttpApiTest`**: extend the existing smoke test with the three new endpoints (assert-does-not-throw + print, matching current style).
- **`LiveDataWebSocketTest`**: extend to assert the new `events` flow emits a `DanmakuEvent` from a real room, and that a brotli (protover 3) frame decodes.
- Repository/ViewModel/UI covered by the project's existing test conventions (light; smoke where patterns exist).

## Phased delivery

1. **Phase 1 — Data layer**: endpoints, entities, `LiveAreaRepository`, `LivePlayUrlResolver` (+ test), WebSocket `Flow` + brotli. Shippable library code + tests; nothing in the app yet.
2. **Phase 2 — Discovery**: `DrawerItem.直播`, `LiveScreen` + `LiveViewModel` + `LiveRoomCard`. Browsable; taps do nothing (or a toast). Independently verifiable.
3. **Phase 3 — Playback**: `playLiveUrl` + `LivePlayerActivity`/`Screen`/`ViewModel` + `BvLivePlayer` + minimal controller. Tap → watch (no chat yet). "Watchable" milestone.
4. **Phase 4 — Live chat**: wire WebSocket → `danmakuPlayer.send()`; danmaku toggle/opacity in controller. Feature complete.

Each phase is a reviewable checkpoint.

## Risks & unknowns (to resolve during implementation)

- **WBI signing / anti-crawler** on `api.live.bilibili.com` list endpoints — `BiliHttpApi` has signing plugins; `BiliLiveHttpApi` does not. May need porting or a `SESSDATA` cookie. Phase-1 spike.
- **Brotli in WebSocket** — add a brotli decoder (`org.brotli:dec` or OkHttp's brotli lib). Small concrete dependency.
- **ExoPlayer live HLS** — Media3 handles live HLS natively, but bilibili HLS may need `refer`/UA headers and may be flv-fallback-only in some areas; resolver must degrade gracefully.
- **Entity field-name accuracy** — modeled from known endpoint shapes; Phase 1 must capture a real response per endpoint and confirm field names before writing mappers.
