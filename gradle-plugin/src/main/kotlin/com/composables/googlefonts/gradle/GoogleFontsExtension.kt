package com.composables.googlefonts.gradle

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class GoogleFontsExtension @Inject constructor(private val objects: ObjectFactory) {
    /** Package containing the generated GoogleFonts object. */
    val packageName: Property<String> = objects.property(String::class.java)
        .convention("com.composables.googlefonts.generated")

    /** Existing CMP resources to merge with bundled fonts. */
    abstract val resourcesDirectory: DirectoryProperty

    internal val families = mutableListOf<GoogleFontFamilySpec>()

    fun family(name: String, configure: Action<GoogleFontFamilySpec> = Action {}) {
        val spec = GoogleFontFamilySpec(name.trim())
        configure.execute(spec)
        families += spec
    }
}

class GoogleFontFamilySpec internal constructor(val family: String) {
    /** Override the generated property name for families with unusual names. */
    var accessor: String = family.split(Regex("[^A-Za-z0-9]+")).joinToString("") {
        it.replaceFirstChar(Char::uppercaseChar)
    }
    private var selectedWeights = listOf(400)
    private var includeItalic = false

    fun weights(vararg weights: Int) { selectedWeights = weights.toList() }

    /** Include italic faces as well as normal faces for every selected weight. */
    fun italic() { includeItalic = true }

    internal fun encode(): String {
        require(family.isNotBlank() && family.none { it.isISOControl() }) { "Enter a Google Fonts family name." }
        require(accessor.matches(Regex("[A-Z][A-Za-z0-9_]*")) && accessor != "GoogleFonts") {
            "Choose an accessor beginning with an uppercase letter for $family."
        }
        require(selectedWeights.isNotEmpty() && selectedWeights.all { it in 1..1000 }) {
            "Select at least one weight between 1 and 1000 for $family."
        }
        return listOf(family, accessor, selectedWeights.distinct().sorted().joinToString(","), includeItalic).joinToString("\t")
    }
}

internal data class BundledFamily(val family: String, val accessor: String, val weights: List<Int>, val italic: Boolean) {
    val faces: List<BundledFace>
        get() = weights.flatMap { weight ->
            (if (italic) listOf(false, true) else listOf(false)).map { BundledFace(this, weight, it) }
        }

    companion object {
        fun decode(encoded: String): BundledFamily {
            val parts = encoded.split('\t')
            return BundledFamily(parts[0], parts[1], parts[2].split(',').map(String::toInt), parts[3].toBooleanStrict())
        }
    }
}

internal data class BundledFace(val family: BundledFamily, val weight: Int, val italic: Boolean) {
    val resourceName: String get() = "googlefonts_${family.accessor.lowercase()}_${weight}_${if (italic) "italic" else "normal"}"
}

internal fun validateFamilies(encoded: List<String>): List<BundledFamily> {
    val families = encoded.map(BundledFamily::decode)
    val names = families.map { it.accessor.lowercase() }
    if (names.distinct().size != names.size) throw GradleException("Each Google Fonts family needs a unique accessor name.")
    return families
}
