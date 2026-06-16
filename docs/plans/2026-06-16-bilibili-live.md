# Bilibili Live (TV) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a TV-only "直播" feature: browse live rooms by area → tap → watch the live stream with a scrolling danmaku chat overlay.

**Architecture:** Dedicated live player (Approach A), delivered in 4 phases. New `bili-api` endpoints + entities + repositories feed a TV discovery screen (`DrawerItem.Live`) and a dedicated `LivePlayerActivity` that reuses the `ExoMediaPlayer` engine (via a new `playLiveUrl` HLS path) and the `AkDanmakuPlayer` renderer. The VOD player is untouched.

**Tech Stack:** Kotlin, Ktor (bili-api HTTP), kotlinx.serialization, Koin (annotations + `@ComponentScan` auto-discovery), Jetpack Compose for TV, Media3/ExoPlayer (`HlsMediaSource`), Kuaishou `akdanmaku` (`DanmakuPlayer`), JUnit5.

**Design doc:** `docs/plans/2026-06-16-bilibili-live-design.md`

---

## How to build & test

- **bili-api unit tests:** `./gradlew :bili-api:test`
- **One test:** `./gradlew :bili-api:test --tests "dev.aaa1115910.biliapi.repositories.LivePlayUrlResolverTest"`
- **Build TV app:** `./gradlew :app:assembleLiteTvDebug` (flavors: channel `lite`/`default` × platform `mobile`/`tv`; the live feature targets `tv`)
- **Compile-check TV Kotlin only:** `./gradlew :app:compileLiteTvDebugKotlin`

**Conventions confirmed from the codebase:**
- `BiliLiveHttpApi` (`bili-api/.../http/BiliLiveHttpApi.kt`) is a Ktor `object` on `api.live.bilibili.com`, returns `BiliResponse<T>` (`.getResponseData()` throws on non-zero code; `.data` is nullable). **No WBI signing** — live area/room endpoints are public; only an optional `SESSDATA` cookie is needed.
- Koin: any `@Single` in `dev.aaa1115910.biliapi.repositories` and any `@KoinViewModel` anywhere under `dev.aaa1115910.bv.**` is **auto-discovered** by `@ComponentScan` — no manual registration.
- `AuthRepository.sessionData` provides the `SESSDATA` value (see `RecommendVideoRepository` usage).
- Mirror the paging shape: repository returns `{ list, nextPage, noMore }`; ViewModel uses `mutableStateListOf` + `addAllWithMainContext` + `loadMore` (see `PopularViewModel`).

---

# Phase 1 — Data layer (`bili-api`)

Shippable library code + tests. Nothing in the app yet.

## Task 1.1: Capture real endpoint responses (de-risk entity field names)

The DTO field names below are modeled from the known bilibili live endpoints; confirm them against a real response before relying on the mappers.

**Files:**
- Modify: `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApiProbeTest.kt` (Create — throwaway)

**Step 1: Add a throwaway probe test** that calls each new endpoint and prints the raw JSON.

```kotlin
package dev.aaa1115910.biliapi.http

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class BiliLiveHttpApiProbeTest {
    // Pick a currently-live room id from the live homepage for room/v2 tests.
    private val liveRoomId = 6

    @Test
    fun `dump area list`() = runBlocking {
        val text = BiliLiveHttpApi.rawGet("/xlive/web-interface/v1/index/getList")
        println("AREA_LIST: $text")
    }

    @Test
    fun `dump room list`() = runBlocking {
        val text = BiliLiveHttpApi.rawGet(
            "/xlive/web-interface/v1/second/getList?parent_area_id=2&area_id=21&page=1&page_size=10"
        )
        println("ROOM_LIST: $text")
    }

    @Test
    fun `dump playurl v2`() = runBlocking {
        val text = BiliLiveHttpApi.rawGet(
            "/xlive/web-room/v2/index/getRoomPlayInfo?protocol=0,1&format=0,1,2&codec=0,1&qn=10000&platform=web&ptype=16&dolby=5&panorama=1&room_id=$liveRoomId"
        )
        println("PLAYURL_V2: $text")
    }
}
```

**Step 2: Add a temporary `rawGet` helper** to `BiliLiveHttpApi` (will be removed after Task 1.2):

```kotlin
// inside object BiliLiveHttpApi
suspend fun rawGet(pathWithQuery: String): String =
    client.get(pathWithQuery).bodyAsText()
```
(add imports `io.ktor.client.request.get`, `io.ktor.client.statement.bodyAsText`)

**Step 3: Run and eyeball**

Run: `./gradlew :bili-api:test --tests "dev.aaa1115910.biliapi.http.BiliLiveHttpApiProbeTest"`
Expected: three dumps print; confirm the JSON key names match the DTOs in Task 1.3 (`game_list`, `list` with `roomid/uid/uname/title/cover/user_cover/online/area_name/area/parent_area_id/live_status`, and `playurl_info.playurl.stream[].format[].codec[]{current_qn,base_url,url[]}`). **Adjust the DTOs in 1.3 if any differ.**

**Step 4: Commit**

```bash
git add bili-api/src/test/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApiProbeTest.kt bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApi.kt
git commit -m "chore(live): probe live list/playurl endpoints to confirm field names"
```

> Note: this task needs network access to `api.live.bilibili.com`. If the environment blocks it, skip the run and proceed using the field names as written, then fix in Task 1.2's smoke test.

---

## Task 1.2: Add the three endpoints to `BiliLiveHttpApi`

**Files:**
- Modify: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApi.kt`
- Modify (remove the throwaway): delete `rawGet` + the probe test file from Task 1.1 once smoke tests pass.
- Test: `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApiTest.kt`

**Step 1: Add the response DTOs** in `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/live/LiveListEntities.kt` (Create):

```kotlin
package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LiveAreaListData(
    @SerialName("game_list") val gameList: List<LiveParentArea> = emptyList(),
    @SerialName("live_list") val liveList: List<LiveRoomInfo> = emptyList()
)

@Serializable
data class LiveParentArea(
    val id: Int = 0,
    val name: String = "",
    val list: List<LiveArea> = emptyList()
)

@Serializable
data class LiveArea(
    val id: Int = 0,
    @SerialName("parent_id") val parentId: Int = 0,
    val name: String = "",
    val pic: String = ""
)

