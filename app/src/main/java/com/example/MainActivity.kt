package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DasturchiViewModel
import com.example.ui.components.CyberGridBackground
import com.example.ui.screens.AiAgentScreen
import com.example.ui.screens.ApiConfigScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RuntimeScreen
import com.example.ui.theme.DasturchiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: DasturchiViewModel = viewModel()
            val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            DasturchiTheme(darkTheme = isDark) {
                DasturchiAppShell(viewModel = viewModel)
            }
        }
    }
}

private data class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
private fun DasturchiAppShell(viewModel: DasturchiViewModel) {
    val apiConfig by viewModel.apiConfig.collectAsStateWithLifecycle()
    val apiRawJson by viewModel.apiRawJson.collectAsStateWithLifecycle()
    val liveOps by viewModel.liveOperations.collectAsStateWithLifecycle()
    val latestZipPath by viewModel.latestGeneratedZipPath.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAgentBusy by viewModel.isAgentBusy.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val allFileCount by viewModel.allFileCount.collectAsStateWithLifecycle()
    val openedFilePath by viewModel.openedFilePath.collectAsStateWithLifecycle()
    val openedFileContent by viewModel.openedFileContent.collectAsStateWithLifecycle()
    val inspectedArchiveName by viewModel.inspectedArchiveName.collectAsStateWithLifecycle()
    val inspectedEntries by viewModel.inspectedEntries.collectAsStateWithLifecycle()
    val terminalLines by viewModel.terminalLines.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val serverLogs by viewModel.serverLogs.collectAsStateWithLifecycle()
    val botState by viewModel.botState.collectAsStateWithLifecycle()
    val automationTasks by viewModel.automationTasks.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var runtimeSubTab by rememberSaveable { mutableIntStateOf(0) }
    var showApiConfigScreen by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val destinations = listOf(
        NavDestination("Bosh Menyu", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
        NavDestination("AI Agent", Icons.Rounded.Psychology, Icons.Outlined.Psychology, "nav_agent"),
        NavDestination("Fayllar", Icons.Rounded.Folder, Icons.Outlined.Folder, "nav_files"),
        NavDestination("Server & Run", Icons.Rounded.Terminal, Icons.Outlined.Terminal, "nav_runtime")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Box(modifier = Modifier.fillMaxSize()) {
            CyberGridBackground()

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useRail && !showApiConfigScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                        ) {
                            destinations.forEachIndexed { idx, dest ->
                                val selected = selectedTab == idx
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = idx },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label, maxLines = 1) },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && !showApiConfigScreen) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                        ) {
                            destinations.forEachIndexed { idx, dest ->
                                val selected = selectedTab == idx
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { selectedTab = idx },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        AnimatedContent(
                            targetState = if (showApiConfigScreen) -1 else selectedTab,
                            transitionSpec = {
                                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 16 })
                                    .togetherWith(fadeOut(tween(180)))
                            },
                            label = "main_nav_anim"
                        ) { state ->
                            when (state) {
                                -1 -> ApiConfigScreen(
                                    apiConfig = apiConfig,
                                    rawJson = apiRawJson,
                                    onSaveConfig = viewModel::saveApiConfig,
                                    onSaveRawJson = viewModel::saveApiRawJson,
                                    onExportApiJson = viewModel::exportApiJson,
                                    onImportApiJson = viewModel::importApiJson,
                                    onBack = { showApiConfigScreen = false }
                                )

                                0 -> HomeScreen(
                                    apiConfig = apiConfig,
                                    fileCount = allFileCount,
                                    serverStatus = serverStatus,
                                    botState = botState,
                                    liveOperations = liveOps,
                                    isDarkTheme = isDark,
                                    onToggleTheme = viewModel::toggleTheme,
                                    onNavigateToAgent = { selectedTab = 1 },
                                    onAutoCreateKotlinZip = {
                                        selectedTab = 1
                                        viewModel.autoGenerateKotlinAiStudioZip()
                                    },
                                    onOpenApiConfig = { showApiConfigScreen = true }
                                )

                                1 -> AiAgentScreen(
                                    apiConfig = apiConfig,
                                    messages = chatMessages,
                                    liveOperations = liveOps,
                                    latestGeneratedZipPath = latestZipPath,
                                    isBusy = isAgentBusy,
                                    onSendPrompt = { viewModel.sendPromptToGroq(it) },
                                    onAutoKotlinZip = { viewModel.autoGenerateKotlinAiStudioZip() },
                                    onClearOps = viewModel::clearLiveOperations,
                                    onDownloadZip = { zipPath, uri ->
                                        viewModel.prepareExportZip(zipPath)
                                        viewModel.completeExportZip(uri)
                                    },
                                    onOpenApiConfig = { showApiConfigScreen = true }
                                )

                                2 -> FilesScreen(
                                    currentFolder = currentFolder,
                                    files = files,
                                    openedFilePath = openedFilePath,
                                    openedFileContent = openedFileContent,
                                    inspectedArchiveName = inspectedArchiveName,
                                    inspectedEntries = inspectedEntries,
                                    onNavigateToFolder = viewModel::navigateToFolder,
                                    onNavigateUp = viewModel::navigateUp,
                                    onCreateFolder = viewModel::createFolder,
                                    onCreateFile = viewModel::createFile,
                                    onOpenFile = viewModel::openFileForEditing,
                                    onUpdateDraft = viewModel::updateOpenedFileDraft,
                                    onSaveFile = viewModel::saveOpenedFile,
                                    onCloseEditor = viewModel::closeFileEditor,
                                    onDeleteEntry = viewModel::deleteEntry,
                                    onCreateZipInWorkspace = viewModel::createZipInWorkspace,
                                    onPrepareExportZip = viewModel::prepareExportZip,
                                    onCompleteExportZip = viewModel::completeExportZip,
                                    onInspectWorkspaceArchive = viewModel::inspectWorkspaceArchive,
                                    onExtractWorkspaceArchive = viewModel::extractWorkspaceArchive,
                                    onInspectExternalArchive = viewModel::inspectAndExtractExternalArchive,
                                    onClearArchiveView = viewModel::clearInspectedArchive,
                                    onRunPythonFile = { pyPath ->
                                        viewModel.startPythonBot(pyPath)
                                        runtimeSubTab = 1
                                        selectedTab = 3
                                    },
                                    onOpenApiConfig = { showApiConfigScreen = true }
                                )

                                else -> RuntimeScreen(
                                    selectedTab = runtimeSubTab,
                                    onSelectTab = { runtimeSubTab = it },
                                    terminalLines = terminalLines,
                                    onRunTerminal = viewModel::runTerminalCommand,
                                    botState = botState,
                                    onStartBot = viewModel::startPythonBot,
                                    onStopBot = viewModel::stopPythonBot,
                                    onTestBotMessage = viewModel::sendTestMessageToBot,
                                    serverStatus = serverStatus,
                                    serverLogs = serverLogs,
                                    onStartServer = viewModel::startLocalServer,
                                    onStopServer = viewModel::stopLocalServer,
                                    automationTasks = automationTasks,
                                    onAddAutomation = viewModel::addAutomationTask,
                                    onToggleAutomation = viewModel::toggleAutomationTask,
                                    onRunAutomationOnce = viewModel::runAutomationOnce,
                                    onDeleteAutomation = viewModel::deleteAutomationTask
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
