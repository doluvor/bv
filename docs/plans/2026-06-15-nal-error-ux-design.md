# NAL-Error UX Recovery — Design

**Date:** 2026-06-15
**Status:** Design (awaiting implementation)
**Branch:** `develop-lite`
**Baseline commit:** `2d025b06` (player diagnostics + malformed-container recovery, pre source-switching)

## Problem

Mid-stream `PlaybackException` with `ERROR_CODE_PARSING_CONTAINER_MALFORMED`
("invalid NAL length") occurs intermittently on some videos. The root cause is
**bad bytes from one source** — a particular CDN edge, a truncated read, or
corruption in a single encode. Today's behaviour:

- `ExoMediaPlayer.onPlayerError` silently seeks forward up to 20× (up to ~10 s
  of stutter/rebuffer with no UI feedback), then
- surfaces a harsh, technical `PlayErrorTip` ("播放器正在抽风") with no recovery
  actions.

Both halves are poor UX: the silent stall feels frozen, and the failure screen
offers no way forward.

## Goals

1. **Silent recovery** — most NAL errors resolve invisibly (a brief rebuffer that
   reads as normal network variance).
2. **Graceful failure** — when every source is genuinely unplayable, show a
   friendly, actionable TV screen.
3. Reuse the candidate-source data the VM already assembles — no new API calls
   on the common path.

## Non-goals

- Eliminating the brief rebuffer on each retry. A parse error forces a
  re-prepare; ExoPlayer holds the last decoded frame on the surface, so it is a
  brief *freeze*, not a black flash. That is as seamless as a mid-stream parse
  error gets.
- A mobile error screen. Mobile `BvPlayer` has none today; only the
  silent-recovery half applies to mobile.

## Architecture: recovery state machine

```
malformed / NAL error
   │
   ▼
[ seek-forward on current source ]   per-source budget: 3 attempts × 500 ms
   │ still failing after budget
   ▼
player fires  onSourceUnrecoverable(error, resumePositionMs)   ← NEW callback
   │
   ▼
VM advances candidate cursor
   ├─ candidates remain → playUrl(next) + prepare() + seekTo(resumePos)
   │                       reset per-source counter;  STATE_READY = silent success
   └─ cursor exhausted   → isError = true  →  TV graceful screen
   │
   ▼  (if source switched)
[ new source ] → back to top
```

**Why seek-first:** a re-fetch of the *same* source often yields clean bytes
(transient bad edge), and seeking past localized corruption is cheap.
Source-switching is reserved for genuinely-bad sources.

## Candidate source strategy

Candidates are built **once at load** from data already in `playData` — no extra
API call on the common path. Order (video; audio keeps its own short list, used
only if audio also fails):

1. **Mirrors** — `[videoItem.baseUrl, *videoItem.backUrl].distinct()`.
   `PlayData.kt:335-343`; collected at `VideoPlayerV3ViewModel.kt:373-375`.
2. **Synthetic AliCdn rewrite** of the primary — `replaceUrlDomainWithAliCdn()`
   (`VideoPlayerV3ViewModel.kt:591-603`).
3. *(Phase 2)* **P2P/local fallbacks** that `selectOfficialCdnUrl` (`:605`)
   currently drops — `.mcdn.bilivideo.`, `.szbdyd.com`, raw IPs. Still playable.
4. *(Phase 2)* **Alternate encode** — other `dashVideos` items (different
   quality/codec), often on different CDNs. Heavier: the picture changes.
5. *(Phase 2, nuclear)* **Re-fetch `loadPlayData`** — Bilibili rotates CDN
   assignment per request, so a fresh fetch usually yields different hosts. Cap
   at 1 to avoid loops.

**Default: switch video only** (NAL is a video parse error) — less disruption,
audio stays put.

## Components & changes

### Player core
- **`VideoPlayerListener`** (`player/core/.../VideoPlayerListener.kt`): add
  `fun onSourceUnrecoverable(error: Exception, resumePositionMs: Long) {}`
  (default no-op).
