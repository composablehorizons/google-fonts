package com.composables.googlefonts

import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleFontRequestTest {
    @Test
    fun italicRequestIncludesNormalAndItalicFacesForEachWeight() {
        val request = GoogleFontRequest("Inter", listOf(400, 700), italic = true)

        assertEquals(
            listOf(
                GoogleFontFace(400, italic = false),
                GoogleFontFace(400, italic = true),
                GoogleFontFace(700, italic = false),
                GoogleFontFace(700, italic = true),
            ),
            request.faces,
        )
    }

    @Test
    fun nonItalicRequestIncludesOnlyNormalFaces() {
        val request = GoogleFontRequest("Inter", listOf(400, 700), italic = false)

        assertEquals(
            listOf(GoogleFontFace(400, italic = false), GoogleFontFace(700, italic = false)),
            request.faces,
        )
    }
}
