package com.example.data.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class TerminalCommandResult(
    val exitCode: Int,
    val outputLines: List<String>,
    val errorLines: List<String>
)

class TerminalExecutor(private val workspaceManager: WorkspaceManager) {

    suspend fun execute(
        rawCommand: String,
        currentSubDir: String = "",
        onLine: (String, Boolean) -> Unit = { _, _ -> }
    ): TerminalCommandResult = withContext(Dispatchers.IO) {
        val command = rawCommand.trim()
        if (command.isEmpty()) {
            return@withContext TerminalCommandResult(0, emptyList(), emptyList())
        }

        // Built-in helpful commands for sandboxed Android environment
        when {
            command == "help" -> {
                val helpLines = listOf(
                    "Dasturchi AI Terminal Buyruqlari:",
                    "  ls, pwd, mkdir <nom>, touch <fayl>, cat <fayl>, rm <fayl>",
                    "  tree            - Barcha fayl va papkalar daraxti",
                    "  zip <manba> <arxiv.zip> - Papka yoki faylni ZIP qilish",
                    "  uname -a, date, echo, grep, wc, head, tail, df -h"
                )
                helpLines.forEach { onLine(it, false) }
                return@withContext TerminalCommandResult(0, helpLines, emptyList())
            }
            command == "tree" -> {
                val tree = workspaceManager.listAllFilesRecursive(currentSubDir)
                if (tree.isEmpty()) {
                    onLine("(bo'sh papka)", false)
                } else {
                    tree.forEach { onLine(it, false) }
                }
                return@withContext TerminalCommandResult(0, tree, emptyList())
            }
            command.startsWith("zip ") -> {
                val parts = command.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (parts.size >= 3) {
                    val res = workspaceManager.createZip(parts[1], parts[2])
                    return@withContext res.fold(
                        onSuccess = { file ->
                            val msg = "ZIP yaratildi: ${file.name} (${file.length()} bayt)"
                            onLine(msg, false)
                            TerminalCommandResult(0, listOf(msg), emptyList())
                        },
                        onFailure = { err ->
                            val msg = "ZIP xatosi: ${err.message}"
                            onLine(msg, true)
                            TerminalCommandResult(1, emptyList(), listOf(msg))
                        }
                    )
                }
            }
        }

        val workingDir: File = runCatching { workspaceManager.resolveSafe(currentSubDir) }
            .getOrElse { workspaceManager.workspaceRoot }

        val outLines = mutableListOf<String>()
        val errLines = mutableListOf<String>()

        runCatching {
            val pb = ProcessBuilder("/system/bin/sh", "-c", command)
            pb.directory(workingDir)
            pb.environment()["HOME"] = workspaceManager.workspaceRoot.absolutePath
            pb.environment()["PWD"] = workingDir.absolutePath
            val process = pb.start()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                val l = line ?: continue
                outLines.add(l)
                onLine(l, false)
            }
            while (stderrReader.readLine().also { line = it } != null) {
                val l = line ?: continue
                errLines.add(l)
                onLine(l, true)
            }

            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                val timeoutMsg = "Buyruq vaqt limiti (15s) tugadi"
                errLines.add(timeoutMsg)
                onLine(timeoutMsg, true)
                return@withContext TerminalCommandResult(124, outLines, errLines)
            }
            TerminalCommandResult(process.exitValue(), outLines, errLines)
        }.getOrElse { ex ->
            val msg = "Terminal xatosi: ${ex.message}"
            errLines.add(msg)
            onLine(msg, true)
            TerminalCommandResult(1, outLines, errLines)
        }
    }
}
