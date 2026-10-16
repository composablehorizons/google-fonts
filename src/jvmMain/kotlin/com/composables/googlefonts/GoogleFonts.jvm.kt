package com.composables.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val fontFiles by lazy { FontFileCache(defaultCacheDirectory(), ::download) }
private val families = mutableMapOf<GoogleFontRequest, FontFamily>()
private val familyLock = Mutex()

@Composable
internal actual fun rememberGoogleFontLoader(): suspend (GoogleFontRequest) -> FontFamily =
    remember {
        // Headless verification opts into deterministic typography without network/cache timing.
        if (java.lang.Boolean.getBoolean("googlefonts.verificationFallback"))
            { _: GoogleFontRequest -> FontFamily.SansSerif }
        else ::loadGoogleFontFamily
    }

/** Optional observer for headless export; each start returns its completion callback. */
object GoogleFontLoading {
    @Volatile var observer: ((String) -> (Throwable?) -> Unit)? = null
}

internal suspend fun loadGoogleFontFamily(request: GoogleFontRequest): FontFamily {
    val completed =
        GoogleFontLoading.observer?.invoke("${request.family} ${request.weights} ${request.style}")
    try {
        val family = familyLock.withLock {
            families[request]
                ?: FontFamily(
                        request.weights.map { weight ->
                            val key =
                                FontFace(request.family, weight, request.style == FontStyle.Italic)
                            val bytes = fontFiles.load(key)
                            Font(
                                "google-fonts:${key.cacheKey}",
                                bytes,
                                FontWeight(weight),
                                request.style,
                            )
                        }
                    )
                    .also { families[request] = it }
        }

        completed?.invoke(null)
        return family
    } catch (failure: Throwable) {
        completed?.invoke(failure)
        throw failure
    }
}

internal actual fun reportFontFailure(request: GoogleFontRequest, error: Exception) {
    Logger.getLogger("GoogleFonts")
        .log(Level.WARNING, "Could not load ${request.family}; using fallback", error)
}

internal data class FontFace(val family: String, val weight: Int, val italic: Boolean = false) {
    val cssUri: URI
        get() =
            URI(
                "https://fonts.googleapis.com/css2?family=${URLEncoder.encode(family, Charsets.UTF_8)}" +
                    ":ital,wght@${if (italic) 1 else 0},$weight"
            )

    val cacheKey: String
        get() =
            MessageDigest.getInstance("SHA-256")
                .digest(cssUri.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
}

internal class FontFileCache(
    private val directory: Path,
    private val fetch: suspend (URI) -> ByteArray,
    private val validate: (ByteArray) -> Unit = { bytes ->
        java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, bytes.inputStream())
    },
) {
    private val lock = Mutex()

    suspend fun load(face: FontFace): ByteArray = lock.withLock {
        val file = directory.resolve("${face.cacheKey}.ttf")
        val cached =
            runInterruptible(Dispatchers.IO) {
                try {
                    Files.readAllBytes(file).also(validate)
                } catch (_: Exception) {
                    null
                }
            }
        if (cached != null) return@withLock cached

        val css = fetch(face.cssUri).toString(Charsets.UTF_8)
        val uri = fontUri(css)
        val bytes = fetch(uri)
        runInterruptible(Dispatchers.IO) {
            validate(bytes)
            // A read-only cache must not prevent a successfully downloaded font from displaying.
            try {
                Files.createDirectories(directory)
                val temporary = Files.createTempFile(directory, "download-", ".tmp")
                try {
                    Files.write(temporary, bytes)
                    Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } finally {
                    Files.deleteIfExists(temporary)
                }
            } catch (_: java.io.IOException) {
                // Continue with the in-memory font if persistence is unavailable.
            }
        }
        bytes
    }
}

internal fun fontUri(css: String): URI {
    val matches = Regex("url\\(([^)]+)\\)\\s+format\\(['\"]truetype['\"]\\)").findAll(css).toList()
    require(matches.size == 1) { "Expected one complete TrueType font from Google Fonts" }
    val uri = URI(matches.single().groupValues[1].trim('\'', '"', ' '))
    require(uri.scheme == "https" && uri.host == "fonts.gstatic.com") {
        "Unexpected font download URL"
    }
    return uri
}

private val http by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build() }

private suspend fun download(uri: URI): ByteArray =
    runInterruptible(Dispatchers.IO) {
        val request =
            HttpRequest.newBuilder(uri)
                // Google supplies a complete TTF to this client instead of browser-specific WOFF
                // subsets.
                .header("User-Agent", "Java/17")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) {
            "Google Fonts returned HTTP ${response.statusCode()}"
        }
        response.body()
    }

private fun defaultCacheDirectory(): Path {
    val home = System.getProperty("user.home")
    val base =
        when {
            System.getProperty("os.name").startsWith("Mac") -> Path.of(home, "Library", "Caches")
            System.getProperty("os.name").startsWith("Windows") ->
                System.getenv("LOCALAPPDATA")?.let(Path::of) ?: Path.of(home, "AppData", "Local")
            else -> System.getenv("XDG_CACHE_HOME")?.let(Path::of) ?: Path.of(home, ".cache")
        }
    return base.resolve("com.composables.googlefonts").resolve("v1")
}
