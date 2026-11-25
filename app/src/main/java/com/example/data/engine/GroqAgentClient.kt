package com.example.data.engine

import com.example.data.model.ApiConfig
import com.example.data.model.LiveOperation
import com.example.data.model.OperationStatus
import com.example.data.model.OperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqAgentClient(
    private val apiConfigManager: ApiConfigManager,
    private val workspaceManager: WorkspaceManager,
    private val terminalExecutor: TerminalExecutor,
    private val localWebServer: LocalWebServer,
    private val pythonBotRunner: PythonBotRunner
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedWorkingModel: String? = null

    suspend fun runAgentPrompt(
        userPrompt: String,
        historyPairs: List<Pair<String, String>>,
        onOpStarted: (LiveOperation) -> Unit,
        onOpUpdated: (LiveOperation) -> Unit,
        onZipReady: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val config: ApiConfig = apiConfigManager.loadConfig()
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Groq API kaliti kiritilmagan. 'api.json' bo'limiga kirib 'api_key' maydoniga Groq kalitingizni yozing."
                )
            )
        }

        val thinkingOp = LiveOperation(
            type = OperationType.THINKING,
            title = "Dasturchi AI Tayyorlanmoqda",
            detail = "Groq hisobingizdagi faol model aniqlanmoqda...",
            status = OperationStatus.RUNNING
        )
        onOpStarted(thinkingOp)

        val candidates = resolveCandidateModels(config)
        if (candidates.isEmpty()) {
            onOpUpdated(
                thinkingOp.copy(
                    status = OperationStatus.ERROR,
                    detail = "Groq API dan mavjud modellar ro'yxatini olib bo'lmadi. API kalitni va internetni tekshiring."
                )
            )
            return@withContext Result.failure(
                IllegalStateException("Groq API da ishlaydigan model topilmadi. API kalitni tekshiring.")
            )
        }

        val currentFiles = workspaceManager.listAllFilesRecursive(limit = 80).joinToString(", ")
        val systemPrompt = """
            Sening isming — **Dasturchi AI**! Sen shunchaki matn yozuvchi chatbot emas, balki telefonda real fayllar, papkalar, ZIP/SWB arxivlar, veb-serverlar, terminal buyruqlari, Python dasturlari va Google AI Studio Kotlin loyihalarini mustaqil yarata oladigan avtonom dasturchi AI studiyasisan.

            Qoidalar:
            1. O'zingning kimligingni doim bil: sen **Dasturchi AI** san. Foydalanuvchi bilan o'zbek tilida aniq, professional va jonli muloqot qil.
            2. Foydalanuvchi nima desa (veb-sayt, o'yin, Python skripti, Telegram bot, kalkulyator, API mijoz, Android Kotlin ilova, papka/fayl, ZIP arxiv), darhol o'zing o'ylab reja tuz va tegishli tool'larni chaqirib HAQIQIY fayllarni yarat!
            3. Google AI Studio uchun Kotlin ilova yoki ZIP yaratish so'ralsa, foydalanuvchidan qo'shimcha savol so'ramasdan o'zing zamonaviy g'oya va to'liq Jetpack Compose `MainActivity.kt` kodini yoz, `scaffold_kotlin_ai_studio_app` tool'ini chaqir va darhol `create_zip` bilan `.zip` arxivini yaratib ber!
            4. Python tili bo'yicha istalgan narsani (hisob-kitoblar, ma'lumot tahlili, skriptlar, API so'rovlar, algoritmlar, mini-dasturlar, botlar) `write_file` bilan yozib `run_python_script` orqali darhol ishga tushira olasan.
            5. Veb-sayt yoki HTML/CSS/JS loyihalarini yaratib `start_localhost_server` orqali `127.0.0.1` da yoqib bera olasan.
            6. Hozirgi workspace fayllari: [$currentFiles]
            7. Oxirida bajargan barcha ishlaring haqida qisqa va tushunarli hisobot ber.
        """.trimIndent()

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            historyPairs.takeLast(8).forEach { (role, text) ->
                put(JSONObject().put("role", role).put("content", text))
            }
            put(JSONObject().put("role", "user").put("content", userPrompt))
        }

        val tools = buildToolDefinitions()
        val endpoint = config.baseUrl.trimEnd('/') + "/chat/completions"

        var activeModel: String? = null
        var useTools = true
        var finalReply = ""
        val maxIterations = 8

        for (step in 1..maxIterations) {
            val callOutcome = executeCompletionWithAutoModel(
                endpoint = endpoint,
                apiKey = config.apiKey,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                messages = messages,
                tools = tools,
                preferredModel = activeModel,
                candidateModels = candidates,
                initialUseTools = useTools
            )

            val success = callOutcome.getOrElse { err ->
                onOpUpdated(
                    thinkingOp.copy(
                        status = OperationStatus.ERROR,
                        detail = "Groq API xatosi: ${err.message}"
                    )
                )
                return@withContext Result.failure(err)
            }

            activeModel = success.modelUsed
            useTools = success.usedTools
            cachedWorkingModel = activeModel

            if (step == 1) {
                onOpUpdated(
                    thinkingOp.copy(
                        status = OperationStatus.SUCCESS,
                        detail = "Avto-ulandi: $activeModel • Amallar bajarilmoqda"
                    )
                )
            }

            val choice = success.responseJson.getJSONArray("choices").getJSONObject(0)
            val assistantMsg = choice.getJSONObject("message")
            messages.put(assistantMsg)

            val toolCalls = assistantMsg.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                finalReply = assistantMsg.optString("content", "").ifBlank {
                    "Vazifa Dasturchi AI tomonidan muvaffaqiyatli bajarildi."
                }
                break
            }

            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.getJSONObject(i)
                val tcId = tc.getString("id")
                val fn = tc.getJSONObject("function")
                val fnName = fn.getString("name")
                val argsJson = runCatching { JSONObject(fn.optString("arguments", "{}")) }
                    .getOrDefault(JSONObject())

                val toolResult = executeToolWithLiveFeed(
                    name = fnName,
                    args = argsJson,
                    onOpStarted = onOpStarted,
                    onOpUpdated = onOpUpdated,
                    onZipReady = onZipReady
                )

                messages.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", tcId)
                        put("name", fnName)
                        put("content", toolResult)
                    }
                )
            }
        }

        if (finalReply.isBlank()) {
            finalReply = "Barcha rejalashtirilgan amallar Dasturchi AI tomonidan bajarildi."
        }
        Result.success(finalReply)
    }

    private data class CompletionSuccess(
        val modelUsed: String,
        val usedTools: Boolean,
        val responseJson: JSONObject
    )

    private fun executeCompletionWithAutoModel(
        endpoint: String,
        apiKey: String,
        temperature: Double,
        maxTokens: Int,
        messages: JSONArray,
        tools: JSONArray,
        preferredModel: String?,
        candidateModels: List<String>,
        initialUseTools: Boolean
    ): Result<CompletionSuccess> {
        val ordered = buildList {
            if (!preferredModel.isNullOrBlank()) add(preferredModel)
            candidateModels.forEach { if (it != preferredModel) add(it) }
        }

        var lastError: Throwable = IllegalStateException("Groq modeliga ulanib bo'lmadi")

        // Pass 1: try candidate models with tool calling enabled
        if (initialUseTools) {
            for (modelId in ordered) {
                val res = singleCompletionCall(endpoint, apiKey, modelId, temperature, maxTokens, messages, tools)
                if (res.isSuccess) {
                    return Result.success(CompletionSuccess(modelId, true, res.getOrThrow()))
                }
                val err = res.exceptionOrNull()!!
                lastError = err
                if (isAuthError(err.message)) {
                    return Result.failure(err)
                }
            }
        }

        // Pass 2: if tool calling wasn't supported by available models, fall back cleanly to standard chat completion
        for (modelId in ordered) {
            val res = singleCompletionCall(endpoint, apiKey, modelId, temperature, maxTokens, messages, null)
            if (res.isSuccess) {
                return Result.success(CompletionSuccess(modelId, false, res.getOrThrow()))
            }
            val err = res.exceptionOrNull()!!
            lastError = err
            if (isAuthError(err.message)) {
                return Result.failure(err)
            }
        }

        return Result.failure(lastError)
    }

    private fun singleCompletionCall(
        endpoint: String,
        apiKey: String,
        modelId: String,
        temperature: Double,
        maxTokens: Int,
        messages: JSONArray,
        tools: JSONArray?
    ): Result<JSONObject> {
        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messages)
            if (tools != null) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }

        val req = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(bodyStr).getJSONObject("error").getString("message")
                    }.getOrDefault("HTTP ${resp.code}: $bodyStr")
                    error(errMsg)
                }
                JSONObject(bodyStr)
            }
        }
    }

    private fun resolveCandidateModels(config: ApiConfig): List<String> {
        val fetched = fetchAvailableGroqModels(config.baseUrl, config.apiKey)
        val priorityHints = listOf(
            "llama-3.3-70b",
            "llama-3.1-8b",
            "meta-llama/llama-4",
            "openai/gpt-oss",
            "qwen",
            "deepseek",
            "gemma",
            "mixtral",
            "llama"
        )

        val result = LinkedHashSet<String>()
        // Cached working model from this session first (if still in fetched list or non-empty)
        cachedWorkingModel?.let { cached ->
            if (fetched.isEmpty() || fetched.contains(cached)) {
                result.add(cached)
            }
        }

        // If user explicitly set a non-auto model in api.json and it exists in fetched, try it first too
        val configured = config.model.trim()
        if (configured.isNotBlank() && !configured.equals("auto", ignoreCase = true)) {
            if (fetched.isEmpty() || fetched.contains(configured)) {
                result.add(configured)
            }
        }

        // Sort fetched models by chat/tool suitability
        val ranked = fetched.sortedWith(compareBy { id ->
            val lower = id.lowercase()
            val idx = priorityHints.indexOfFirst { lower.contains(it) }
            if (idx >= 0) idx else priorityHints.size
        })
        result.addAll(ranked)

        // Safe fallback list if /models endpoint was temporarily unreachable
        if (result.isEmpty()) {
            result.addAll(
                listOf(
                    "llama-3.1-8b-instant",
                    "llama-3.3-70b-versatile",
                    "openai/gpt-oss-20b",
                    "qwen/qwen3-32b",
                    "gemma2-9b-it"
                )
            )
        }
        return result.toList()
    }

    private fun fetchAvailableGroqModels(baseUrl: String, apiKey: String): List<String> {
        val modelsEndpoint = baseUrl.trimEnd('/') + "/models"
        val req = Request.Builder()
            .url(modelsEndpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string().orEmpty()
                val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
                val models = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i)
                    val id = m.optString("id", "").trim()
                    val active = m.optBoolean("active", true)
                    if (id.isNotEmpty() && active && isChatModelId(id)) {
                        models.add(id)
                    }
                }
                models
            }
        }.getOrDefault(emptyList())
    }

    private fun isChatModelId(id: String): Boolean {
        val lower = id.lowercase()
        val nonChatKeywords = listOf("whisper", "tts", "orpheus", "playai", "guard", "safeguard", "embed")
        return nonChatKeywords.none { lower.contains(it) }
    }

    private fun isAuthError(msg: String?): Boolean {
        if (msg == null) return false
        val lower = msg.lowercase()
        return lower.contains("invalid api key") ||
            lower.contains("invalid_api_key") ||
            lower.contains("unauthorized") ||
            lower.contains("authentication")
    }

    private suspend fun executeToolWithLiveFeed(
        name: String,
        args: JSONObject,
        onOpStarted: (LiveOperation) -> Unit,
        onOpUpdated: (LiveOperation) -> Unit,
        onZipReady: (String) -> Unit
    ): String {
        return when (name) {
            "create_folder" -> {
                val path = args.optString("path")
                val op = LiveOperation(
                    type = OperationType.CREATE_FOLDER,
                    title = "Papka yaratilmoqda",
                    detail = path,
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                workspaceManager.createFolder(path).fold(
                    onSuccess = {
                        onOpUpdated(op.copy(status = OperationStatus.SUCCESS, detail = "Papka yaratildi: $path"))
                        "OK: Papka yaratildi: $path"
                    },
                    onFailure = { err ->
                        onOpUpdated(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                        "ERROR: ${err.message}"
                    }
                )
            }

            "write_file" -> {
                val path = args.optString("path")
                val content = args.optString("content")
                val op = LiveOperation(
                    type = OperationType.WRITE_FILE,
                    title = "Fayl yozilmoqda: $path",
                    detail = "${content.length} belgi yozilmoqda...",
                    codePreview = content.take(450),
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                workspaceManager.writeFile(path, content).fold(
                    onSuccess = { file ->
                        onOpUpdated(
                            op.copy(
                                status = OperationStatus.SUCCESS,
                                detail = "Saqlandi (${file.length()} bayt)"
                            )
                        )
                        "OK: Fayl yozildi: $path (${file.length()} bayt)"
                    },
                    onFailure = { err ->
                        onOpUpdated(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                        "ERROR: ${err.message}"
                    }
                )
            }

            "read_file" -> {
                val path = args.optString("path")
                val op = LiveOperation(
                    type = OperationType.READ_FILE,
                    title = "Fayl o'qilmoqda",
                    detail = path,
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                workspaceManager.readFile(path).fold(
                    onSuccess = { text ->
                        onOpUpdated(
                            op.copy(
                                status = OperationStatus.SUCCESS,
                                detail = "O'qildi (${text.length} belgi)",
                                codePreview = text.take(350)
                            )
                        )
                        text.take(6000)
                    },
                    onFailure = { err ->
                        onOpUpdated(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                        "ERROR: ${err.message}"
                    }
                )
            }

            "list_files" -> {
                val path = args.optString("path", "")
                val op = LiveOperation(
                    type = OperationType.READ_FILE,
                    title = "Fayllar ro'yxati olinmoqda",
                    detail = path.ifBlank { "/" },
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                val list = workspaceManager.listAllFilesRecursive(path, limit = 100)
                onOpUpdated(op.copy(status = OperationStatus.SUCCESS, detail = "${list.size} ta element topildi"))
                if (list.isEmpty()) "(bo'sh)" else list.joinToString("\n")
            }

            "create_zip" -> {
                val source = args.optString("source_path")
                val zipName = args.optString("zip_name")
                val op = LiveOperation(
                    type = OperationType.ZIP_CREATE,
                    title = "ZIP arxiv yaratilmoqda",
                    detail = "$source -> $zipName",
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                workspaceManager.createZip(source, zipName).fold(
                    onSuccess = { file ->
                        val rel = file.canonicalPath
                            .removePrefix(workspaceManager.workspaceRoot.canonicalPath)
                            .trimStart('/')
                        onZipReady(rel)
                        onOpUpdated(
                            op.copy(
                                status = OperationStatus.SUCCESS,
                                detail = "${file.name} tayyor (${file.length()} bayt) • Yuklashga tayyor!"
                            )
                        )
                        "OK: ZIP yaratildi: $rel (${file.length()} bayt)"
                    },
                    onFailure = { err ->
                        onOpUpdated(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                        "ERROR: ${err.message}"
                    }
                )
            }

            "inspect_archive" -> {
                val path = args.optString("path")
                val op = LiveOperation(
                    type = OperationType.SWB_INSPECT,
                    title = "ZIP/SWB arxiv tekshirilmoqda",
                    detail = path,
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                workspaceManager.inspectArchiveFile(path).fold(
                    onSuccess = { entries ->
                        val summary = entries.take(30).joinToString("\n") {
                            "${it.name} (${it.sizeBytes}B)"
                        }
                        onOpUpdated(
                            op.copy(
                                status = OperationStatus.SUCCESS,
                                detail = "${entries.size} ta ichki element topildi",
                                codePreview = summary.take(400)
                            )
                        )
                        summary
                    },
                    onFailure = { err ->
                        onOpUpdated(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                        "ERROR: ${err.message}"
                    }
                )
            }

            "run_terminal" -> {
                val cmd = args.optString("command")
                val op = LiveOperation(
                    type = OperationType.TERMINAL,
                    title = "Terminal buyrug'i bajarilmoqda",
                    detail = "$ $cmd",
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                val res = terminalExecutor.execute(cmd)
                val combined = (res.outputLines + res.errorLines).joinToString("\n")
                onOpUpdated(
                    op.copy(
                        status = if (res.exitCode == 0) OperationStatus.SUCCESS else OperationStatus.ERROR,
                        detail = "Exit code: ${res.exitCode}",
                        codePreview = combined.take(450)
                    )
                )
                "Exit ${res.exitCode}:\n${combined.take(3000)}"
            }

            "start_localhost_server" -> {
                val root = args.optString("root_folder", "web_studio_demo")
                val port = args.optInt("port", 8080)
                val op = LiveOperation(
                    type = OperationType.SERVER,
                    title = "Localhost Web Server ishga tushirilmoqda",
                    detail = "Papka: $root • Port: $port",
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                localWebServer.start(root, port).fold(
                    onSuccess = { st ->
                        onOpUpdated(
                            op.copy(
                                status = OperationStatus.SUCCESS,
                                detail = "Faol: ${st.url} (Papka: ${st.rootFolder})"
                            )
                        )
                        "OK: Localhost server ishga tushdi: ${st.url}"
                    },
                    onFailure = { err ->
                        onOpUpdated(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                        "ERROR: ${err.message}"
                    }
                )
            }

            "run_python_script" -> {
                val scriptPath = args.optString("script_path")
                val op = LiveOperation(
                    type = OperationType.BOT_RUN,
                    title = "Python skript ishga tushirilmoqda",
                    detail = scriptPath,
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                pythonBotRunner.startScript(scriptPath)
                onOpUpdated(
                    op.copy(
                        status = OperationStatus.SUCCESS,
                        detail = "Ishga tushirildi: $scriptPath (Server & Run -> Python Studio da jonli natija)"
                    )
                )
                "OK: Python skript ishga tushirildi: $scriptPath"
            }

            "scaffold_kotlin_ai_studio_app" -> {
                val folder = args.optString("project_name", "kotlin_aistudio_app")
                val title = args.optString("app_title", "Kotlin Ilova")
                val activityCode = args.optString("main_activity_code", "")
                val op = LiveOperation(
                    type = OperationType.KOTLIN_SCAFFOLD,
                    title = "Google AI Studio Kotlin loyihasi yaratilmoqda",
                    detail = "$folder ($title)",
                    codePreview = activityCode.take(400),
                    status = OperationStatus.RUNNING
                )
                onOpStarted(op)
                workspaceManager.scaffoldKotlinAiStudioProject(
                    projectFolder = folder,
                    appTitle = title,
                    mainActivityCode = activityCode.ifBlank { null }
                ).fold(
                    onSuccess = { files ->
                        onOpUpdated(
                            op.copy(
                                status = OperationStatus.SUCCESS,
                                detail = "${files.size} ta Kotlin/Gradle fayl yaratildi: $folder"
                            )
                        )
                        "OK: Loyiha yaratildi ($folder):\n" + files.joinToString("\n")
                    },
                    onFailure = { err ->
                        onOpUpdated(op.copy(status = OperationStatus.ERROR, detail = err.message ?: "Xato"))
                        "ERROR: ${err.message}"
                    }
                )
            }

            else -> "Noma'lum tool: $name"
        }
    }

    private fun buildToolDefinitions(): JSONArray {
        fun tool(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject {
            return JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", name)
                    put("description", desc)
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", props)
                        put("required", JSONArray(required))
                    })
                })
            }
        }

        fun strProp(desc: String) = JSONObject().put("type", "string").put("description", desc)
        fun intProp(desc: String) = JSONObject().put("type", "integer").put("description", desc)

        return JSONArray().apply {
            put(
                tool(
                    "create_folder",
                    "Workspace ichida yangi papka yaratadi.",
                    JSONObject().put("path", strProp("Nisbiy papka yo'li, masalan: my_project/src")),
                    listOf("path")
                )
            )
            put(
                tool(
                    "write_file",
                    "Workspace ichida har qanday fayl (.py, .kt, .html, .json, .js, .xml va h.k.) yaratadi yoki yangilaydi.",
                    JSONObject()
                        .put("path", strProp("Nisbiy fayl yo'li"))
                        .put("content", strProp("Faylning to'liq matn/kod tarkibi")),
                    listOf("path", "content")
                )
            )
            put(
                tool(
                    "read_file",
                    "Workspace ichidagi fayl matnini o'qiydi.",
                    JSONObject().put("path", strProp("O'qiladigan fayl yo'li")),
                    listOf("path")
                )
            )
            put(
                tool(
                    "list_files",
                    "Workspace yoki papka ichidagi fayl va papkalarni ko'rsatadi.",
                    JSONObject().put("path", strProp("Papka yo'li (bo'sh bo'lsa asosiy workspace)")),
                    emptyList()
                )
            )
            put(
                tool(
                    "create_zip",
                    "Papka yoki faylni .zip arxiviga o'raydi.",
                    JSONObject()
                        .put("source_path", strProp("Arxivlanadigan papka yoki fayl yo'li"))
                        .put("zip_name", strProp("Chiqish ZIP fayl nomi, masalan: loyiha.zip")),
                    listOf("source_path", "zip_name")
                )
            )
            put(
                tool(
                    "inspect_archive",
                    "Workspace ichidagi .zip yoki .swb (Sketchware) arxiv tarkibini o'qiydi.",
                    JSONObject().put("path", strProp("Arxiv fayl yo'li")),
                    listOf("path")
                )
            )
            put(
                tool(
                    "run_terminal",
                    "Sandboxed workspace ichida shell/terminal buyrug'ini bajaradi.",
                    JSONObject().put("command", strProp("Terminal buyrug'i, masalan: ls -la")),
                    listOf("command")
                )
            )
            put(
                tool(
                    "start_localhost_server",
                    "Telefonda 127.0.0.1 localhost HTTP veb-serverini yoqadi.",
                    JSONObject()
                        .put("root_folder", strProp("Xizmat ko'rsatiladigan papka nomi"))
                        .put("port", intProp("Port raqami, odatda 8080")),
                    listOf("root_folder")
                )
            )
            put(
                tool(
                    "run_python_script",
                    "Har qanday Python dasturi yoki Telegram bot skriptini telefonda ishga tushiradi.",
                    JSONObject().put("script_path", strProp("Python fayl yo'li, masalan: python_lab/tahlil_va_hisob.py")),
                    listOf("script_path")
                )
            )
            put(
                tool(
                    "scaffold_kotlin_ai_studio_app",
                    "Google AI Studio uchun tayyor Kotlin Jetpack Compose loyiha tuzilmasini yaratadi.",
                    JSONObject()
                        .put("project_name", strProp("Loyiha papkasi nomi, masalan: aistudio_kotlin_app"))
                        .put("app_title", strProp("Ilova nomi"))
                        .put("main_activity_code", strProp("MainActivity.kt uchun to'liq Kotlin Compose kodi")),
                    listOf("project_name", "app_title")
                )
            )
        }
    }
}
