package com.code.EstesioTech.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ChatMessage
import com.code.EstesioTech.data.bluetooth.BleManager
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Terminal BLE — exibe em tempo real todos os dados enviados pelo ESP32.
 *
 * Funciona como um chat assimetrico:
 *  - Mensagens do ESP32 (TYPE_RECEIVED) ficam na esquerda
 *  - Mensagens de sistema (conexao, erro) ficam centralizadas e opacas
 *
 * keepConnectionAlive: flag que impede o disconnect() ao navegar para Home.
 * Sem ela, clicar no botao Home desconectaria o ESP32.
 */
class DeviceControlActivity : ComponentActivity(), BleManager.ConnectionListener {

    private val messages         = mutableStateListOf<ChatMessage>()
    private var keepConnectionAlive = false

    override fun attachBaseContext(newBase: android.content.Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        BleManager.setListener(this)

        val address = intent.getStringExtra("DEVICE_ADDRESS")
        if (address != null) {
            BleManager.connectToDevice(address, this)
            messages.add(ChatMessage("Conectando a $address...", ChatMessage.TYPE_SYSTEM))
        }

        val prefs       = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val isDark      = prefs.getBoolean("dark_theme", true)
        val colorMode   = prefs.getInt("color_blind_mode", 0)
        val fontScale   = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDark, colorBlindMode = colorMode, fontScale = fontScale) {
                val colors = MaterialTheme.colorScheme
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("Terminal ESP32", fontWeight = FontWeight.Bold, color = colors.onSurface)
                                    Text(
                                        text     = if (BleManager.isConnected()) "Conectado" else "Aguardando...",
                                        color    = if (BleManager.isConnected()) Color(0xFF4CAF50) else colors.error,
                                        fontSize = 12.sp
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, null, tint = colors.onSurface)
                                }
                            },
                            actions = {
                                // Botao Home: navega sem desconectar o BLE
                                IconButton(onClick = {
                                    keepConnectionAlive = true
                                    val intent = Intent(this@DeviceControlActivity, HomeActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(intent)
                                    finish()
                                }) {
                                    Icon(Icons.Default.Home, null, tint = colors.onSurface)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
                        )
                    },
                    containerColor = colors.background
                ) { padding ->
                    ChatScreen(messages = messages, modifier = Modifier.padding(padding))
                }
            }
        }
    }

    // Callbacks BLE — sempre na main thread (BleManager garante isso)
    override fun onConnected()              = messages.add(ChatMessage("CONECTADO", ChatMessage.TYPE_SYSTEM)).run { Unit }
    override fun onDisconnected()           = messages.add(ChatMessage("DESCONECTADO", ChatMessage.TYPE_SYSTEM)).run { Unit }
    override fun onDataReceived(data: String) = messages.add(ChatMessage(data.trim(), ChatMessage.TYPE_RECEIVED)).run { Unit }
    override fun onError(message: String)   = messages.add(ChatMessage("ERRO: $message", ChatMessage.TYPE_SYSTEM)).run { Unit }

    override fun onDestroy() {
        super.onDestroy()
        BleManager.setListener(null)
        if (!keepConnectionAlive) BleManager.disconnect()
    }
}

@Composable
fun ChatScreen(messages: List<ChatMessage>, modifier: Modifier) {
    val listState = rememberLazyListState()
    val colors    = MaterialTheme.colorScheme
    val timeFmt   = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Auto-scroll para a ultima mensagem
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            state               = listState,
            modifier            = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.timestamp }) { msg ->
                ChatBubble(msg = msg, colors = colors, timeFmt = timeFmt)
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: ChatMessage,
    colors: ColorScheme,
    timeFmt: SimpleDateFormat
) {
    val isSystem   = msg.type == ChatMessage.TYPE_SYSTEM
    val isReceived = msg.type == ChatMessage.TYPE_RECEIVED

    // Prefixo visual para mensagens de sistema (icones de status)
    val (prefix, bubbleColor) = when {
        isSystem && msg.message.contains("CONECT") && !msg.message.contains("DES") ->
            "" to Color(0xFF1B5E20).copy(alpha = 0.15f)
        isSystem && msg.message.contains("DESCONECT") ->
            "" to colors.error.copy(alpha = 0.1f)
        isSystem && msg.message.contains("ERRO") ->
            "" to colors.error.copy(alpha = 0.15f)
        isSystem ->
            "" to colors.surface
        isReceived ->
            "" to colors.secondary.copy(alpha = 0.15f)
        else ->
            "" to colors.primary.copy(alpha = 0.15f)
    }

    val alignment = when {
        isSystem   -> Alignment.CenterHorizontally
        isReceived -> Alignment.Start
        else       -> Alignment.End
    }

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape  = RoundedCornerShape(
                topStart     = if (isReceived) 4.dp else 16.dp,
                topEnd       = if (isSystem || isReceived) 16.dp else 4.dp,
                bottomStart  = 16.dp,
                bottomEnd    = 16.dp
            ),
            color  = bubbleColor,
            border = if (isSystem) null
            else BorderStroke(1.dp, if (isReceived) colors.secondary.copy(0.3f) else colors.primary.copy(0.3f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text       = "$prefix${msg.message}",
                    color      = if (isSystem) colors.onSurface.copy(0.7f) else colors.onSurface,
                    fontSize   = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                // Timestamp
                Text(
                    text      = timeFmt.format(Date(msg.timestamp)),
                    color     = colors.onSurface.copy(0.4f),
                    fontSize  = 10.sp,
                    modifier  = Modifier.align(Alignment.End).padding(top = 2.dp)
                )
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
                ChatMessage("Pressao: 3", ChatMessage.TYPE_RECEIVED),
                ChatMessage("CONECTADO", ChatMessage.TYPE_SYSTEM)
            ),
            modifier = Modifier
        )
    }
}