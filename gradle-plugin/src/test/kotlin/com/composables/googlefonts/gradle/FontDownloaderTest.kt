package com.composables.googlefonts.gradle

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI
import kotlin.test.*

class FontDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val font = byteArrayOf(0, 1, 0, 0) + ByteArray(20) { 7 }
    private val family = BundledFamily("Inter", "Inter", listOf(400, 700), true)

    @Test fun `downloads selected faces and licenses once then rebuilds offline from cache`() {
        val root = temporary.newFolder()
        val requests = mutableListOf<URI>()
        val fetch: (URI) -> ByteArray = { uri ->
            requests += uri
            when (uri.host) {
                "fonts.googleapis.com" -> "src: url(https://fonts.gstatic.com/inter-${uri.rawQuery.hashCode()}.ttf) format('truetype');".toByteArray()
                "fonts.gstatic.com" -> font
                else -> "SIL OPEN FONT LICENSE".toByteArray()
            }
        }
        bundle(root, false, fetch)
        assertEquals(4, root.resolve("fonts").listFiles()!!.size)
        assertTrue(root.resolve("licenses/Inter.txt").isFile)
        assertEquals(4, requests.count { it.host == "fonts.googleapis.com" })
        assertTrue(requests.filter { it.host == "fonts.googleapis.com" }.any { "@1,700" in it.toString() })
        val lock = root.resolve("google-fonts.lock").readText()
        root.resolve("fonts").deleteRecursively()
        root.resolve("licenses").deleteRecursively()
        bundle(root, true) { error("Offline rebuild must not make network requests") }
        assertEquals(lock, root.resolve("google-fonts.lock").readText())
        assertContentEquals(font, root.resolve("fonts/${family.faces.first().resourceName}.ttf").readBytes())
    }

    @Test fun `checksum mismatch fails without changing the lockfile`() {
        val root = temporary.newFolder()
        seed(root)
        root.resolve("cache").deleteRecursively()
        val lock = root.resolve("google-fonts.lock").readText()
        assertFailsWith<org.gradle.api.GradleException> { bundle(root, false) { "changed".toByteArray() } }
        assertEquals(lock, root.resolve("google-fonts.lock").readText())
    }

    @Test fun `corrupted cached font is fetched again using the locked url`() {
        val root = temporary.newFolder()
        seed(root)
        root.resolve("cache/${sha256(font)}").writeText("corrupt")
        val requests = mutableListOf<URI>()
        bundle(root, false) { requests += it; font }
        assertEquals(listOf("fonts.gstatic.com"), requests.map { it.host })
    }

    @Test fun `offline cache miss gives an actionable failure`() {
        val root = temporary.newFolder()
        seed(root)
        root.resolve("cache").deleteRecursively()
        val failure = assertFailsWith<org.gradle.api.GradleException> { bundle(root, true) { error("Network request") } }
        assertContains(failure.message!!, "without --offline")
    }

    @Test fun `changing family with same accessor resolves new fonts`() {
        val root = temporary.newFolder()
        seed(root)
        var downloads = 0
        FontDownloader(root.resolve("cache"), false, fetch = { uri ->
            downloads++
            if (uri.host == "fonts.googleapis.com") "url(https://fonts.gstatic.com/roboto.ttf) format('truetype')".toByteArray()
            else if (uri.host == "fonts.gstatic.com") font
            else "LICENSE".toByteArray()
        }).bundle(listOf(family.copy(family = "Roboto")), root.resolve("google-fonts.lock"), root.resolve("fonts"), root.resolve("licenses"))
        assertTrue(downloads > 0)
        assertContains(root.resolve("google-fonts.lock").readText(), "family=Roboto")
    }

    @Test fun `browser subsets and non-font responses are rejected`() {
        val root = temporary.newFolder()
        assertFailsWith<org.gradle.api.GradleException> { bundle(root, false) { uri ->
            if (uri.host == "fonts.googleapis.com") "url(https://fonts.gstatic.com/a.woff2) format('woff2')".toByteArray()
            else "LICENSE".toByteArray()
        } }
        assertFailsWith<IllegalArgumentException> { validateFont("<html>error page</html>".toByteArray()) }
        assertFalse(root.resolve("google-fonts.lock").exists())
    }

    @Test fun `missing OFL license falls back to Apache license`() {
        val root = temporary.newFolder()
        val paths = mutableListOf<String>()
        bundle(root, false) { uri ->
            paths += uri.path
            when {
                "/ofl/" in uri.path -> throw MissingFontResource(uri)
                uri.host == "fonts.googleapis.com" -> "url(https://fonts.gstatic.com/font.ttf) format('truetype')".toByteArray()
                uri.host == "fonts.gstatic.com" -> font
                else -> "Apache License".toByteArray()
            }
        }
        assertTrue(paths.any { "/apache/inter/LICENSE.txt" in it })
    }

    private fun seed(root: File) = bundle(root, false) { uri ->
        when (uri.host) {
            "fonts.googleapis.com" -> "url(https://fonts.gstatic.com/font.ttf) format('truetype')".toByteArray()
            "fonts.gstatic.com" -> font
            else -> "LICENSE".toByteArray()
        }
    }

    private fun bundle(root: File, offline: Boolean, fetch: (URI) -> ByteArray) =
        FontDownloader(root.resolve("cache"), offline, fetch).bundle(listOf(family), root.resolve("google-fonts.lock"), root.resolve("fonts"), root.resolve("licenses"))
}
