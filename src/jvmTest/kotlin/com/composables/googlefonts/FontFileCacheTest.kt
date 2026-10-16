package com.composables.googlefonts

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class FontFileCacheTest {
    private val face = FontFace("Montserrat", 800)
    private val font = byteArrayOf(1, 2, 3)
    private val css = "src: url(https://fonts.gstatic.com/example.ttf) format('truetype');".toByteArray()
    private val validate: (ByteArray) -> Unit = { require(it.contentEquals(font)) }

    @Test fun `fresh repository loads persisted font without any network`() = withCache { directory ->
        var downloads = 0
        val cache = FontFileCache(directory, { downloads++; if (it.host == "fonts.googleapis.com") css else font }, validate)
        assertContentEquals(font, cache.load(face))
        assertEquals(2, downloads)
        val offline = FontFileCache(directory, { error("No network available") }, validate)
        assertContentEquals(font, offline.load(face))
    }

    @Test fun `simultaneous callers share a download`() = withCache { directory ->
        var downloads = 0
        val cache = FontFileCache(directory, {
            downloads++
            delay(10)
            if (it.host == "fonts.googleapis.com") css else font
        }, validate)
        List(8) { async { cache.load(face) } }.awaitAll().forEach { assertContentEquals(font, it) }
        assertEquals(2, downloads)
    }

    @Test fun `corrupt cache is replaced`() = withCache { directory ->
        Files.write(directory.resolve("${face.cacheKey}.ttf"), byteArrayOf(0))
        val cache = FontFileCache(directory, { if (it.host == "fonts.googleapis.com") css else font }, validate)
        assertContentEquals(font, cache.load(face))
        assertContentEquals(font, Files.readAllBytes(directory.resolve("${face.cacheKey}.ttf")))
    }

    @Test fun `failed download can be retried without poisoning cache`() = withCache { directory ->
        var fail = true
        val cache = FontFileCache(directory, {
            if (fail) error("Offline")
            if (it.host == "fonts.googleapis.com") css else font
        }, validate)
        assertFailsWith<IllegalStateException> { cache.load(face) }
        fail = false
        assertContentEquals(font, cache.load(face))
    }

    @Test fun `cancellation releases lock and allows the next request`() = withCache { directory ->
        val started = CompletableDeferred<Unit>()
        var stall = true
        val cache = FontFileCache(directory, {
            if (stall) {
                started.complete(Unit)
                awaitCancellation()
            }
            if (it.host == "fonts.googleapis.com") css else font
        }, validate)
        val job = launch { cache.load(face) }
        started.await()
        job.cancelAndJoin()
        stall = false
        assertContentEquals(font, cache.load(face))
    }

    @Test fun `invalid downloaded data is never persisted`() = withCache { directory ->
        val cache = FontFileCache(directory, { if (it.host == "fonts.googleapis.com") css else byteArrayOf(0) }, validate)
        assertFailsWith<IllegalArgumentException> { cache.load(face) }
        assertEquals(0, directory.toFile().listFiles()!!.size)
    }

    @Test fun `malformed or subset font responses are rejected`() {
        assertFailsWith<IllegalArgumentException> { fontUri("<html>Error</html>") }
        assertFailsWith<IllegalArgumentException> { fontUri("src: url(https://example.com/font.ttf) format('truetype');") }
        assertFailsWith<IllegalArgumentException> { fontUri(css.toString(Charsets.UTF_8).repeat(2)) }
    }

    @Test fun `weights and styles have independent cache entries`() {
        assertNotEquals(face.cacheKey, face.copy(weight = 400).cacheKey)
        assertNotEquals(face.cacheKey, face.copy(italic = true).cacheKey)
        assertEquals("https://fonts.googleapis.com/css2?family=Open+Sans:ital,wght@0,400", FontFace("Open Sans", 400).cssUri.toString())
    }

    private fun withCache(block: suspend kotlinx.coroutines.CoroutineScope.(java.nio.file.Path) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("google-fonts-test")
        try { block(directory) } finally { directory.toFile().deleteRecursively() }
    }
}
