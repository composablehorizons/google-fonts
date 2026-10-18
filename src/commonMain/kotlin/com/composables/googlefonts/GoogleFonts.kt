package com.composables.googlefonts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.CancellationException

private val loadedFamilies = mutableStateMapOf<GoogleFontRequest, FontFamily>()

@Composable
fun rememberGoogleFontFamily(
    family: String,
    weights: List<Int> = listOf(400),
    style: FontStyle = FontStyle.Normal,
    fallback: FontFamily = FontFamily.SansSerif,
): FontFamily {
    val request = GoogleFontRequest(family.trim(), weights.distinct().sorted(), style)
    val loaded = loadedFamilies[request]
    val load = rememberGoogleFontLoader()
    LaunchedEffect(request, load) {
        try {
            if (loadedFamilies[request] == null) loadedFamilies[request] = load(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportFontFailure(request, error)
        }
    }
    return loaded ?: fallback
}

internal data class GoogleFontRequest(
    val family: String,
    val weights: List<Int>,
    val style: FontStyle,
) {
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
