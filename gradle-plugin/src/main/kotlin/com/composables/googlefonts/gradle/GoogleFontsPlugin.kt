package com.composables.googlefonts.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

class GoogleFontsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("googleFonts", GoogleFontsExtension::class.java)
        extension.resourcesDirectory.convention(project.layout.projectDirectory.dir("src/commonMain/composeResources"))
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            extension.resourcesDirectory.convention(project.layout.projectDirectory.dir("src/main/composeResources"))
        }
        val generate = project.tasks.register("generateGoogleFonts", GenerateGoogleFonts::class.java) { task ->
            task.group = "google fonts"
            task.description = "Bundles configured Google Fonts and generates Compose accessors."
            task.packageName.set(extension.packageName)
            task.offline.set(project.gradle.startParameter.isOffline)
            task.lockFile.set(project.layout.projectDirectory.file("google-fonts.lock"))
            task.cacheDirectory.set(project.gradle.gradleUserHomeDir.resolve("caches/com.composables.google-fonts/v1"))
            task.kotlinDirectory.set(project.layout.buildDirectory.dir("generated/googleFonts/kotlin"))
            task.wasmDirectory.set(project.layout.buildDirectory.dir("generated/googleFonts/wasmJs"))
            task.assetsDirectory.set(project.layout.buildDirectory.dir("generated/googleFonts/assets"))
        }

        project.afterEvaluate {
            val encoded = extension.families.map { it.encode() }
            validateFamilies(encoded)
            generate.configure { it.families.set(encoded) }
            if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                configureMultiplatform(project, extension, generate)
            } else if (project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") &&
                !project.plugins.hasPlugin("com.android.application") && !project.plugins.hasPlugin("com.android.library")) {
                val main = project.extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName("main")
                configureComposeResources(project, extension, generate, main)
            } else if (!project.plugins.hasPlugin("com.android.application") && !project.plugins.hasPlugin("com.android.library")) {
                throw GradleException("Apply com.composables.google-fonts to an Android, Compose Multiplatform, or Compose JVM module.")
            }
        }

        listOf("com.android.application", "com.android.library").forEach { id ->
            project.pluginManager.withPlugin(id) {
                project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                    if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                        generate.configure {
                            it.multiplatform.set(false)
                            it.resourcePackage.set(variant.namespace)
                            it.resourceClass.set("R")
                            it.resourceDirectory.set(project.layout.buildDirectory.dir("generated/googleFonts/res"))
                        }
                        val res = variant.sources.res ?: throw GradleException("Enable Android resources to bundle Google Fonts.")
                        val kotlin = variant.sources.kotlin ?: throw GradleException("Enable Kotlin sources to use Google Fonts.")
                        res.addGeneratedSourceDirectory(generate, GenerateGoogleFonts::resourceDirectory)
                        kotlin.addGeneratedSourceDirectory(generate, GenerateGoogleFonts::kotlinDirectory)
                        variant.sources.assets?.addGeneratedSourceDirectory(generate, GenerateGoogleFonts::assetsDirectory)
                    }
                }
            }
        }
        project.pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
            project.extensions.getByType(KotlinMultiplatformExtension::class.java).targets
                .withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach { target ->
                    target.androidResources.enable = true
                }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureMultiplatform(project: Project, extension: GoogleFontsExtension, generate: TaskProvider<GenerateGoogleFonts>) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        configureComposeResources(project, extension, generate, kotlin.sourceSets.getByName("commonMain"))
        kotlin.targets.configureEach { target ->
            if (target.platformType == KotlinPlatformType.wasm) {
                target.compilations.getByName("main").defaultSourceSet.kotlin.srcDir(generate.flatMap { it.wasmDirectory })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureComposeResources(project: Project, extension: GoogleFontsExtension,
        generate: TaskProvider<GenerateGoogleFonts>, sourceSet: KotlinSourceSet) {
        if (!project.plugins.hasPlugin("org.jetbrains.compose")) throw GradleException("Apply org.jetbrains.compose to bundle Google Fonts in a Compose module.")
        val resources = project.extensions.getByType(ComposeExtension::class.java).extensions.getByType(ResourcesExtension::class.java)
        if (resources.generateResClass == resources.never) throw GradleException("Enable Compose resource accessors to bundle Google Fonts.")
        val resourcePackage = resources.packageOfResClass.ifEmpty {
            fun identifier(value: String) = value.lowercase().replace('-', '_').let {
                if (it.firstOrNull()?.isDigit() == true) "_$it" else it
            }
            val group = identifier(project.group.toString())
            val module = identifier(project.name)
            listOf(group, module, "generated.resources").filter { it.isNotEmpty() }.joinToString(".")
        }
        generate.configure {
            it.multiplatform.set(true)
            it.resourcePackage.set(resourcePackage)
            it.resourceClass.set(resources.nameOfResClass)
            it.resourceDirectory.set(project.layout.buildDirectory.dir("generated/googleFonts/composeResources"))
            it.existingResources.from(extension.resourcesDirectory)
        }
        resources.customDirectory(sourceSet.name, generate.flatMap { it.resourceDirectory })
        sourceSet.kotlin.srcDir(generate.flatMap { it.kotlinDirectory })
        sourceSet.dependencies { implementation(ComposePlugin.CommonComponentsDependencies.resources) }
    }
}
