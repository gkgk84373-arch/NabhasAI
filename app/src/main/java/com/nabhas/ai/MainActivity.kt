package com.nabhas.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nabhas.ai.api.ChatMessage
import com.nabhas.ai.ui.theme.NabhasAITheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NabhasAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember {
        mutableStateOf<AppUpdater.AvailableUpdate?>(null)
    }
    var isCheckingForUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val appUpdater = remember(context) { AppUpdater(context) }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startContinuousListening()
        } else {
            viewModel.startContinuousListening()
        }
    }

    val isLoading = viewModel.isLoading.value
    val messages = viewModel.messages

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
        ) {
            Header(
                onSettingsClick = { showSettings = true },
                onClearClick = { showClearConfirmation = true },
                onVoiceClick = {
                    if (viewModel.isListening.value) {
                        viewModel.stopContinuousListening()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.startContinuousListening()
                        } else {
                            microphonePermissionLauncher.launch(
                                Manifest.permission.RECORD_AUDIO
                            )
                        }
                    }
                },
                isListening = viewModel.isListening.value
            )

            AgentsAndSkillsSection(viewModel = viewModel)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyChatMessage()
                    }
                }

                itemsIndexed(messages) { _, message ->
                    ChatBubble(
                        message = message,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(message.content))
                        }
                    )
                }
            }

            viewModel.errorMessage.value?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = "Thinking…",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Nabhas AI…") },
                    maxLines = 5
                )

                Button(
                    onClick = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    enabled = !isLoading && inputText.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                ) {
                    Text("Send")
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState
        ) {
            SettingsSheet(
                viewModel = viewModel,
                updateStatus = updateStatus,
                hasAvailableUpdate = availableUpdate != null,
                isCheckingForUpdate = isCheckingForUpdate,
                isDownloadingUpdate = isDownloadingUpdate,
                onCheckForUpdate = {
                    if (!isCheckingForUpdate && !isDownloadingUpdate) {
                        isCheckingForUpdate = true
                        updateStatus = "Checking GitHub for updates…"

                        appUpdater.checkForUpdate { result ->
                            isCheckingForUpdate = false
                            when (result) {
                                is AppUpdater.UpdateCheckResult.NoUpdate -> {
                                    availableUpdate = null
                                    updateStatus = "You're up to date."
                                }

                                is AppUpdater.UpdateCheckResult.UpdateAvailable -> {
                                    availableUpdate = result.update
                                    updateStatus = "A new update is available."
                                }

                                is AppUpdater.UpdateCheckResult.Failure -> {
                                    availableUpdate = null
                                    updateStatus = "Update check failed: ${
                                        result.cause.message ?: "Unknown error"
                                    }"
                                }
                            }
                        }
                    }
                },
                onDownloadAndInstall = {
                    val update = availableUpdate
                    if (update != null && !isDownloadingUpdate) {
                        isDownloadingUpdate = true
                        updateStatus = "Downloading the update…"

                        appUpdater.downloadAndInstall(update) { result ->
                            isDownloadingUpdate = false
                            when (result) {
                                is AppUpdater.InstallResult.InstallerOpened -> {
                                    updateStatus = "Installer opened. Follow the prompts to install the update."
                                }

                                is AppUpdater.InstallResult.InstallPermissionRequired -> {
                                    updateStatus =
                                        "Allow Nabhas AI to install unknown apps in Settings, then tap Download & Install again."
                                }

                                is AppUpdater.InstallResult.Failure -> {
                                    updateStatus = "Update installation failed: ${
                                        result.cause.message ?: "Unknown error"
                                    }"
                                }
                            }
                        }
                    }
                },
                onSaved = {
                    showSettings = false
                    coroutineScope.launch { sheetState.hide() }
                }
            )
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear conversation?") },
            text = {
                Text("This will remove all messages from the current conversation.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Clear chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AgentsAndSkillsSection(viewModel: ChatViewModel) {
    var skillMenuExpanded by remember { mutableStateOf(false) }
    val selectedSkill = viewModel.selectedSkill.value

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Agents & Skills",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Agent mode",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Use goal-oriented responses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = viewModel.agentMode.value,
                    onCheckedChange = viewModel::setAgentMode
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Skill",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Active skill: $selectedSkill",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    OutlinedButton(onClick = { skillMenuExpanded = true }) {
                        Text(selectedSkill)
                    }

                    DropdownMenu(
                        expanded = skillMenuExpanded,
                        onDismissRequest = { skillMenuExpanded = false }
                    ) {
                        viewModel.skillOptions.forEach { skill ->
                            DropdownMenuItem(
                                text = { Text(skill) },
                                onClick = {
                                    viewModel.setSelectedSkill(skill)
                                    skillMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (selectedSkill.equals("Auto", ignoreCase = true)) {
                Text(
                    text = "Auto chooses a skill for each message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Header(
    onSettingsClick: () -> Unit,
    onClearClick: () -> Unit,
    onVoiceClick: () -> Unit,
    isListening: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Nabhas AI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your AI assistant",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(onClick = onVoiceClick) {
            Text(if (isListening) "Stop voice" else "Voice")
        }

        TextButton(onClick = onClearClick) {
            Text("Clear")
        }

        TextButton(onClick = onSettingsClick) {
            Text("Settings")
        }
    }
}

@Composable
private fun EmptyChatMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hey, what can I help with?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Send a message or use voice input to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"
    val horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment
    ) {
        Surface(
            color = bubbleColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "Nabhas AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = message.content,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Copy")
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    viewModel: ChatViewModel,
    updateStatus: String?,
    hasAvailableUpdate: Boolean,
    isCheckingForUpdate: Boolean,
    isDownloadingUpdate: Boolean,
    onCheckForUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onSaved: () -> Unit
) {
    val currentSettings = viewModel.apiSettings.value
    var baseUrl by remember(currentSettings.baseUrl) {
        mutableStateOf(currentSettings.baseUrl)
    }
    var apiKey by remember(currentSettings.apiKey) {
        mutableStateOf(currentSettings.apiKey)
    }
    var primaryModel by remember(currentSettings.primaryModel) {
        mutableStateOf(currentSettings.primaryModel)
    }
    var useFallbackChain by remember(currentSettings.useFallbackChain) {
        mutableStateOf(currentSettings.useFallbackChain)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "API settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") },
            placeholder = { Text("https://coarena.ai/api/v1") },
            singleLine = true
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            placeholder = { Text("Enter your API key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        OutlinedTextField(
            value = primaryModel,
            onValueChange = { primaryModel = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Primary model") },
            placeholder = { Text("gpt-6-luna") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fallback chain", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Try other models if the primary model fails",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = useFallbackChain,
                onCheckedChange = { useFallbackChain = it }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Speak responses", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Read assistant replies aloud",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.speakResponses.value,
                onCheckedChange = viewModel::setSpeakResponses
            )
        }

        Button(
            onClick = {
                viewModel.saveApiSettings(
                    ApiSettings(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        primaryModel = primaryModel,
                        useFallbackChain = useFallbackChain
                    )
                )
                onSaved()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save settings")
        }

        Text(
            text = "App Update",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "Check GitHub for the latest Nabhas AI APK.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = onCheckForUpdate,
            enabled = !isCheckingForUpdate && !isDownloadingUpdate,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isCheckingForUpdate) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text("Checking…", modifier = Modifier.padding(start = 8.dp))
            } else {
                Text("Check for Update")
            }
        }

        if (hasAvailableUpdate) {
            Button(
                onClick = onDownloadAndInstall,
                enabled = !isCheckingForUpdate && !isDownloadingUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDownloadingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Downloading…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Download & Install")
                }
            }
        }

        updateStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
