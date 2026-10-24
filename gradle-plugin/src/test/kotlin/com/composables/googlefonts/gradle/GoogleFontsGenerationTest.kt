package com.composables.googlefonts.gradle

import org.junit.Test
import kotlin.test.*

class GoogleFontsGenerationTest {
    @Test fun `DSL selects normal and italic weights deterministically`() {
        val spec = GoogleFontFamilySpec("Open Sans").apply { weights(700, 400, 700); italic() }
        val family = BundledFamily.decode(spec.encode())
        assertEquals("OpenSans", family.accessor)
        assertEquals(listOf(400, 700), family.weights)
        assertEquals(4, family.faces.size)
    }

    @Test fun `invalid weights and accessor collisions fail early`() {
        assertFailsWith<IllegalArgumentException> { GoogleFontFamilySpec("Inter").apply { weights(0) }.encode() }
        assertFailsWith<IllegalArgumentException> { GoogleFontFamilySpec("Inter").apply { weights() }.encode() }
        assertFailsWith<IllegalArgumentException> { GoogleFontFamilySpec("123 Font").encode() }
        assertFailsWith<org.gradle.api.GradleException> {
            validateFamilies(listOf(GoogleFontFamilySpec("Inter").encode(), GoogleFontFamilySpec("inter").encode()))
        }
    }

    @Test fun `Android accessors use native resources without CMP`() {
        val code = generateAccessors(families(), "example.fonts", "example.app", "R", false)
        assertContains(code, "import example.app.R")
        assertContains(code, "Font(R.font.googlefonts_inter_700_italic, weight = FontWeight(700), style = FontStyle.Italic)")
        assertFalse(code.contains("org.jetbrains.compose.resources"))
    }

    @Test fun `CMP and preload accessors agree on resource weight and style`() {
        val code = generateAccessors(families(), "example.fonts", "example.resources", "AppRes", true)
        val preload = generatePreload(families(), "example.fonts", "example.resources", "AppRes")
        assertContains(code, "import example.resources.AppRes")
        assertContains(code, "AppRes.font.googlefonts_inter_700_italic")
        assertContains(preload, "preloadFont(AppRes.font.googlefonts_inter_700_italic, weight = FontWeight(700), style = FontStyle.Italic)")
        assertContains(preload, "return ready0 && ready1 && ready2 && ready3")
    }

    private fun families() = listOf(BundledFamily("Inter", "Inter", listOf(400, 700), true))
}
