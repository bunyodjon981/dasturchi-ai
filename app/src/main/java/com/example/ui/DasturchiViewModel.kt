package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.ApiConfigManager
import com.example.data.engine.AutomationScheduler
import com.example.data.engine.GroqAgentClient
import com.example.data.engine.LocalServerStatus
import com.example.data.engine.LocalWebServer
import com.example.data.engine.PythonBotRunner
import com.example.data.engine.TerminalExecutor
import com.example.data.engine.WorkspaceManager
import com.example.data.model.ApiConfig
import com.example.data.model.ArchiveEntryInfo
import com.example.data.model.AutomationTask
import com.example.data.model.BotRuntimeState
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRole
import com.example.data.model.LiveOperation
import com.example.data.model.OperationStatus
import com.example.data.model.OperationType
import com.example.data.model.ServerLogEntry
import com.example.data.model.TerminalLine
import com.example.data.model.WorkspaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DasturchiViewModel(application: Application) : AndroidViewModel(application) {

    val apiConfigManager = ApiConfigManager(application)
    val workspaceManager = WorkspaceManager(application)
    val terminalExecutor = TerminalExecutor(workspaceManager)
    val localWebServer = LocalWebServer(workspaceManager)
    val pythonBotRunner = PythonBotRunner(workspaceManager)
    val automationScheduler = AutomationScheduler(
        application,
        terminalExecutor,
        pythonBotRunner,
        localWebServer
    )

    private val groqAgent = GroqAgentClient(
        apiConfigManager,
        workspaceManager,
        terminalExecutor,
        localWebServer,
        pythonBotRunner
    )

    private val _apiConfig = MutableStateFlow(apiConfigManager.loadConfig())
    val apiConfig: StateFlow<ApiConfig> = _apiConfig.asStateFlow()

    private val _apiRawJson = MutableStateFlow(apiConfigManager.readRawJson())
    val apiRawJson: StateFlow<String> = _apiRawJson.asStateFlow()

    private val _liveOperations = MutableStateFlow<List<LiveOperation>>(emptyList())
    val liveOperations: StateFlow<List<LiveOperation>> = _liveOperations.asStateFlow()

    private val _latestGeneratedZipPath = MutableStateFlow<String?>(null)
    val latestGeneratedZipPath: StateFlow<String?> = _latestGeneratedZipPath.asStateFlow()

    private val _chatMessages = MutableStateFlow(
        listOf(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                text = "Salom! Men Dasturchi AI — Groq AI asosidagi mustaqil dasturchi studiyangizman. Nima buyursangiz ham (istalgan dasturlash tilida kod, Python skriptlar, veb-sayt va localhost server, Google AI Studio Kotlin ilova + ZIP, ZIP/SWB arxivlar, terminal buyruqlari) o'zim o'ylab, real vaqtda bajarib beraman."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAgentBusy = MutableStateFlow(false)
    val isAgentBusy: StateFlow<Boolean> = _isAgentBusy.asStateFlow()

    private val _currentFolder = MutableStateFlow("")
    val currentFolder: StateFlow<String> = _currentFolder.asStateFlow()

    private val _files = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val files: StateFlow<List<WorkspaceFile>> = _files.asStateFlow()

    private val _allFileCount = MutableStateFlow(0)
    val allFileCount: StateFlow<Int> = _allFileCount.asStateFlow()

    private val _openedFilePath = MutableStateFlow<String?>(null)
    val openedFilePath: StateFlow<String?> = _openedFilePath.asStateFlow()

    private val _openedFileContent = MutableStateFlow("")
    val openedFileContent: StateFlow<String> = _openedFileContent.asStateFlow()

    private val _inspectedArchiveName = MutableStateFlow<String?>(null)
    val inspectedArchiveName: StateFlow<String?> = _inspectedArchiveName.asStateFlow()

    private val _inspectedEntries = MutableStateFlow<List<ArchiveEntryInfo>>(emptyList())
    val inspectedEntries: StateFlow<List<ArchiveEntryInfo>> = _inspectedEntries.asStateFlow()

    private val _pendingExportSourcePath = MutableStateFlow<String?>(null)

    private val _terminalLines = MutableStateFlow(
        listOf(
            TerminalLine(text = "Dasturchi AI Terminal • Yordam uchun 'help' yozing")
        )
    )
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    val serverStatus: StateFlow<LocalServerStatus> = localWebServer.status
    val serverLogs: StateFlow<List<ServerLogEntry>> = localWebServer.logs
    val botState: StateFlow<BotRuntimeState> = pythonBotRunner.state
    val automationTasks: StateFlow<List<AutomationTask>> = automationScheduler.tasks

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    init {
        syncApiJsonToWorkspace()
        refreshFiles()
    }

    private fun syncApiJsonToWorkspace() {
        runCatching {
            workspaceManager.writeFile("api.json", apiConfigManager.readRawJson())
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun refreshFiles() {
        _files.value = workspaceManager.listFiles(_currentFolder.value)
        _allFileCount.value = workspaceManager.listAllFilesRecursive(limit = 500).size
    }

    fun navigateToFolder(relativePath: String) {
        _currentFolder.value = relativePath
        refreshFiles()
    }

    fun navigateUp() {
        val cur = _currentFolder.value
        if (cur.isEmpty()) return
        val parent = cur.substringBeforeLast('/', "")
        _currentFolder.value = parent
        refreshFiles()
    }

    fun createFolder(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val rel = if (_currentFolder.value.isEmpty()) clean else "${_currentFolder.value}/$clean"
        val op = addLiveOp(OperationType.CREATE_FOLDER, "Papka yaratilmoqda", rel)
        workspaceManager.createFolder(rel).fold(
            onSuccess = {
                updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "Yaratildi: $rel"))
                refreshFiles()
                showBanner("Papka yaratildi: $clean")
            },
            onFailure = { err ->
                updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                showBanner("Xato: ${err.message}")
            }
        )
    }

    fun createFile(name: String, initialContent: String = "") {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val rel = if (_currentFolder.value.isEmpty()) clean else "${_currentFolder.value}/$clean"
        val op = addLiveOp(OperationType.WRITE_FILE, "Fayl yaratilmoqda", rel, initialContent.take(300))
        workspaceManager.writeFile(rel, initialContent).fold(
            onSuccess = {
                updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "Saqlandi: $rel"))
                refreshFiles()
                openFileForEditing(rel)
            },
            onFailure = { err ->
                updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                showBanner("Xato: ${err.message}")
            }
        )
    }

    fun openFileForEditing(relativePath: String) {
        workspaceManager.readFile(relativePath).fold(
            onSuccess = { text ->
                _openedFilePath.value = relativePath
                _openedFileContent.value = text
            },
            onFailure = { showBanner("Faylni ochib bo'lmadi: ${it.message}") }
        )
    }

    fun updateOpenedFileDraft(newText: String) {
        _openedFileContent.value = newText
    }

    fun saveOpenedFile() {
        val path = _openedFilePath.value ?: return
        val content = _openedFileContent.value
        val op = addLiveOp(OperationType.WRITE_FILE, "Fayl saqlanmoqda", path, content.take(300))

        // If user edited workspace/api.json directly in the file editor, keep ApiConfigManager in sync!
        if (path == "api.json") {
            val ok = saveApiRawJson(content)
            if (ok) {
                updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "api.json yangilandi"))
            } else {
                updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = "JSON format xatosi"))
            }
            return
        }

        workspaceManager.writeFile(path, content).fold(
            onSuccess = {
                updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "Saqlandi: $path"))
                refreshFiles()
                showBanner("Saqlandi: $path")
            },
            onFailure = { err ->
                updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                showBanner("Xato: ${err.message}")
            }
        )
    }

    fun closeFileEditor() {
        _openedFilePath.value = null
    }

    fun deleteEntry(relativePath: String) {
        workspaceManager.deleteEntry(relativePath).fold(
            onSuccess = {
                refreshFiles()
                showBanner("O'chirildi: $relativePath")
            },
            onFailure = { showBanner("Xato: ${it.message}") }
        )
    }

    fun createZipInWorkspace(sourceRelPath: String, zipName: String) {
        val op = addLiveOp(OperationType.ZIP_CREATE, "ZIP yaratilmoqda", "$sourceRelPath -> $zipName")
        viewModelScope.launch(Dispatchers.IO) {
            workspaceManager.createZip(sourceRelPath, zipName).fold(
                onSuccess = { file ->
                    val rel = file.canonicalPath
                        .removePrefix(workspaceManager.workspaceRoot.canonicalPath)
                        .trimStart('/')
                    _latestGeneratedZipPath.value = rel
                    updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "${file.name} (${file.length()} bayt)"))
                    refreshFiles()
                    showBanner("ZIP yaratildi: ${file.name}")
                },
                onFailure = { err ->
                    updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                    showBanner("ZIP xatosi: ${err.message}")
                }
            )
        }
    }

    fun prepareExportZip(sourceRelPath: String) {
        _pendingExportSourcePath.value = sourceRelPath
    }

    fun completeExportZip(destUri: Uri) {
        val src = _pendingExportSourcePath.value ?: _currentFolder.value
        _pendingExportSourcePath.value = null
        val op = addLiveOp(OperationType.ZIP_CREATE, "Telefonga ZIP yuklanmoqda", src)
        viewModelScope.launch(Dispatchers.IO) {
            workspaceManager.exportZipToUri(src, getApplication<Application>().contentResolver, destUri).fold(
                onSuccess = { bytes ->
                    updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "Telefonga saqlandi ($bytes bayt)"))
                    showBanner("ZIP telefoningizga saqlandi ($bytes bayt)")
                },
                onFailure = { err ->
                    updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                    showBanner("Yuklash xatosi: ${err.message}")
                }
            )
        }
    }

    fun inspectWorkspaceArchive(relativePath: String) {
        val op = addLiveOp(OperationType.SWB_INSPECT, "Arxiv ochilmoqda", relativePath)
        viewModelScope.launch(Dispatchers.IO) {
            workspaceManager.inspectArchiveFile(relativePath).fold(
                onSuccess = { entries ->
                    _inspectedArchiveName.value = relativePath
                    _inspectedEntries.value = entries
                    updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "${entries.size} ta element o'qildi"))
                },
                onFailure = { err ->
                    updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                    showBanner("Arxiv xatosi: ${err.message}")
                }
            )
        }
    }

    fun inspectAndExtractExternalArchive(uri: Uri, extractToWorkspace: Boolean) {
        val resolver = getApplication<Application>().contentResolver
        val op = addLiveOp(OperationType.SWB_INSPECT, "ZIP/SWB o'qilmoqda", uri.lastPathSegment ?: "arxiv")
        viewModelScope.launch(Dispatchers.IO) {
            workspaceManager.inspectArchiveFromUri(resolver, uri).fold(
                onSuccess = { entries ->
                    _inspectedArchiveName.value = uri.lastPathSegment ?: "import_arxiv"
                    _inspectedEntries.value = entries
                    if (extractToWorkspace) {
                        val folderName = "imported_${System.currentTimeMillis() % 10000}"
                        workspaceManager.extractArchiveFromUri(resolver, uri, folderName)
                        refreshFiles()
                        updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "${entries.size} ta fayl '$folderName' ga ochildi"))
                        showBanner("Arxiv '$folderName' papkasiga ochildi")
                    } else {
                        updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "${entries.size} ta element ko'rildi"))
                    }
                },
                onFailure = { err ->
                    updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                    showBanner("Arxivni o'qib bo'lmadi: ${err.message}")
                }
            )
        }
    }

    fun extractWorkspaceArchive(archiveRelPath: String) {
        val targetFolder = archiveRelPath.substringAfterLast('/').substringBeforeLast('.') + "_ochilgan"
        val op = addLiveOp(OperationType.ZIP_EXTRACT, "Arxiv ochilmoqda", "$archiveRelPath -> $targetFolder")
        viewModelScope.launch(Dispatchers.IO) {
            workspaceManager.extractArchiveFile(archiveRelPath, targetFolder).fold(
                onSuccess = { list ->
                    updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "${list.size} ta fayl chiqarildi"))
                    refreshFiles()
                    showBanner("${list.size} ta fayl '$targetFolder' papkasiga chiqarildi")
                },
                onFailure = { err ->
                    updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                    showBanner("Ochish xatosi: ${err.message}")
                }
            )
        }
    }

    fun clearInspectedArchive() {
        _inspectedArchiveName.value = null
        _inspectedEntries.value = emptyList()
    }

    /**
     * One-tap autonomous Google AI Studio Kotlin + Compose project generation & ZIP packaging.
     * Asks zero questions from the user: AI thinks up a real app, writes all files live, and zips it!
     */
    fun autoGenerateKotlinAiStudioZip() {
        val autoPrompt = """
            Google AI Studio uchun foydali va chiroyli Kotlin + Jetpack Compose ilovasini HECH NARSA SO'RAMASDAN o'zing mustaqil o'ylab yarat!
            1) Yangi loyiha papkasi uchun qisqa nom tanla (masalan: aistudio_smart_app_${System.currentTimeMillis() % 1000}) va chiroyli ilova nomi qo'y.
            2) `scaffold_kotlin_ai_studio_app` tool'ini to'liq, ishlaydigan va zamonaviy Material 3 Jetpack Compose `MainActivity.kt` kodi bilan chaqir.
            3) Keyin darhol `create_zip` tool'i bilan o'sha papkani `.zip` arxiviga o'ra!
            Barcha jarayonni foydalanuvchiga qisqa o'zbekcha tushuntirib ber.
        """.trimIndent()
        sendPromptToGroq(autoPrompt, displayAsUserText = "Google AI Studio uchun avtomatik Kotlin ilova yasab ZIP qilib ber")
    }

    fun sendPromptToGroq(prompt: String, displayAsUserText: String = prompt) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _isAgentBusy.value) return

        val userMsg = ChatMessage(role = ChatRole.USER, text = displayAsUserText)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAgentBusy.value = true
        _latestGeneratedZipPath.value = null

        val currentRunOps = mutableListOf<LiveOperation>()
        val historyPairs = _chatMessages.value
            .filter { it.role != ChatRole.SYSTEM }
            .dropLast(1)
            .map { (if (it.role == ChatRole.USER) "user" else "assistant") to it.text }

        viewModelScope.launch(Dispatchers.IO) {
            val result = groqAgent.runAgentPrompt(
                userPrompt = clean,
                historyPairs = historyPairs,
                onOpStarted = { op ->
                    currentRunOps.add(op)
                    addLiveOpDirect(op)
                },
                onOpUpdated = { updated ->
                    val idx = currentRunOps.indexOfFirst { it.id == updated.id }
                    if (idx >= 0) currentRunOps[idx] = updated
                    updateLiveOp(updated)
                    refreshFiles()
                },
                onZipReady = { zipRelPath ->
                    _latestGeneratedZipPath.value = zipRelPath
                }
            )

            refreshFiles()
            result.fold(
                onSuccess = { reply ->
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        role = ChatRole.ASSISTANT,
                        text = reply,
                        attachedOps = currentRunOps.toList()
                    )
                },
                onFailure = { err ->
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        role = ChatRole.SYSTEM,
                        text = "Xatolik: ${err.message}",
                        attachedOps = currentRunOps.toList()
                    )
                }
            )
            _isAgentBusy.value = false
        }
    }

    fun clearLiveOperations() {
        _liveOperations.value = emptyList()
    }

    fun dismissLatestZipBanner() {
        _latestGeneratedZipPath.value = null
    }

    fun runTerminalCommand(command: String) {
        val clean = command.trim()
        if (clean.isEmpty()) return
        if (clean == "clear") {
            _terminalLines.value = emptyList()
            return
        }
        _terminalLines.value = (_terminalLines.value + TerminalLine(text = "$ $clean", isCommand = true)).takeLast(200)
        val op = addLiveOp(OperationType.TERMINAL, "Terminal buyrug'i", "$ $clean")

        viewModelScope.launch(Dispatchers.IO) {
            val res = terminalExecutor.execute(clean, _currentFolder.value) { line, isErr ->
                _terminalLines.value = (_terminalLines.value + TerminalLine(text = line, isError = isErr)).takeLast(200)
            }
            updateLiveOp(
                op.copy(
                    status = if (res.exitCode == 0) OperationStatus.SUCCESS else OperationStatus.ERROR,
                    detail = "Exit code: ${res.exitCode}"
                )
            )
            refreshFiles()
        }
    }

    fun startLocalServer(rootFolder: String, port: Int) {
        val op = addLiveOp(OperationType.SERVER, "Localhost Server yoqilmoqda", "$rootFolder : $port")
        localWebServer.start(rootFolder, port).fold(
            onSuccess = { st ->
                updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "Faol: ${st.url}"))
                showBanner("Localhost server faol: ${st.url}")
            },
            onFailure = { err ->
                updateLiveOp(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                showBanner("Server xatosi: ${err.message}")
            }
        )
    }

    fun stopLocalServer() {
        localWebServer.stop()
        showBanner("Localhost server to'xtatildi")
    }

    fun startPythonBot(scriptPath: String) {
        val op = addLiveOp(OperationType.BOT_RUN, "Python skript ishga tushirilmoqda", scriptPath)
        pythonBotRunner.startScript(scriptPath)
        updateLiveOp(op.copy(status = OperationStatus.SUCCESS, detail = "Ishga tushdi: $scriptPath"))
    }

    fun stopPythonBot() {
        pythonBotRunner.stop()
    }

    fun sendTestMessageToBot(message: String) {
        pythonBotRunner.testLocalMessage(message)
    }

    fun addAutomationTask(name: String, type: String, target: String, intervalSec: Int) {
        automationScheduler.addTask(name, type, target, intervalSec)
        showBanner("Avtomatlashtirilgan vazifa qo'shildi")
    }

    fun toggleAutomationTask(id: String) {
        automationScheduler.toggleTask(id)
    }

    fun runAutomationOnce(id: String) {
        automationScheduler.runOnce(id)
    }

    fun deleteAutomationTask(id: String) {
        automationScheduler.deleteTask(id)
    }

    fun saveApiConfig(config: ApiConfig) {
        apiConfigManager.saveConfig(config)
        _apiConfig.value = apiConfigManager.loadConfig()
        _apiRawJson.value = apiConfigManager.readRawJson()
        syncApiJsonToWorkspace()
        refreshFiles()
        showBanner("api.json saqlandi!")
    }

    fun saveApiRawJson(rawJson: String): Boolean {
        return apiConfigManager.saveRawJson(rawJson).fold(
            onSuccess = { cfg ->
                _apiConfig.value = cfg
                _apiRawJson.value = apiConfigManager.readRawJson()
                syncApiJsonToWorkspace()
                refreshFiles()
                showBanner("api.json yangilandi!")
                true
            },
            onFailure = { err ->
                showBanner("JSON format xatosi: ${err.message}")
                false
            }
        )
    }

    fun exportApiJson(uri: Uri) {
        apiConfigManager.exportToUri(getApplication<Application>().contentResolver, uri).fold(
            onSuccess = { showBanner("api.json telefonga eksport qilindi") },
            onFailure = { showBanner("Eksport xatosi: ${it.message}") }
        )
    }

    fun importApiJson(uri: Uri) {
        apiConfigManager.importFromUri(getApplication<Application>().contentResolver, uri).fold(
            onSuccess = { cfg ->
                _apiConfig.value = cfg
                _apiRawJson.value = apiConfigManager.readRawJson()
                syncApiJsonToWorkspace()
                refreshFiles()
                showBanner("api.json yuklandi!")
            },
            onFailure = { showBanner("Import xatosi: ${it.message}") }
        )
    }

    private fun addLiveOp(
        type: OperationType,
        title: String,
        detail: String,
        codePreview: String = ""
    ): LiveOperation {
        val op = LiveOperation(type = type, title = title, detail = detail, codePreview = codePreview)
        addLiveOpDirect(op)
        return op
    }

    private fun addLiveOpDirect(op: LiveOperation) {
        _liveOperations.value = (listOf(op) + _liveOperations.value).take(40)
    }

    private fun updateLiveOp(updated: LiveOperation) {
        _liveOperations.value = _liveOperations.value.map { if (it.id == updated.id) updated else it }
    }

    override fun onCleared() {
        super.onCleared()
        localWebServer.stop()
        pythonBotRunner.stop()
    }
}
