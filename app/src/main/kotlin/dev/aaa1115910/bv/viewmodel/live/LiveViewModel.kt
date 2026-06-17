package dev.aaa1115910.bv.viewmodel.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.entity.live.LiveRoomItem
import dev.aaa1115910.biliapi.http.entity.live.LiveArea
import dev.aaa1115910.biliapi.http.entity.live.LiveParentArea
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

/** 选中的分区。loadHome 成功后会自动选中第一个子分区。 */
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

    /** 加载分区目录，并自动选中第一个子分区以立即展示直播间。 */
    suspend fun loadHome() {
        loading = true
        runCatching {
            val areas = liveAreaRepository.getAreas()
            parentAreas.clearWithMain()
            parentAreas.addAllWithMainContext(areas)
            val firstParent = areas.firstOrNull { it.list.isNotEmpty() }
            val firstArea = firstParent?.list?.firstOrNull()
            if (firstParent != null && firstArea != null) {
                selectArea(firstParent, firstArea)
            }
        }.onFailure {
            logger.fError { "Load live areas failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载直播间失败: ${it.localizedMessage}".toast(BVApp.context)
            }
        }
        loading = false
        refreshing = false
    }

    suspend fun selectArea(parentArea: LiveParentArea, area: LiveArea) {
        selectedArea = LiveAreaSelection(parentArea, area)
        rooms.clearWithMain()
        nextPage = 1
        noMore = false
        loadMoreInternal()
    }

    suspend fun loadMore() {
        if (loading || noMore) return
        loadMoreInternal()
    }

    private suspend fun loadMoreInternal() {
        val startSelection = selectedArea ?: return
        loading = true
        runCatching {
            val page = liveAreaRepository.getRooms(
                parentAreaId = startSelection.area.parentId,
                areaId = startSelection.area.id,
                page = nextPage
            )
            // 切换分区时丢弃过期请求的结果。
            if (selectedArea != startSelection) return@runCatching
            if (page.list.isNotEmpty()) {
                nextPage = page.nextPage
                rooms.addAllWithMainContext(page.list)
            }
            noMore = page.noMore
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
