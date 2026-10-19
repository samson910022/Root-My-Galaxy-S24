package dev.samson.s24root

import java.net.HttpURLConnection
import java.net.URL

/**
 * Opens GET connections without letting a redirect downgrade them.
 *
 * [HttpURLConnection]'s own redirect handling follows a Location header
 * wherever it points, plain http included, so every hop is resolved here
 * instead: the initial URL and every redirect target must be https before a
 * request is made to it, and the chain is capped so a redirect loop cannot
 * spin forever.
 */
internal object HttpsRedirects {
    private const val MAX_REDIRECTS = 5

    /** The codes [HttpURLConnection] would itself follow when redirects are enabled. */
    private val REDIRECT_CODES = setOf(
        HttpURLConnection.HTTP_MOVED_PERM, // 301
        HttpURLConnection.HTTP_MOVED_TEMP, // 302
        HttpURLConnection.HTTP_SEE_OTHER, // 303
        307, // HTTP_TEMPORARY_REDIRECT, which the android.jar stubs do not name
        308, // HTTP_PERMANENT_REDIRECT, which the android.jar stubs do not name
    )

    /**
     * Returns a connected [HttpURLConnection] for [url] with every redirect
     * already followed. The response code of the final connection is left
     * unchecked so each caller keeps its own treatment of non-OK responses.
     */
    fun open(
        url: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        userAgent: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpURLConnection {
        var connection = connect(requireHttps(URL(url)), connectTimeoutMillis, readTimeoutMillis, userAgent, headers)
        try {
            var redirects = 0
            while (true) {
                val code = connection.responseCode
                if (code !in REDIRECT_CODES) return connection
                require(redirects < MAX_REDIRECTS) {
                    "Refusing to follow more than $MAX_REDIRECTS redirects while opening $url"
                }
                val location = connection.getHeaderField("Location")
                require(!location.isNullOrBlank()) {
                    "Redirect from ${connection.url} (HTTP $code) has no Location header"
                }
                val next = resolveHttps(connection.url, location)
                connection.disconnect()
                connection = connect(next, connectTimeoutMillis, readTimeoutMillis, userAgent, headers)
                redirects++
            }
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
    }

    /**
     * Resolves [location] the way a browser would - relative, absolute or
     * protocol-relative - and refuses anything that is not https. A protocol
     * relative location (//host/path) inherits the base's protocol, so from an
     * https base it stays https.
     */
    internal fun resolveHttps(base: URL, location: String): URL {
        val resolved = URL(base, location)
        require(resolved.protocol == "https") { "Refusing non-HTTPS redirect to $resolved" }
        return resolved
    }

    /** Fails before any connection is opened, so an http URL is never upgraded around. */
    private fun requireHttps(url: URL): URL {
        require(url.protocol == "https") { "Refusing non-HTTPS URL $url" }
        return url
    }

    private fun connect(
        url: URL,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
        userAgent: String,
        headers: Map<String, String>,
    ): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = connectTimeoutMillis
        readTimeout = readTimeoutMillis
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", userAgent)
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
    }
}
