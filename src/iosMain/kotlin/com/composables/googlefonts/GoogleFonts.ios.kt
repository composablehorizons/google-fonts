@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.composables.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.*

private val client by lazy {
    HttpClient(Darwin) {
        expectSuccess = true
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
    }
}
private val families = mutableMapOf<GoogleFontRequest, FontFamily>()
private val lock = Mutex()

@Composable
internal actual fun rememberGoogleFontLoader(): suspend (GoogleFontRequest) -> FontFamily =
    remember { ::loadGoogleFontFamily }

internal suspend fun loadGoogleFontFamily(request: GoogleFontRequest): FontFamily = lock.withLock {
    families[request] ?: withContext(Dispatchers.Default) {
        FontFamily(request.weights.map { weight ->
            val key = "${request.family.encodeURLParameter()}-${weight}-${request.style.value}"
            val directory = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
                .first() as String
            val path = "$directory/google-fonts-$key.ttf"
            val cached = NSData.dataWithContentsOfFile(path)?.let { data ->
                data.bytes?.readBytes(data.length.toInt())
            }?.takeIf(::isFont)
            val bytes = cached ?: run {
                val italic = if (request.style == FontStyle.Italic) 1 else 0
                val css = client.get(
                    "https://fonts.googleapis.com/css2?family=${request.family.encodeURLParameter()}:ital,wght@$italic,$weight"
                ) { header("User-Agent", "Java/17") }.bodyAsText()
                val matches = Regex("url\\(([^)]+)\\)\\s+format\\(['\"]truetype['\"]\\)").findAll(css).toList()
                require(matches.size == 1) { "Expected one complete TrueType font" }
                val url = matches.single().groupValues[1].trim('\'', '"', ' ')
                require(url.startsWith("https://fonts.gstatic.com/")) { "Unexpected font URL" }
                client.get(url).bodyAsBytes().also { bytes ->
                    require(isFont(bytes)) { "Invalid font response" }
                    bytes.usePinned {
                        NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
                            .writeToFile(path, atomically = true)
                    }
                }
            }
            Font("google-fonts:$key", bytes, FontWeight(weight), request.style)
        })
    }.also { families[request] = it }
}

private fun isFont(bytes: ByteArray): Boolean = bytes.size > 12 &&
    (bytes.take(4) == listOf<Byte>(0, 1, 0, 0) || bytes.copyOfRange(0, 4).decodeToString() == "OTTO")

internal actual fun reportFontFailure(request: GoogleFontRequest, error: Exception) {
    NSLog("GoogleFonts: could not load ${request.family}; using fallback. $error")
}
