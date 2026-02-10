package com.code.EstesioTech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

class HomeActivity : ComponentActivity() {

    // MAGIA NEGRA: Isso força o idioma a ser aplicado antes de qualquer coisa na tela
    override fun attachBaseContext(newBase: Context) {
        // Lê a preferência
        val prefs = newBase.getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val langCode = prefs.getString("language", "pt") ?: "pt"

        // Cria a configuração de locale
        val locale = Locale(langCode)
        val config = newBase.resources.configuration
        config.setLocale(locale)

        // Aplica e retorna o contexto modificado
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode, fontScale = fontScale) {
                HomeScreen(
                    onNavigateToSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onLogout = {
                        EstesioCloud.logout()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    },
                    onNavigateToSelection = { name, cpf ->
                        val intent = Intent(this, SelectionActivity::class.java).apply {
                            putExtra("PATIENT_NAME", name)
                            putExtra("PATIENT_CPF", cpf)
                        }
                        startActivity(intent)
                    },
                    onNavigateToHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onNavigateToDevice = { startActivity(Intent(this, MainActivity::class.java)) }
                )
            }
        }
    }

    // Recria a tela ao voltar das configurações se algo mudou
    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val currentLang = resources.configuration.locales[0].language
        val savedLang = prefs.getString("language", "pt")

        // Se o idioma salvo for diferente do atual da tela, recria
        if (currentLang != savedLang) {
            recreate()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSelection: (String, String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDevice: () -> Unit
) {
    var doctorName by remember { mutableStateOf("Doutor(a)") }
    var showCpfDialog by remember { mutableStateOf(false) }
    var showCreatePatientDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var tempCpf by remember { mutableStateOf("") }

    // Estado do Bluetooth
    var isDeviceConnected by remember { mutableStateOf(false) }
    // Estado dos Pacientes Recentes
    var recentPatients by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        EstesioCloud.getUserName { name -> if (name.isNotEmpty()) doctorName = name }
        // Busca recentes
        EstesioCloud.getRecentPatients(limit = 3, onSuccess = { recentPatients = it }, onError = { /* Silencioso na home */ })

        // Monitora Bluetooth
        while(true) {
            isDeviceConnected = try { BleManager.isConnected() } catch(e: Exception) { false }
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                            Text(text = doctorName.take(1).uppercase(), color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            // Usa Resources para garantir tradução
                            Text(stringResource(R.string.welcome) + " $doctorName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                            Text("EstesioTech", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.6f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Config", tint = colors.onSurface) }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Sair", tint = colors.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // CARD STATUS BLUETOOTH
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if(isDeviceConnected) colors.primaryContainer else colors.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Status", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if(isDeviceConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled, null, tint = if(isDeviceConnected) Color(0xFF2E7D32) else colors.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if(isDeviceConnected) stringResource(R.string.device_status_connected) else stringResource(R.string.device_status_disconnected), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        }
                    }
                    Button(
                        onClick = { if(isDeviceConnected) { BleManager.disconnect(); isDeviceConnected = false } else { onNavigateToDevice() } },
                        colors = ButtonDefaults.buttonColors(containerColor = if(isDeviceConnected) colors.error else colors.primary)
                    ) {
                        Text(if(isDeviceConnected) "DESCONECTAR" else stringResource(R.string.action_connect), fontSize = 12.sp)
                    }
                }
            }

            Text(stringResource(R.string.quick_actions), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onBackground)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeActionButton(title = stringResource(R.string.new_exam), icon = Icons.Default.Add, color = colors.primary, modifier = Modifier.weight(1f), onClick = { showCpfDialog = true })
                HomeActionButton(title = stringResource(R.string.history), icon = Icons.Default.History, color = colors.secondary, modifier = Modifier.weight(1f), onClick = onNavigateToHistory)
            }

            // PACIENTES RECENTES
            if (recentPatients.isNotEmpty()) {
                Text("Últimos Pacientes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentPatients.forEach { p ->
                        val pName = p["name"] as? String ?: ""
                        val pCpf = p["cpf"] as? String ?: ""
                        val pDate = p["lastExam"] as? java.util.Date ?: java.util.Date()

                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onNavigateToSelection(pName, pCpf) },
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.dp, colors.outline.copy(alpha=0.1f))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = colors.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(pName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                                    Text("CPF: $pCpf", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                Spacer(Modifier.weight(1f))
                                Text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(pDate), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG CPF
    if (showCpfDialog) {
        Dialog(onDismissRequest = { showCpfDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Buscar Paciente", style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempCpf,
                        onValueChange = { tempCpf = it },
                        label = { Text(stringResource(R.string.cpf_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.onSurface, unfocusedTextColor = colors.onSurface)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        if (tempCpf.isNotEmpty()) {
                            EstesioCloud.checkPatient(tempCpf,
                                onFound = { name, _, _ -> showCpfDialog = false; onNavigateToSelection(name, tempCpf) },
                                onNotFound = { showCpfDialog = false; showCreatePatientDialog = true },
                                onError = { showCpfDialog = false; showOfflineDialog = true }
                            )
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = colors.primary), modifier = Modifier.fillMaxWidth()) { Text("Buscar / Criar") }
                }
            }
        }
    }

    if (showOfflineDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            title = { Text("Erro de Conexão", color = colors.error) },
            text = { Text("Não foi possível conectar. Deseja usar o modo offline?", color = colors.onSurface) },
            confirmButton = { Button(onClick = { showOfflineDialog = false; onNavigateToSelection("Paciente Offline", tempCpf) }) { Text("Sim, Offline") } },
            dismissButton = { TextButton(onClick = { showOfflineDialog = false }) { Text("Cancelar") } },
            containerColor = colors.surface
        )
    }

    if (showCreatePatientDialog) {
        NewPatientDialog(tempCpf, { showCreatePatientDialog = false }, { n, c, a ->
            EstesioCloud.createPatient(c, n, a, "",
                onSuccess = { showCreatePatientDialog = false; onNavigateToSelection(n, c) },
                onError = { /* Log erro */ }
            )
        })
    }
}

@Composable
fun HomeActionButton(title: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.height(120.dp).clickable { onClick() }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.1f), Color.Transparent))))
            Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPatientDialog(initialCpf: String, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf(initialCpf) }
    var age by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.new_patient_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name_hint)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = cpf, onValueChange = { cpf = it }, label = { Text(stringResource(R.string.cpf_hint)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Idade") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(24.dp))
                Button(onClick = { if(name.isNotEmpty()) onConfirm(name, cpf, age) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) { Text(stringResource(R.string.start)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    EstesioTechTheme(darkTheme = true) {
        HomeScreen({}, {}, { _, _ -> }, {}, {})
    }
}