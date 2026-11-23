package com.arflix.tv.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Launchers can read only generated artwork, including after ARVIO exits/reboots.
 * No network, account data, arbitrary file paths or writable files are exposed.
 */
class LauncherArtworkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String {
        resolve(uri)
        return "image/jpeg"
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = resolve(uri)
        val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }.toTypedArray()
        return MatrixCursor(columns).apply {
            addRow(columns.map { if (it == OpenableColumns.SIZE) file.length() else "artwork.jpg" })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only artwork")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only artwork")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only artwork")

    private fun resolve(uri: Uri): File {
        val context = requireNotNull(context)
        if (uri.scheme != "content" || uri.authority != authority(context) || uri.pathSegments.size != 1 ||
            uri.query != null || uri.fragment != null || !isArtworkName(uri.lastPathSegment.orEmpty())) {
            throw FileNotFoundException("Unknown artwork")
        }
        return fileFor(context, uri.lastPathSegment!!).also {
            if (!it.isFile) throw FileNotFoundException("Artwork no longer available")
        }
    }

    companion object {
        private val filename = Regex("[a-f0-9]{64}\\.jpg")
        internal fun isArtworkName(name: String) = filename.matches(name)
        internal fun directory(context: Context) = File(context.filesDir, "launcher-artwork")
        internal fun fileFor(context: Context, name: String): File {
            require(isArtworkName(name))
            return File(directory(context), name)
        }
        internal fun authority(context: Context) = "${context.packageName}.launcher-artwork"
        internal fun uriFor(context: Context, name: String): Uri {
            require(isArtworkName(name))
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath(name).build()
        }
    }
}
