package com.example.data.engine

import com.example.data.model.BotRuntimeState
import com.example.data.model.TerminalLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class PythonBotRunner(private val workspaceManager: WorkspaceManager) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runnerJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(BotRuntimeState())
    val state: StateFlow<BotRuntimeState> = _state.asStateFlow()

    private var activeScriptSource: String = ""
    private val variables = mutableMapOf<String, Any?>()
    private val listVars = mutableMapOf<String, MutableList<Any?>>()
    private val dictVars = mutableMapOf<String, MutableMap<String, Any?>>()
    private val functions = mutableMapOf<String, PyFunction>()

    private data class PyFunction(val params: List<String>, val bodyLines: List<Pair<Int, String>>)

    fun startScript(relativePath: String) {
        stop()
        val fileResult = workspaceManager.readFile(relativePath)
        fileResult.onFailure { err ->
            _state.value = BotRuntimeState(
                isRunning = false,
                scriptPath = relativePath,
                logs = listOf(TerminalLine(text = "Xato: ${err.message}", isError = true))
            )
            return
        }

        val source = fileResult.getOrThrow()
        activeScriptSource = source
        variables.clear()
        listVars.clear()
        dictVars.clear()
        functions.clear()

        fun log(text: String, isError: Boolean = false) {
            val line = TerminalLine(text = text, isError = isError)
            _state.value = _state.value.copy(logs = (_state.value.logs + line).takeLast(160))
        }

        _state.value = BotRuntimeState(
            isRunning = true,
            scriptPath = relativePath,
            logs = listOf(TerminalLine(text = "[Python Studio] Ishga tushirilmoqda: $relativePath"))
        )

        runnerJob = scope.launch {
            val hasNativePython = runCatching {
                val p = ProcessBuilder("which", "python3").start()
                p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
            }.getOrDefault(false)

            val token = extractTelegramToken(source)
            if (hasNativePython && token == null) {
                log("[Native Python3] Bajarilmoqda...", false)
                val file = workspaceManager.resolveSafe(relativePath)
                runCatching {
                    val pb = ProcessBuilder("python3", file.absolutePath)
                    pb.directory(file.parentFile)
                    val proc = pb.start()
                    proc.inputStream.bufferedReader().forEachLine { log(it, false) }
                    proc.errorStream.bufferedReader().forEachLine { log(it, true) }
                    val code = proc.waitFor()
                    log("[Native Python3] Yakunlandi (exit=$code)", false)
                }.onFailure {
                    log("Python xatosi: ${it.message}", true)
                }
                _state.value = _state.value.copy(isRunning = false)
                return@launch
            }

            runCatching {
                executePythonSource(source, ::log)
            }.onFailure { err ->
                log("[Python Xato]: ${err.message}", true)
            }

            val handlers = detectHandlers(source)
            _state.value = _state.value.copy(activeHandlers = handlers)

            if (token != null && !token.contains("YOUR_TELEGRAM_BOT_TOKEN") && token.contains(":")) {
                log("[Telegram API] Token aniqlandi. Bot ulanmoqda...", false)
                runTelegramPollingLoop(token, ::log)
            } else if (token != null) {
                log("[Telegram Bot Rejimi] Sinash uchun pastdagi maydondan buyruq/matn yuboring.", false)
                while (isActive) {
                    delay(1000)
                }
            } else {
                log("[Python Studio] Skript muvaffaqiyatli bajarildi.", false)
                _state.value = _state.value.copy(isRunning = false)
            }
        }
    }

    fun stop() {
        runnerJob?.cancel()
        runnerJob = null
        if (_state.value.isRunning) {
            val stopLine = TerminalLine(text = "[Python Studio] To'xtatildi.")
            _state.value = _state.value.copy(
                isRunning = false,
                logs = (_state.value.logs + stopLine).takeLast(160)
            )
        }
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    fun testLocalMessage(messageText: String, userName: String = "Foydalanuvchi") {
        if (activeScriptSource.isBlank()) {
            appendLog("Avval biror Python skriptini ishga tushiring.", isError = true)
            return
        }
        appendLog(">> [Kirish]: $messageText")
        val reply = if (functions.containsKey("handle_message") || extractTelegramToken(activeScriptSource) != null) {
            evaluateBotReply(messageText, userName)
        } else {
            // Try passing input to main(x) or process(x) if defined, else evaluate expression
            val targetFn = listOf("main", "process", "solve", "run").firstOrNull { functions.containsKey(it) }
            if (targetFn != null) {
                val res = invokeFunction(targetFn, listOf(messageText)) { line, err -> appendLog(line, err) }
                res?.toString() ?: "Bajarildi"
            } else {
                evalExpr(messageText, variables).toString()
            }
        }
        appendLog("<< [Natija]: $reply")
        _state.value = _state.value.copy(updatesProcessed = _state.value.updatesProcessed + 1)
    }

    private suspend fun executePythonSource(source: String, log: (String, Boolean) -> Unit) {
        val rawLines = source.lines()
        val indexed = rawLines.map { line ->
            val indent = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 4 else 1 }
            indent to line.trim()
        }.filter { it.second.isNotEmpty() && !it.second.startsWith("#") }

        executeBlock(indexed, 0, indexed.size, variables, log)
    }

    private suspend fun executeBlock(
        lines: List<Pair<Int, String>>,
        startIdx: Int,
        endIdx: Int,
        localScope: MutableMap<String, Any?>,
        log: (String, Boolean) -> Unit
    ): Any? {
        var i = startIdx
        var lastIfMatched = false

        while (i < endIdx) {
            val (indent, stmt) = lines[i]

            when {
                stmt.startsWith("import ") || stmt.startsWith("from ") -> {
                    i++
                }

                stmt.startsWith("def ") && stmt.endsWith(":") -> {
                    val sig = stmt.removePrefix("def ").dropLast(1).trim()
                    val fnName = sig.substringBefore("(").trim()
                    val paramStr = sig.substringAfter("(", "").substringBeforeLast(")")
                    val params = paramStr.split(",").map { it.trim().substringBefore("=").trim() }.filter { it.isNotEmpty() }
                    val blockEnd = findBlockEnd(lines, i + 1, endIdx, indent)
                    functions[fnName] = PyFunction(params, lines.subList(i + 1, blockEnd))
                    i = blockEnd
                }

                stmt.startsWith("if ") && stmt.endsWith(":") -> {
                    val cond = stmt.removePrefix("if ").dropLast(1).trim()
                    val matched = evalBool(cond, localScope)
                    lastIfMatched = matched
                    val blockEnd = findBlockEnd(lines, i + 1, endIdx, indent)
                    if (matched) {
                        val ret = executeBlock(lines, i + 1, blockEnd, localScope, log)
                        if (ret is ReturnSignal) return ret
                    }
                    i = blockEnd
                }

                stmt.startsWith("elif ") && stmt.endsWith(":") -> {
                    val blockEnd = findBlockEnd(lines, i + 1, endIdx, indent)
                    if (!lastIfMatched) {
                        val cond = stmt.removePrefix("elif ").dropLast(1).trim()
                        val matched = evalBool(cond, localScope)
                        lastIfMatched = matched
                        if (matched) {
                            val ret = executeBlock(lines, i + 1, blockEnd, localScope, log)
                            if (ret is ReturnSignal) return ret
                        }
                    }
                    i = blockEnd
                }

                stmt == "else:" -> {
                    val blockEnd = findBlockEnd(lines, i + 1, endIdx, indent)
                    if (!lastIfMatched) {
                        val ret = executeBlock(lines, i + 1, blockEnd, localScope, log)
                        if (ret is ReturnSignal) return ret
                    }
                    i = blockEnd
                }

                stmt.startsWith("for ") && stmt.endsWith(":") -> {
                    val header = stmt.removePrefix("for ").dropLast(1).trim()
                    val varName = header.substringBefore(" in ").trim()
                    val iterExpr = header.substringAfter(" in ").trim()
                    val blockEnd = findBlockEnd(lines, i + 1, endIdx, indent)
                    val iterable = evalIterable(iterExpr, localScope)
                    for (item in iterable.take(200)) {
                        localScope[varName] = item
                        val ret = executeBlock(lines, i + 1, blockEnd, localScope, log)
                        if (ret is ReturnSignal) return ret
                    }
                    i = blockEnd
                }

                stmt.startsWith("while ") && stmt.endsWith(":") -> {
                    val cond = stmt.removePrefix("while ").dropLast(1).trim()
                    val blockEnd = findBlockEnd(lines, i + 1, endIdx, indent)
                    var guard = 0
                    while (evalBool(cond, localScope) && guard < 150) {
                        guard++
                        val ret = executeBlock(lines, i + 1, blockEnd, localScope, log)
                        if (ret is ReturnSignal) return ret
                    }
                    i = blockEnd
                }

                stmt.startsWith("return ") -> {
                    val expr = stmt.removePrefix("return ").trim()
                    return ReturnSignal(evalExpr(expr, localScope))
                }

                stmt == "return" -> return ReturnSignal(null)

                stmt.startsWith("print(") && stmt.endsWith(")") -> {
                    val inner = stmt.removePrefix("print(").dropLast(1)
                    val args = splitTopLevelArgs(inner)
                    val rendered = args.joinToString(" ") { arg ->
                        formatPyValue(evalExpr(arg, localScope))
                    }
                    log(rendered, false)
                    i++
                }

                stmt.startsWith("time.sleep(") && stmt.endsWith(")") -> {
                    val sec = evalExpr(stmt.removePrefix("time.sleep(").dropLast(1), localScope).toString().toDoubleOrNull() ?: 0.2
                    delay((sec * 1000).toLong().coerceIn(50L, 3000L))
                    i++
                }

                // Augmented assignment (+=, -=)
                stmt.contains("+=") -> {
                    val lhs = stmt.substringBefore("+=").trim()
                    val rhs = stmt.substringAfter("+=").trim()
                    val cur = localScope[lhs]
                    val addVal = evalExpr(rhs, localScope)
                    localScope[lhs] = addValues(cur, addVal)
                    i++
                }

                // Assignment
                Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(.+)$""").matches(stmt) && !stmt.contains("==") -> {
                    val m = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(.+)$""").find(stmt)!!
                    val name = m.groupValues[1]
                    val expr = m.groupValues[2].trim()
                    localScope[name] = evalExpr(expr, localScope)
                    i++
                }

                // Standalone function call
                Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\((.*)\)$""").matches(stmt) -> {
                    val m = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\((.*)\)$""").find(stmt)!!
                    val fnName = m.groupValues[1]
                    val args = splitTopLevelArgs(m.groupValues[2]).map { evalExpr(it, localScope) }
                    if (functions.containsKey(fnName)) {
                        invokeFunction(fnName, args, log)
                    }
                    i++
                }

                else -> i++
            }
        }
        return null
    }

    private data class ReturnSignal(val value: Any?)

    private fun invokeFunction(
        fnName: String,
        argValues: List<Any?>,
        log: (String, Boolean) -> Unit
    ): Any? {
        val fn = functions[fnName] ?: return null
        val scopeMap = variables.toMutableMap()
        fn.params.forEachIndexed { index, paramName ->
            scopeMap[paramName] = argValues.getOrNull(index)
        }
        // Run synchronously via simple block runner
        return runBlockingSafe(fn.bodyLines, scopeMap, log)
    }

    private fun runBlockingSafe(
        lines: List<Pair<Int, String>>,
        scopeMap: MutableMap<String, Any?>,
        log: (String, Boolean) -> Unit
    ): Any? {
        // Use non-suspending block execution for nested function calls
        var result: Any? = null
        kotlinx.coroutines.runBlocking {
            val res = executeBlock(lines, 0, lines.size, scopeMap, log)
            result = if (res is ReturnSignal) res.value else res
        }
        return result
    }

    private fun findBlockEnd(lines: List<Pair<Int, String>>, from: Int, limit: Int, parentIndent: Int): Int {
        var idx = from
        while (idx < limit && lines[idx].first > parentIndent) {
            idx++
        }
        return idx
    }

    private fun evalIterable(expr: String, scopeMap: Map<String, Any?>): List<Any?> {
        val trimmed = expr.trim()
        if (trimmed.startsWith("range(") && trimmed.endsWith(")")) {
            val args = splitTopLevelArgs(trimmed.removePrefix("range(").dropLast(1))
                .mapNotNull { evalExpr(it, scopeMap).toString().toDoubleOrNull()?.toInt() }
            return when (args.size) {
                1 -> (0 until args[0]).toList()
                2 -> (args[0] until args[1]).toList()
                3 -> (args[0] until args[1] step args[2].coerceAtLeast(1)).toList()
                else -> emptyList()
            }
        }
        val evaluated = evalExpr(trimmed, scopeMap)
        if (evaluated is List<*>) return evaluated
        if (evaluated is String) return evaluated.map { it.toString() }
        return emptyList()
    }

    private fun evalBool(cond: String, scopeMap: Map<String, Any?>): Boolean {
        val c = cond.trim()
        if (c == "True") return true
        if (c == "False") return false
        val ops = listOf("==", "!=", ">=", "<=", ">", "<")
        for (op in ops) {
            if (c.contains(op)) {
                val parts = c.split(op, limit = 2)
                val l = evalExpr(parts[0], scopeMap)
                val r = evalExpr(parts[1], scopeMap)
                val ld = l.toString().toDoubleOrNull()
                val rd = r.toString().toDoubleOrNull()
                return when (op) {
                    "==" -> formatPyValue(l) == formatPyValue(r)
                    "!=" -> formatPyValue(l) != formatPyValue(r)
                    ">" -> (ld ?: 0.0) > (rd ?: 0.0)
                    "<" -> (ld ?: 0.0) < (rd ?: 0.0)
                    ">=" -> (ld ?: 0.0) >= (rd ?: 0.0)
                    "<=" -> (ld ?: 0.0) <= (rd ?: 0.0)
                    else -> false
                }
            }
        }
        val v = evalExpr(c, scopeMap)
        return when (v) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.isNotEmpty()
            is List<*> -> v.isNotEmpty()
            else -> v != null
        }
    }

    private fun evalExpr(raw: String, scopeMap: Map<String, Any?>): Any? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed == "True") return true
        if (trimmed == "False") return false
        if (trimmed == "None") return null

        // f-string
        if ((trimmed.startsWith("f\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("f'") && trimmed.endsWith("'"))
        ) {
            val body = trimmed.substring(2, trimmed.length - 1)
            return Regex("""\{([^}]+)\}""").replace(body) { m ->
                formatPyValue(evalExpr(m.groupValues[1], scopeMap))
            }
        }

        // String literal
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // List literal
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val inner = trimmed.substring(1, trimmed.length - 1)
            return splitTopLevelArgs(inner).map { evalExpr(it, scopeMap) }.toMutableList()
        }

        // Number
        trimmed.toLongOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }

        // Built-in functions
        if (trimmed == "time.ctime()") {
            return SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH).format(Date())
        }
        if (trimmed.startsWith("len(") && trimmed.endsWith(")")) {
            val inner = evalExpr(trimmed.removePrefix("len(").dropLast(1), scopeMap)
            return when (inner) {
                is String -> inner.length
                is Collection<*> -> inner.size
                is Map<*, *> -> inner.size
                else -> 0
            }
        }
        if (trimmed.startsWith("sum(") && trimmed.endsWith(")")) {
            val inner = evalExpr(trimmed.removePrefix("sum(").dropLast(1), scopeMap)
            if (inner is List<*>) {
                return inner.sumOf { it.toString().toDoubleOrNull() ?: 0.0 }
            }
        }
        if (trimmed.startsWith("math.sqrt(") && trimmed.endsWith(")")) {
            val v = evalExpr(trimmed.removePrefix("math.sqrt(").dropLast(1), scopeMap).toString().toDoubleOrNull() ?: 0.0
            return sqrt(v)
        }
        if (trimmed.startsWith("abs(") && trimmed.endsWith(")")) {
            val v = evalExpr(trimmed.removePrefix("abs(").dropLast(1), scopeMap).toString().toDoubleOrNull() ?: 0.0
            return abs(v)
        }
        if (trimmed.startsWith("random.randint(") && trimmed.endsWith(")")) {
            val args = splitTopLevelArgs(trimmed.removePrefix("random.randint(").dropLast(1))
                .mapNotNull { evalExpr(it, scopeMap).toString().toIntOrNull() }
            if (args.size == 2 && args[1] >= args[0]) {
                return Random.nextInt(args[0], args[1] + 1)
            }
        }
        if (trimmed.startsWith("requests.get(") && trimmed.endsWith(")")) {
            val url = evalExpr(trimmed.removePrefix("requests.get(").dropLast(1), scopeMap).toString()
            return fetchHttpText(url)
        }

        // Function call
        val callMatch = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\((.*)\)$""").find(trimmed)
        if (callMatch != null && functions.containsKey(callMatch.groupValues[1])) {
            val fnName = callMatch.groupValues[1]
            val args = splitTopLevelArgs(callMatch.groupValues[2]).map { evalExpr(it, scopeMap) }
            return invokeFunction(fnName, args) { line, err -> appendLog(line, err) }
        }

        // Variable lookup
        if (scopeMap.containsKey(trimmed)) return scopeMap[trimmed]

        // Simple binary math / string concatenation (+, -, *, /, **)
        for (op in listOf("+", "-", "*", "/")) {
            val idx = findTopLevelOperator(trimmed, op[0])
            if (idx > 0) {
                val left = evalExpr(trimmed.substring(0, idx), scopeMap)
                val right = evalExpr(trimmed.substring(idx + 1), scopeMap)
                return when (op) {
                    "+" -> addValues(left, right)
                    "-" -> (left.toString().toDoubleOrNull() ?: 0.0) - (right.toString().toDoubleOrNull() ?: 0.0)
                    "*" -> (left.toString().toDoubleOrNull() ?: 0.0) * (right.toString().toDoubleOrNull() ?: 0.0)
                    "/" -> {
                        val d = right.toString().toDoubleOrNull() ?: 1.0
                        if (d == 0.0) "ZeroDivisionError" else (left.toString().toDoubleOrNull() ?: 0.0) / d
                    }
                    else -> trimmed
                }
            }
        }

        return trimmed
    }

    private fun fetchHttpText(url: String): String {
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                resp.body?.string()?.take(2000) ?: "HTTP ${resp.code}"
            }
        }.getOrElse { "HTTP Xato: ${it.message}" }
    }

    private fun addValues(a: Any?, b: Any?): Any {
        if (a is Number && b is Number) {
            return if (a is Long && b is Long) a + b else a.toDouble() + b.toDouble()
        }
        return formatPyValue(a) + formatPyValue(b)
    }

    private fun formatPyValue(v: Any?): String = when (v) {
        null -> "None"
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private fun findTopLevelOperator(expr: String, op: Char): Int {
        var depth = 0
        var inQuote: Char? = null
        for (i in expr.indices.reversed()) {
            val c = expr[i]
            if (inQuote != null) {
                if (c == inQuote) inQuote = null
                continue
            }
            if (c == '"' || c == '\'') {
                inQuote = c
                continue
            }
            if (c == ')' || c == ']') depth++
            else if (c == '(' || c == '[') depth--
            else if (depth == 0 && c == op) return i
        }
        return -1
    }

    private fun splitTopLevelArgs(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var inQuote: Char? = null
        for (c in raw) {
            if (inQuote != null) {
                cur.append(c)
                if (c == inQuote) inQuote = null
                continue
            }
            if (c == '"' || c == '\'') {
                inQuote = c
                cur.append(c)
                continue
            }
            when (c) {
                '(', '[', '{' -> {
                    depth++
                    cur.append(c)
                }
                ')', ']', '}' -> {
                    depth--
                    cur.append(c)
                }
                ',' -> if (depth == 0) {
                    result.add(cur.toString().trim())
                    cur.clear()
                } else {
                    cur.append(c)
                }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) result.add(cur.toString().trim())
        return result
    }

    private suspend fun runTelegramPollingLoop(token: String, log: (String, Boolean) -> Unit) {
        val meReq = Request.Builder()
            .url("https://api.telegram.org/bot$token/getMe")
            .get()
            .build()

        val botUsername = runCatching {
            client.newCall(meReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!json.optBoolean("ok", false)) {
                    error(json.optString("description", "Token yaroqsiz"))
                }
                json.getJSONObject("result").optString("username", "telegram_bot")
            }
        }.getOrElse { err ->
            log("[Telegram Xato]: ${err.message}", true)
            _state.value = _state.value.copy(isRunning = false)
            return
        }

        _state.value = _state.value.copy(botUsername = "@$botUsername")
        log("[Telegram API] Ulandi: @$botUsername — Long polling faol!", false)

        var offset = 0L
        while (scope.isActive && _state.value.isRunning) {
            runCatching {
                val updatesReq = Request.Builder()
                    .url("https://api.telegram.org/bot$token/getUpdates?offset=$offset&timeout=15")
                    .get()
                    .build()
                client.newCall(updatesReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    if (json.optBoolean("ok", false)) {
                        val arr = json.getJSONArray("result")
                        for (i in 0 until arr.length()) {
                            val upd = arr.getJSONObject(i)
                            offset = upd.getLong("update_id") + 1
                            val msg = upd.optJSONObject("message") ?: continue
                            val chatId = msg.getJSONObject("chat").getLong("id")
                            val firstName = msg.optJSONObject("from")?.optString("first_name") ?: "User"
                            val text = msg.optString("text", "")
                            if (text.isNotEmpty()) {
                                log("[TG @$botUsername] $firstName: $text", false)
                                val reply = evaluateBotReply(text, firstName)
                                sendTelegramMessage(token, chatId, reply)
                                log("[TG Javob -> $chatId]: $reply", false)
                                _state.value = _state.value.copy(
                                    updatesProcessed = _state.value.updatesProcessed + 1
                                )
                            }
                        }
                    }
                }
            }.onFailure { err ->
                log("[Polling ogohlantirish]: ${err.message}", true)
                delay(2500)
            }
            delay(350)
        }
    }

    private fun sendTelegramMessage(token: String, chatId: Long, text: String) {
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("text", text)
            .build()
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendMessage")
            .post(body)
            .build()
        runCatching { client.newCall(req).execute().close() }
    }

    private fun evaluateBotReply(text: String, userName: String): String {
        if (functions.containsKey("handle_message")) {
            val res = invokeFunction("handle_message", listOf(text, userName)) { line, err ->
                appendLog(line, err)
            }
            if (res != null) return formatPyValue(res)
        }
        return when {
            text.startsWith("/start") -> "Salom $userName! Men Dasturchi AI Python tizimida ishlayapman."
            text.startsWith("/help") -> "Buyruqlar: /start, /time yoki matn yuboring."
            text.startsWith("/time") -> "Vaqt: ${SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault()).format(Date())}"
            else -> "Qabul qilindi ($userName): $text"
        }
    }

    private fun extractTelegramToken(source: String): String? {
        val patterns = listOf(
            Regex("""(?:BOT_)?TOKEN\s*=\s*["']([^"']+)["']"""),
            Regex("""(?:TeleBot|Bot)\(\s*["']([^"']+)["']"""),
            Regex("""\.token\(\s*["']([^"']+)["']\s*\)""")
        )
        for (p in patterns) {
            val match = p.find(source)
            if (match != null) return match.groupValues[1].trim()
        }
        return null
    }

    private fun detectHandlers(source: String): List<String> {
        val found = mutableSetOf<String>()
        functions.keys.forEach { found.add("$it()") }
        if (found.isEmpty()) found.add("script")
        return found.toList()
    }

    private fun appendLog(text: String, isError: Boolean = false) {
        val line = TerminalLine(text = text, isError = isError)
        _state.value = _state.value.copy(logs = (_state.value.logs + line).takeLast(160))
    }
}
