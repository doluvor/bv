package dev.aaa1115910.bv.tv.screens.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.aaa1115910.biliapi.entity.live.LiveRoomItem
import dev.aaa1115910.bv.util.ImageSize
import dev.aaa1115910.bv.util.resizedImageUrl

/**
 * 直播间卡片：16:9 封面 + 红色“●LIVE”角标 + 标题 + 主播名 + 人气 + 分区。
 * 焦点样式与 [SmallVideoCard] 保持一致（聚焦边框 + 信息上移动画）。
 */
@Composable
fun LiveRoomCard(
    modifier: Modifier = Modifier,
    room: LiveRoomItem,
    onClick: (LiveRoomItem) -> Unit = {},
    onFocus: () -> Unit = {}
) {
    var hasFocus by remember { mutableStateOf(false) }
    val infoOffsetY by animateDpAsState(
        targetValue = if (hasFocus) 8.dp else 0.dp,
        animationSpec = spring(),
        label = "live info offset y"
    )

    Column(modifier = modifier) {
        Card(
            onClick = { onClick(room) },
            colors = CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                pressedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = CardDefaults.shape(shape = MaterialTheme.shapes.large),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(width = 3.dp, color = MaterialTheme.colorScheme.border),
                    shape = MaterialTheme.shapes.large
                )
            )
        ) {
            LiveCover(
                cover = room.cover,
                online = room.online,
                areaName = room.areaName,
                modifier = Modifier.onFocusChanged {
                    hasFocus = it.isFocused
                    if (hasFocus) onFocus()
                }
            )
        }

        LiveInfo(
            modifier = Modifier.offset(y = infoOffsetY),
            title = room.title,
            uname = room.uname
        )
    }
}

@Composable
private fun LiveCover(
    modifier: Modifier = Modifier,
    cover: String,
    online: Int,
    areaName: String
) {
    var width by remember { mutableStateOf(200.dp) }
    val showInfo by remember { derivedStateOf { width > 160.dp } }

    BoxWithConstraints(
        modifier = modifier.clip(MaterialTheme.shapes.large),
        contentAlignment = Alignment.BottomCenter
    ) {
        width = maxWidth
        val shadowAlpha by remember(online, areaName) {
            derivedStateOf { if (showInfo) 0.8f else 0f }
        }

        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large),
            model = cover.takeIf { it.isNotBlank() }?.resizedImageUrl(ImageSize.SmallVideoCardCover),
            contentDescription = null,
            contentScale = ContentScale.FillBounds
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = shadowAlpha)
                        )
                    )
                )
        )

        // 左上：●LIVE 角标
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            shape = RoundedCornerShape(4.dp),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFFFB7299)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        if (showInfo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatLiveOnline(online),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = areaName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LiveInfo(
    modifier: Modifier = Modifier,
    title: String,
    uname: String
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = uname,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 人气值格式化，沿用项目 [VideoCardData] 里的“万”规则：
 * >= 10000 显示 “x万”，否则原样。
 */
private fun formatLiveOnline(online: Int): String =
    if (online >= 10000) "${online / 10000}万" else online.toString()