@Serializable
data class LiveRoomListData(
    val list: List<LiveRoomInfo> = emptyList(),
    val count: Int = 0,
    @SerialName("has_more") val hasMore: Int = 0
)

@Serializable
data class LiveRoomInfo(
    val roomid: Int = 0,
    val uid: Long = 0,
    val uname: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("user_cover") val userCover: String = "",
    val online: Int = 0,
    @SerialName("area_name") val areaName: String = "",
    val area: Int = 0,
    @SerialName("parent_area_id") val parentAreaId: Int = 0,
    @SerialName("live_status") val liveStatus: Int = 0
)
```

**Step 2: Add the v2 playurl DTOs** in `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/live/LivePlayUrlV2Data.kt` (Create):

```kotlin
package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LivePlayUrlV2Data(
    @SerialName("playurl_info") val playurlInfo: PlayurlInfo? = null
)

@Serializable
data class PlayurlInfo(
    val playurl: Playurl? = null
)

@Serializable
data class Playurl(
    val stream: List<LiveStream> = emptyList(),
    @SerialName("g_qn_desc") val gQnDesc: List<QnDesc> = emptyList()
)

@Serializable
data class LiveStream(
    @SerialName("protocol_name") val protocolName: String = "",
    val format: List<LiveStreamFormat> = emptyList()
)

@Serializable
data class LiveStreamFormat(
    @SerialName("format_name") val formatName: String = "",
    val codec: List<LiveStreamCodec> = emptyList()
)

@Serializable
data class LiveStreamCodec(
    @SerialName("codec_name") val codecName: String = "",
    @SerialName("current_qn") val currentQn: Int = 0,
    @SerialName("base_url") val baseUrl: String = "",
    val url: List<String> = emptyList()
)

@Serializable
data class QnDesc(
    val qn: Int = 0,
    val desc: String = ""
)
```

**Step 3: Add the three endpoint methods** to `object BiliLiveHttpApi` (add import `io.ktor.client.request.header`):

```kotlin
/** 直播首页：分区树 + 推荐直播间 */
suspend fun getLiveAreaList(sessData: String = ""): BiliResponse<LiveAreaListData> =
    client.get("/xlive/web-interface/v1/index/getList") {
        if (sessData.isNotEmpty()) header("Cookie", "SESSDATA=$sessData;")
    }.body()

/** 某子分区的直播间列表（分页） */
suspend fun getLiveRoomList(
    parentAreaId: Int,
    areaId: Int,
    page: Int,
    pageSize: Int = 30,
    sessData: String = ""
): BiliResponse<LiveRoomListData> =
    client.get("/xlive/web-interface/v1/second/getList") {
        parameter("parent_area_id", parentAreaId)
        parameter("area_id", areaId)
        parameter("page", page)
        parameter("page_size", pageSize)
        if (sessData.isNotEmpty()) header("Cookie", "SESSDATA=$sessData;")
    }.body()

/** 直播间 v2 播放地址（结构化 playurl；v1 的 play_url 为 null 不可用） */
suspend fun getLiveRoomPlayInfoV2(
    roomId: Int,
    qn: Int = 10000,
    sessData: String = ""
): BiliResponse<LivePlayUrlV2Data> =
    client.get("/xlive/web-room/v2/index/getRoomPlayInfo") {
        parameter("room_id", roomId)
        parameter("protocol", "0,1")
        parameter("format", "0,1,2")
        parameter("codec", "0,1")
        parameter("qn", qn)
        parameter("platform", "web")
        parameter("ptype", 16)
        parameter("dolby", 5)
        parameter("panorama", 1)
        if (sessData.isNotEmpty()) header("Cookie", "SESSDATA=$sessData;")
    }.body()
```

Add the necessary imports for the entity types at the top of `BiliLiveHttpApi.kt`:
```kotlin
import dev.aaa1115910.biliapi.http.entity.live.LiveAreaListData
import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data
import dev.aaa1115910.biliapi.http.entity.live.LiveRoomListData
```

**Step 4: Remove the throwaway `rawGet` + probe test file** added in Task 1.1.

**Step 5: Add smoke tests** to `BiliLiveHttpApiTest.kt`:

```kotlin
@Test
fun `get live area list`() {
    Assertions.assertDoesNotThrow {
        runBlocking {
            val response = BiliLiveHttpApi.getLiveAreaList()
            println(response)
        }
    }
}

@Test
fun `get live room list`() {
    Assertions.assertDoesNotThrow {
        runBlocking {
            val response = BiliLiveHttpApi.getLiveRoomList(parentAreaId = 2, areaId = 21, page = 1)
            println(response)
        }
    }
}

@Test
fun `get live room play info v2`() {
    Assertions.assertDoesNotThrow {
        runBlocking {
            val response = BiliLiveHttpApi.getLiveRoomPlayInfoV2(roomId = 6)
            println(response)
        }
    }
}
```

**Step 6: Run tests**

Run: `./gradlew :bili-api:test`
Expected: PASS (these hit the network; if offline, they'll fail at the network call — that's acceptable, do not block on it; the resolver test in 1.4 is the one that must pass offline).

**Step 7: Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApi.kt \
        bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/live/LiveListEntities.kt \
        bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/entity/live/LivePlayUrlV2Data.kt \
        bili-api/src/test/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApiTest.kt
git rm bili-api/src/test/kotlin/dev/aaa1115910/biliapi/http/BiliLiveHttpApiProbeTest.kt
git commit -m "feat(live-api): add area-list, room-list, and v2 playurl endpoints"
```

---

## Task 1.3: Domain entities + mappers

**Files:**
- Create: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/entity/live/LiveRoomItem.kt`
- Create: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/entity/live/ResolvedLivePlayUrl.kt`

