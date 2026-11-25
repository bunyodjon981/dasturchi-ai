package com.example.data.engine

import android.content.Context
import com.example.data.model.AutomationTask
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AutomationScheduler(
    private val context: Context,
    private val terminalExecutor: TerminalExecutor,
    private val pythonBotRunner: PythonBotRunner,
    private val localWebServer: LocalWebServer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val storeFile = File(context.filesDir, "automations.json")

    private val _tasks = MutableStateFlow<List<AutomationTask>>(emptyList())
    val tasks: StateFlow<List<AutomationTask>> = _tasks.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        if (!storeFile.exists()) {
            val defaults = listOf(
                AutomationTask(
                    name = "Workspace holatini tekshirish",
                    taskType = "SHELL_COMMAND",
                    target = "ls -la",
                    intervalSeconds = 60,
                    isEnabled = false
                ),
                AutomationTask(
                    name = "Localhost Server Nazorati",
                    taskType = "SERVER_HEALTH",
                    target = "web_studio_demo",
                    intervalSeconds = 30,
                    isEnabled = false
                )
            )
            _tasks.value = defaults
            saveTasks()
            return
        }

        runCatching {
            val arr = JSONArray(storeFile.readText(Charsets.UTF_8))
            val list = mutableListOf<AutomationTask>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    AutomationTask(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        taskType = o.getString("taskType"),
                        target = o.getString("target"),
                        intervalSeconds = o.optInt("intervalSeconds", 30),
                        isEnabled = false,
                        lastRunMs = o.optLong("lastRunMs", 0L),
                        lastResult = o.optString("lastResult", "Tayyor")
                    )
                )
            }
            _tasks.value = list
        }
    }

    private fun saveTasks() {
        val arr = JSONArray()
        _tasks.value.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("taskType", t.taskType)
                    put("target", t.target)
                    put("intervalSeconds", t.intervalSeconds)
                    put("lastRunMs", t.lastRunMs)
                    put("lastResult", t.lastResult)
                }
            )
        }
        storeFile.writeText(arr.toString(2), Charsets.UTF_8)
    }

    fun addTask(name: String, taskType: String, target: String, intervalSeconds: Int) {
        val task = AutomationTask(
            name = name.trim().ifBlank { "Avtomat vazifa" },
            taskType = taskType,
            target = target.trim(),
            intervalSeconds = intervalSeconds.coerceIn(5, 3600)
        )
        _tasks.value = _tasks.value + task
        saveTasks()
    }

    fun deleteTask(id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _tasks.value = _tasks.value.filterNot { it.id == id }
        saveTasks()
    }

    fun toggleTask(id: String) {
        val task = _tasks.value.find { it.id == id } ?: return
        val enable = !task.isEnabled
        updateTask(id) { it.copy(isEnabled = enable) }
        if (enable) {
            jobs[id]?.cancel()
            jobs[id] = scope.launch {
                while (isActive) {
                    executeSingleTask(id)
                    val interval = (_tasks.value.find { it.id == id }?.intervalSeconds ?: 30) * 1000L
                    delay(interval)
                }
            }
        } else {
            jobs[id]?.cancel()
            jobs.remove(id)
        }
    }

    fun runOnce(id: String) {
        scope.launch { executeSingleTask(id) }
    }

    private suspend fun executeSingleTask(id: String) {
        val task = _tasks.value.find { it.id == id } ?: return
        val resultMsg = when (task.taskType) {
            "SHELL_COMMAND" -> {
                val res = terminalExecutor.execute(task.target)
                val out = res.outputLines.firstOrNull() ?: "Exit ${res.exitCode}"
                "Buyruq (${res.exitCode}): $out"
            }
            "PYTHON_SCRIPT" -> {
                pythonBotRunner.startScript(task.target)
                "Python skript ishga tushirildi: ${task.target}"
            }
            "SERVER_HEALTH" -> {
                if (!localWebServer.status.value.isRunning) {
                    localWebServer.start(task.target.ifBlank { "web_studio_demo" })
                    "Server qayta yoqildi"
                } else {
                    "Server faol: ${localWebServer.status.value.url}"
                }
            }
            else -> "Bajarildi"
        }

        updateTask(id) {
            it.copy(lastRunMs = System.currentTimeMillis(), lastResult = resultMsg)
        }
        saveTasks()
    }

    private fun updateTask(id: String, transform: (AutomationTask) -> AutomationTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }
}
