package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.ApiConfigManager
import com.example.data.engine.LocalWebServer
import com.example.data.engine.WorkspaceManager
import com.example.data.model.ApiConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Dasturchi AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Dasturchi AI", appName)
    }

    @Test
    fun `api json config can be saved and reloaded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ApiConfigManager(context)
        manager.saveConfig(
            ApiConfig(
                provider = "groq",
                baseUrl = "https://api.groq.com/openai/v1/",
                model = "llama-3.3-70b-versatile",
                apiKey = "test_key_123"
            )
        )
        val loaded = manager.loadConfig()
        assertEquals("groq", loaded.provider)
        assertEquals("test_key_123", loaded.apiKey)
        assertTrue(manager.apiJsonFile.exists())
    }

    @Test
    fun `workspace scaffolds kotlin project creates zip and inspects swb`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ws = WorkspaceManager(context)

        val scaffolded = ws.scaffoldKotlinAiStudioProject("demo_kt", "Demo App").getOrThrow()
        assertTrue(scaffolded.any { it.endsWith("MainActivity.kt") })

        val zipFile = ws.createZip("demo_kt", "demo_kt.zip").getOrThrow()
        assertTrue(zipFile.exists() && zipFile.length() > 100)

        // Create a mock .swb Sketchware archive and inspect it
        val swbFile = File(ws.workspaceRoot, "sample.swb")
        ZipOutputStream(FileOutputStream(swbFile)).use { zos ->
            zos.putNextEntry(ZipEntry("project"))
            zos.write("{\"sc_id\":\"601\",\"my_app_name\":\"SwbApp\"}".toByteArray())
            zos.closeEntry()
        }
        val entries = ws.inspectArchiveFile("sample.swb").getOrThrow()
        assertEquals(1, entries.size)
        assertTrue(entries[0].isSketchwareJson)
    }

    @Test
    fun `local web server serves index html on localhost`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ws = WorkspaceManager(context)
        val server = LocalWebServer(ws)
        val status = server.start("web_studio_demo", 0).getOrThrow()
        try {
            assertTrue(status.isRunning)
            val client = OkHttpClient()
            val resp = client.newCall(Request.Builder().url(status.url).get().build()).execute()
            assertEquals(200, resp.code)
            val body = resp.body?.string().orEmpty()
            assertTrue(body.contains("Dasturchi AI Web Server"))
            resp.close()
        } finally {
            server.stop()
        }
    }
}
