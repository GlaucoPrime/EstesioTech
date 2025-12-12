package com.code.EstesioTech

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.code.EstesioTech.ui.theme.EstesioTechTheme

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EstesioTechTheme {
                val context = LocalContext.current
                var isConnected by remember { mutableStateOf(BleManager.isConnected()) }
                var showTestSelectionDialog by remember { mutableStateOf(false) }
                var userName by remember { mutableStateOf("Doutor(a)") }

                // Busca o nome do usuário ao iniciar
                LaunchedEffect(Unit) {
                    EstesioCloud.getUserName { name -> userName = name }
                }

                // Monitora conexão Bluetooth
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isConnected = BleManager.isConnected()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (showTestSelectionDialog) {
                    TestTypeDialog(
                        onDismiss = { showTestSelectionDialog = false },
                        onSelect = { type ->
                            showTestSelectionDialog = false
                            if (type == "hansen") {
                                if (isConnected) startActivity(Intent(context, SelectionActivity::class.java))
                                else Toast.makeText(context, "⚠️ Conecte o hardware primeiro!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Este módulo será liberado na versão Beta.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                HomeScreen(
                    userName = userName,
                    isConnected = isConnected,
                    onConnectClick = {
                        if (isConnected) Toast.makeText(context, "Dispositivo já conectado.", Toast.LENGTH_SHORT).show()
                        else startActivity(Intent(context, MainActivity::class.java))
                    },
                    onStartTestClick = { showTestSelectionDialog = true },
                    onHistoryClick = { startActivity(Intent(context, HistoryActivity::class.java)) },
                    onLogoutClick = {
                        // Limpa "Lembrar de Mim"
                        getSharedPreferences("EstesioPrefs", MODE_PRIVATE).edit().clear().apply()
                        EstesioCloud.logout()
                        BleManager.disconnect() // Agora sim desconecta tudo
                        startActivity(Intent(context, LoginActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun TestTypeDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecione o Protocolo", color = Color.White) },
        text = {
            Column {
                Text("Escolha o tipo de avaliação:", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                TestOption("Hanseníase (Estesiometria)", true) { onSelect("hansen") }
                TestOption("Neuropatia Periférica", false) { onSelect("neuro") }
                TestOption("Neuropatia Diabética", false) { onSelect("diabete") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFCF6679)) }
        },
        containerColor = Color(0xFF1A2634),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun TestOption(name: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        // Visual diferente se estiver desabilitado
        colors = ButtonDefaults.buttonColors(
            containerColor = if(enabled) Color(0xFF00ACC1).copy(alpha=0.2f) else Color.Black.copy(alpha=0.2f),
            contentColor = if(enabled) Color(0xFF00ACC1) else Color.Gray
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, if(enabled) Color(0xFF00ACC1) else Color.Transparent, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = if(enabled) FontWeight.Bold else FontWeight.Normal)
            if(!enabled) Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun HomeScreen(
    userName: String,
    isConnected: Boolean,
    onConnectClick: () -> Unit,
    onStartTestClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF101820), Color(0xFF000000))))
            .padding(24.dp)
    ) {
        // CABEÇALHO COM NOME
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Olá, $userName", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Bem-vindo ao Lab", color = Color.Gray, fontSize = 14.sp)
            }
            IconButton(onClick = onLogoutClick) {
                Icon(Icons.Default.ExitToApp, "Sair", tint = Color(0xFFCF6679))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        StatusCard(isConnected)
        Spacer(modifier = Modifier.height(24.dp))

        Text("AÇÕES", color = Color(0xFF00ACC1), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            DashboardButton("Parear\nHardware", if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, if (isConnected) Color(0xFF4CAF50) else Color(0xFF2196F3), Modifier.weight(1f), onConnectClick)
            Spacer(modifier = Modifier.width(16.dp))
            DashboardButton("Novo\nTeste", Icons.Default.PlayArrow, if (isConnected) Color(0xFF00ACC1) else Color.Gray, Modifier.weight(1f), onStartTestClick)
        }

        Spacer(modifier = Modifier.height(16.dp))
        DashboardButton("Histórico de Pacientes", Icons.Default.History, Color(0xFFFF9800), Modifier.fillMaxWidth(), onHistoryClick, true)
    }
}

@Composable
fun StatusCard(isConnected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if(isConnected) Color(0xFF4CAF50).copy(alpha=0.3f) else Color(0xFFF44336).copy(alpha=0.3f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Status da Conexão", color = Color.Gray, fontSize = 12.sp)
                Text(text = if (isConnected) "Estesiômetro Online" else "Desconectado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun DashboardButton(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit, isHorizontal: Boolean = false) {
    Card(
        modifier = modifier.height(if (isHorizontal) 80.dp else 140.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        if (isHorizontal) {
            Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, null, tint = color, modifier = Modifier.size(40.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// --- PREVIEW DA HOME ---
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    com.code.EstesioTech.ui.theme.EstesioTechTheme {
        HomeScreen(
            userName = "Dr. Glauco",
            isConnected = true, // Mude para false para ver como fica desconectado
            onConnectClick = {},
            onStartTestClick = {},
            onHistoryClick = {},
            onLogoutClick = {}
        )
    }
}
