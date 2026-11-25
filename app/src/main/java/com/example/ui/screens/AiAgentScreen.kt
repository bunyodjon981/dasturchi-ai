package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ApiConfig
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRole
import com.example.data.model.LiveOperation
import com.example.ui.components.LiveOperationCard
import com.example.ui.components.PulsingStatusDot
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald

@Composable
fun AiAgentScreen(
    apiConfig: ApiConfig,
    messages: List<ChatMessage>,
    liveOperations: List<LiveOperation>,
    latestGeneratedZipPath: String?,
    isBusy: Boolean,
    onSendPrompt: (String) -> Unit,
    onAutoKotlinZip: () -> Unit,
    onClearOps: () -> Unit,
    onDownloadZip: (String, Uri) -> Unit,
    onOpenApiConfig: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showLiveHud by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val zipSrc = latestGeneratedZipPath
        if (uri != null && zipSrc != null) {
            onDownloadZip(zipSrc, uri)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "Python da matematik tahlil va tasodifiy sonlar dasturi yoz va ishga tushir",
        "Chiroyli HTML/CSS veb-sayt yasab localhostda yoq",
        "Python Telegram bot yoz va tayyorla",
        "Workspace ichidagi barcha fayllarni tahlil qil"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Real-Time Operations HUD Header
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLiveHud = !showLiveHud },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PulsingStatusDot(
                            isActive = isBusy || liveOperations.isNotEmpty(),
                            activeColor = if (isBusy) AmberSpark else ElectricEmerald
                        )
                        Text(
                            text = if (isBusy) "Dasturchi AI Hozir Ishlamoqda..." else "Jonli Jarayonlar (Live HUD)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (liveOperations.isNotEmpty()) {
                            IconButton(
                                onClick = onClearOps,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = "Jarayonlarni tozalash",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showLiveHud) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = "HUD ochish/yopish",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (apiConfig.apiKey.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AmberSpark.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AmberSpark.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenApiConfig)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = AmberSpark, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Groq API kaliti bo'sh. Shu yerni bosib api.json ga kiriting.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberSpark
                            )
                        }
                    }
                }

                // Ready-to-download ZIP Banner
                AnimatedVisibility(
                    visible = latestGeneratedZipPath != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    latestGeneratedZipPath?.let { zipRel ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ElectricEmerald.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ZIP Arxiv Tayyor!", style = MaterialTheme.typography.labelLarge, color = ElectricEmerald)
                                    Text(zipRel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Button(
                                    onClick = {
                                        val suggested = zipRel.substringAfterLast('/')
                                        saveZipLauncher.launch(suggested)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricEmerald, contentColor = Color(0xFF002114)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("download_generated_zip_btn")
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Yuklash", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showLiveHud && liveOperations.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        liveOperations.take(3).forEach { op ->
                            LiveOperationCard(operation = op)
                        }
                    }
                }
            }
        }

        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Quick Prompts + Instant Auto Kotlin ZIP chip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            item {
                FilterChip(
                    selected = false,
                    enabled = !isBusy,
                    onClick = onAutoKotlinZip,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ElectricEmerald
                        )
                    },
                    label = { Text("Avto Google AI Studio ZIP") }
                )
            }
            items(quickPrompts) { prompt ->
                FilterChip(
                    selected = false,
                    onClick = { inputText = prompt },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ElectricCyan
                        )
                    },
                    label = { Text(prompt, maxLines = 1) }
                )
            }
        }

        // Prompt Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Dasturchi AI ga istalgan vazifa bering...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input"),
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = if (isBusy) AmberSpark.copy(alpha = 0.2f) else ElectricEmerald,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isBusy && inputText.isNotBlank()) {
                            val toSend = inputText
                            inputText = ""
                            onSendPrompt(toSend)
                        }
                        .testTag("ai_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = AmberSpark
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Yuborish",
                                tint = Color(0xFF002114)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatRole.USER
    val isError = message.role == ChatRole.SYSTEM

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isError -> CoralError.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }

    val borderColor = when {
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        isError -> CoralError.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = bubbleColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isUser) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = null,
                            tint = if (isError) CoralError else ElectricEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = when (message.role) {
                            ChatRole.USER -> "Siz"
                            ChatRole.ASSISTANT -> "Dasturchi AI"
                            ChatRole.SYSTEM -> "Tizim xabari"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (message.role) {
                            ChatRole.USER -> ElectricCyan
                            ChatRole.ASSISTANT -> ElectricEmerald
                            ChatRole.SYSTEM -> CoralError
                        }
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (message.attachedOps.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Bajarilgan amallar (${message.attachedOps.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    message.attachedOps.forEach { op ->
                        LiveOperationCard(operation = op)
                    }
                }
            }
        }
    }
}
