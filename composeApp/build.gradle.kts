import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

// 动态生成版本号文件任务
val generateProjectConfig = tasks.register("generateProjectConfig") {
    val version = appVersion // 捕获到局部变量以兼容 Configuration Cache
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

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

//    listOf(
//        iosArm64(),
//        iosSimulatorArm64()
//    ).forEach { iosTarget ->
//        iosTarget.binaries.framework {
//            baseName = "ComposeApp"
//            isStatic = true
//        }
//    }

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
            // https://mvnrepository.com/artifact/org.jetbrains.skiko/skiko-android
            implementation("org.jetbrains.skiko:skiko-android:0.9.37.3")
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
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
            // Source: https://mvnrepository.com/artifact/org.jetbrains.compose.material/material-icons-extended
            implementation(libs.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.coroutines.core)
            // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-datetime
            implementation(libs.kotlinx.datetime)

            // https://mvnrepository.com/artifact/io.ktor/ktor-client-core
            implementation(libs.ktor.client.core)
            // https://mvnrepository.com/artifact/io.ktor/ktor-client-auth
            implementation(libs.ktor.client.auth)
            // https://mvnrepository.com/artifact/io.ktor/ktor-http
            implementation(libs.ktor.http)
            // https://mvnrepository.com/artifact/io.ktor/ktor-io
            implementation(libs.ktor.io)
            // Source: https://mvnrepository.com/artifact/io.ktor/ktor-utils
            implementation(libs.ktor.utils)
            // https://mvnrepository.com/artifact/io.ktor/ktor-client-content-negotiation
            implementation(libs.ktor.client.content.negotiation)
            // https://mvnrepository.com/artifact/io.ktor/ktor-client-logging
            implementation(libs.ktor.client.logging)
            // https://mvnrepository.com/artifact/io.ktor/ktor-serialization-kotlinx-json
            implementation(libs.ktor.serialization.kotlinx.json)
            // https://mvnrepository.com/artifact/io.coil-kt.coil3/coil-compose
            implementation(libs.coil.compose)
            // https://mvnrepository.com/artifact/io.coil-kt.coil3/coil-network-ktor3
            implementation(libs.coil.network.ktor3)
            // https://mvnrepository.com/artifact/org.jetbrains.skiko/skiko
            implementation("org.jetbrains.skiko:skiko:0.9.37.3")

            implementation("org.jetbrains.compose.ui:ui-graphics:1.10.0")
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-test
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            // 启用 expect-actual 类支持，允许在多平台项目中使用 expect/actual 修饰类
            "-Xexpect-actual-classes"
        )

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

// 动态解析版本号
// 优先顺序：Gradle 参数 (-PversionName) > 环境变量 (GITHUB_REF_NAME) > 本地 Git Tag > 默认值 1.0.0
val appVersion = (project.findProperty("versionName")?.toString()?.trim()
    ?: System.getenv("GITHUB_REF_NAME")?.let { tag ->
        if (tag.startsWith("v")) tag.removePrefix("v").substringBefore("-") else null
    } 
    ?: try {
        // 本地环境尝试获取最近的 tag (Configuration Cache 安全方式)
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
        }.standardOutput.asText.get().trim()
        .let { tag -> if (tag.startsWith("v")) tag.removePrefix("v").substringBefore("-") else null }
    } catch (e: Exception) { null }

    ?: "1.0.0").let { version ->
    // 强制正则校验：必须是 X.Y 或 X.Y.Z 格式（纯数字），否则回退到 1.0.0
    val regex = Regex("""^\d+(\.\d+){1,2}$""")
    if (regex.matches(version)) version else {
        if (version.isNotBlank() && version != "1.0.0") {
            println("Warning: Invalid version format '$version', falling back to '1.0.0'")
        }
        "1.0.0"
    }
}
println("Configuring GeminiUIForge version: $appVersion")

android {
    namespace = "org.gemini.ui.forge"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.gemini.ui.forge"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = appVersion
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("KEY_STORE_FILE") ?: "../app.jks")
            storePassword = project.findProperty("KEY_STORE_PASSWORD")?.toString() ?: ""
            keyAlias = project.findProperty("KEY_ALIAS")?.toString() ?: ""
            keyPassword = project.findProperty("KEY_PASSWORD")?.toString() ?: ""
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "org.gemini.ui.forge.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "GeminiUIForge"
            packageVersion = appVersion
            description = "Gemini UI Forge - 便携版"
            copyright = "© 2026 Gemini"


            // 针对 Windows 的配置
            windows {
                shortcut = true // 是否创建桌面快捷方式
                menu = true     // 是否加入开始菜单
//                upgradeUuid = "..." // 固定的 UUID 方便后续覆盖更新
            }
        }
    }
}


tasks.withType<JavaExec> {
    systemProperties(
        "stdout.encoding" to "utf-8",
        "stderr.encoding" to "utf-8",
        // 兼容老版本
        "sun.stdout.encoding" to "utf-8",
        "sun.stderr.encoding" to "utf-8"
    )
}