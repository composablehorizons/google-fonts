package com.composables.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val client = HttpClient { expectSuccess = true }
private val families = mutableMapOf<GoogleFontRequest, FontFamily>()
private val lock = Mutex()

@Composable
internal actual fun rememberGoogleFontLoader(): suspend (GoogleFontRequest) -> FontFamily =
    remember {
        ::loadFamily
    }

private suspend fun loadFamily(request: GoogleFontRequest): FontFamily =
    lock.withLock {
        families[request]
            ?: FontFamily(
                    request.weights.map { weight ->
                        val family = request.family.encodeURLParameter()
                        val italic = if (request.style == FontStyle.Italic) 1 else 0
                        val css =
                            client
                                .get(
                                    "https://fonts.googleapis.com/css2?family=$family:ital,wght@$italic,$weight"
                                )
                                .bodyAsText()
                        // Browser CSS splits fonts by script. These English previews need the Latin
                        // subset;
                        // select it explicitly instead of depending on Google's stylesheet
                        // ordering.
                        val faces = css.split("@font-face")
                        val latin =
                            faces.firstOrNull { "U+0000-00FF" in it }
                                ?: faces.singleOrNull { "url(" in it }
                                ?: error("Google Fonts did not return a Latin font face")
                        val url =
                            Regex("url\\((https://fonts\\.gstatic\\.com/[^)]+)\\)")
                                .find(latin)
                                ?.groupValues
                                ?.get(1) ?: error("Google Fonts did not return a font URL")
                        Font(
                            "google-fonts:$family:$weight:$italic",
                            client.get(url).body<ByteArray>(),
                            FontWeight(weight),
                            request.style,
                        )
                    }
                )
                .also { families[request] = it }
    }

internal actual fun reportFontFailure(request: GoogleFontRequest, error: Exception) {
    println("Could not load ${request.family}; using fallback: ${error.message}")
}
