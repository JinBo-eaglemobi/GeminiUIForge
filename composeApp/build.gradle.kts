import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

// 动态解析版本号
val appVersion = (project.findProperty("versionName")?.toString()?.trim()
    ?: System.getenv("GITHUB_REF_NAME")?.let { tag ->
        if (tag.startsWith("v")) tag.removePrefix("v").substringBefore("-") else null
    }
    ?: try {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
        }.standardOutput.asText.get().trim()
            .let { tag -> if (tag.startsWith("v")) tag.removePrefix("v").substringBefore("-") else null }
    } catch (e: Exception) {
        null
    }
    ?: "1.0.0").let { version ->
    val cleanVersion = version.filter { it.isDigit() || it == '.' }
    val segments = cleanVersion.split(".")
    val finalVer = if (segments.size > 3) {
        segments.take(3).joinToString(".")
    } else cleanVersion
    val regex = Regex("""^\d+(\.\d+){1,2}$""")
    if (regex.matches(finalVer)) finalVer else "1.0.0"
}

// 动态生成版本号文件任务
val generateProjectConfig = tasks.register("generateProjectConfig") {
    val version = appVersion 
    val outputDir = layout.buildDirectory.dir("generated/projectConfig/kotlin/commonMain/org/gemini/ui/forge")
    outputs.dir(outputDir)
    doLast {
        val configFile = outputDir.get().file("ProjectConfig.kt").asFile
        configFile.parentFile.mkdirs()
        configFile.writeText(
            """
            package org.gemini.ui.forge

            object ProjectConfig {
                const val VERSION = "$version"
            }
            """.trimIndent()
        )
    }
}

kotlin {

    android {
        namespace = "org.gemini.ui.forge.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
    }

    jvm()

    js {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                }
                showProgress
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateProjectConfig.map { it.outputs.files.asPath })
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation("org.jetbrains.skiko:skiko-android:0.9.37.3")
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.http)
            implementation(libs.ktor.io)
            implementation(libs.ktor.utils)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation("org.jetbrains.skiko:skiko:0.9.37.3")
            implementation("org.jetbrains.compose.ui:ui-graphics:1.10.0")
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
            implementation(libs.slf4j.api)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    configurations.all {
        resolutionStrategy {
            eachDependency {
                val group = requested.group
                when (group) {
                    "org.jetbrains.kotlinx" -> {
                        val name = requested.name
                        if (name.startsWith("kotlinx-coroutines")) useVersion(libs.versions.kotlinx.coroutines.get())
                    }
                    "org.jetbrains.kotlin" -> {
                        val name = requested.name
                        if (name == "kotlin-stdlib") useVersion(libs.versions.kotlin.get())
                    }
                    "io.ktor" -> useVersion(libs.versions.ktor.get())
                }
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.gemini.ui.forge.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "GeminiUIForge"
            packageVersion = appVersion
            description = "Gemini UI Forge"
            copyright = "Copyright 2026 Gemini"
            windows {
                shortcut = true
                menu = true
            }
        }
    }
}

tasks.withType<JavaExec> {
    systemProperties(
        "stdout.encoding" to "utf-8",
        "stderr.encoding" to "utf-8",
        "sun.stdout.encoding" to "utf-8",
        "sun.stderr.encoding" to "utf-8"
    )
}
