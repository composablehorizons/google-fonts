package com.composables.googlefonts

import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

@Composable
internal actual fun rememberGoogleFontLoader(): suspend (GoogleFontRequest) -> FontFamily {
    val directory = File(LocalContext.current.applicationContext.cacheDir, "google-fonts")
    return remember(directory) { { request -> loadGoogleFontFamily(directory, request) } }
}

private val client by lazy {
    HttpClient(OkHttp) {
        expectSuccess = true
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
    }
}
private val families = mutableMapOf<GoogleFontRequest, FontFamily>()
private val lock = Mutex()

private suspend fun loadGoogleFontFamily(directory: File, request: GoogleFontRequest): FontFamily = lock.withLock {
    families[request] ?: withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs()) { "Could not create font cache" }
        FontFamily(request.weights.map { weight ->
            val italic = if (request.style == FontStyle.Italic) 1 else 0
            val url = "https://fonts.googleapis.com/css2?family=${request.family.encodeURLParameter()}:ital,wght@$italic,$weight"
            val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val file = File(directory, "$key.ttf")
            val valid = file.isFile && runCatching { Typeface.createFromFile(file) }.isSuccess
            if (!valid) {
                val css = client.get(url) { header("User-Agent", "Java/17") }.bodyAsText()
                val matches = Regex("url\\(([^)]+)\\)\\s+format\\(['\"]truetype['\"]\\)").findAll(css).toList()
                require(matches.size == 1) { "Expected one complete TrueType font" }
                val fontUrl = matches.single().groupValues[1].trim('\'', '"', ' ')
                require(fontUrl.startsWith("https://fonts.gstatic.com/")) { "Unexpected font URL" }
                val bytes = client.get(fontUrl).bodyAsBytes()
                val temporary = File.createTempFile("font-", ".ttf", directory)
                try {
                    temporary.writeBytes(bytes)
                    Typeface.createFromFile(temporary)
                    check(temporary.renameTo(file)) { "Could not store downloaded font" }
                } finally {
                    temporary.delete()
                }
            }
            Font(file, weight = FontWeight(weight), style = request.style)
        })
    }.also { families[request] = it }
}

internal actual fun reportFontFailure(request: GoogleFontRequest, error: Exception) {
    Log.w("GoogleFonts", "Could not load ${request.family}; using fallback", error)
}
