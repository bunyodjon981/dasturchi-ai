package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApiConfig
import com.example.ui.theme.AmberSpark
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun ApiConfigScreen(
    apiConfig: ApiConfig,
    rawJson: String,
    onSaveConfig: (ApiConfig) -> Unit,
    onSaveRawJson: (String) -> Boolean,
    onExportApiJson: (Uri) -> Unit,
    onImportApiJson: (Uri) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var isRawJsonMode by remember { mutableStateOf(false) }
    var baseUrl by remember(apiConfig) { mutableStateOf(apiConfig.baseUrl) }
    var apiKey by remember(apiConfig) { mutableStateOf(apiConfig.apiKey) }
    var temperature by remember(apiConfig) { mutableFloatStateOf(apiConfig.temperature.toFloat()) }
    var showKey by remember { mutableStateOf(false) }
    var jsonDraft by remember(rawJson) { mutableStateOf(rawJson) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportApiJson) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportApiJson) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Orqaga")
                    }
                    Column {
                        Text("api.json Konfiguratsiyasi", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Model avtomatik tanlanadi — faqat kalit kifoya",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilterChip(
                    selected = isRawJsonMode,
                    onClick = { isRawJsonMode = !isRawJsonMode },
                    leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(if (isRawJsonMode) "Forma" else "JSON") },
                    modifier = Modifier.testTag("toggle_raw_json_btn")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("api.json Yuklash")
            }
            OutlinedButton(
                onClick = { exportLauncher.launch("api.json") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Telefonga Saqlash")
            }
        }

        if (isRawJsonMode) {
            OutlinedTextField(
                value = jsonDraft,
                onValueChange = { jsonDraft = it },
                label = { Text("api.json fayl matni") },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("raw_api_json_editor"),
                shape = RoundedCornerShape(16.dp)
            )

            Button(
                onClick = { onSaveRawJson(jsonDraft) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricEmerald, contentColor = Color(0xFF002114)),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_raw_api_json_btn")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("api.json ni Saqlash", fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ElectricCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ElectricCyan)
                        Text(
                            text = "Avto-Model rejimi yoqilgan: Dasturchi AI Groq hisobingizdagi ochiq va ishlaydigan modelni o'zi avtomatik topib ulanadi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Groq API Key (api_key)") },
                    placeholder = { Text("gsk_...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Kalitni ko'rsatish"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input")
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Groq Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ijodkorlik (Temperature)", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "%.2f".format(temperature),
                                fontFamily = JetBrainsMonoFamily,
                                color = AmberSpark
                            )
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0f..1.5f
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSaveConfig(
                        apiConfig.copy(
                            baseUrl = baseUrl,
                            model = "auto",
                            apiKey = apiKey,
                            temperature = temperature.toDouble()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricEmerald, contentColor = Color(0xFF002114)),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_api_config_btn")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("api.json ga Saqlash", fontWeight = FontWeight.Bold)
            }
        }
    }
}
