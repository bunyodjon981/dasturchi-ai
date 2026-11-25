package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.engine.LocalServerStatus
import com.example.data.model.AutomationTask
import com.example.data.model.BotRuntimeState
import com.example.data.model.ServerLogEntry
import com.example.data.model.TerminalLine
import com.example.ui.components.PulsingStatusDot
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VioletAccent

@Composable
fun RuntimeScreen(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    terminalLines: List<TerminalLine>,
    onRunTerminal: (String) -> Unit,
    botState: BotRuntimeState,
    onStartBot: (String) -> Unit,
    onStopBot: () -> Unit,
    onTestBotMessage: (String) -> Unit,
    serverStatus: LocalServerStatus,
    serverLogs: List<ServerLogEntry>,
    onStartServer: (String, Int) -> Unit,
    onStopServer: () -> Unit,
    automationTasks: List<AutomationTask>,
    onAddAutomation: (String, String, String, Int) -> Unit,
    onToggleAutomation: (String) -> Unit,
    onRunAutomationOnce: (String) -> Unit,
    onDeleteAutomation: (String) -> Unit
) {
    val tabs = listOf(
        "Terminal" to Icons.Rounded.Terminal,
        "Python Studio" to Icons.Rounded.SmartToy,
        "Localhost" to Icons.Rounded.Dns,
        "Avtomat" to Icons.Rounded.Schedule
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.coerceIn(0, tabs.lastIndex),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ) {
            tabs.forEachIndexed { idx, (label, icon) ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { onSelectTab(idx) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                            Text(label)
                        }
                    },
                    modifier = Modifier.testTag("runtime_tab_$idx")
                )
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            label = "runtime_tab_anim",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                0 -> TerminalTab(terminalLines, onRunTerminal)
                1 -> PythonBotTab(botState, onStartBot, onStopBot, onTestBotMessage)
                2 -> LocalhostServerTab(serverStatus, serverLogs, onStartServer, onStopServer)
                else -> AutomationTab(
                    tasks = automationTasks,
                    onAddTask = onAddAutomation,
                    onToggleTask = onToggleAutomation,
                    onRunOnce = onRunAutomationOnce,
                    onDeleteTask = onDeleteAutomation
                )
            }
        }
    }
}

