package dev.aaa1115910.bv.viewmodel.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
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
