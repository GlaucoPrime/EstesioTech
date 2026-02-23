package com.code.EstesioTech.ui.screens

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
import androidx.compose.ui.window.Dialog
import com.code.EstesioTech.HistoryActivity
import com.code.EstesioTech.R
import com.code.EstesioTech.data.bletooth.BleManager
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.CpfVisualTransformation
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : ComponentActivity() {
    private var lastLocale: String? = null
    private var lastTheme: Boolean = true
    private var lastColorMode: Int = 0
    private var lastFontScale: Float = 1.0f

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val langCode = prefs.getString("language", "pt") ?: "pt"
        val locale = Locale(langCode)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saveCurrentSettings()
        loadUI()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val currentLang = resources.configuration.locales[0].language
        val savedLang = prefs.getString("language", "pt")

        if (currentLang != savedLang || hasSettingsChanged()) {
            recreate()
        }
    }

    private fun saveCurrentSettings() {
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        lastLocale = prefs.getString("language", "pt")
        lastTheme = prefs.getBoolean("dark_theme", true)
        lastColorMode = prefs.getInt("color_blind_mode", 0)
        lastFontScale = prefs.getFloat("font_scale", 1.0f)
    }

    private fun hasSettingsChanged(): Boolean {
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        return lastLocale != prefs.getString("language", "pt") ||
                lastTheme != prefs.getBoolean("dark_theme", true) ||
                lastColorMode != prefs.getInt("color_blind_mode", 0) ||
                lastFontScale != prefs.getFloat("font_scale", 1.0f)
    }

    private fun loadUI() {
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

    var isDeviceConnected by remember { mutableStateOf(false) }
    var recentPatients by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        EstesioCloud.getUserName { name -> if (name.isNotEmpty()) doctorName = name }
        EstesioCloud.getRecentPatients(limit = 3, onSuccess = { recentPatients = it }, onError = { })

        while(true) {
            isDeviceConnected = try { BleManager.isConnected() } catch(e: Exception) { false }
            delay(1500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = doctorName.take(1).uppercase(), color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.welcome) + " $doctorName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("EstesioTech Pro", style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.7f))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Configurações", tint = colors.onSurface) }
                    IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, "Sair", tint = colors.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // CARD DE STATUS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDeviceConnected) Color(0xFF2E7D32) else colors.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Estesiômetro", style = MaterialTheme.typography.labelMedium, color = if(isDeviceConnected) Color.White.copy(alpha=0.8f) else colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if(isDeviceConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled, null, tint = if(isDeviceConnected) Color.White else colors.error, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(text = if(isDeviceConnected) stringResource(R.string.device_status_connected) else stringResource(
                                R.string.device_status_disconnected), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if(isDeviceConnected) Color.White else colors.onSurfaceVariant)
                        }
                    }

                    Button(
                        onClick = {
                            if(isDeviceConnected) { BleManager.disconnect(); isDeviceConnected = false; Toast.makeText(context, "Desconectado", Toast.LENGTH_SHORT).show() }
                            else { onNavigateToDevice() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if(isDeviceConnected) Color.White else colors.primary, contentColor = if(isDeviceConnected) Color(0xFF2E7D32) else colors.onPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = if(isDeviceConnected) "Desconectar" else stringResource(R.string.action_connect), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // AÇÕES RÁPIDAS
            Column {
                Text(stringResource(R.string.quick_actions), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.onBackground)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BigActionCard(title = stringResource(R.string.new_exam), icon = Icons.Default.Add, color = colors.primary, modifier = Modifier.weight(1f), onClick = { showCpfDialog = true })
                    BigActionCard(title = stringResource(R.string.history), icon = Icons.Default.History, color = colors.secondary, modifier = Modifier.weight(1f), onClick = onNavigateToHistory)
                }
            }

            // RECENTES
            if (recentPatients.isNotEmpty()) {
                Column {
                    Text("Últimos Atendimentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onBackground)
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentPatients.forEach { patient ->
                            val pName = patient["name"] as? String ?: "Paciente"
                            val pCpf = patient["cpf"] as? String ?: ""
                            val pDate = patient["lastExam"] as? Date ?: Date()
                            PatientItemCard(pName, pCpf, pDate, colors) { onNavigateToSelection(pName, pCpf) }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---
    if (showCpfDialog) {
        Dialog(onDismissRequest = { showCpfDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Buscar Paciente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Text("Digite o CPF", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = tempCpf,
                        onValueChange = {
                            // Filtra para aceitar apenas números e no máximo 11 dígitos
                            val numbersOnly = it.filter { char -> char.isDigit() }
                            if (numbersOnly.length <= 11) tempCpf = numbersOnly
                        },
                        label = { Text(stringResource(R.string.cpf_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CpfVisualTransformation(), // AQUI APLICAMOS A MÁSCARA
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (tempCpf.isNotEmpty()) {
                                EstesioCloud.checkPatient(tempCpf,
                                    onFound = { name, _, _ -> showCpfDialog = false; onNavigateToSelection(name, tempCpf) },
                                    onNotFound = { showCpfDialog = false; showCreatePatientDialog = true },
                                    onError = { showCpfDialog = false; showOfflineDialog = true }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CONTINUAR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showOfflineDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            title = { Text("Sem Conexão", color = colors.error) },
            text = { Text("Não foi possível conectar ao servidor. Deseja iniciar um exame offline?", color = colors.onSurface) },
            confirmButton = { Button(onClick = { showOfflineDialog = false; onNavigateToSelection("Paciente Offline", tempCpf) }) { Text("Sim, Offline") } },
            dismissButton = { TextButton(onClick = { showOfflineDialog = false }) { Text("Cancelar") } },
            containerColor = colors.surface
        )
    }

    if (showCreatePatientDialog) {
        NewPatientDialog(tempCpf, { showCreatePatientDialog = false }, { n, c, a ->
            EstesioCloud.createPatient(c, n, a, "",
                onSuccess = { showCreatePatientDialog = false; onNavigateToSelection(n, c) },
                onError = { /* Log */ }
            )
        })
    }
}

@Composable
fun BigActionCard(title: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(160.dp).clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.15f), Color.Transparent))))
            Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.Start) {
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                }
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun PatientItemCard(name: String, cpf: String, date: Date, colors: ColorScheme, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = colors.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                // Usando máscara manual para exibição na lista
                val formattedCpf = if(cpf.length == 11) "${cpf.substring(0,3)}.${cpf.substring(3,6)}.${cpf.substring(6,9)}-${cpf.substring(9,11)}" else cpf
                Text("CPF: $formattedCpf", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Último", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(date), style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.Bold)
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
    val context = LocalContext.current // Contexto obtido corretamente

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Person, null, tint = colors.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.new_patient_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("Preencha para iniciar o exame", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)

                Spacer(Modifier.height(24.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(
                    R.string.name_hint)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))

                // MÁSCARA APLICADA AQUI TAMBÉM
                OutlinedTextField(
                    value = cpf,
                    onValueChange = {
                        val numbersOnly = it.filter { char -> char.isDigit() }
                        if (numbersOnly.length <= 11) cpf = numbersOnly
                    },
                    label = { Text(stringResource(R.string.cpf_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CpfVisualTransformation()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Idade") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(
                        R.string.cancel)) }
                    Button(onClick = {
                        if(name.isNotEmpty() && cpf.length == 11) {
                            onConfirm(name, cpf, age)
                        } else {
                            Toast.makeText(context, "Preencha corretamente", Toast.LENGTH_SHORT).show() // Toast agora funciona!
                        }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = colors.primary), shape = RoundedCornerShape(12.dp)) { Text(stringResource(
                        R.string.start)) }
                }
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