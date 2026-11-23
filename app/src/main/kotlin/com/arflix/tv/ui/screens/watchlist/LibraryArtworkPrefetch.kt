package com.arflix.tv.ui.screens.watchlist

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.util.TmdbImageSizing
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal fun libraryArtworkPrefetchRange(lastVisible: Int, count: Int, columns: Int): IntRange {
    val start = (lastVisible + 1).coerceIn(0, count)
    return start until (start + columns * 2).coerceAtMost(count)
}

/** Warm only the next two rows; visible artwork keeps priority over speculative downloads. */
@Composable
internal fun LibraryArtworkPrefetch(
    grid: LazyGridState, items: List<MediaItem>, width: Dp, columns: Int, poster: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
    val heightPx = (widthPx / if (poster) (2f / 3f) else (16f / 9f)).toInt().coerceAtLeast(1)
    LaunchedEffect(grid, items, widthPx, heightPx, columns, poster) {
        val permits = Semaphore(2)
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collectLatest { last ->
            if (last < 0) return@collectLatest
            delay(80)
            coroutineScope {
                libraryArtworkPrefetchRange(last, items.size, columns).map { index ->
                    async {
                        permits.withPermit {
                            val item = items[index]
                            val raw = (if (poster) item.image else item.backdrop ?: item.image)
                                .takeIf(String::isNotBlank) ?: return@withPermit
                            val url = TmdbImageSizing.forSlot(raw, widthPx, heightPx,
                                TmdbImageSizing.cardArtworkKind(raw, item.backdrop, !poster))
                            context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                                .size(widthPx, heightPx).scale(Scale.FILL).precision(Precision.INEXACT)
                                .allowHardware(true).memoryCacheKey("$url|${widthPx}x$heightPx").build())
                        }
                    }
                }.awaitAll()
            }
        }
    }
}