**Step 1: Create `LiveRoomItem`** (mirrors `UgcItem`'s role):

```kotlin
package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.LiveRoomInfo

data class LiveRoomItem(
    val roomId: Int,
    val uid: Long,
    val uname: String,
    val title: String,
    val cover: String,
    val online: Int,
    val areaName: String,
    val parentAreaId: Int,
    val areaId: Int
) {
    companion object {
        fun fromRoomInfo(info: LiveRoomInfo) = LiveRoomItem(
            roomId = info.roomid,
            uid = info.uid,
            uname = info.uname,
            title = info.title,
            cover = info.userCover.ifBlank { info.cover },
            online = info.online,
            areaName = info.areaName,
            parentAreaId = info.parentAreaId,
            areaId = info.area
        )
    }
}
```

**Step 2: Create `ResolvedLivePlayUrl`**:

```kotlin
package dev.aaa1115910.biliapi.entity.live

/** The single playable stream URL chosen by LivePlayUrlResolver. */
data class ResolvedLivePlayUrl(
    val url: String,
    val qn: Int,
    val protocolName: String,
    val formatName: String
)
```

**Step 3: Build the module** to confirm it compiles:

Run: `./gradlew :bili-api:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/entity/live/
git commit -m "feat(live-api): add LiveRoomItem and ResolvedLivePlayUrl domain models"
```

---

## Task 1.4: `LivePlayUrlResolver` (TDD — the highest-value test)

Pure selection logic: prefer `http_hls`, format `ts` > `fmp4`, highest `current_qn`, first `url`. Returns `null` when no Media3-playable (HLS) stream exists (bilibili `http_stream`/flv isn't playable by Media3 without a custom extractor — out of scope for v1).

**Files:**
- Test: `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/repositories/LivePlayUrlResolverTest.kt` (Create)
- Create: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/LivePlayUrlResolver.kt`

**Step 1: Write the failing test**

```kotlin
package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data
import dev.aaa1115910.biliapi.http.entity.live.LiveStream
import dev.aaa1115910.biliapi.http.entity.live.LiveStreamCodec
import dev.aaa1115910.biliapi.http.entity.live.LiveStreamFormat
import dev.aaa1115910.biliapi.http.entity.live.Playurl
import dev.aaa1115910.biliapi.http.entity.live.PlayurlInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LivePlayUrlResolverTest {

    private fun hlsTs(qn: Int, vararg urls: String) = LivePlayUrlV2Data(
        playurlInfo = PlayurlInfo(
            playurl = Playurl(
                stream = listOf(
                    LiveStream(
                        protocolName = "http_hls",
                        format = listOf(
                            LiveStreamFormat(
                                formatName = "ts",
                                codec = listOf(LiveStreamCodec(currentQn = qn, url = urls.toList()))
                            )
                        )
                    )
                )
            )
        )
    )

    @Test
    fun `returns ts url when http_hls ts present`() {
        val data = hlsTs(qn = 10000, "https://hls/ts.m3u8")
        val resolved = LivePlayUrlResolver.resolve(data)
        assertEquals("https://hls/ts.m3u8", resolved?.url)
        assertEquals("ts", resolved?.formatName)
        assertEquals(10000, resolved?.qn)
    }

    @Test
    fun `prefers ts over fmp4`() {
        val data = LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(playurl = Playurl(stream = listOf(
                LiveStream(protocolName = "http_hls", format = listOf(
                    LiveStreamFormat(formatName = "fmp4",
                        codec = listOf(LiveStreamCodec(currentQn = 10000, url = listOf("https://hls/fmp4.m3u8")))),
                    LiveStreamFormat(formatName = "ts",
                        codec = listOf(LiveStreamCodec(currentQn = 10000, url = listOf("https://hls/ts.m3u8"))))
                ))
            )))
        )
        assertEquals("https://hls/ts.m3u8", LivePlayUrlResolver.resolve(data)?.url)
    }

    @Test
    fun `picks highest current_qn`() {
        val data = LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(playurl = Playurl(stream = listOf(
                LiveStream(protocolName = "http_hls", format = listOf(
                    LiveStreamFormat(formatName = "ts", codec = listOf(
                        LiveStreamCodec(currentQn = 400, url = listOf("https://hls/400.m3u8")),
                        LiveStreamCodec(currentQn = 10000, url = listOf("https://hls/10k.m3u8"))
                    ))
                ))
            )))
        )
        assertEquals("https://hls/10k.m3u8", LivePlayUrlResolver.resolve(data)?.url)
        assertEquals(10000, LivePlayUrlResolver.resolve(data)?.qn)
    }

    @Test
    fun `returns null when only flv http_stream available`() {
        val data = LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(playurl = Playurl(stream = listOf(
                LiveStream(protocolName = "http_stream", format = listOf(
                    LiveStreamFormat(formatName = "flv",
                        codec = listOf(LiveStreamCodec(currentQn = 10000, url = listOf("https://flv/x.flv"))))
                ))
            )))
        )
        assertNull(LivePlayUrlResolver.resolve(data))
    }

    @Test
    fun `returns null for empty data`() {
        assertNull(LivePlayUrlResolver.resolve(LivePlayUrlV2Data()))
    }
}
```

**Step 2: Run the test to verify it fails**

Run: `./gradlew :bili-api:test --tests "dev.aaa1115910.biliapi.repositories.LivePlayUrlResolverTest"`
Expected: FAIL (unresolved reference `LivePlayUrlResolver`).

**Step 3: Write the minimal implementation**

`bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/LivePlayUrlResolver.kt`:

```kotlin
package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.ResolvedLivePlayUrl
import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data

/**
 * Picks one Media3-playable stream URL from a v2 playurl response.
 *
 * Order: protocol `http_hls` only (Media3 cannot play bilibili flv); within it,
 * format `ts` > `fmp4`; highest `current_qn`; first `url`. Returns null if no
 * playable HLS stream exists.
 */
object LivePlayUrlResolver {
    private val HLS_FORMAT_PRIORITY = listOf("ts", "fmp4")

    fun resolve(data: LivePlayUrlV2Data): ResolvedLivePlayUrl? {
        val streams = data.playurlInfo?.playurl?.stream ?: return null
        val hls = streams.firstOrNull { it.protocolName == "http_hls" } ?: return null
        for (fmt in HLS_FORMAT_PRIORITY) {
            val format = hls.format.firstOrNull { it.formatName == fmt } ?: continue
            val codec = format.codec.maxByOrNull { it.currentQn } ?: continue
            val url = codec.url.firstOrNull() ?: continue
            return ResolvedLivePlayUrl(
                url = url,
                qn = codec.currentQn,
                protocolName = "http_hls",
                formatName = fmt
            )
        }
        return null
    }
}
```

**Step 4: Run the test to verify it passes**

Run: `./gradlew :bili-api:test --tests "dev.aaa1115910.biliapi.repositories.LivePlayUrlResolverTest"`
Expected: PASS (5 tests).

**Step 5: Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/LivePlayUrlResolver.kt \
        bili-api/src/test/kotlin/dev/aaa1115910/biliapi/repositories/LivePlayUrlResolverTest.kt
git commit -m "feat(live-api): add LivePlayUrlResolver (hls ts>fmp4, highest qn)"
```

