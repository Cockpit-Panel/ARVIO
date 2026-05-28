@file:Suppress("UnsafeOptInUsageError")

package com.arflix.tv.ui.screens.tv.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import kotlinx.coroutines.delay

internal enum class FullscreenGuideTab(val label: String) {
    Past("Past 48h"),
    Now("Now"),
    Later("Later");

    fun next(): FullscreenGuideTab = entries[(ordinal + 1) % entries.size]
    fun previous(): FullscreenGuideTab = entries[(ordinal + entries.size - 1) % entries.size]
}

private enum class GuideProgramState {
    PastPlayable,
    Live,
    Future,
}

private data class GuideProgramItem(
    val program: IptvProgram,
    val state: GuideProgramState,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun FullscreenGuideOverlay(
    visible: Boolean,
    channel: EnrichedChannel?,
    guide: IptvNowNext?,
    selectedProgram: IptvProgram?,
    selectedTab: FullscreenGuideTab,
    isTouchDevice: Boolean,
    onSelectedTabChange: (FullscreenGuideTab) -> Unit,
    onDismiss: () -> Unit,
    onProgramSelect: (IptvProgram?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channel == null) return

    BackHandler(enabled = visible, onBack = onDismiss)

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val catchupSupported = remember(channel) { channel.supportsFullscreenCatchup() }
    val pastWindowStart = nowMillis - 48L * 60L * 60_000L
    val past = remember(guide, nowMillis, catchupSupported) {
        if (!catchupSupported) {
            emptyList()
        } else {
            guide?.recent.orEmpty()
                .asSequence()
                .filter { it.endUtcMillis <= nowMillis && it.endUtcMillis >= pastWindowStart }
                .distinctBy { "${it.startUtcMillis}:${it.endUtcMillis}:${it.title}" }
                .sortedByDescending { it.startUtcMillis }
                .map { GuideProgramItem(it, GuideProgramState.PastPlayable) }
                .toList()
        }
    }
    val live = remember(guide, nowMillis) {
        guide?.now
            ?.takeIf { it.isLive(nowMillis) }
            ?.let { listOf(GuideProgramItem(it, GuideProgramState.Live)) }
            .orEmpty()
    }
    val future = remember(guide, nowMillis) {
        buildList {
            guide?.next?.let { add(it) }
            guide?.later?.let { add(it) }
            addAll(guide?.upcoming.orEmpty())
        }
            .asSequence()
            .filter { it.startUtcMillis > nowMillis }
            .distinctBy { "${it.startUtcMillis}:${it.endUtcMillis}:${it.title}" }
            .sortedBy { it.startUtcMillis }
            .map { GuideProgramItem(it, GuideProgramState.Future) }
            .toList()
    }
    val items = when (selectedTab) {
        FullscreenGuideTab.Past -> past
        FullscreenGuideTab.Now -> live
        FullscreenGuideTab.Later -> future
    }

    val enter = if (isTouchDevice) {
        slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180))
    } else {
        slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180))
    }
    val exit = if (isTouchDevice) {
        slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160))
    } else {
        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160))
    }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Box(
                modifier = Modifier
                    .align(if (isTouchDevice) Alignment.BottomCenter else Alignment.CenterEnd)
                    .then(
                        if (isTouchDevice) {
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.74f)
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        } else {
                            Modifier
                                .width(470.dp)
                                .fillMaxHeight()
                                .padding(top = 24.dp, end = 24.dp, bottom = 24.dp)
                        }
                    )
                    .clip(RoundedCornerShape(if (isTouchDevice) 22.dp else 18.dp))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(if (isTouchDevice) 22.dp else 18.dp)
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xF2191B22),
                            1f to Color(0xF0090A0E),
                        )
                    )
                    .padding(18.dp),
            ) {
                FullscreenGuideContent(
                    channel = channel,
                    selectedProgram = selectedProgram,
                    selectedTab = selectedTab,
                    pastCount = past.size,
                    liveCount = live.size,
                    futureCount = future.size,
                    items = items,
                    catchupSupported = catchupSupported,
                    nowMillis = nowMillis,
                    isTouchDevice = isTouchDevice,
                    onSelectedTabChange = onSelectedTabChange,
                    onDismiss = onDismiss,
                    onProgramSelect = onProgramSelect,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FullscreenGuideContent(
    channel: EnrichedChannel,
    selectedProgram: IptvProgram?,
    selectedTab: FullscreenGuideTab,
    pastCount: Int,
    liveCount: Int,
    futureCount: Int,
    items: List<GuideProgramItem>,
    catchupSupported: Boolean,
    nowMillis: Long,
    isTouchDevice: Boolean,
    onSelectedTabChange: (FullscreenGuideTab) -> Unit,
    onDismiss: () -> Unit,
    onProgramSelect: (IptvProgram?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isTouchDevice) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close guide",
                        tint = LiveColors.Fg,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            ChannelLogo(channel = channel, size = if (isTouchDevice) 42.dp else 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = LiveType.ChannelName.copy(
                        color = LiveColors.Fg,
                        fontSize = if (isTouchDevice) 18.sp else 19.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuideChip("CH ${channel.number}", LiveColors.FgDim, Color.White.copy(alpha = 0.08f))
                    GuideChip(channel.quality.label, LiveColors.FgDim, Color.White.copy(alpha = 0.08f))
                    if (catchupSupported) {
                        GuideChip("Catchup", LiveColors.Bg, LiveColors.Accent)
                    }
                }
            }
        }

        Text(
            text = "Browse the last 48 hours and upcoming guide without leaving playback.",
            style = LiveType.BodySynopsis.copy(
                color = LiveColors.FgDim,
                fontSize = if (isTouchDevice) 12.sp else 11.sp,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideTabButton(
                label = "Past",
                count = pastCount,
                selected = selectedTab == FullscreenGuideTab.Past,
                enabled = catchupSupported,
                onClick = { onSelectedTabChange(FullscreenGuideTab.Past) },
            )
            GuideTabButton(
                label = "Now",
                count = liveCount,
                selected = selectedTab == FullscreenGuideTab.Now,
                enabled = true,
                onClick = { onSelectedTabChange(FullscreenGuideTab.Now) },
            )
            GuideTabButton(
                label = "Later",
                count = futureCount,
                selected = selectedTab == FullscreenGuideTab.Later,
                enabled = true,
                onClick = { onSelectedTabChange(FullscreenGuideTab.Later) },
            )
        }

        GuideLiveButton(
            isSelected = selectedProgram == null,
            onClick = { onProgramSelect(null) },
        )

        val listState = rememberLazyListState()
        val firstFocusRequester = remember { FocusRequester() }
        LaunchedEffect(selectedTab, items.size) {
            if (items.isNotEmpty()) {
                listState.scrollToItem(0)
                delay(90)
                runCatching { firstFocusRequester.requestFocus() }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    GuideEmptyState(
                        selectedTab = selectedTab,
                        catchupSupported = catchupSupported,
                    )
                }
            } else {
                itemsIndexed(
                    items = items,
                    key = { _, item -> "${item.state}:${item.program.startUtcMillis}:${item.program.title}" },
                ) { index, item ->
                    GuideProgramRow(
                        item = item,
                        selected = item.program == selectedProgram,
                        nowMillis = nowMillis,
                        focusRequester = if (index == 0) firstFocusRequester else null,
                        onClick = {
                            when (item.state) {
                                GuideProgramState.PastPlayable -> onProgramSelect(item.program)
                                GuideProgramState.Live -> onProgramSelect(null)
                                GuideProgramState.Future -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideLiveButton(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || isSelected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) LiveColors.Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(
                BorderStroke(1.dp, if (active) LiveColors.Accent else Color.White.copy(alpha = 0.08f)),
                RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.hasFocus }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionCenter || ev.key == Key.Enter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = LiveColors.Accent,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Go Live",
                style = LiveType.CellTitle.copy(color = LiveColors.Fg, fontSize = 14.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Return to the live broadcast",
                style = LiveType.BodySynopsis.copy(color = LiveColors.FgDim, fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GuideChip("LIVE", Color.White, LiveColors.LiveRed)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideProgramRow(
    item: GuideProgramItem,
    selected: Boolean,
    nowMillis: Long,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val playable = item.state != GuideProgramState.Future
    val active = focused || selected
    val bg = when {
        active -> LiveColors.PanelRaised.copy(alpha = 0.94f)
        item.state == GuideProgramState.Future -> Color.White.copy(alpha = 0.045f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    val border = when {
        selected -> LiveColors.Accent
        focused -> LiveColors.Fg.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.hasFocus }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionCenter || ev.key == Key.Enter)) {
                    if (playable) onClick()
                    true
                } else {
                    false
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
            .clickable(enabled = playable, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when (item.state) {
                        GuideProgramState.PastPlayable -> LiveColors.Accent.copy(alpha = 0.18f)
                        GuideProgramState.Live -> LiveColors.LiveRed.copy(alpha = 0.22f)
                        GuideProgramState.Future -> Color.White.copy(alpha = 0.08f)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (item.state) {
                    GuideProgramState.PastPlayable -> Icons.Default.Replay
                    GuideProgramState.Live -> Icons.Default.PlayArrow
                    GuideProgramState.Future -> Icons.Default.Schedule
                },
                contentDescription = null,
                tint = when (item.state) {
                    GuideProgramState.PastPlayable -> LiveColors.Accent
                    GuideProgramState.Live -> LiveColors.LiveRed
                    GuideProgramState.Future -> LiveColors.FgDim
                },
                modifier = Modifier.size(22.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatTimeWindow(item.program),
                    style = LiveType.TimeMono.copy(color = LiveColors.FgDim, fontSize = 10.sp),
                )
                GuideChip(
                    label = when (item.state) {
                        GuideProgramState.PastPlayable -> "Replay"
                        GuideProgramState.Live -> "Live"
                        GuideProgramState.Future -> startsLabel(item.program, nowMillis)
                    },
                    fg = when (item.state) {
                        GuideProgramState.PastPlayable -> LiveColors.Bg
                        GuideProgramState.Live -> Color.White
                        GuideProgramState.Future -> LiveColors.FgDim
                    },
                    bg = when (item.state) {
                        GuideProgramState.PastPlayable -> LiveColors.Accent
                        GuideProgramState.Live -> LiveColors.LiveRed
                        GuideProgramState.Future -> Color.White.copy(alpha = 0.08f)
                    },
                )
            }
            Text(
                text = item.program.title,
                style = LiveType.ProgramTitle.copy(
                    color = if (item.state == GuideProgramState.Future) LiveColors.FgDim else LiveColors.Fg,
                    fontSize = 15.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.program.description.isNullOrBlank()) {
                Text(
                    text = item.program.description!!,
                    style = LiveType.BodySynopsis.copy(color = LiveColors.FgMute, fontSize = 10.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.state == GuideProgramState.Live) {
                progressOf(item.program)?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = LiveColors.Accent,
                        trackColor = LiveColors.Panel,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideEmptyState(
    selectedTab: FullscreenGuideTab,
    catchupSupported: Boolean,
) {
    val text = when {
        selectedTab == FullscreenGuideTab.Past && !catchupSupported ->
            "This channel does not expose catchup playback."
        selectedTab == FullscreenGuideTab.Past ->
            "No playable shows were found in the last 48 hours yet."
        selectedTab == FullscreenGuideTab.Now ->
            "No current programme data is available."
        else ->
            "No upcoming programme data is available yet."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(14.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = LiveType.BodySynopsis.copy(color = LiveColors.FgDim, fontSize = 13.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideTabButton(
    label: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fg = when {
        selected -> LiveColors.Bg
        enabled -> LiveColors.Fg
        else -> LiveColors.FgMute
    }
    val bg = when {
        selected -> LiveColors.Accent
        enabled -> Color.White.copy(alpha = 0.075f)
        else -> Color.White.copy(alpha = 0.035f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = LiveType.Badge.copy(color = fg, fontSize = 11.sp))
        Text(count.toString(), style = LiveType.Badge.copy(color = fg.copy(alpha = 0.74f), fontSize = 10.sp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GuideChip(label: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = LiveType.Badge.copy(color = fg, fontSize = 9.sp), maxLines = 1)
    }
}

private fun EnrichedChannel.supportsFullscreenCatchup(): Boolean {
    val channelSource = this.source
    if (channelSource.catchupDays > 0) return true
    if (!channelSource.catchupType.isNullOrBlank() || !channelSource.catchupSource.isNullOrBlank()) return true
    if (channelSource.xtreamStreamId != null) return true
    return channelSource.streamUrl.contains("/live/", ignoreCase = true)
}

private fun startsLabel(program: IptvProgram, nowMillis: Long): String {
    val minutes = ((program.startUtcMillis - nowMillis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 60 -> "in ${minutes}m"
        minutes < 24 * 60 -> "in ${minutes / 60}h ${minutes % 60}m"
        else -> "later"
    }
}
