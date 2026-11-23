package com.arflix.tv.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AtomicFile
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.TvContractCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.arflix.tv.R
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.provider.LauncherArtworkProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class LauncherArtwork(val uri: Uri?, val aspectRatio: Int)

/** Prepares artwork off the main thread; launchers only read completed local JPEGs. */
@Singleton
class LauncherArtworkRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    internal suspend fun artworkFor(
        item: ContinueWatchingItem,
        profileId: String,
        language: String,
        logoUrl: String?
    ): LauncherArtwork = withContext(Dispatchers.IO) {
        val candidates = launcherArtworkCandidates(item)
        val key = launcherArtworkKey(item, profileId, language, logoUrl)
        val file = LauncherArtworkProvider.fileFor(context, "$key.jpg")
        if (file.isFile && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            return@withContext localArtwork(file)
        }

        try {
            var background: Bitmap? = null
            var loadedSource: String? = null
            for (url in candidates) {
                background = loadBitmap(url, 960, 540)
                if (background != null) {
                    loadedSource = url
                    break
                }
            }
            val logo = logoUrl?.takeIf { it.isNotBlank() }?.let { loadBitmap(it, 500, 120, Scale.FIT) }
            // Keep degraded artwork under a different URI. Retrying the original
            // image/logo next time can then replace it, including in launcher caches.
            val complete = (candidates.isEmpty() || (background != null && loadedSource == candidates.first())) &&
                (logoUrl.isNullOrBlank() || logo != null)
            val output = if (!complete) {
                val fallbackKey = artworkDigest("$key|fallback|${loadedSource.orEmpty()}|${logo != null}")
                LauncherArtworkProvider.fileFor(context, "$fallbackKey.jpg")
            } else file
            if (output.isFile && output.length() > 0L) {
                output.setLastModified(System.currentTimeMillis())
                return@withContext localArtwork(output)
            }
            val font = ResourcesCompat.getFont(context, R.font.inter_variablefont_opsz_wght) ?: Typeface.DEFAULT
            // No working image: still provide a readable title card, without
            // exposing a private home-server image URL to another application.
            val source = background ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(23, 25, 28))
            }
            val bitmap = try { renderLauncherArtwork(source, logo, item.title, font) }
                finally { if (background == null) source.recycle() }
            try {
                output.parentFile?.mkdirs()
                val atomic = AtomicFile(output)
                val stream = atomic.startWrite()
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream))
                    atomic.finishWrite(stream)
                } catch (error: Exception) {
                    atomic.failWrite(stream)
                    throw error
                }
            } finally {
                bitmap.recycle()
            }
            localArtwork(output)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Storage failure must not block the remaining launcher entries or
            // publish authenticated source-image URLs as a fallback.
            LauncherArtwork(null, TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
        }
    }

    internal fun prune(keep: Set<Uri>) {
        val keptNames = keep.mapNotNull { it.lastPathSegment }.toSet()
        LauncherArtworkProvider.directory(context).listFiles().orEmpty()
            .filter { LauncherArtworkProvider.isArtworkName(it.name) }
            .sortedByDescending(File::lastModified)
            .filterNot { it.name in keptNames }
            .drop((96 - keptNames.size).coerceAtLeast(0))
            .forEach { it.delete() }
    }

    internal fun clear() {
        LauncherArtworkProvider.directory(context).listFiles().orEmpty()
            .filter { LauncherArtworkProvider.isArtworkName(it.name) }.forEach { it.delete() }
    }

    private fun localArtwork(file: File) = LauncherArtwork(
        LauncherArtworkProvider.uriFor(context, file.name),
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
    )

    private suspend fun loadBitmap(url: String, width: Int, height: Int, scale: Scale = Scale.FILL): Bitmap? =
        withTimeoutOrNull(3_000L) {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).size(width, height).scale(scale)
                    .allowHardware(false).allowRgb565(false).bitmapConfig(Bitmap.Config.ARGB_8888).build()
            )
            (result as? SuccessResult)?.drawable?.toBitmap()
        }
}

internal fun launcherArtworkCandidates(item: ContinueWatchingItem): List<String> = listOfNotNull(
    item.episodeStillPath.takeIf { item.mediaType == MediaType.TV }, item.backdropPath, item.posterPath
).filter { it.isNotBlank() }.distinct()

internal fun launcherArtworkKey(item: ContinueWatchingItem, profile: String, language: String, logo: String?): String {
    val parts = listOf("launcher-art-v1", profile, language, item.mediaType.name, item.id.toString(),
        item.season.toString(), item.episode.toString(), item.title, logo.orEmpty()) + launcherArtworkCandidates(item)
    // Length-prefix fields, so values containing separators cannot alias another entry.
    val input = parts.joinToString("") { "${it.length}:$it" }
    return artworkDigest(input)
}

private fun artworkDigest(input: String): String = MessageDigest.getInstance("SHA-256")
    .digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Matches Home's landscape artwork: episode still, gentle scrim and bottom-left clearlogo. */
internal fun renderLauncherArtwork(background: Bitmap, logo: Bitmap?, title: String, font: Typeface): Bitmap {
    val width = 960f
    val height = 540f
    val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val scale = maxOf(width / background.width, height / background.height)
    val scaledWidth = background.width * scale
    val scaledHeight = background.height * scale
    canvas.drawBitmap(background, null, RectF((width - scaledWidth) / 2, (height - scaledHeight) / 2,
        (width + scaledWidth) / 2, (height + scaledHeight) / 2), paint)
    paint.shader = LinearGradient(0f, height * .40f, 0f, height,
        intArrayOf(Color.TRANSPARENT, 0xB3000000.toInt()), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width, height, paint)
    paint.shader = null
    val left = width * .035f
    val bottom = height * .90f
    if (logo != null) {
        val fit = minOf(width * .48f / logo.width, height * .16f / logo.height)
        canvas.drawBitmap(logo, null, RectF(left, bottom - logo.height * fit,
            left + logo.width * fit, bottom), paint)
    } else {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * .088f
            typeface = Typeface.create(font, Typeface.BOLD)
            setShadowLayer(3f, 0f, 2f, Color.BLACK)
        }
        val textWidth = (width * .87f).toInt()
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
        canvas.save()
        canvas.translate(left, bottom - layout.height)
        layout.draw(canvas)
        canvas.restore()
    }
    return bitmap
}
