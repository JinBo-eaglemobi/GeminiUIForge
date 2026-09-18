import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.uiToolingPreview)
}

// 统一解析并合并全生命周期 JVM 启动参数（单一真实数据源，构建、运行、打包、热重载处处生效）
fun resolveUnifiedJvmArgs(): List<String> {
    val unifiedArgs = mutableListOf(
        "--enable-native-access=ALL-UNNAMED"
    )
    val userHome = System.getProperty("user.home")
    val vmOptionsFile = File(userHome, ".geminiuiforge/app.vmoptions")
    if (vmOptionsFile.exists()) {
        val customArgs = vmOptionsFile.readLines().map { it.trim() }.filter { it.startsWith("-") }
        unifiedArgs.addAll(customArgs)
    } else {
        unifiedArgs.add("-Xmx1G")
        unifiedArgs.add("-Xms512M")
    }
    return unifiedArgs.distinct()
}

compose.desktop {
    application {
        mainClass = "org.gemini.ui.forge.MainKt"
        jvmArgs(*resolveUnifiedJvmArgs().toTypedArray())
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "GeminiUIForge"
            packageVersion = appVersion
            description = "Gemini UI Forge"
            copyright = "Copyright 2026 Gemini"
            modules("jdk.management", "jdk.management.agent", "jdk.attach", "jdk.jcmd", "jdk.internal.jvmstat")
            windows {
                shortcut = true
                menu = true
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(resolveUnifiedJvmArgs())

    systemProperties(
        "stdout.encoding" to "utf-8",
        "stderr.encoding" to "utf-8",
        "sun.stdout.encoding" to "utf-8",
        "sun.stderr.encoding" to "utf-8"
    )
}
