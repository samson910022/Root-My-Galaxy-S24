package dev.samson.s24root

import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpsRedirectsTest {
    @Test
    fun relativeLocationResolvesAgainstTheHttpsBase() {
        val resolved = HttpsRedirects.resolveHttps(
            URL("https://raw.githubusercontent.com/owner/repo/main/support/targets-v3.json"),
            "payloads/artifact.bin",
        )
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/support/payloads/artifact.bin",
            resolved.toString(),
        )
    }

    @Test
    fun absoluteHttpsLocationIsAccepted() {
        val resolved = HttpsRedirects.resolveHttps(
            URL("https://api.github.com/repos/owner/repo/releases/latest"),
            "https://objects.githubusercontent.com/download/artifact.apk",
        )
        assertEquals("https://objects.githubusercontent.com/download/artifact.apk", resolved.toString())
    }

    @Test
    fun absoluteHttpLocationIsRejected() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            HttpsRedirects.resolveHttps(
                URL("https://example.invalid/artifact"),
                "http://example.invalid/artifact",
            )
        }
        assertTrue(
            "unexpected message: ${error.message}",
            error.message.orEmpty().contains("Refusing non-HTTPS redirect"),
        )
    }

    @Test
    fun protocolRelativeLocationInheritsHttps() {
        val resolved = HttpsRedirects.resolveHttps(
            URL("https://raw.githubusercontent.com/owner/repo/main/file"),
            "//objects.githubusercontent.com/download/artifact.apk",
        )
        assertEquals("https://objects.githubusercontent.com/download/artifact.apk", resolved.toString())
    }

    @Test
    fun openRefusesAnInitialHttpUrlBeforeConnecting() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            HttpsRedirects.open(
                url = "http://insecure.invalid/payload",
                connectTimeoutMillis = 1_000,
                readTimeoutMillis = 1_000,
                userAgent = "HttpsRedirectsTest",
            )
        }
        assertTrue(
            "unexpected message: ${error.message}",
            error.message.orEmpty().contains("Refusing non-HTTPS URL"),
        )
    }
}
