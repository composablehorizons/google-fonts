package com.composables.googlefonts.gradle

import org.gradle.api.GradleException
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties

internal class FontDownloader(
    private val cache: File,
    private val offline: Boolean,
    private val fetch: (URI) -> ByteArray = ::fetchFontResource,
    private val progress: (String) -> Unit = {},
) {
    fun bundle(families: List<BundledFamily>, lockFile: File, fonts: File, licenses: File) = synchronized(cacheMutex) {
        cache.mkdirs()
        // Coordinate parallel module builds sharing the same download cache.
        FileChannel.open(cache.resolve("cache.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                val previous = Properties().apply { if (lockFile.isFile) lockFile.inputStream().use(::load) }
                val next = Properties()
                families.forEach { family ->
                    val licenseKey = "${family.accessor}.license"
                    val license = lockedResource(previous, next, licenseKey, family.family) { resolveLicense(family.family) }
                    licenses.resolve("${family.accessor}.txt").apply { parentFile.mkdirs(); writeBytes(license) }
                    family.faces.forEach { face ->
                        val bytes = lockedResource(previous, next, face.resourceName, family.family) { resolveFont(face) }
                        validateFont(bytes)
                        fonts.resolve("${face.resourceName}.ttf").apply { parentFile.mkdirs(); writeBytes(bytes) }
                    }
                }
                val content = buildString {
                    appendLine("# Google Fonts lockfile. Commit this file. Delete an entry to resolve it again.")
                    next.stringPropertyNames().sorted().forEach { key -> appendLine("$key=${next.getProperty(key)}") }
                }
                if (!lockFile.isFile || lockFile.readText() != content) atomicWrite(lockFile, content.toByteArray())
            }
        }
    }

    private fun lockedResource(previous: Properties, next: Properties, key: String, family: String, resolve: () -> Pair<URI, ByteArray>): ByteArray {
        val request = URLEncoder.encode(family, Charsets.UTF_8)
        val matchesRequest = previous.getProperty("$key.family") == request
        val url = previous.getProperty("$key.url").takeIf { matchesRequest }
        val checksum = previous.getProperty("$key.sha256").takeIf { matchesRequest }
        if ((url == null) != (checksum == null)) throw GradleException("Incomplete Google Fonts lock entry: $key. Remove that entry and rebuild.")
        val bytes: ByteArray
        val resolvedUrl: String
        val resolvedChecksum: String
        if (url != null) {
            requireNotNull(checksum)
            require(checksum.matches(Regex("[a-f0-9]{64}"))) { "Invalid font checksum for $key." }
            resolvedUrl = url
            resolvedChecksum = checksum
            val cached = cache.resolve(checksum)
            bytes = cached.takeIf { it.isFile }?.readBytes()?.takeIf { sha256(it) == checksum } ?: run {
                if (offline) throw GradleException("$key is not cached. Build once without --offline to download your fonts.")
                progress("Downloading $key")
                fetch(allowedUri(url)).also {
                    if (sha256(it) != checksum) throw GradleException("Font checksum changed for $key. Restore the locked file or remove its lock entry to update it.")
                    atomicWrite(cached, it)
                }
            }
        } else {
            if (offline) throw GradleException("$key is not locked. Build once without --offline to download your fonts.")
            progress("Downloading $key")
            val (uri, downloaded) = resolve()
            bytes = downloaded
            resolvedUrl = uri.toString()
            resolvedChecksum = sha256(bytes)
            atomicWrite(cache.resolve(resolvedChecksum), bytes)
        }
        next.setProperty("$key.url", resolvedUrl)
        next.setProperty("$key.sha256", resolvedChecksum)
        next.setProperty("$key.family", request)
        return bytes
    }

    private fun resolveFont(face: BundledFace): Pair<URI, ByteArray> {
        val name = URLEncoder.encode(face.family.family, Charsets.UTF_8)
        val cssUri = URI("https://fonts.googleapis.com/css2?family=$name:ital,wght@${if (face.italic) 1 else 0},${face.weight}")
        val css = fetch(cssUri).toString(Charsets.UTF_8)
        val matches = Regex("url\\(([^)]+)\\)\\s+format\\(['\"]truetype['\"]\\)").findAll(css).toList()
        if (matches.size != 1) throw GradleException("Google Fonts did not return a complete TrueType font for ${face.family.family} ${face.weight}.")
        val uri = allowedUri(matches.single().groupValues[1].trim('\'', '"', ' '))
        require(uri.host == "fonts.gstatic.com") { "Unexpected Google Fonts font URL: $uri" }
        return uri to fetch(uri).also(::validateFont)
    }

    private fun resolveLicense(family: String): Pair<URI, ByteArray> {
        val slug = family.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
        val paths = listOf("ofl/$slug/OFL.txt", "apache/$slug/LICENSE.txt", "ufl/$slug/LICENCE.txt")
        for (path in paths) {
            val uri = URI("https://raw.githubusercontent.com/google/fonts/main/$path")
            try {
                return uri to fetch(uri).also { require(it.isNotEmpty()) { "Empty font license for $family." } }
            } catch (missing: MissingFontResource) {
                // Font families are distributed under one of these three license directories.
            }
        }
        throw GradleException("Could not find the redistribution license for $family in Google Fonts.")
    }

    companion object {
        private val cacheMutex = Any()
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun validateFont(bytes: ByteArray) {
    require(bytes.size > 12 && (bytes.take(4) == listOf<Byte>(0, 1, 0, 0) || bytes.copyOfRange(0, 4).decodeToString() == "OTTO")) {
        "Google Fonts returned an invalid font file."
    }
}

private fun allowedUri(url: String): URI = URI(url).also {
    require(it.scheme == "https" && it.host in setOf("fonts.gstatic.com", "raw.githubusercontent.com") && it.userInfo == null) {
        "Unexpected Google Fonts resource URL: $url"
    }
}

internal fun atomicWrite(file: File, bytes: ByteArray) {
    file.parentFile.mkdirs()
    val temporary = Files.createTempFile(file.parentFile.toPath(), "googlefonts-", ".tmp")
    try {
        Files.write(temporary, bytes)
        Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal class MissingFontResource(uri: URI) : RuntimeException("Font resource not found: $uri")

private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build() }

private fun fetchFontResource(uri: URI): ByteArray {
    val request = HttpRequest.newBuilder(uri).header("User-Agent", "Java/17").timeout(Duration.ofSeconds(30)).GET().build()
    val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
    if (response.statusCode() == 404) throw MissingFontResource(uri)
    if (response.statusCode() != 200) throw GradleException("Could not download $uri (HTTP ${response.statusCode()}). Check your connection and font selection, then rebuild.")
    return response.body()
}
