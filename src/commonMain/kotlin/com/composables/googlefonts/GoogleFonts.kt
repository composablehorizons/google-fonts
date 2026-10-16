package com.composables.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.CancellationException

@Composable
fun rememberGoogleFontFamily(
    family: String,
    weights: List<Int> = listOf(400),
    style: FontStyle = FontStyle.Normal,
    fallback: FontFamily = FontFamily.SansSerif,
): FontFamily {
    val request = GoogleFontRequest(family.trim(), weights.distinct().sorted(), style)
    var loaded by remember(request) { mutableStateOf<FontFamily?>(null) }
    val load = rememberGoogleFontLoader()
    LaunchedEffect(request, load) {
        try {
            loaded = load(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportFontFailure(request, error)
        }
    }
    return loaded ?: fallback
}

internal data class GoogleFontRequest(val family: String, val weights: List<Int>, val style: FontStyle) {
    init {
        require(family.isNotBlank()) { "A Google Fonts family name is required" }
        require(weights.isNotEmpty() && weights.all { it in 1..1000 }) {
            "Request at least one font weight between 1 and 1000"
        }
    }
}

@Composable
internal expect fun rememberGoogleFontLoader(): suspend (GoogleFontRequest) -> FontFamily
internal expect fun reportFontFailure(request: GoogleFontRequest, error: Exception)
