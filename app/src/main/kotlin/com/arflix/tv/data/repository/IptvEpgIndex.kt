package com.arflix.tv.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram

/**
 * Local guide index used by the Live TV page.
 *
 * The JSON IPTV snapshot is intentionally capped so it cannot grow without
 * bound on 50k-channel lists. This SQLite index stores parsed program rows
 * separately, allowing the UI to query only the visible channels instantly.
 */
internal class IptvEpgIndex(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE epg_programs (
                source_key TEXT NOT NULL,
                channel_id TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                PRIMARY KEY(source_key, channel_id, start_ms, end_ms, title)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_epg_programs_window ON epg_programs(source_key, channel_id, start_ms, end_ms)"
        )
        db.execSQL(
            """
            CREATE TABLE epg_sources (
                source_key TEXT PRIMARY KEY NOT NULL,
                updated_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS epg_programs")
        db.execSQL("DROP TABLE IF EXISTS epg_sources")
        onCreate(db)
    }

    fun replaceAll(sourceKey: String, nowNext: Map<String, IptvNowNext>, updatedAtMs: Long) {
        if (sourceKey.isBlank() || nowNext.isEmpty()) return
        val rows = flatten(nowNext)
        if (rows.isEmpty()) return

        writableDatabase.runInTransaction {
            delete("epg_programs", "source_key = ?", arrayOf(sourceKey))
            insertRows(sourceKey, rows)
            upsertSource(sourceKey, updatedAtMs)
        }
    }

    fun replaceChannels(sourceKey: String, nowNext: Map<String, IptvNowNext>, updatedAtMs: Long) {
        if (sourceKey.isBlank() || nowNext.isEmpty()) return
        val rows = flatten(nowNext)
        if (rows.isEmpty()) return

        writableDatabase.runInTransaction {
            nowNext.keys
                .asSequence()
                .filter { it.isNotBlank() }
                .chunked(MAX_SQL_ARGS - 1)
                .forEach { channelIds ->
                    val placeholders = channelIds.joinToString(",") { "?" }
                    val args = arrayOf(sourceKey) + channelIds.toTypedArray()
                    delete("epg_programs", "source_key = ? AND channel_id IN ($placeholders)", args)
                }
            insertRows(sourceKey, rows)
            upsertSource(sourceKey, updatedAtMs)
        }
    }

    fun loadNowNext(
        sourceKey: String,
        channelIds: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
        pastWindowMs: Long = DEFAULT_PAST_WINDOW_MS,
        futureWindowMs: Long = DEFAULT_FUTURE_WINDOW_MS
    ): Map<String, IptvNowNext> {
        if (sourceKey.isBlank() || channelIds.isEmpty()) return emptyMap()
        val startBound = nowMs - pastWindowMs
        val endBound = nowMs + futureWindowMs
        val grouped = LinkedHashMap<String, MutableList<IptvProgram>>()

        readableDatabase.useQueryChunks(
            sourceKey = sourceKey,
            channelIds = channelIds,
            startBound = startBound,
            endBound = endBound
        ) { channelId, program ->
            grouped.getOrPut(channelId) { mutableListOf() }.add(program)
        }

        if (grouped.isEmpty()) return emptyMap()
        return buildMap {
            grouped.forEach { (channelId, programs) ->
                buildNowNext(programs, nowMs)?.let { put(channelId, it) }
            }
        }
    }

    fun deleteSource(sourceKey: String) {
        if (sourceKey.isBlank()) return
        writableDatabase.runInTransaction {
            delete("epg_programs", "source_key = ?", arrayOf(sourceKey))
            delete("epg_sources", "source_key = ?", arrayOf(sourceKey))
        }
    }

    private fun SQLiteDatabase.useQueryChunks(
        sourceKey: String,
        channelIds: Set<String>,
        startBound: Long,
        endBound: Long,
        onProgram: (String, IptvProgram) -> Unit
    ) {
        channelIds
            .asSequence()
            .filter { it.isNotBlank() }
            .chunked(MAX_SQL_ARGS - 3)
            .forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                val sql = """
                    SELECT channel_id, start_ms, end_ms, title, description
                    FROM epg_programs
                    WHERE source_key = ?
                      AND channel_id IN ($placeholders)
                      AND end_ms > ?
                      AND start_ms < ?
                    ORDER BY channel_id, start_ms
                """.trimIndent()
                val args = buildList {
                    add(sourceKey)
                    addAll(chunk)
                    add(startBound.toString())
                    add(endBound.toString())
                }.toTypedArray()

                rawQuery(sql, args).use { cursor ->
                    val channelCol = cursor.getColumnIndexOrThrow("channel_id")
                    val startCol = cursor.getColumnIndexOrThrow("start_ms")
                    val endCol = cursor.getColumnIndexOrThrow("end_ms")
                    val titleCol = cursor.getColumnIndexOrThrow("title")
                    val descCol = cursor.getColumnIndexOrThrow("description")
                    while (cursor.moveToNext()) {
                        val channelId = cursor.getString(channelCol).orEmpty()
                        val startMs = cursor.getLong(startCol)
                        val endMs = cursor.getLong(endCol)
                        val title = cursor.getString(titleCol).orEmpty()
                        if (channelId.isBlank() || title.isBlank() || endMs <= startMs) continue
                        val description = if (cursor.isNull(descCol)) null else cursor.getString(descCol)
                        onProgram(
                            channelId,
                            IptvProgram(
                                title = title,
                                description = description?.takeIf { it.isNotBlank() },
                                startUtcMillis = startMs,
                                endUtcMillis = endMs
                            )
                        )
                    }
                }
            }
    }

    private fun SQLiteDatabase.insertRows(sourceKey: String, rows: List<ProgramRow>) {
        val statement = compileStatement(
            """
            INSERT OR REPLACE INTO epg_programs
            (source_key, channel_id, start_ms, end_ms, title, description)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )
        try {
            rows.forEach { row ->
                statement.clearBindings()
                statement.bindString(1, sourceKey)
                statement.bindString(2, row.channelId)
                statement.bindLong(3, row.program.startUtcMillis)
                statement.bindLong(4, row.program.endUtcMillis)
                statement.bindString(5, row.program.title)
                val description = row.program.description
                if (description.isNullOrBlank()) {
                    statement.bindNull(6)
                } else {
                    statement.bindString(6, description)
                }
                statement.executeInsert()
            }
        } finally {
            statement.close()
        }
    }

    private fun SQLiteDatabase.upsertSource(sourceKey: String, updatedAtMs: Long) {
        compileStatement(
            "INSERT OR REPLACE INTO epg_sources(source_key, updated_ms) VALUES (?, ?)"
        ).use { statement ->
            statement.bindString(1, sourceKey)
            statement.bindLong(2, updatedAtMs)
            statement.executeInsert()
        }
    }

    private fun flatten(nowNext: Map<String, IptvNowNext>): List<ProgramRow> {
        return buildList {
            nowNext.forEach { (channelId, item) ->
                val normalizedId = channelId.trim()
                if (normalizedId.isBlank()) return@forEach
                item.allPrograms()
                    .filter { it.title.isNotBlank() && it.endUtcMillis > it.startUtcMillis }
                    .distinctBy { "${it.startUtcMillis}|${it.endUtcMillis}|${it.title}" }
                    .forEach { program -> add(ProgramRow(normalizedId, program.compactForIndex())) }
            }
        }
    }

    private fun IptvNowNext.allPrograms(): List<IptvProgram> {
        return buildList {
            now?.let(::add)
            next?.let(::add)
            later?.let(::add)
            addAll(upcoming)
            addAll(recent)
        }
    }

    private fun IptvProgram.compactForIndex(): IptvProgram =
        copy(
            title = title.trim(),
            description = description?.trim()?.take(MAX_DESCRIPTION_CHARS)
        )

    private fun buildNowNext(programs: List<IptvProgram>, nowMs: Long): IptvNowNext? {
        if (programs.isEmpty()) return null
        val sorted = programs
            .asSequence()
            .filter { it.endUtcMillis > it.startUtcMillis }
            .distinctBy { "${it.startUtcMillis}|${it.endUtcMillis}|${it.title}" }
            .sortedBy { it.startUtcMillis }
            .toList()
        if (sorted.isEmpty()) return null

        val now = sorted.lastOrNull { it.isLive(nowMs) }
        val future = sorted
            .asSequence()
            .filter { it.startUtcMillis > nowMs }
            .take(MAX_UPCOMING_PROGRAMS)
            .toList()
        val recent = sorted
            .filter { it.endUtcMillis <= nowMs }
            .takeLast(MAX_RECENT_PROGRAMS)

        val result = IptvNowNext(
            now = now,
            next = future.getOrNull(0),
            later = future.getOrNull(1),
            upcoming = future,
            recent = recent
        )
        return if (
            result.now != null ||
            result.next != null ||
            result.later != null ||
            result.upcoming.isNotEmpty() ||
            result.recent.isNotEmpty()
        ) {
            result
        } else {
            null
        }
    }

    private inline fun SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private data class ProgramRow(
        val channelId: String,
        val program: IptvProgram
    )

    private companion object {
        const val DATABASE_NAME = "arvio_iptv_epg_index.db"
        const val DATABASE_VERSION = 1
        const val MAX_SQL_ARGS = 900
        const val MAX_DESCRIPTION_CHARS = 320
        const val MAX_UPCOMING_PROGRAMS = 64
        const val MAX_RECENT_PROGRAMS = 240
        const val DEFAULT_PAST_WINDOW_MS = 48L * 60L * 60_000L
        const val DEFAULT_FUTURE_WINDOW_MS = 72L * 60L * 60_000L
    }
}