---

## Task 1.5: Repositories (`LiveAreaRepository`, `LiveRoomRepository`)

**Files:**
- Create: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/LiveAreaRepository.kt`
- Create: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/LiveRoomRepository.kt`

**Step 1: Create `LiveAreaRepository`**

```kotlin
package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.LiveRoomItem
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import dev.aaa1115910.biliapi.http.entity.live.LiveParentArea
import org.koin.core.annotation.Single

data class LiveAreaHome(
    val areas: List<LiveParentArea>,
    val recommendedRooms: List<LiveRoomItem>
)

data class LiveRoomPage(
    val list: List<LiveRoomItem>,
    val nextPage: Int,
    val noMore: Boolean
)

@Single
class LiveAreaRepository(
    private val authRepository: AuthRepository
) {
    private val sessData get() = authRepository.sessionData ?: ""

    /** 首页：分区树 + 推荐直播间（同一次 getList 调用） */
    suspend fun getAreaHome(): LiveAreaHome {
        val data = BiliLiveHttpApi.getLiveAreaList(sessData).getResponseData()
        return LiveAreaHome(
            areas = data.gameList,
            recommendedRooms = data.liveList.map { LiveRoomItem.fromRoomInfo(it) }
        )
    }

    /** 子分区分页直播间 */
    suspend fun getRooms(parentAreaId: Int, areaId: Int, page: Int): LiveRoomPage {
        val data = BiliLiveHttpApi.getLiveRoomList(
            parentAreaId = parentAreaId,
            areaId = areaId,
            page = page,
            sessData = sessData
        ).getResponseData()
        return LiveRoomPage(
            list = data.list.map { LiveRoomItem.fromRoomInfo(it) },
            nextPage = page + 1,
            noMore = data.hasMore == 0
        )
    }
}
```

**Step 2: Create `LiveRoomRepository`**

```kotlin
package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.ResolvedLivePlayUrl
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import org.koin.core.annotation.Single

@Single
class LiveRoomRepository(
    private val authRepository: AuthRepository
) {
    private val sessData get() = authRepository.sessionData ?: ""

    /** live_status: 0 未开播, 1 直播中, 2 轮播 */
    suspend fun getLiveStatus(roomId: Int): Int =
        BiliLiveHttpApi.getLiveRoomPlayInfo(roomId).data?.liveStatus ?: 0

    /** 解析 v2 playurl 为单个可播放地址；不可播放返回 null */
    suspend fun getPlayUrl(roomId: Int): ResolvedLivePlayUrl? {
        val data = BiliLiveHttpApi.getLiveRoomPlayInfoV2(roomId, sessData = sessData).getResponseData()
        return LivePlayUrlResolver.resolve(data)
    }
}
```

**Step 3: Build to confirm wiring + Koin discovery**

Run: `./gradlew :bili-api:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/LiveAreaRepository.kt \
        bili-api/src/main/kotlin/dev/aaa1115910/biliapi/repositories/LiveRoomRepository.kt
git commit -m "feat(live-api): add LiveAreaRepository and LiveRoomRepository"
```

---

## Task 1.6: Brotli (protover 3) support in `LiveDataWebSocket`

`LiveDataWebSocket` hardcodes `protover=2` (zlib, works) but brotli (protover 3) is a TODO. Bilibili sometimes serves brotli; implement it for robustness.

**Files:**
- Modify: `bili-api/build.gradle.kts` (add brotli dependency)
- Modify: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/websocket/LiveDataWebSocket.kt:163-166`
- Create: `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/util/BrotliDecompress.kt`
- Modify: `bili-api/src/test/kotlin/dev/aaa1115910/biliapi/websocket/LiveDataWebSocketTest.kt`

**Step 1: Add the brotli dependency** to `bili-api/build.gradle.kts` `dependencies` block:

```kotlin
testImplementation(kotlin("test"))
// add:
implementation("org.brotli:dec:0.1.2")
```
(Confirm the version resolves; if the BOM/coordinates differ in this project, use whatever the project already pulls — check `libs/` and existing `dependencies`.)

**Step 2: Create the decompress helper** `bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/util/BrotliDecompress.kt` (mirror the existing `zlibDecompress`):

```kotlin
package dev.aaa1115910.biliapi.http.util

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayOutputStream

fun brotliDecompress(data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    BrotliInputStream(data.inputStream()).use { it.copyTo(out) }
    return out.toByteArray()
}
```

**Step 3: Wire protover 3** in `LiveDataWebSocket.handleLiveEventBody` (replace the `// 3 ->` TODO branch at lines 163-166):

```kotlin
//普通包正文使用brotli压缩,解压为一个带头部的协议0普通包
3 -> {
    val decompress = bytePack.readByteArray().brotliDecompress()
    result += handleLiveEventBodyDecompress(decompress)
}
```
Add import `import dev.aaa1115910.biliapi.http.util.brotliDecompress`.

**Step 4: Extend the smoke test** to assert a real room emits at least one `DanmakuEvent`:

```kotlin
@Test
fun `websocket emits danmaku events`() {
    Assertions.assertDoesNotThrow {
        runBlocking {
            var received = false
            withTimeout(15_000) {
                LiveDataWebSocket.connectLiveEvent(roomId = 6) { event ->
                    if (event is DanmakuEvent) received = true
                }
                // connectLiveEvent suspends until cancelled; poll briefly
                delay(12_000)
            }
            Assertions.assertTrue(received) { "expected at least one DANMU_MSG in 12s" }
        }
    }
}
```
(add imports `kotlinx.coroutines.withTimeout`, `dev.aaa1115910.biliapi.http.entity.live.DanmakuEvent`)

**Step 5: Run the test**

Run: `./gradlew :bili-api:test --tests "dev.aaa1115910.biliapi.websocket.LiveDataWebSocketTest"`
Expected: PASS (needs network; flaky in CI — acceptable).

**Step 6: Commit**

