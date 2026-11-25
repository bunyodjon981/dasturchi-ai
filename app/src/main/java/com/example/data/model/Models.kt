package com.example.data.model

import java.util.UUID

data class ApiConfig(
    val provider: String = "groq",
    val baseUrl: String = "https://api.groq.com/openai/v1/",
    val model: String = "auto",
    val apiKey: String = "",
    val temperature: Double = 0.35,
    val maxTokens: Int = 4096
)

data class WorkspaceFile(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val extension: String,
    val lastModified: Long
)

enum class OperationType {
    THINKING,
    CREATE_FOLDER,
    WRITE_FILE,
    READ_FILE,
    ZIP_CREATE,
    ZIP_EXTRACT,
    SWB_INSPECT,
    TERMINAL,
    SERVER,
    BOT_RUN,
    KOTLIN_SCAFFOLD,
    AUTOMATION
}

enum class OperationStatus {
    RUNNING,
    SUCCESS,
    ERROR
}

data class LiveOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: OperationType,
    val title: String,
    val detail: String,
    val codePreview: String = "",
    val status: OperationStatus = OperationStatus.RUNNING,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val attachedOps: List<LiveOperation> = emptyList()
)

data class TerminalLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isError: Boolean = false,
    val isCommand: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class ServerLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val method: String,
    val path: String,
    val statusCode: Int,
    val clientIp: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class BotRuntimeState(
    val isRunning: Boolean = false,
    val scriptPath: String = "",
    val botUsername: String = "",
    val updatesProcessed: Int = 0,
    val activeHandlers: List<String> = emptyList(),
    val logs: List<TerminalLine> = emptyList()
)

data class AutomationTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val taskType: String, // "PYTHON_SCRIPT", "SHELL_COMMAND", "SERVER_HEALTH"
    val target: String,
    val intervalSeconds: Int = 30,
    val isEnabled: Boolean = false,
    val lastRunMs: Long = 0L,
    val lastResult: String = "Hali ishga tushirilmagan"
)

data class ArchiveEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val previewText: String = "",
    val isSketchwareJson: Boolean = false
)
