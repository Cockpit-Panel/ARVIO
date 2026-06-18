package com.arflix.tv.network

import com.arflix.tv.util.Constants
import com.arflix.tv.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Intercepts API calls to TMDB and Trakt and routes them through backend proxy Functions.
 * This keeps API keys secure on the server - they never exist in the app.
 */
class ApiProxyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url

        if (!hasProxyConfig()) {
            return chain.proceed(originalRequest)
        }

        return when (originalUrl.host) {
            "api.themoviedb.org" -> {
                // TMDB browsing is very high-volume. Proxy it only when the
                // build explicitly opts in; otherwise use the direct API key
                // and OkHttp cache to avoid runaway Function billing.
                if (BuildConfig.ENABLE_TMDB_EDGE_PROXY) {
                    val proxyRequest = rewriteForTmdbProxy(originalRequest) ?: originalRequest
                    chain.proceed(proxyRequest)
                } else {
                    chain.proceed(originalRequest)
                }
            }
            "api.trakt.tv" -> {
                // Trakt catalog/watchlist/scrobble traffic can also be noisy.
                // Keep proxy support available for future secret-only builds,
                // but do not route all user traffic through backend functions by default.
                if (BuildConfig.ENABLE_TRAKT_EDGE_PROXY) {
                    val proxyRequest = rewriteForTraktProxy(originalRequest) ?: originalRequest
                    chain.proceed(proxyRequest)
                } else {
                    chain.proceed(originalRequest)
                }
            }
            else -> {
                // Pass through other requests unchanged
                chain.proceed(originalRequest)
            }
        }
    }

    private fun rewriteForTmdbProxy(originalRequest: Request): Request? {
        val originalUrl = originalRequest.url

        // Extract the path and remove /3 prefix (proxy adds it)
        // e.g., /3/trending/movie/day -> /trending/movie/day
        val path = originalUrl.encodedPath.let { if (it.startsWith("/3/")) it.removePrefix("/3") else it }

        // Build proxy URL with path parameter
        val proxyUrlBuilder = (Constants.TMDB_PROXY_URL.toHttpUrlOrNull() ?: return null).newBuilder()
            .addQueryParameter("path", path)

        // Forward all original query parameters except api_key
        for (i in 0 until originalUrl.querySize) {
            val name = originalUrl.queryParameterName(i)
            if (name != "api_key") {
                originalUrl.queryParameterValue(i)?.let { value ->
                    proxyUrlBuilder.addQueryParameter(name, value)
                }
            }
        }

        return originalRequest.newBuilder()
            .url(proxyUrlBuilder.build())
            .header("apikey", Constants.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
            .build()
    }

    private fun rewriteForTraktProxy(originalRequest: Request): Request? {
        val originalUrl = originalRequest.url

        // Extract the path
        val path = originalUrl.encodedPath

        // Build proxy URL with path and method parameters
        val proxyUrlBuilder = (Constants.TRAKT_PROXY_URL.toHttpUrlOrNull() ?: return null).newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("method", originalRequest.method)

        // Forward all original query parameters
        for (i in 0 until originalUrl.querySize) {
            val name = originalUrl.queryParameterName(i)
            originalUrl.queryParameterValue(i)?.let { value ->
                proxyUrlBuilder.addQueryParameter(name, value)
            }
        }

        // Get the user's auth token from original request if present
        val authHeader = originalRequest.header("Authorization")
        val userToken = authHeader?.removePrefix("Bearer ")?.trim()

        val requestBuilder = originalRequest.newBuilder()
            .url(proxyUrlBuilder.build())
            .header("apikey", Constants.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${Constants.SUPABASE_ANON_KEY}")
            .header("Cache-Control", "no-store")

        // Forward user token in custom header
        if (!userToken.isNullOrEmpty()) {
            requestBuilder.header("x-user-token", userToken)
        }

        // For POST/DELETE, keep the body but remove trakt-specific headers (proxy adds them)
        requestBuilder.removeHeader("trakt-api-key")
        requestBuilder.removeHeader("trakt-api-version")

        return requestBuilder.build()
    }

    private fun hasProxyConfig(): Boolean {
        if (Constants.USE_NETLIFY_CLOUD_SYNC) {
            return Constants.NETLIFY_BACKEND_URL.startsWith("https://")
        }
        val supabaseUrl = Constants.SUPABASE_URL.trim()
        val anonKey = Constants.SUPABASE_ANON_KEY.trim()
        return supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("your-project", ignoreCase = true) &&
            anonKey.length > 40 &&
            !anonKey.startsWith("your-", ignoreCase = true)
    }
}