```bash
git add bili-api/build.gradle.kts \
        bili-api/src/main/kotlin/dev/aaa1115910/biliapi/http/util/BrotliDecompress.kt \
        bili-api/src/main/kotlin/dev/aaa1115910/biliapi/websocket/LiveDataWebSocket.kt \
        bili-api/src/test/kotlin/dev/aaa1115910/biliapi/websocket/LiveDataWebSocketTest.kt
git commit -m "feat(live-api): implement brotli (protover 3) in LiveDataWebSocket"
```

---

# Phase 2 — Discovery UI (TV)

Browsable area directory; taps do nothing yet (or a toast). Independently verifiable.

## Task 2.1: Add `DrawerItem.Live` + wire it in `MainScreen`

**Files:**
- Modify: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt` (enum ~L195-208 + buildList ~L146-156)
- Modify: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` (focusRequesters ~L81-111 + `when(screen)` ~L152-176)

**Step 1: Add the enum entry** in `DrawerContent.kt` (e.g. after `Following`):

```kotlin
Live(displayName = "直播", displayIcon = Icons.Default.LiveTv),
```
(add import `androidx.compose.material.icons.filled.LiveTv`; if `LiveTv` isn't in the bundled set, use `Icons.Default.Tv` or `Icons.Default.PlayCircle`.)

**Step 2: Add to the `buildList`** (after `add(DrawerItem.Following)`):

```kotlin
add(DrawerItem.Live)
```

**Step 3: Add a focus requester** in `MainScreen.kt` near the other `remember { FocusRequester() }` lines:

```kotlin
val liveFocusRequester = remember { FocusRequester() }
```
And in the `onFocusToContent` `when`:
```kotlin
DrawerItem.Live -> liveFocusRequester.requestFocus()
```

**Step 4: Add the screen branch** in the `AnimatedContent` `when (screen)`:

```kotlin
DrawerItem.Live -> LiveContent(navFocusRequester = liveFocusRequester)
```

**Step 5: Build** (will fail until `LiveContent` exists in 2.3 — create a stub now so it compiles):

Temporarily in `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveContent.kt`:
```kotlin
package dev.aaa1115910.bv.tv.screens.main
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
@Composable
fun LiveContent(navFocusRequester: FocusRequester) {
    Box { Text("直播 (coming soon)") }
}
```

Run: `./gradlew :app:compileLiteTvDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt \
        app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/MainScreen.kt \
        app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveContent.kt
git commit -m "feat(live-tv): add 直播 drawer item and route to LiveContent"
```

---

## Task 2.2: `LiveViewModel`

**Files:**
- Create: `app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/live/LiveViewModel.kt`

**Step 1: Create the ViewModel** (mirrors `PopularViewModel`):

```kotlin
package dev.aaa1115910.bv.viewmodel.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.tv.material3.DrawerValue
import dev.aaa1115910.biliapi.entity.live.LiveRoomItem
import dev.aaa1115910.biliapi.http.entity.live.LiveArea
import dev.aaa1115910.biliapi.http.entity.live.LiveParentArea
import dev.aaa1115910.biliapi.repositories.LiveAreaHome
import dev.aaa1115910.biliapi.repositories.LiveAreaRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.addAllWithMainContext
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

/** Selected area. null = 热门推荐 (recommended rooms). */
data class LiveAreaSelection(val parentArea: LiveParentArea, val area: LiveArea)

@KoinViewModel
class LiveViewModel(
    private val liveAreaRepository: LiveAreaRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    val parentAreas = mutableStateListOf<LiveParentArea>()
    val rooms = mutableStateListOf<LiveRoomItem>()
    var selectedArea by mutableStateOf<LiveAreaSelection?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set

    private var nextPage = 1
    private var noMore = false

    init {
        logger.fInfo { "init LiveViewModel" }
    }

    suspend fun loadHome() {
        loading = true
        runCatching {
            val home: LiveAreaHome = liveAreaRepository.getAreaHome()
            parentAreas.clearWithMain()
            parentAreas.addAllWithMainContext(home.areas)
            selectedArea = null
            rooms.clear()
            nextPage = 1
            noMore = false
            rooms.addAllWithMainContext(home.recommendedRooms)
        }.onFailure {
            logger.fError { "Load live home failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载直播间失败: ${it.localizedMessage}".toast(BVApp.context)
            }
        }
        loading = false
    }

    suspend fun selectArea(parentArea: LiveParentArea, area: LiveArea) {
        selectedArea = LiveAreaSelection(parentArea, area)
        rooms.clear()
        nextPage = 1
        noMore = false
        loadMore()
    }

    suspend fun selectRecommended() {
        selectedArea = null
        refreshing = true
        loadHome()
    }

    suspend fun loadMore() {
        if (loading || noMore) return
        val selection = selectedArea
        loading = true
        runCatching {
            if (selection == null) {
                // recommended list is not paged in v1; just re-fetch home
                val home = liveAreaRepository.getAreaHome()
                rooms.clear()
                rooms.addAllWithMainContext(home.recommendedRooms)
                noMore = true
            } else {
                val page = liveAreaRepository.getRooms(
                    parentAreaId = selection.parentArea.id,
                    areaId = selection.area.id,
                    page = nextPage
                )
                if (page.list.isNotEmpty()) {
                    nextPage = page.nextPage
                    rooms.addAllWithMainContext(page.list)
                }
                noMore = page.noMore
            }
        }.onFailure {
            logger.fError { "Load more live rooms failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载直播间失败: ${it.localizedMessage}".toast(BVApp.context)
            }
        }
        loading = false
    }

    private suspend fun <T> MutableList<T>.clearWithMain() =
        withContext(Dispatchers.Main) { clear() }
}
```
(Remove the unused `DrawerValue` import if the linter flags it.)

**Step 2: Build**

Run: `./gradlew :app:compileLiteTvDebugKotlin`
Expected: BUILD SUCCESSFUL (Koin discovers the `@KoinViewModel` automatically).

**Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/live/LiveViewModel.kt
git commit -m "feat(live-tv): add LiveViewModel for area directory paging"
```

---

## Task 2.3: `LiveContent` screen — area rail + room grid + `LiveRoomCard`

**Files:**
- Replace stub: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveContent.kt`
- Create: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveRoomCard.kt`

**Step 1: Create `LiveRoomCard`** (cover 16:9, title, host, online count, ●LIVE dot, area badge; D-pad focusable). Reuse the project's image-loading util (`dev.aaa1115910.bv.util.PreviewImageUrl`/coil — check how `SmallVideoCard`/other TV cards load covers and mirror it):

```kotlin
package dev.aaa1115910.bv.tv.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage  // confirm the import the project uses
import dev.aaa1115910.biliapi.entity.live.LiveRoomItem

@Composable
fun LiveRoomCard(
    room: LiveRoomItem,
    modifier: Modifier = Modifier,
    onClick: (LiveRoomItem) -> Unit
) {
    // TODO(match existing TV card styling): clickable + onFocus D-pad scale.
    Surface(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp)),
        onClick = { onClick(room) }
    ) {
        Box {
            AsyncImage(model = room.cover, contentDescription = room.title,
                modifier = Modifier.fillMaxWidth())
            // ●LIVE badge + online count overlay (bottom-left), title/host below
            // Format online count human-readably (e.g. 1.2万). Reuse the project's
            // count-format util if one exists (search "fun.*count" in app util).
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, null, tint = Color.Red, modifier = Modifier.size(12.dp))
                    Text(" ${room.online}", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Column(Modifier.padding(8.dp)) {
            Text(room.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(room.uname, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
            Text(room.areaName, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
        }
    }
}
```
> Match the actual card component used elsewhere on TV (look at `FollowingContent.kt` / the UGC grid) for focus handling, scaling, and image loading — adapt the above to that style rather than introducing a new one.

**Step 2: Implement `LiveContent`** — left area rail + `LazyVerticalGrid`, mirroring `PopularPage`'s grid + `OnBottomReached`:

```kotlin
package dev.aaa1115910.bv.tv.screens.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import dev.aaa1115910.bv.viewmodel.live.LiveViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun LiveContent(
    navFocusRequester: FocusRequester,
    liveViewModel: LiveViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        if (liveViewModel.parentAreas.isEmpty()) liveViewModel.loadHome()
    }

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Left rail: 热门推荐 + parent areas -> sub-areas
        LazyColumn(modifier = Modifier.width(160.dp).padding(end = 16.dp)) {
            item {
                NavigationDrawerItem(
                    selected = liveViewModel.selectedArea == null,
                    onClick = { /* selectRecommended() — see note */ },
                    leadingContent = null
                ) { Text("热门推荐") }
            }
            items(liveViewModel.parentAreas) { parent ->
                parent.list.forEach { area ->
                    NavigationDrawerItem(
                        selected = false,  // compare to selectedArea
                        onClick = { /* liveViewModel.selectArea(parent, area) */ }
                    ) { Text(area.name) }
                }
            }
        }
        // Right grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(liveViewModel.rooms) { room ->
                LiveRoomCard(room, onClick = { /* Phase 3: open LivePlayerActivity */ })
            }
            // TODO: wire OnBottomReached -> liveViewModel.loadMore() (mirror PopularPage)
        }
    }
}
```
> The `onClick` handlers and `OnBottomReached` are intentionally shown as TODOs/skeletons — fill them by copying the exact calls (`liveViewModel.selectArea(...)`, `liveViewModel.selectRecommended()`, `liveViewModel.loadMore()`) and the project's `OnBottomReached` composable from `PopularPage.kt`. Match `PopularPage`'s grid spacing, focus, and pull-to-refresh.

**Step 3: Build + run on TV**

Run: `./gradlew :app:assembleLiteTvDebug`
Manual: install on a TV/emulator, open the 直播 drawer item. Confirm: area rail renders, recommended rooms load and show covers/titles, D-pad navigates areas.

**Step 4: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveContent.kt \
        app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveRoomCard.kt
git commit -m "feat(live-tv): add area directory screen and live room card"
```

