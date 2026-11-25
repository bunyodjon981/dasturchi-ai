package com.example.data.engine

import com.example.data.model.ServerLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

data class LocalServerStatus(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val rootFolder: String = "web_studio_demo",
    val url: String = "http://127.0.0.1:8080"
)

class LocalWebServer(private val workspaceManager: WorkspaceManager) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _status = MutableStateFlow(LocalServerStatus())
    val status: StateFlow<LocalServerStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    fun start(rootRelativeFolder: String, preferredPort: Int = 8080): Result<LocalServerStatus> {
        stop()
        return runCatching {
            val rootDir = workspaceManager.resolveSafe(rootRelativeFolder)
            if (!rootDir.exists()) rootDir.mkdirs()

            val socket = bindAvailablePort(preferredPort)
            val actualPort = socket.localPort
            serverSocket = socket

            val newStatus = LocalServerStatus(
                isRunning = true,
                port = actualPort,
                rootFolder = rootRelativeFolder,
                url = "http://127.0.0.1:$actualPort"
            )
            _status.value = newStatus
            appendLog("SERVER", "START / ($rootRelativeFolder)", 200, "127.0.0.1")

            acceptJob = scope.launch {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client, rootDir) }
                }
            }
            newStatus
        }
    }

    private fun bindAvailablePort(preferredPort: Int): ServerSocket {
        val candidates = listOf(preferredPort, 8080, 8081, 8088, 9000, 0)
        for (port in candidates) {
            val socket = runCatching {
                ServerSocket(port, 50, InetAddress.getByName("127.0.0.1")).apply {
                    reuseAddress = true
                }
            }.getOrNull()
            if (socket != null) return socket
        }
        return ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        if (_status.value.isRunning) {
            appendLog("SERVER", "STOP", 200, "127.0.0.1")
        }
        _status.value = _status.value.copy(isRunning = false)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun handleClient(client: Socket, rootDir: File) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedOutputStream(socket.getOutputStream())
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val rawPath = parts.getOrNull(1) ?: "/"

            // Drain headers
            while (true) {
                val h = reader.readLine() ?: break
                if (h.isEmpty()) break
            }

            val cleanPath = URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8")
                .trimStart('/')
            val clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"

            if (method != "GET" && method != "HEAD") {
                sendTextResponse(out, 405, "Method Not Allowed", "text/plain", "405 Method Not Allowed")
                appendLog(method, "/$cleanPath", 405, clientIp)
                return
            }

            val requested = if (cleanPath.isEmpty()) rootDir else File(rootDir, cleanPath)
            val rootCanonical = rootDir.canonicalPath
            val reqCanonical = requested.canonicalPath

            if (reqCanonical != rootCanonical && !reqCanonical.startsWith(rootCanonical + File.separator)) {
                sendTextResponse(out, 403, "Forbidden", "text/plain", "403 Forbidden")
                appendLog(method, "/$cleanPath", 403, clientIp)
                return
            }

            val targetFile = if (requested.isDirectory) {
                val indexHtml = File(requested, "index.html")
                if (indexHtml.exists() && indexHtml.isFile) indexHtml else requested
            } else {
                requested
            }

            if (!targetFile.exists()) {
                sendTextResponse(
                    out,
                    404,
                    "Not Found",
                    "text/html; charset=utf-8",
                    "<html><body style='background:#090d16;color:#f1f5f9;font-family:sans-serif;padding:32px'><h2>404 - Fayl topilmadi</h2><p>/$cleanPath</p></body></html>"
                )
                appendLog(method, "/$cleanPath", 404, clientIp)
                return
            }

            if (targetFile.isDirectory) {
                val html = buildDirectoryListingHtml(cleanPath, targetFile)
                sendTextResponse(out, 200, "OK", "text/html; charset=utf-8", html)
                appendLog(method, "/$cleanPath", 200, clientIp)
                return
            }

            val bytes = targetFile.readBytes()
            val mime = guessMimeType(targetFile.extension.lowercase())
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mime\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(header.toByteArray(Charsets.UTF_8))
            if (method != "HEAD") out.write(bytes)
            out.flush()
            appendLog(method, "/$cleanPath", 200, clientIp)
        }
    }

    private fun buildDirectoryListingHtml(relPath: String, dir: File): String {
        val items = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
        val listItems = items.joinToString("\n") { f ->
            val slash = if (f.isDirectory) "/" else ""
            val prefix = if (relPath.isEmpty()) "" else "/$relPath"
            "<li><a href=\"$prefix/${f.name}$slash\" style=\"color:#38bdf8\">${f.name}$slash</a></li>"
        }
        return """
            <html><body style="background:#090d16;color:#f1f5f9;font-family:monospace;padding:24px;">
            <h2 style="color:#00e5a3;">Localhost Indeks: /$relPath</h2>
            <ul>$listItems</ul>
            </body></html>
        """.trimIndent()
    }

    private fun sendTextResponse(
        out: BufferedOutputStream,
        code: Int,
        reason: String,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun guessMimeType(ext: String): String = when (ext) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "application/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "txt", "md", "py", "kt", "xml", "sh" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun appendLog(method: String, path: String, statusCode: Int, clientIp: String) {
        val entry = ServerLogEntry(method = method, path = path, statusCode = statusCode, clientIp = clientIp)
        _logs.value = (listOf(entry) + _logs.value).take(80)
    }
}
