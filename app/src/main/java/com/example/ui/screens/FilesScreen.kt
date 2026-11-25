package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArchiveEntryInfo
import com.example.data.model.WorkspaceFile
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.VioletAccent

@Composable
fun FilesScreen(
    currentFolder: String,
    files: List<WorkspaceFile>,
    openedFilePath: String?,
    openedFileContent: String,
    inspectedArchiveName: String?,
    inspectedEntries: List<ArchiveEntryInfo>,
    onNavigateToFolder: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onUpdateDraft: (String) -> Unit,
    onSaveFile: () -> Unit,
    onCloseEditor: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onCreateZipInWorkspace: (String, String) -> Unit,
    onPrepareExportZip: (String) -> Unit,
    onCompleteExportZip: (Uri) -> Unit,
    onInspectWorkspaceArchive: (String) -> Unit,
    onExtractWorkspaceArchive: (String) -> Unit,
    onInspectExternalArchive: (Uri, Boolean) -> Unit,
    onClearArchiveView: () -> Unit,
    onRunPythonFile: (String) -> Unit,
    onOpenApiConfig: () -> Unit
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var pendingExternalExtract by remember { mutableStateOf(false) }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let(onCompleteExportZip)
    }

    val openArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onInspectExternalArchive(it, pendingExternalExtract) }
    }

    // If Code Editor is open, show full-screen editor with BackHandler
    if (openedFilePath != null) {
        BackHandler { onCloseEditor() }
        CodeEditorPane(
            path = openedFilePath,
            content = openedFileContent,
            onContentChange = onUpdateDraft,
            onSave = onSaveFile,
            onClose = onCloseEditor,
            onRunPython = if (openedFilePath.endsWith(".py", true)) {
                { onRunPythonFile(openedFilePath) }
            } else null
        )
        return
    }

    // If Archive Inspector (.zip / .swb) is open
    if (inspectedArchiveName != null) {
        BackHandler { onClearArchiveView() }
        ArchiveInspectorPane(
            archiveName = inspectedArchiveName,
            entries = inspectedEntries,
            onClose = onClearArchiveView
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Path & Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (currentFolder.isNotEmpty()) {
                            IconButton(onClick = onNavigateUp, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Orqaga"
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "/workspace" + if (currentFolder.isEmpty()) "" else "/$currentFolder",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    AssistChip(
                        onClick = onOpenApiConfig,
                        leadingIcon = {
                            Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberSpark)
                        },
                        label = { Text("api.json") }
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        ActionPillButton(
                            label = "+ Papka",
                            icon = Icons.Rounded.CreateNewFolder,
                            accent = ElectricEmerald,
                            onClick = { showNewFolderDialog = true },
                            testTag = "create_folder_btn"
                        )
                    }
                    item {
                        ActionPillButton(
                            label = "+ Fayl",
                            icon = Icons.Rounded.NoteAdd,
                            accent = ElectricCyan,
                            onClick = { showNewFileDialog = true },
                            testTag = "create_file_btn"
                        )
                    }
                    item {
                        ActionPillButton(
                            label = "ZIP Yuklash",
                            icon = Icons.Rounded.Download,
                            accent = AmberSpark,
                            onClick = {
                                val target = currentFolder.ifEmpty { "" }
                                val zipFileName = (currentFolder.substringAfterLast('/').ifEmpty { "workspace" }) + ".zip"
                                onPrepareExportZip(target)
                                exportZipLauncher.launch(zipFileName)
                            },
                            testTag = "download_zip_btn"
                        )
                    }
                    item {
                        ActionPillButton(
                            label = "SWB/ZIP Ochish",
                            icon = Icons.Rounded.Archive,
                            accent = VioletAccent,
                            onClick = {
                                pendingExternalExtract = true
                                openArchiveLauncher.launch(arrayOf("*/*"))
                            },
                            testTag = "open_swb_zip_btn"
                        )
                    }
                }
            }
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Bu papka bo'sh", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Yuqoridagi tugmalar orqali fayl, papka yarating yoki SWB/ZIP oching.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.relativePath }) { item ->
                    WorkspaceFileRow(
                        item = item,
                        onOpen = {
                            if (item.isDirectory) {
                                onNavigateToFolder(item.relativePath)
                            } else if (item.extension in setOf("zip", "swb")) {
                                onInspectWorkspaceArchive(item.relativePath)
                            } else {
                                onOpenFile(item.relativePath)
                            }
                        },
                        onExtract = { onExtractWorkspaceArchive(item.relativePath) },
                        onZipAndDownload = {
                            onPrepareExportZip(item.relativePath)
                            val name = item.name.substringBeforeLast('.') + ".zip"
                            exportZipLauncher.launch(name)
                        },
                        onZipHere = {
                            onCreateZipInWorkspace(item.relativePath, "${item.name}.zip")
                        },
                        onRunPython = { onRunPythonFile(item.relativePath) },
                        onDelete = { onDeleteEntry(item.relativePath) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showNewFolderDialog) {
        SimpleNameDialog(
            title = "Yangi Papka",
            label = "Papka nomi",
            defaultName = "yangi_loyiha",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = {
                showNewFolderDialog = false
                onCreateFolder(it)
            }
        )
    }

    if (showNewFileDialog) {
        SimpleNameDialog(
            title = "Yangi Fayl",
            label = "Fayl nomi (.py, .kt, .html, .json...)",
            defaultName = "script.py",
            onDismiss = { showNewFileDialog = false },
            onConfirm = {
                showNewFileDialog = false
                onCreateFile(it, "")
            }
        )
    }
}

@Composable
private fun WorkspaceFileRow(
    item: WorkspaceFile,
    onOpen: () -> Unit,
    onExtract: () -> Unit,
    onZipAndDownload: () -> Unit,
    onZipHere: () -> Unit,
    onRunPython: () -> Unit,
    onDelete: () -> Unit
) {
    val isArchive = !item.isDirectory && item.extension in setOf("zip", "swb")
    val isPython = !item.isDirectory && item.extension == "py"

    val iconColor = when {
        item.isDirectory -> AmberSpark
        isArchive -> VioletAccent
        isPython -> ElectricEmerald
        item.extension in setOf("kt", "java") -> ElectricCyan
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when {
        item.isDirectory -> Icons.Rounded.Folder
        isArchive -> Icons.Rounded.FolderZip
        else -> Icons.Rounded.Description
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isDirectory) "${item.sizeBytes} ta element" else "${item.sizeBytes} bayt • ${item.extension.uppercase().ifEmpty { "FAYL" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPython) {
                IconButton(onClick = onRunPython) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Python ishga tushirish", tint = ElectricEmerald)
                }
            }

            if (isArchive) {
                IconButton(onClick = onExtract) {
                    Icon(Icons.Rounded.Unarchive, contentDescription = "Chiqarish", tint = VioletAccent)
                }
            } else {
                IconButton(onClick = onZipHere) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = "ZIP qilish", tint = AmberSpark)
                }
            }

            IconButton(onClick = onZipAndDownload) {
                Icon(Icons.Rounded.Download, contentDescription = "Telefonga yuklash", tint = ElectricCyan)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "O'chirish", tint = CoralError.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun CodeEditorPane(
    path: String,
    content: String,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onRunPython: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Yopish")
                    }
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onRunPython != null) {
                        Button(
                            onClick = {
                                onSave()
                                onRunPython()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VioletAccent, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run")
                        }
                    }
                    Button(
                        onClick = onSave,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricEmerald, contentColor = Color(0xFF002114)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_file_btn")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Saqlash")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("code_editor_field"),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ArchiveInspectorPane(
    archiveName: String,
    entries: List<ArchiveEntryInfo>,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, VioletAccent.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, tint = VioletAccent)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(archiveName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${entries.size} ta element aniqlandi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Yopish")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries) { entry ->
                var expanded by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = entry.previewText.isNotBlank()) { expanded = !expanded }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(entry.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (entry.isSketchwareJson) {
                                Surface(shape = RoundedCornerShape(999.dp), color = VioletAccent.copy(alpha = 0.16f)) {
                                    Text(
                                        "SWB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VioletAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${entry.sizeBytes}B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        AnimatedVisibility(visible = expanded && entry.previewText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(
                                    text = entry.previewText,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.5.sp,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SimpleNameDialog(
    title: String,
    label: String,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) { Text("Yaratish") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Bekor qilish") }
        }
    )
}