- **`ExoMediaPlayer.onPlayerError`** (`player/core/.../impl/exo/ExoMediaPlayer.kt`):
  - Reduce `MAX_MALFORMED_RETRIES` from 20 → **3** (tunable; see Open decisions).
  - Post-cap fallthrough changes from `mPlayerEventListener?.onError(error)` to
    `mPlayerEventListener?.onSourceUnrecoverable(error, currentPosition)`.
  - Seek-retry loop otherwise unchanged.
- **`AbstractVideoPlayer`**: no change beyond the listener default.

### ViewModel
- **`VideoPlayerV3ViewModel`** (`app/src/main/.../VideoPlayerV3ViewModel.kt`,
  `:363-410`):
  - In resolution-select, **store** `videoCandidates: List<String>` +
    `videoCandidateIndex` instead of collapsing to one url via
    `selectOfficialCdnUrl`. Keep applying AliCdn rewrite under the proxy path.
  - New `onSourceUnrecoverable` handler:
    - advance index; if within list → `playUrl(candidates[index], audioUrl)` +
      `prepare()` + `seekTo(resumePos)`;
    - if exhausted → set `isError` (existing error flow → TV screen).
  - Guard `resumePos` against `> duration`.
  - Cap Phase-2 re-fetch at 1.

### TV UI
- **`PlayStateTips` / `PlayErrorTip`** (`player/tv/.../controller/PlayStateTips.kt`,
  `:129`): replace the harsh screen with
  - Headline: "当前线路无法播放".
  - D-pad-focusable buttons: `[重试]` (cycle from candidate 0) · `[跳过此处]`
    (seek +30 s, retry) · `[刷新播放地址]` (re-fetch playInfo, retry).
  - Collapsible `[技术详情]` toggle → existing diagnostics trail (collapsed by
    default, so it is not scary).

### Shared entity
- **`VideoPlayerStateData`**: no change (recovery is silent; `isError` only on
  total failure).

## Edge cases / error handling
- Empty `backUrl` → short candidate list → reaches synthetic / encode / re-fetch
  sooner. Fine.
- `resumePositionMs > duration` → clamp to `duration − margin`.
- Re-fetch capped at 1 → no infinite refresh loop.
- Proxy path (`enableProxy && proxyArea != MainLand`): candidates =
  AliCdn-rewritten + original backups.
- Danmaku / subtitle sync are driven by position; `seekTo` on the new source
  keeps them aligned.

## Testing
- **Unit** — VM candidate-cursor advance / exhaustion as a pure function over a
  list.
- **Manual** — point one candidate at a bad URL (or use the known malformed
  stream); verify silent cycling; screen appears only when all sources fail;
  resume position preserved; the three TV buttons work via remote.
- **Regression** — normal playback unaffected; a single transient error
  recovers invisibly.

## Phasing
- **Phase 1 (core):** seek-budget reduction + `onSourceUnrecoverable` + VM cursor
  over mirrors + AliCdn rewrite + TV graceful screen. Delivers both UX goals for
  the common case.
- **Phase 2 (optional resilience):** add P2P fallbacks, alternate-encode switch,
  and the 1-shot playInfo re-fetch as later candidates.

## Open decisions (confirm before / during implementation)
1. **Per-source seek budget** — recommend **3** (≈1.5 s/source; total worst-case
   a few seconds across candidates). Alternative: keep **20** (≈10 s/source →
   long total stall with multiple sources). Optional: escalate the step
   (500 ms → 1 s → 2 s) to escape larger corrupt regions faster.
2. **Phase-2 candidates** — include P2P / alternate-encode / re-fetch (max
   resilience) or ship Phase 1 only (simpler; no quality change, no extra API
   call)?

## References
- `ExoMediaPlayer.kt` — `onPlayerError`, `MAX_MALFORMED_RETRIES`,
  `customLoadErrorHandlingPolicy`, `LoggingDataSource`/`PlayerDiagnostics`.
- `VideoPlayerV3ViewModel.kt:363-410` (resolution select / `playUrl`),
  `:591-603` (`replaceUrlDomainWithAliCdn`), `:605-624` (`selectOfficialCdnUrl`).
- `PlayData.kt:335-343` — `DashVideo(baseUrl: String, backUrl: List<String>)`.
- `PlayStateTips.kt:129` — `PlayErrorTip`.