@Composable
private fun TerminalTab(
    lines: List<TerminalLine>,
    onRunCommand: (String) -> Unit
) {
    var cmdInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    val quickCmds = listOf("ls -la", "tree", "pwd", "uname -a", "date", "help", "clear")

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF060911),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(lines, key = { it.id }) { line ->
                    val color = when {
                        line.isCommand -> ElectricEmerald
                        line.isError -> CoralError
                        else -> Color(0xFFE2E8F0)
                    }
                    Text(
                        text = line.text,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = color
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickCmds) { cmd ->
                AssistChip(
                    onClick = { onRunCommand(cmd) },
                    label = { Text(cmd, fontFamily = JetBrainsMonoFamily, fontSize = 12.sp) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = cmdInput,
                onValueChange = { cmdInput = it },
                placeholder = { Text("Terminal buyrug'i (masalan: ls -la)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_input"),
                shape = RoundedCornerShape(14.dp)
            )
            Button(
                onClick = {
                    val toRun = cmdInput
                    cmdInput = ""
                    onRunCommand(toRun)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AmberSpark, contentColor = Color(0xFF2B1B00)),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("terminal_run_btn")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Bajarish")
            }
        }
    }
}

@Composable
private fun PythonBotTab(
    botState: BotRuntimeState,
    onStartBot: (String) -> Unit,
    onStopBot: () -> Unit,
    onTestBotMessage: (String) -> Unit
) {
    var scriptPath by remember(botState.scriptPath) {
        mutableStateOf(botState.scriptPath.ifBlank { "python_lab/tahlil_va_hisob.py" })
    }
    var testMsg by remember { mutableStateOf("Salom Python!") }
    val listState = rememberLazyListState()

    LaunchedEffect(botState.logs.size) {
        if (botState.logs.isNotEmpty()) listState.animateScrollToItem(botState.logs.size - 1)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, VioletAccent.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsingStatusDot(isActive = botState.isRunning, activeColor = ElectricEmerald)
                        Text(
                            text = if (botState.isRunning) {
                                "Python Faol ${botState.botUsername}".trim()
                            } else {
                                "Universal Python Studio"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "${botState.updatesProcessed} amal",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            "python_lab/tahlil_va_hisob.py",
                            "telegram_echo_bot/bot.py"
                        )
                    ) { preset ->
                        FilterChip(
                            selected = scriptPath == preset,
                            onClick = { scriptPath = preset },
                            label = { Text(preset.substringAfterLast('/'), fontFamily = JetBrainsMonoFamily, fontSize = 11.5.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = scriptPath,
                    onValueChange = { scriptPath = it },
                    label = { Text("Istalgan .py fayl yo'li") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (botState.isRunning) onStopBot() else onStartBot(scriptPath)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (botState.isRunning) CoralError else ElectricEmerald,
                            contentColor = if (botState.isRunning) Color.White else Color(0xFF002114)
                        ),
                        modifier = Modifier.weight(1f).testTag("bot_start_stop_btn")
                    ) {
                        Icon(
                            imageVector = if (botState.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (botState.isRunning) "To'xtatish" else "Ishga tushirish", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Console Output
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF060911),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (botState.logs.isEmpty()) {
                    item {
                        Text(
                            text = "Python skriptingizdagi TOKEN ni BotFather tokeniga almashtirib Run bosing — bot telefoningizda real Telegram API bilan ishlaydi.",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    items(botState.logs, key = { it.id }) { line ->
                        Text(
                            text = line.text,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = if (line.isError) CoralError else ElectricCyan
                        )
                    }
                }
            }
        }

        // Local Bot Handler Tester
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = testMsg,
                onValueChange = { testMsg = it },
                label = { Text("Botga test xabar") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("bot_test_input")
            )
            Button(
                onClick = { onTestBotMessage(testMsg) },
                colors = ButtonDefaults.buttonColors(containerColor = VioletAccent, contentColor = Color.White),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Sinash")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LocalhostServerTab(
    status: LocalServerStatus,
    logs: List<ServerLogEntry>,
    onStartServer: (String, Int) -> Unit,
    onStopServer: () -> Unit
) {
    val context = LocalContext.current
    var rootFolder by remember(status.rootFolder) { mutableStateOf(status.rootFolder) }
    var portText by remember(status.port) { mutableStateOf(status.port.toString()) }
    var showPreview by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulsingStatusDot(isActive = status.isRunning, activeColor = ElectricEmerald)
                        Column {
                            Text(
                                text = if (status.isRunning) "Server Faol" else "Localhost Veb-Server",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (status.isRunning) status.url else "127.0.0.1:8080",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }

                    if (status.isRunning) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { showPreview = !showPreview }) {
                                Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showPreview) "Loglar" else "Ko'rish")
                            }
                            IconButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(status.url)))
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Brauzerda ochish", tint = ElectricEmerald)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rootFolder,
                        onValueChange = { rootFolder = it },
                        label = { Text("Sayt papkasi") },
                        singleLine = true,
                        modifier = Modifier.weight(1.7f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        if (status.isRunning) {
                            onStopServer()
                            showPreview = false
                        } else {
                            onStartServer(rootFolder, portText.toIntOrNull() ?: 8080)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status.isRunning) CoralError else ElectricCyan,
                        contentColor = if (status.isRunning) Color.White else Color(0xFF001E2E)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("server_toggle_btn")
                ) {
                    Icon(if (status.isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (status.isRunning) "Serverni To'xtatish" else "Localhost Serverni Yoqish", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showPreview && status.isRunning) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl(status.url)
                        }
                    },
                    update = { webView ->
                        if (webView.url != status.url) webView.loadUrl(status.url)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF060911),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Text(
                                text = "HTTP so'rov loglari shu yerda real vaqtda ko'rinadi...",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    } else {
                        items(logs, key = { it.id }) { entry ->
                            val statusColor = if (entry.statusCode < 400) ElectricEmerald else CoralError
                            Text(
                                text = "[${entry.statusCode}] ${entry.method} ${entry.path} (${entry.clientIp})",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationTab(
    tasks: List<AutomationTask>,
    onAddTask: (String, String, String, Int) -> Unit,
    onToggleTask: (String) -> Unit,
    onRunOnce: (String) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Avtomatlashtirish", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Skript, buyruq va server nazoratini avtomatik bajaring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricEmerald, contentColor = Color(0xFF002114)),
                modifier = Modifier.testTag("add_automation_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Yangi")
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (task.isEnabled) ElectricEmerald.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${task.taskType} • ${task.target} (${task.intervalSeconds}s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                            Switch(
                                checked = task.isEnabled,
                                onCheckedChange = { onToggleTask(task.id) }
                            )
                        }

                        Text(
                            text = "Oxirgi natija: ${task.lastResult}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRunOnce(task.id) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Hozir bajarish")
                            }
                            IconButton(onClick = { onDeleteTask(task.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "O'chirish", tint = CoralError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("Python Bot Nazorati") }
        var taskType by remember { mutableStateOf("SHELL_COMMAND") }
        var target by remember { mutableStateOf("ls -la") }
        var interval by remember { mutableStateOf("30") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Yangi Avtomat Vazifa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Vazifa nomi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SHELL_COMMAND", "PYTHON_SCRIPT", "SERVER_HEALTH").forEach { t ->
                            FilterChip(
                                selected = taskType == t,
                                onClick = {
                                    taskType = t
                                    target = when (t) {
                                        "PYTHON_SCRIPT" -> "telegram_echo_bot/bot.py"
                                        "SERVER_HEALTH" -> "web_studio_demo"
                                        else -> "ls -la"
                                    }
                                },
                                label = {
                                    Text(
                                        when (t) {
                                            "PYTHON_SCRIPT" -> "Python"
                                            "SERVER_HEALTH" -> "Server"
                                            else -> "Terminal"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Buyruq / Fayl / Papka") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { interval = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Interval (soniya)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAddDialog = false
                    onAddTask(name, taskType, target, interval.toIntOrNull() ?: 30)
                }) {
                    Text("Qo'shish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Bekor qilish") }
            }
        )
    }
}
