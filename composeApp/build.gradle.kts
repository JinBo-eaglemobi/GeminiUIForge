import org.gradle.kotlin.dsl.kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    js(IR) {
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

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            // Source: https://mvnrepository.com/artifact/org.jetbrains.compose.foundation/foundation-desktop
            implementation("org.jetbrains.compose.foundation:foundation-desktop:1.10.0")
            // https://mvnrepository.com/artifact/org.jetbrains.skiko/skiko-android
//            implementation("org.jetbrains.skiko:skiko-android:0.9.37.3")
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
            implementation(libs.kotlinx.coroutines.core)
            // Source: https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-datetime
            api(libs.kotlinx.datetime)

            // https://mvnrepository.com/artifact/io.ktor/ktor-client-core
            implementation(libs.ktor.client.core)
            // https://mvnrepository.com/artifact/io.ktor/ktor-client-auth
            implementation(libs.ktor.client.auth)
            // https://mvnrepository.com/artifact/io.ktor/ktor-client-json
            implementation(libs.ktor.client.json)
            // https://mvnrepository.com/artifact/io.ktor/ktor-client-serialization
            implementation(libs.ktor.client.serialization)
            // https://mvnrepository.com/artifact/io.ktor/ktor-http
            implementation(libs.ktor.http)
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

android {
    namespace = "org.gemini.ui.forge"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.gemini.ui.forge"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.gemini.ui.forge"
            packageVersion = "1.0.0"
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