---

# Phase 3 — Playback

Tap a room → watch the live stream (no chat yet). "Watchable" milestone.

## Task 3.1: `playLiveUrl` on the engine (HLS, VOD path untouched)

**Files:**
- Modify: `player/core/src/main/kotlin/dev/aaa1115910/bv/player/AbstractVideoPlayer.kt`
- Modify: `player/core/src/main/kotlin/dev/aaa1115910/bv/player/impl/exo/ExoMediaPlayer.kt`

**Step 1: Add the open method** to `AbstractVideoPlayer` (after `playUrl`):

```kotlin
/** 设置直播播放地址（HLS）。默认空实现，由支持直播的后端覆写。 */
open fun playLiveUrl(videoUrl: String) {}
```

**Step 2: Override in `ExoMediaPlayer`** (add import `androidx.media3.exoplayer.source.HlsMediaSource`):

```kotlin
@OptIn(UnstableApi::class)
override fun playLiveUrl(videoUrl: String) {
    malformedRetryCount = 0
    mMediaSource = HlsMediaSource.Factory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)
        .createMediaSource(MediaItem.fromUri(videoUrl))
}
```

**Step 3: Build**

Run: `./gradlew :player:core:compileKotlin`
Expected: BUILD SUCCESSFUL (VOD `playUrl` is unchanged — no regression).

**Step 4: Commit**

```bash
git add player/core/src/main/kotlin/dev/aaa1115910/bv/player/AbstractVideoPlayer.kt \
        player/core/src/main/kotlin/dev/aaa1115910/bv/player/impl/exo/ExoMediaPlayer.kt
git commit -m "feat(player): add playLiveUrl (HlsMediaSource) to ExoMediaPlayer"
```

---

## Task 3.2: `LivePlayerViewModel`

**Files:**
- Create: `app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/live/LivePlayerViewModel.kt`

**Step 1: Create the ViewModel** (loads playurl → `playLiveUrl`; chat wired in Phase 4):

```kotlin
package dev.aaa1115910.bv.viewmodel.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.repositories.LiveRoomRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class LivePlayerViewModel(
    private val liveRoomRepository: LiveRoomRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    var videoPlayer: AbstractVideoPlayer? = null
    var roomId by mutableStateOf(0)
    var title by mutableStateOf("")
    var uname by mutableStateOf("")

    var loadState by mutableStateOf(LiveLoadState.Loading)
        private set

    suspend fun load(roomId: Int, title: String, uname: String) {
        this.roomId = roomId
        this.title = title
        this.uname = uname
        loadState = LiveLoadState.Loading
        runCatching {
            val status = liveRoomRepository.getLiveStatus(roomId)
            if (status != 1) {
                loadState = LiveLoadState.NotLive
                return@runCatching
            }
            val resolved = liveRoomRepository.getPlayUrl(roomId)
            val url = resolved?.url
            if (url.isNullOrBlank()) {
                loadState = LiveLoadState.Unplayable
                return@runCatching
            }
            logger.fInfo { "Play live room $roomId -> $url" }
            videoPlayer?.playLiveUrl(url)
            videoPlayer?.prepare()
            videoPlayer?.start()
            loadState = LiveLoadState.Playing
        }.onFailure {
            logger.fError { "Load live playurl failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载直播失败: ${it.localizedMessage}".toast(BVApp.context)
            }
            loadState = LiveLoadState.Unplayable
        }
    }

    override fun onCleared() {
        super.onCleared()
        videoPlayer?.release()
    }
}

enum class LiveLoadState { Loading, Playing, NotLive, Unplayable }
```

