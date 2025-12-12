package com.code.EstesioTech

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme

// NOTA: A classe ChatMessage já existe em outro arquivo (ChatMessage.kt),
// então não precisamos redefiní-la aqui. Usaremos a original.

class DeviceControlActivity : ComponentActivity(), BleManager.ConnectionListener {

    private val messages = mutableStateListOf<ChatMessage>()
    private var keepConnectionAlive = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BleManager.setListener(this)

        val address = intent.getStringExtra("DEVICE_ADDRESS")
        if (address != null) {
            BleManager.connectToDevice(address, this)
            // Usa o tipo SYSTEM (2)
            messages.add(ChatMessage("Conectando a $address...", ChatMessage.TYPE_SYSTEM))
        }

        setContent {
            EstesioTechTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Terminal ESP32", color = Color.White) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                            },
                            actions = {
                                IconButton(onClick = {
                                    keepConnectionAlive = true
                                    val intent = Intent(this@DeviceControlActivity, HomeActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    startActivity(intent)
                                    finish()
                                }) { Icon(Icons.Default.Home, null, tint = Color.White) }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF101820))
                        )
                    }
                ) { padding ->
                    ChatScreen(messages, Modifier.padding(padding))
                }
            }
        }
    }

    // Callbacks do Bluetooth
    override fun onConnected() {
        runOnUiThread {
            messages.add(ChatMessage("✅ CONECTADO!", ChatMessage.TYPE_SYSTEM))
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            messages.add(ChatMessage("❌ DESCONECTADO", ChatMessage.TYPE_SYSTEM))
        }
    }

    override fun onDataReceived(data: String) {
        runOnUiThread {
            // Usa o tipo RECEIVED (1)
            messages.add(ChatMessage(data, ChatMessage.TYPE_RECEIVED))
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            messages.add(ChatMessage("ERRO: $message", ChatMessage.TYPE_SYSTEM))
        }
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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                // Lógica corrigida para usar Int (TYPE_...) em vez de Boolean
                val align = when (msg.type) {
                    ChatMessage.TYPE_SYSTEM -> Alignment.CenterHorizontally
                    ChatMessage.TYPE_SENT -> Alignment.End
                    else -> Alignment.Start // TYPE_RECEIVED
                }

                val color = when (msg.type) {
                    ChatMessage.TYPE_SYSTEM -> Color.Gray
                    ChatMessage.TYPE_SENT -> Color(0xFF2196F3) // Azul
                    else -> Color(0xFF00ACC1) // Ciano/Verde
                }

                val isSystem = msg.type == ChatMessage.TYPE_SYSTEM

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
                    Surface(
                        color = if(isSystem) Color.Transparent else color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = if(isSystem) null else androidx.compose.foundation.BorderStroke(1.dp, color)
                    ) {
                        Text(
                            text = msg.message, // Aqui usamos .message (que existe na classe original)
                            color = if(isSystem) Color.Gray else Color.White,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
@androidx.compose.ui.tooling.preview.Preview
@Composable
fun ChatScreenPreview() {
    com.code.EstesioTech.ui.theme.EstesioTechTheme {
        ChatScreen(
            messages = listOf(
                ChatMessage("Conectando...", ChatMessage.TYPE_SYSTEM),
                ChatMessage("Olá ESP32!", ChatMessage.TYPE_SENT),
                ChatMessage("Pressão: 3 (Violeta)", ChatMessage.TYPE_RECEIVED),
                ChatMessage("Enviado", ChatMessage.TYPE_RECEIVED)
            ),
            modifier = androidx.compose.ui.Modifier
        )
    }
}
