package com.example.data.engine

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.data.model.ArchiveEntryInfo
import com.example.data.model.WorkspaceFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class WorkspaceManager(private val context: Context) {

    val workspaceRoot: File = File(context.filesDir, "workspace").apply {
        if (!exists()) mkdirs()
    }

    init {
        seedStarterWorkspaceIfEmpty()
    }

    fun resolveSafe(relativePath: String): File {
        val clean = relativePath.trim().trimStart('/')
        val target = if (clean.isEmpty()) workspaceRoot else File(workspaceRoot, clean)
        val rootCanonical = workspaceRoot.canonicalPath
        val targetCanonical = target.canonicalPath
        require(targetCanonical == rootCanonical || targetCanonical.startsWith(rootCanonical + File.separator)) {
            "Xavfsizlik xatosi: ruxsat etilmagan yo'l ($relativePath)"
        }
        return target
    }

    fun listFiles(relativeDir: String = ""): List<WorkspaceFile> {
        val dir = resolveSafe(relativeDir)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        val rootPath = workspaceRoot.canonicalPath
        return children
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                val rel = file.canonicalPath.removePrefix(rootPath).trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')
                WorkspaceFile(
                    name = file.name,
                    relativePath = rel,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) (file.listFiles()?.size?.toLong() ?: 0L) else file.length(),
                    extension = if (file.isDirectory) "" else file.extension.lowercase(),
                    lastModified = file.lastModified()
                )
            }
    }

    fun listAllFilesRecursive(relativeDir: String = "", limit: Int = 200): List<String> {
        val dir = resolveSafe(relativeDir)
        if (!dir.exists()) return emptyList()
        val rootPath = workspaceRoot.canonicalPath
        val results = mutableListOf<String>()
        dir.walkTopDown().forEach { f ->
            if (results.size >= limit) return@forEach
            if (f != dir) {
                val rel = f.canonicalPath.removePrefix(rootPath).trimStart(File.separatorChar)
                    .replace(File.separatorChar, '/')
                results.add(if (f.isDirectory) "$rel/" else rel)
            }
        }
        return results
    }

    fun createFolder(relativePath: String): Result<File> = runCatching {
        val folder = resolveSafe(relativePath)
        if (!folder.exists() && !folder.mkdirs()) {
            error("Papka yaratib bo'lmadi: $relativePath")
        }
        folder
    }

    fun writeFile(relativePath: String, content: String): Result<File> = runCatching {
        val file = resolveSafe(relativePath)
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        file.writeText(content, Charsets.UTF_8)
        file
    }

    fun readFile(relativePath: String): Result<String> = runCatching {
        val file = resolveSafe(relativePath)
        require(file.exists() && file.isFile) { "Fayl topilmadi: $relativePath" }
        file.readText(Charsets.UTF_8)
    }

    fun deleteEntry(relativePath: String): Result<Unit> = runCatching {
        val target = resolveSafe(relativePath)
        require(target.canonicalPath != workspaceRoot.canonicalPath) {
            "Asosiy workspace papkasini o'chirib bo'lmaydi"
        }
        if (target.isDirectory) target.deleteRecursively() else target.delete()
        Unit
    }

    fun createZip(sourceRelativePath: String, destZipRelativePath: String): Result<File> = runCatching {
        val source = resolveSafe(sourceRelativePath)
        require(source.exists()) { "Manba topilmadi: $sourceRelativePath" }
        val zipPath = if (destZipRelativePath.endsWith(".zip", ignoreCase = true) ||
            destZipRelativePath.endsWith(".swb", ignoreCase = true)
        ) destZipRelativePath else "$destZipRelativePath.zip"

        val destFile = resolveSafe(zipPath)
        destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            zipRecursive(source, if (source.canonicalPath == workspaceRoot.canonicalPath) "" else source.name, zos, destFile)
        }
        destFile
    }

    fun exportZipToUri(sourceRelativePath: String, contentResolver: ContentResolver, destUri: Uri): Result<Long> = runCatching {
        val source = resolveSafe(sourceRelativePath)
        require(source.exists()) { "Manba topilmadi: $sourceRelativePath" }
        var totalEntries = 0L
        if (source.isFile && (source.extension.equals("zip", true) || source.extension.equals("swb", true))) {
            contentResolver.openOutputStream(destUri)?.use { out ->
                FileInputStream(source).use { it.copyTo(out) }
            } ?: error("SAF chiqish oqimini ochib bo'lmadi")
            return@runCatching source.length()
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            totalEntries = zipRecursive(
                source,
                if (source.canonicalPath == workspaceRoot.canonicalPath) "" else source.name,
                zos,
                null
            )
        }
        val bytes = baos.toByteArray()
        contentResolver.openOutputStream(destUri)?.use { out ->
            out.write(bytes)
        } ?: error("SAF faylga yozib bo'lmadi")
        if (totalEntries == 0L) bytes.size.toLong() else bytes.size.toLong()
    }

    private fun zipRecursive(file: File, entryName: String, zos: ZipOutputStream, excludeFile: File?): Long {
        if (excludeFile != null && file.canonicalPath == excludeFile.canonicalPath) return 0L
        var count = 0L
        if (file.isDirectory) {
            val children = file.listFiles() ?: emptyArray()
            if (entryName.isNotEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            }
            for (child in children) {
                val childName = if (entryName.isEmpty()) child.name else "$entryName/${child.name}"
                count += zipRecursive(child, childName, zos, excludeFile)
            }
        } else {
            val name = entryName.ifEmpty { file.name }
            zos.putNextEntry(ZipEntry(name))
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
            count++
        }
        return count
    }

    fun inspectArchiveFile(relativePath: String): Result<List<ArchiveEntryInfo>> = runCatching {
        val file = resolveSafe(relativePath)
        require(file.exists() && file.isFile) { "Arxiv topilmadi: $relativePath" }
        FileInputStream(file).use { parseArchiveStream(it) }
    }

    fun inspectArchiveFromUri(contentResolver: ContentResolver, uri: Uri): Result<List<ArchiveEntryInfo>> = runCatching {
        contentResolver.openInputStream(uri)?.use { parseArchiveStream(it) }
            ?: error("Arxivni ochib bo'lmadi")
    }

    private fun parseArchiveStream(inputStream: InputStream): List<ArchiveEntryInfo> {
        val entries = mutableListOf<ArchiveEntryInfo>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val bytes = if (!entry.isDirectory) readEntryBytesCapped(zis, 128 * 1024) else ByteArray(0)
                val decoded = if (bytes.isNotEmpty()) decodeArchiveEntryText(entry.name, bytes) else ""
                val isSw = entry.name in setOf("project", "view", "logic", "file", "library", "resource") ||
                    decoded.trimStart().startsWith("{")
                entries.add(
                    ArchiveEntryInfo(
                        name = entry.name,
                        sizeBytes = if (entry.size >= 0) entry.size else bytes.size.toLong(),
                        isDirectory = entry.isDirectory,
                        previewText = decoded.take(3000),
                        isSketchwareJson = isSw
                    )
                )
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    fun extractArchiveFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        targetRelativeFolder: String
    ): Result<List<String>> = runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
            extractArchiveStream(stream, targetRelativeFolder)
        } ?: error("Arxivni ochib bo'lmadi")
    }

    fun extractArchiveFile(
        archiveRelativePath: String,
        targetRelativeFolder: String
    ): Result<List<String>> = runCatching {
        val file = resolveSafe(archiveRelativePath)
        require(file.exists() && file.isFile) { "Arxiv topilmadi: $archiveRelativePath" }
        FileInputStream(file).use { extractArchiveStream(it, targetRelativeFolder) }
    }

    private fun extractArchiveStream(inputStream: InputStream, targetRelativeFolder: String): List<String> {
        val destDir = resolveSafe(targetRelativeFolder).apply { if (!exists()) mkdirs() }
        val destCanonical = destDir.canonicalPath
        val extracted = mutableListOf<String>()

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                // Zip Slip protection
                val outCanonical = outFile.canonicalPath
                if (outCanonical != destCanonical && !outCanonical.startsWith(destCanonical + File.separator)) {
                    throw SecurityException("Zip Slip xavfi aniqlandi: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    extracted.add(entry.name)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extracted
    }

    private fun readEntryBytesCapped(zis: ZipInputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream()
        var total = 0
        while (total < maxBytes) {
            val read = zis.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun decodeArchiveEntryText(name: String, bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (isMostlyPrintable(utf8)) return utf8

        // Try zlib inflate (used in some Sketchware .swb payloads)
        runCatching {
            val inflater = Inflater()
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            while (!inflater.finished() && out.size() < 65536) {
                val count = inflater.inflate(buf)
                if (count == 0) break
                out.write(buf, 0, count)
            }
            inflater.end()
            val inflatedText = out.toString(Charsets.UTF_8.name())
            if (inflatedText.isNotBlank() && isMostlyPrintable(inflatedText)) {
                return "[ZLIB/SWB Dekodlangan: $name]\n$inflatedText"
            }
        }

        return "[Binar ma'lumot: ${bytes.size} bayt — $name]"
    }

    private fun isMostlyPrintable(text: String): Boolean {
        if (text.isEmpty()) return true
        val sample = text.take(256)
        val printable = sample.count { it == '\n' || it == '\r' || it == '\t' || (it.code in 32..126) || it.code > 159 }
        return printable.toFloat() / sample.length >= 0.80f
    }

    fun scaffoldKotlinAiStudioProject(
        projectFolder: String,
        appTitle: String,
        packageName: String = "com.example",
        mainActivityCode: String? = null
    ): Result<List<String>> = runCatching {
        val cleanFolder = projectFolder.trim().trim('/').ifBlank { "kotlin_aistudio_app" }
        val createdFiles = mutableListOf<String>()

        fun add(rel: String, text: String) {
            val path = "$cleanFolder/$rel"
            writeFile(path, text).getOrThrow()
            createdFiles.add(path)
        }

        add("settings.gradle.kts", """
            pluginManagement {
              repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
              }
            }
            dependencyResolutionManagement {
              repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
              repositories {
                google()
                mavenCentral()
              }
            }
            rootProject.name = "$appTitle"
            include(":app")
        """.trimIndent())

        add("build.gradle.kts", """
            plugins {
              alias(libs.plugins.android.application) apply false
              alias(libs.plugins.kotlin.compose) apply false
            }
        """.trimIndent())

        add("gradle/libs.versions.toml", """
            [versions]
            agp = "8.9.1"
            kotlin = "2.1.20"
            coreKtx = "1.16.0"
            activityCompose = "1.10.1"
            composeBom = "2025.04.01"

            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
            androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
            androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
            androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }

            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
            kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
        """.trimIndent())

        add("app/build.gradle.kts", """
            plugins {
              alias(libs.plugins.android.application)
              alias(libs.plugins.kotlin.compose)
            }

            android {
              namespace = "$packageName"
              compileSdk = 35

              defaultConfig {
                applicationId = "$packageName.${cleanFolder.lowercase().filter { it.isLetterOrDigit() }.take(8)}"
                minSdk = 24
                targetSdk = 35
                versionCode = 1
                versionName = "1.0"
              }

              compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
              }
              buildFeatures {
                compose = true
              }
            }

            dependencies {
              implementation(platform(libs.androidx.compose.bom))
              implementation(libs.androidx.core.ktx)
              implementation(libs.androidx.activity.compose)
              implementation(libs.androidx.compose.ui)
              implementation(libs.androidx.compose.material3)
            }
        """.trimIndent())

        add("app/src/main/AndroidManifest.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:label="@string/app_name"
                    android:theme="@android:style/Theme.Material.Light.NoActionBar">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent())

        add("app/src/main/res/values/strings.xml", """
            <resources>
                <string name="app_name">$appTitle</string>
            </resources>
        """.trimIndent())

        val pkgPath = packageName.replace('.', '/')
        val activitySrc = mainActivityCode?.takeIf { it.isNotBlank() } ?: """
            package $packageName

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    enableEdgeToEdge()
                    setContent {
                        MaterialTheme(colorScheme = darkColorScheme()) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                MainScreen()
                            }
                        }
                    }
                }
            }

            @Composable
            fun MainScreen() {
                var counter by remember { mutableIntStateOf(0) }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$appTitle",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Google AI Studio uchun Kotlin + Compose ilovasi")
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { counter++ }) {
                        Text("Bosildi: ${'$'}counter")
                    }
                }
            }
        """.trimIndent()

        add("app/src/main/java/$pkgPath/MainActivity.kt", activitySrc)

        add("metadata.json", """
            {
              "name": "$appTitle",
              "description": "Dasturchi AI tomonidan yaratilgan Kotlin Jetpack Compose loyihasi",
              "requestFramePermissions": [],
              "majorCapabilities": ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]
            }
        """.trimIndent())

        createdFiles
    }

    private fun seedStarterWorkspaceIfEmpty() {
        val existing = workspaceRoot.listFiles()
        if (existing != null && existing.isNotEmpty()) return

        writeFile("python_lab/tahlil_va_hisob.py", """
            # Dasturchi AI - Universal Python Skript Namunasi
            # Bu yerda nafaqat bot, balki istalgan Python dasturini ishga tushira olasiz!
            import math
            import random
            import time

            print("=== Dasturchi AI Python Studio ===")
            print(f"Vaqt: {time.ctime()}")

            sonlar = [12, 24, 36, 48, 60]
            print(f"Sonlar ro'yxati: {sonlar}")
            print(f"Yig'indi: {sum(sonlar)}")

            def kvadrat_ildiz_hisobla(x):
                return math.sqrt(x)

            for i in range(1, 5):
                tasodifiy = random.randint(10, 99)
                ildiz = kvadrat_ildiz_hisobla(tasodifiy)
                print(f"{i}-son: {tasodifiy} -> ildizi: {ildiz}")

            def main(kirish):
                return f"Python funksiya javobi: '{kirish}' qayta ishlandi!"
        """.trimIndent())

        writeFile("telegram_echo_bot/bot.py", """
            # Dasturchi AI - Python Telegram Bot namunasi
            # Haqiqiy botni ishga tushirish uchun BotFather tokenini qo'ying:
            TOKEN = "YOUR_TELEGRAM_BOT_TOKEN"

            import time
            import json

            print("Telegram bot tayyorlanmoqda...")

            def handle_start(user_name):
                return f"Salom {user_name}! Men Dasturchi AI da ishlayotgan Python Telegram botman."

            def handle_help(user_name):
                return "Buyruqlar: /start, /help, /time yoki istalgan matn yuboring."

            def handle_message(text, user_name):
                if text == "/start":
                    return handle_start(user_name)
                elif text == "/help":
                    return handle_help(user_name)
                elif text == "/time":
                    return f"Hozirgi vaqt: {time.ctime()}"
                else:
                    return f"Siz yozdingiz: {text}"

            print("Bot handlerlari yuklandi: /start, /help, /time")
        """.trimIndent())

        writeFile("web_studio_demo/index.html", """
            <!DOCTYPE html>
            <html lang="uz">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Dasturchi AI Localhost</title>
              <style>
                body {
                  margin: 0; font-family: system-ui, sans-serif;
                  background: #090d16; color: #f1f5f9;
                  display: flex; align-items: center; justify-content: center;
                  min-height: 100vh; padding: 24px;
                }
                .card {
                  max-width: 520px; width: 100%;
                  background: #111827; border: 1px solid #28354d;
                  border-radius: 20px; padding: 28px;
                  box-shadow: 0 20px 50px rgba(0, 229, 163, 0.08);
                }
                .badge {
                  display: inline-block; padding: 6px 12px; border-radius: 999px;
                  background: rgba(0, 229, 163, 0.14); color: #00e5a3;
                  font-size: 12px; font-weight: 700; letter-spacing: 0.5px;
                }
                h1 { margin: 14px 0 8px; font-size: 26px; }
                p { color: #94a3b8; line-height: 1.6; }
                button {
                  margin-top: 16px; background: #00e5a3; color: #002114;
                  border: 0; padding: 12px 18px; border-radius: 12px;
                  font-weight: 700; cursor: pointer;
                }
              </style>
            </head>
            <body>
              <div class="card">
                <span class="badge">LOCALHOST LIVE • 127.0.0.1</span>
                <h1>Dasturchi AI Web Server</h1>
                <p>Ushbu sahifa telefoningizning o'zida ishlayotgan mahalliy HTTP server orqali uzatilmoqda.</p>
                <button onclick="document.getElementById('status').innerText='Server faol: ' + new Date().toLocaleTimeString()">
                  Serverni tekshirish
                </button>
                <p id="status" style="color:#38bdf8;font-weight:600;"></p>
              </div>
            </body>
            </html>
        """.trimIndent())

        scaffoldKotlinAiStudioProject(
            projectFolder = "kotlin_aistudio_sample",
            appTitle = "Salom Kotlin"
        )
    }
}