**Step 2: Build**

Run: `./gradlew :app:compileLiteTvDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/live/LivePlayerViewModel.kt
git commit -m "feat(live-tv): add LivePlayerViewModel (load playurl -> playLiveUrl)"
```

---

## Task 3.3: `LivePlayerActivity` + `actionStart`

**Files:**
- Create: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/activities/live/LivePlayerActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (register the activity)

**Step 1: Create the activity** (mirrors `VideoPlayerV3Activity` construction):

```kotlin
package dev.aaa1115910.bv.tv.activities.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.PlayerType
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.impl.exo.ExoPlayerFactory
import dev.aaa1115910.bv.tv.screens.LivePlayerScreen
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.viewmodel.live.LivePlayerViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class LivePlayerActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context,
            roomId: Int,
            title: String,
            uname: String
        ) {
            context.startActivity(
                Intent(context, LivePlayerActivity::class.java).apply {
                    putExtra("roomId", roomId)
                    putExtra("title", title)
                    putExtra("uname", uname)
                }
            )
        }
    }

    private val viewModel: LivePlayerViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPlayer()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BVTheme(forceDark = true) {
                LivePlayerScreen(viewModel = viewModel)
            }
        }
        val roomId = intent.getIntExtra("roomId", 0)
        val title = intent.getStringExtra("title") ?: ""
        val uname = intent.getStringExtra("uname") ?: ""
        // load is launched from the screen's LaunchedEffect (needs a coroutine scope),
        // or via lifecycleScope here:
        lifecycleScope.launch {
            viewModel.load(roomId = roomId, title = title, uname = uname)
        }
    }

    private fun initPlayer() {
        val options = VideoPlayerOptions(
            userAgent = getString(R.string.video_player_user_agent_http),
            referer = getString(R.string.video_player_referer),
            enableFfmpegAudioRenderer = Prefs.enableFfmpegAudioRenderer
        )
        viewModel.videoPlayer = when (Prefs.playerType) {
            PlayerType.Media3 -> ExoPlayerFactory().create(this, options)
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.videoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```
(add import `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.launch`)

**Step 2: Register in `AndroidManifest.xml`** — add inside `<application>` (mirror the existing `VideoPlayerV3Activity` entry, including `android:screenOrientation` and theme if used):

```xml
<activity
    android:name=".tv.activities.live.LivePlayerActivity"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboardHidden"
    android:screenOrientation="landscape"
    android:theme="@style/Theme.BV" />
```
(Copy the exact attributes from the existing `VideoPlayerV3Activity` `<activity>` block.)

**Step 3: Build** (will fail until `LivePlayerScreen` exists — Task 3.4 creates it; or add a stub now).

Run: `./gradlew :app:assembleLiteTvDebug`
Expected: BUILD SUCCESSFUL after 3.4.

**Step 4: Commit** (after 3.4 builds):

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/activities/live/LivePlayerActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat(live-tv): add LivePlayerActivity"
```

---

## Task 3.4: `LivePlayerScreen` + `BvLivePlayer` (minimal controller)

**Files:**
- Create: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/LivePlayerScreen.kt`

