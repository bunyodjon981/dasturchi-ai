package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.engine.LocalServerStatus
import com.example.data.model.ApiConfig
import com.example.data.model.BotRuntimeState
import com.example.data.model.LiveOperation
import com.example.ui.components.LiveOperationCard
import com.example.ui.components.PulsingStatusDot
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.VioletAccent

private data class AiNewsItem(
    val tag: String,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val accent: Color
)

@Composable
fun HomeScreen(
    apiConfig: ApiConfig,
    fileCount: Int,
    serverStatus: LocalServerStatus,
    botState: BotRuntimeState,
    liveOperations: List<LiveOperation>,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToAgent: () -> Unit,
    onAutoCreateKotlinZip: () -> Unit,
    onOpenApiConfig: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "hero_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    val newsFeed = remember {
        listOf(
            AiNewsItem(
                tag = "YANGI • GROQ AI",
                title = "Men — Dasturchi AI Studiyangizman",
                body = "Istalgan dasturlash tilida kod yozaman, fayl va papkalarni yarataman, hammasini o'zim o'ylab bajaraman va ekranda jonli ko'rsataman.",
                icon = Icons.Rounded.AutoAwesome,
                accent = ElectricEmerald
            ),
            AiNewsItem(
                tag = "AVTO KOTLIN + ZIP",
                title = "Google AI Studio uchun Avtomatik ZIP",
                body = "Endi ortiqcha savollarsiz bir bosishda Kotlin + Jetpack Compose ilovasini o'zim o'ylab yasayman va tayyor .zip arxivini beraman.",
                icon = Icons.Rounded.FolderZip,
                accent = ElectricCyan
            ),
            AiNewsItem(
                tag = "UNIVERSAL PYTHON",
                title = "Har Qanday Python Dasturini Ishga Tushiring",
                body = "Faqat Telegram bot emas — hisob-kitob, algoritm, API so'rovlar va avtomat skriptlarni telefoningizning o'zida yuriting.",
                icon = Icons.Rounded.Terminal,
                accent = AmberSpark
            ),
            AiNewsItem(
                tag = "SWB & ZIP ARXIV",
                title = "Sketchware (.swb) va ZIP Tahlilchisi",
                body = "Arxivlarni ochib ichidagi fayllarni ko'ring, o'zgartiring va loyihalaringizni to'g'ridan-to'g'ri telefonga ZIP qilib yuklab oling.",
                icon = Icons.Rounded.Archive,
                accent = VioletAccent
            ),
            AiNewsItem(
                tag = "LOCALHOST & API.JSON",
                title = "127.0.0.1 Veb-Server va Ochib Tahrirlash",
                body = "Saytlaringizni mahalliy serverda jonli ko'ring. Groq API kalitingizni esa istalgan payt api.json faylidan o'zgartira olasiz.",
                icon = Icons.Rounded.Dns,
                accent = ElectricCyan
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Clean Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = glowPulse
                                scaleY = glowPulse
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = "Dasturchi AI",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Dasturchi AI",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PulsingStatusDot(
                                isActive = apiConfig.apiKey.isNotBlank(),
                                activeColor = ElectricEmerald,
                                inactiveColor = AmberSpark
                            )
                            Text(
                                text = if (apiConfig.apiKey.isNotBlank()) {
                                    "Groq • Avto-Model Faol"
                                } else {
                                    "api.json kalit kutilmoqda"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("toggle_theme_btn")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Mavzuni almashtirish",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onOpenApiConfig,
                        modifier = Modifier.testTag("home_api_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "api.json sozlamalari",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Animated Identity Hero
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.4f)),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.height(225.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_studio),
                            contentDescription = "Dasturchi AI Studiyasi",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0x77090D16), Color(0xF0090D16))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = ElectricEmerald.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(ElectricEmerald)
                                        )
                                        Text(
                                            text = "DASTURCHI AI • LIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricEmerald
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xAA111827)
                                ) {
                                    Text(
                                        text = "$fileCount fayl" + if (serverStatus.isRunning) " • Server ON" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Salom! Men Dasturchi AI",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Buyruq bering — o'zim o'ylab kod yozaman, loyiha yasayman, Python ishlataman va ZIP tayyorlayman.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 2
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = onNavigateToAgent,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricEmerald,
                                            contentColor = Color(0xFF002114)
                                        ),
                                        modifier = Modifier.testTag("start_ai_agent_btn")
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("AI ga Yozish", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = onAutoCreateKotlinZip,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.75f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                                        modifier = Modifier.testTag("auto_kotlin_zip_btn")
                                    ) {
                                        Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Avto AI Studio ZIP")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Key Warning / api.json Quick Setup Banner if empty
        if (apiConfig.apiKey.isBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AmberSpark.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, AmberSpark.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenApiConfig)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = AmberSpark)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("api.json tayyor — Groq kalitini kiriting", style = MaterialTheme.typography.titleMedium, color = AmberSpark)
                            Text(
                                "Shu yerni bosib api.json ga Groq API kalitingizni qo'ying yoki Secrets panelini ishlating.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // News & Updates about Dasturchi AI
        item {
            SectionHeader(
                title = "Yangiliklar va Imkoniyatlar",
                subtitle = "Dasturchi AI nimalarga qodir?",
                trailing = {
                    Icon(Icons.Rounded.NewReleases, contentDescription = null, tint = ElectricEmerald)
                }
            )
        }

        itemsIndexed(newsFeed) { index, item ->
            AnimatedNewsCard(item = item, visible = visible, index = index)
        }

        // Live Real-time Operations if any active
        if (liveOperations.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Oxirgi Jonli Jarayonlar",
                    subtitle = "Real vaqtda bajarilgan amallar"
                )
            }
            itemsIndexed(liveOperations.take(3)) { _, op ->
                LiveOperationCard(operation = op)
            }
        }

        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
private fun AnimatedNewsCard(
    item: AiNewsItem,
    visible: Boolean,
    index: Int
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.93f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "news_scale_$index"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, delayMillis = index * 70),
        label = "news_alpha_$index"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, item.accent.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(item.accent.copy(alpha = 0.09f), Color.Transparent)
                    )
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = item.accent, modifier = Modifier.size(22.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = item.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = item.accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
