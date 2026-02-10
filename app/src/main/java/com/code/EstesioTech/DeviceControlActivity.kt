package com.code.EstesioTech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme

class DeviceControlActivity : ComponentActivity(), BleManager.ConnectionListener {

    private val messages = mutableStateListOf<ChatMessage>()
    private var keepConnectionAlive = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleUtils.setLocale(this)

        BleManager.setListener(this)

        val address = intent.getStringExtra("DEVICE_ADDRESS")
        if (address != null) {
            BleManager.connectToDevice(address, this)
            messages.add(ChatMessage("Conectando a $address...", ChatMessage.TYPE_SYSTEM))
        }

        val prefs = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode, fontScale = fontScale) {
                val colors = MaterialTheme.colorScheme
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Terminal ESP32", color = colors.onSurface) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, null, tint = colors.onSurface) }
                            },
                            actions = {
                                IconButton(onClick = {
                                    keepConnectionAlive = true
                                    val intent = Intent(this@DeviceControlActivity, HomeActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    startActivity(intent)
                                    finish()
                                }) { Icon(Icons.Default.Home, null, tint = colors.onSurface) }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
                        )
                    },
                    containerColor = colors.background
                ) { padding ->
                    ChatScreen(messages, Modifier.padding(padding))
                }
            }
        }
    }

    override fun onConnected() {
        runOnUiThread { messages.add(ChatMessage("✅ CONECTADO!", ChatMessage.TYPE_SYSTEM)) }
    }

    override fun onDisconnected() {
        runOnUiThread { messages.add(ChatMessage("❌ DESCONECTADO", ChatMessage.TYPE_SYSTEM)) }
    }

    override fun onDataReceived(data: String) {
        runOnUiThread { messages.add(ChatMessage(data, ChatMessage.TYPE_RECEIVED)) }
    }

    override fun onError(message: String) {
        runOnUiThread { messages.add(ChatMessage("ERRO: $message", ChatMessage.TYPE_SYSTEM)) }
    }

    override fun onDestroy() {
        super.onDestroy()
        BleManager.setListener(null)
        if (!keepConnectionAlive) BleManager.disconnect()
    }
}

@Composable
fun ChatScreen(messages: List<ChatMessage>, modifier: Modifier) {
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                val align = when (msg.type) {
                    ChatMessage.TYPE_SYSTEM -> Alignment.CenterHorizontally
                    ChatMessage.TYPE_SENT -> Alignment.End
                    else -> Alignment.Start
                }

                val msgColor = when (msg.type) {
                    ChatMessage.TYPE_SYSTEM -> colors.onSurface.copy(alpha=0.5f)
                    ChatMessage.TYPE_SENT -> colors.primary
                    else -> colors.secondary
                }

                val isSystem = msg.type == ChatMessage.TYPE_SYSTEM

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                    Surface(
                        color = if(isSystem) Color.Transparent else msgColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = if(isSystem) null else androidx.compose.foundation.BorderStroke(1.dp, msgColor)
                    ) {
                        Text(
                            text = msg.message,
                            color = if(isSystem) colors.onSurface.copy(alpha=0.7f) else colors.onSurface,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium, // Usa a fonte dinâmica
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    EstesioTechTheme {
        ChatScreen(
            messages = listOf(
                ChatMessage("Conectando...", ChatMessage.TYPE_SYSTEM),
                ChatMessage("Olá ESP32!", ChatMessage.TYPE_SENT),
                ChatMessage("Pressão: 3", ChatMessage.TYPE_RECEIVED)
            ),
            modifier = Modifier
        )
    }
}