**Step 1: Create the screen** — video surface (reuse `BvVideoPlayer`) + `AkDanmakuPlayer` overlay + a minimal controller. Skeleton (adapt to the project's `BvVideoPlayer`/focus idioms):

```kotlin
package dev.aaa1115910.bv.tv.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import dev.aaa1115910.bv.player.AkDanmakuPlayer
import dev.aaa1115910.bv.player.BvVideoPlayer
import dev.aaa1115910.bv.viewmodel.live.LiveLoadState
import dev.aaa1115910.bv.viewmodel.live.LivePlayerViewModel

@Composable
fun LivePlayerScreen(viewModel: LivePlayerViewModel) {
    LaunchedEffect(viewModel) {
        // danmaku wiring happens in Phase 4 (Task 4.1)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        viewModel.videoPlayer?.let { player ->
            BvVideoPlayer(
                modifier = Modifier.fillMaxSize(),
                videoPlayer = player,
                playerListener = null  // attach a simple listener for buffering/error states
            )
        }
        // AkDanmakuPlayer overlay added in Phase 4

        when (viewModel.loadState) {
            LiveLoadState.Loading -> Text("加载中…", modifier = Modifier.align(Alignment.Center))
            LiveLoadState.NotLive -> Text("未开播", modifier = Modifier.align(Alignment.Center))
            LiveLoadState.Unplayable -> Text("无法播放该直播间", modifier = Modifier.align(Alignment.Center))
            LiveLoadState.Playing -> {
                // ● LIVE badge (top-left) + title/host; minimal controls (play/pause, exit,
                // danmaku toggle) on BACK/menu — mirror the controller chrome style of the
                // TV player but WITHOUT seekbar/history/next-video.
            }
        }
    }
}
```
> `BvVideoPlayer` is the surface composable used inside `BvPlayer.kt` (`player/tv`). Confirm its exact signature from that file and a `VideoPlayerListener` for buffering/error (`onBuffering`/`onError`) to surface states. Keep the controller intentionally minimal — do NOT reuse VOD's `VideoPlayerController`.

**Step 2: Build + run**

Run: `./gradlew :app:assembleLiteTvDebug`
Manual: (after 3.5) tap a live room → stream plays landscape, no seekbar.

**Step 3: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/LivePlayerScreen.kt
git commit -m "feat(live-tv): add LivePlayerScreen with minimal live controls"
```

---

## Task 3.5: Wire `LiveContent` tap → `LivePlayerActivity`

**Files:**
- Modify: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveContent.kt`

**Step 1: Connect the card onClick**

```kotlin
// in LiveContent, obtain context:
val context = androidx.compose.ui.platform.LocalContext.current
// ...
LiveRoomCard(room, onClick = { roomItem ->
    dev.aaa1115910.bv.tv.activities.live.LivePlayerActivity.actionStart(
        context = context,
        roomId = roomItem.roomId,
        title = roomItem.title,
        uname = roomItem.uname
    )
})
```

**Step 2: Build + run end-to-end**

Run: `./gradlew :app:assembleLiteTvDebug`
Manual: open 直播 → pick a room → stream plays. This is the **watchable milestone**.

**Step 3: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/LiveContent.kt
git commit -m "feat(live-tv): open LivePlayerActivity on room tap"
```

---

# Phase 4 — Live chat (danmaku)

Feature complete: WebSocket `DANMU_MSG` → `AkDanmakuPlayer`.

## Task 4.1: Feed live danmaku into `DanmakuPlayer`

**Files:**
- Modify: `app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/live/LivePlayerViewModel.kt`

**Step 1: Add a `DanmakuPlayer` + the WebSocket collection** to `LivePlayerViewModel`:

```kotlin
// new imports
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import dev.aaa1115910.biliapi.http.entity.live.DanmakuEvent
import dev.aaa1115910.biliapi.websocket.LiveDataWebSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// in the class:
var danmakuPlayer: DanmakuPlayer? = null  // created/bound in the screen (AkDanmakuPlayer)
private var danmakuJob: Job? = null
private val _danmakuConnected = MutableStateFlow(false)
val danmakuConnected = _danmakuConnected.asStateFlow()

fun startDanmaku(roomId: Int, scope: CoroutineScope) {
    danmakuJob?.cancel()
    danmakuJob = scope.launch(Dispatchers.IO) {
        runCatching {
            _danmakuConnected.value = false
            LiveDataWebSocket.connectLiveEvent(roomId) { event ->
                if (event is DanmakuEvent) sendDanmaku(event)
            }
        }.onFailure {
            logger.fError { "Live danmaku connect failed: ${it.stackTraceToString()}" }
        }
        _danmakuConnected.value = true  // best-effort; real liveness surfaced via events
    }
}

private fun sendDanmaku(event: DanmakuEvent) {
    val dp = danmakuPlayer ?: return
    val data = DanmakuItemData(
        id = (event.mid and 0xFFFFFFFFL).toInt(),  // or a counter; see akdanmaku samples
        content = event.content,
        mode = DanmakuItemData.DANMAKU_MODE_ROLLING,
        textSize = 25,
        color = 0xFFFFFFFF.toInt(),
        time = 0  // wall-clock: akdanmaku renders live items on send(); confirm API
    )
    // akdanmaku live feed: call the method the library exposes for ad-hoc items
    // (e.g. dp.send(data) / dp.addData(listOf(data))) — confirm against akdanmaku version used.
}
```
> The exact `DanmakuPlayer` API for pushing a live item varies by akdanmaku version. Inspect `com.kuaishou.akdanmaku.ui.DanmakuPlayer` in the project's dependency and use its public send/add method. If wall-clock rendering isn't supported, fall back to assigning incremental `time` from a monotonic clock.

**Step 2: Add cleanup** to `onCleared()`:

```kotlin
override fun onCleared() {
    super.onCleared()
    danmakuJob?.cancel()
    videoPlayer?.release()
    danmakuPlayer?.release()
}
```

**Step 3: Build**

Run: `./gradlew :app:compileLiteTvDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/live/LivePlayerViewModel.kt
git commit -m "feat(live-tv): feed WebSocket DANMU_MSG into DanmakuPlayer"
```

---

## Task 4.2: Render danmaku overlay + toggle/error states

**Files:**
- Modify: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/LivePlayerScreen.kt`

**Step 1: Add the `AkDanmakuPlayer` overlay + start danmaku**

```kotlin
// in LivePlayerScreen:
val context = LocalContext.current
LaunchedEffect(viewModel.videoPlayer) {
    // create a DanmakuPlayer (mirror how BvPlayer creates mDanmakuPlayer),
    // assign viewModel.danmakuPlayer = it, bind to the AkDanmakuPlayer view.
    viewModel.startDanmaku(viewModel.roomId, this)
}

// inside the Box, above the surface:
AkDanmakuPlayer(
    modifier = Modifier.fillMaxSize(),
    danmakuPlayer = viewModel.danmakuPlayer
)
```
Mirror `BvPlayer.kt`'s `DanmakuPlayer` construction (the `mDanmakuPlayer`/`DanmakuView` bind flow at `BvPlayer.kt:119,340-343`) — reuse that exact setup so the renderer behaves consistently.

**Step 2: Add a danmaku on/off + a "弹幕连接失败" hint** in the controller (collect `viewModel.danmakuConnected`; show subtle text when `false`).

**Step 3: Build + run end-to-end**

Run: `./gradlew :app:assembleLiteTvDebug`
Manual: open a popular live room → video plays **and** scrolling danmaku appears within a few seconds; toggling danmaku hides/shows it. **This is the complete feature.**

**Step 4: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/LivePlayerScreen.kt
git commit -m "feat(live-tv): render live danmaku overlay with toggle"
```

---

# Wrap-up

- Run the full bili-api test suite: `./gradlew :bili-api:test` (resolver test must pass offline; network smoke tests pass when online).
- Build the TV app: `./gradlew :app:assembleLiteTvDebug`.
- Manual smoke on a TV/Android-TV emulator: open 直播 → browse areas → tap a live room → watch with danmaku.
- Consider opening a PR from `develop-lite` → `develop` once Phase 4 is verified. See @superpowers:finishing-a-development-branch.

## Risks tracked inline (resolve if hit)
- **Endpoint field-name drift** → Task 1.1 probe confirms before mappers; adjust DTOs in 1.2.
- **akdanmaku live-feed API** → Task 4.1: inspect `com.kuaishou.akdanmaku.ui.DanmakuPlayer` for the push method; use a monotonic `time` fallback.
- **bilibili serves only flv in some areas** → resolver returns `null` → "无法播放" state (honest v1); flv support is a future enhancement.
- **HLS headers (referer/UA)** → `ExoMediaPlayer.okHttpFactory` already sets the configured UA + referer via `VideoPlayerOptions`; pass `video_player_referer` like the VOD player (Task 3.3 does).
