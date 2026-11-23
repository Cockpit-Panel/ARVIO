package com.arflix.tv.data.repository

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.di.RepositoryAccessEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.Continuation
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.Collections
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TraktApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in account benchmark: only disposable calendar metadata is cleared, never account/settings. */
@RunWith(AndroidJUnit4::class)
class ReleaseCalendarLiveDeviceTest {
    @Test fun configuredWatchlistsLoadColdAndWarmWithoutLosingReleases() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("calendarLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val access = EntryPointAccessors.fromApplication(instrumentation.targetContext, RepositoryAccessEntryPoint::class.java)
        val repository = access.releaseCalendarRepository()
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val api = access.tmdbApi()
        val traced = Proxy.newProxyInstance(TmdbApi::class.java.classLoader, arrayOf(TmdbApi::class.java)) { _, method, values ->
            val args: Array<Any?> = values?.map { it }?.toTypedArray() ?: emptyArray()
            val started = SystemClock.elapsedRealtime()
            fun record(error: Throwable? = null) {
                requests += "${method.name}:${args.firstOrNull()} ${SystemClock.elapsedRealtime() - started}ms ${if (error is retrofit2.HttpException) "HTTP_${error.code()}" else error?.javaClass?.simpleName ?: "OK"}"
            }
            @Suppress("UNCHECKED_CAST")
            val continuation = args.lastOrNull() as? Continuation<Any?>
            if (continuation != null) args[args.lastIndex] = object : Continuation<Any?> {
                override val context = continuation.context
                override fun resumeWith(result: Result<Any?>) { record(result.exceptionOrNull()); continuation.resumeWith(result) }
            }
            try {
                method.invoke(api, *args).also { if (it !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) record() }
            } catch (error: InvocationTargetException) { record(error.targetException); throw error.targetException }
        } as TmdbApi
        ReleaseCalendarRepository::class.java.getDeclaredField("tmdbApi").apply { isAccessible = true }.set(repository, traced)
        val traktRepository = access.traktRepository()
        val traktField = TraktRepository::class.java.getDeclaredField("traktApi").apply { isAccessible = true }
        val traktApi = traktField.get(traktRepository) as TraktApi
        val tracedTrakt = Proxy.newProxyInstance(TraktApi::class.java.classLoader, arrayOf(TraktApi::class.java)) { _, method, values ->
            val args: Array<Any?> = values?.map { it }?.toTypedArray() ?: emptyArray()
            val started = SystemClock.elapsedRealtime()
            fun record(error: Throwable? = null) {
                requests += "trakt:${method.name} ${SystemClock.elapsedRealtime() - started}ms ${if (error is retrofit2.HttpException) "HTTP_${error.code()}" else error?.javaClass?.simpleName ?: "OK"}"
            }
            @Suppress("UNCHECKED_CAST")
            val continuation = args.lastOrNull() as? Continuation<Any?>
            if (continuation != null) args[args.lastIndex] = object : Continuation<Any?> {
                override val context = continuation.context
                override fun resumeWith(result: Result<Any?>) { record(result.exceptionOrNull()); continuation.resumeWith(result) }
            }
            try { method.invoke(traktApi, *args).also { if (it !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) record() } }
            catch (error: InvocationTargetException) { record(error.targetException); throw error.targetException }
        } as TraktApi
        traktField.set(traktRepository, tracedTrakt)
        ReleaseCalendarRepository::class.java.getDeclaredField("traktApi").apply { isAccessible = true }.set(repository, tracedTrakt)
        val profile = access.profileManager().getProfileIdSync()
        if (InstrumentationRegistry.getArguments().getString("calendarInvalidate") == "true") repository.invalidateMetadata()
        val month = YearMonth.now()
        val timezone = ZoneId.systemDefault()
        val start = SystemClock.elapsedRealtime()
        var firstReleaseMs: Long? = null
        var lastReleaseMs = 0L
        val discovered = mutableSetOf<String>()
        var lists = CalendarWatchlists(profile, emptyMap())
        val cold = repository.loadCalendar(profile, month, timezone, "NL") { progress ->
            lists = progress.watchlists
            if (firstReleaseMs == null && progress.month.entries.isNotEmpty()) {
                firstReleaseMs = SystemClock.elapsedRealtime() - start
            }
            val ids = progress.month.entries.map { it.id }.toSet()
            if (ids.any { it !in discovered }) lastReleaseMs = SystemClock.elapsedRealtime() - start
            discovered.addAll(ids)
        }
        val coldMs = SystemClock.elapsedRealtime() - start
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "CALENDAR_SOURCES ${lists.items.map { (source, items) -> "${source.id}=${items.size}" }} warnings=${lists.warnings}\n" +
                "CALENDAR_TRAKT_REQUESTS ${requests.filter { it.startsWith("trakt:") }.joinToString("; ")}\n")
        })
        assertTrue("This live test requires configured watchlist titles", mergeCalendarWatchlists(lists.items).isNotEmpty())
        assertTrue("The configured account must have releases this month", cold.entries.isNotEmpty())
        val warmStart = SystemClock.elapsedRealtime()
        var warmFirstMs: Long? = null
        val warm = repository.loadMonth(lists, month, timezone, "NL") { progress ->
            if (warmFirstMs == null && progress.entries.isNotEmpty()) warmFirstMs = SystemClock.elapsedRealtime() - warmStart
        }
        val warmMs = SystemClock.elapsedRealtime() - warmStart
        assertEquals("Warm loading must not omit releases", cold.entries.map { it.id }.toSet(), warm.entries.map { it.id }.toSet())
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "CALENDAR_LIVE titles=${mergeCalendarWatchlists(lists.items).size} releases=${cold.entries.size} " +
                "coldFirst=${firstReleaseMs}ms coldLastRelease=${lastReleaseMs}ms coldComplete=${coldMs}ms warmFirst=${warmFirstMs}ms warmComplete=${warmMs}ms " +
                "providerWarnings=${lists.warnings.size} metadataWarnings=${cold.warnings.size - lists.warnings.size}\n")
        })
        for (offset in InstrumentationRegistry.getArguments().getString("calendarOffsets", "1,2").split(",").map(String::toLong)) {
            requests.clear()
            val nextMonth = month.plusMonths(offset)
            val nextStart = SystemClock.elapsedRealtime()
            val next = repository.loadMonth(lists, nextMonth, timezone, "NL")
            val elapsed = SystemClock.elapsedRealtime() - nextStart
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "CALENDAR_NEW_MONTH month=$nextMonth complete=${elapsed}ms releases=${next.entries.size} warnings=${next.warnings.size}\n" +
                    "CALENDAR_REQUESTS ${requests.joinToString("; ")}\n" +
                    next.entries.joinToString("\n") { "CALENDAR_ENTRY ${it.date} ${it.releaseInstant} ${it.media.id} ${it.media.title} S${it.seasonNumber}E${it.episodeNumber}" } + "\n")
            })
            val repeated = repository.loadMonth(lists, nextMonth, timezone, "NL")
            assertEquals(next.entries.map { it.id }.toSet(), repeated.entries.map { it.id }.toSet())
            if (InstrumentationRegistry.getArguments().getString("calendarRequireFast") == "true") {
                assertTrue("New month took ${elapsed}ms (target <10000ms)", elapsed < 10_000)
            }
        }
        val failedTitles = mergeCalendarWatchlists(lists.items).filter { title ->
            cold.warnings.any { it == "Release dates for ${title.media.title} are unavailable." }
        }
        for (title in failedTitles.take(2)) {
            val status = try {
                if (title.media.mediaType == com.arflix.tv.data.model.MediaType.TV) {
                    access.tmdbApi().getTvDetails(title.media.id, com.arflix.tv.util.Constants.TMDB_API_KEY)
                } else access.tmdbApi().getMovieDetails(title.media.id, com.arflix.tv.util.Constants.TMDB_API_KEY)
                "recovered"
            } catch (error: retrofit2.HttpException) { "HTTP_${error.code()}" }
            catch (_: Exception) { "network_failure" }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "CALENDAR_METADATA_SAMPLE type=${title.media.mediaType} status=$status\n")
            })
        }
    }
}
