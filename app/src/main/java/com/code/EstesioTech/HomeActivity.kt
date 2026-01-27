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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import java.text.SimpleDateFormat
import java.util.Locale

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EstesioTechTheme {
                val context = LocalContext.current
                var isConnected by remember { mutableStateOf(BleManager.isConnected()) }

                var showPatientDialog by remember { mutableStateOf(false) }
                var showTestSelectionDialog by remember { mutableStateOf(false) }
                var selectedPatient by remember { mutableStateOf<Pair<String, String>?>(null) }

                var userName by remember { mutableStateOf("Doutor(a)") }
                var recentPatients by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

                LaunchedEffect(Unit) {
                    EstesioCloud.getUserName { name -> userName = name }
                    EstesioCloud.getRecentPatients(
                        onSuccess = { recentPatients = it },
                        onError = { /* Ignora */ }
                    )
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isConnected = BleManager.isConnected()
                            EstesioCloud.getRecentPatients(
                                onSuccess = { recentPatients = it },
                                onError = { }
                            )
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (showPatientDialog) {
                    PatientSearchDialog(
                        onDismiss = { showPatientDialog = false },
                        onPatientSelected = { cpf, name ->
                            selectedPatient = cpf to name
                            showPatientDialog = false
                            showTestSelectionDialog = true
                        }
                    )
                }

                if (showTestSelectionDialog && selectedPatient != null) {
                    TestTypeDialog(
                        onDismiss = { showTestSelectionDialog = false },
                        onSelect = { type ->
                            showTestSelectionDialog = false
                            if (type == "hansen") {
                                if (isConnected) {
                                    val intent = Intent(context, SelectionActivity::class.java).apply {
                                        putExtra("PATIENT_CPF", selectedPatient!!.first)
                                        putExtra("PATIENT_NAME", selectedPatient!!.second)
                                    }
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(context, "⚠️ Conecte o hardware primeiro!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Em breve.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                HomeScreen(
                    userName = userName,
                    isConnected = isConnected,
                    recentPatients = recentPatients,
                    onConnectClick = {
                        if (isConnected) Toast.makeText(context, "Dispositivo já conectado.", Toast.LENGTH_SHORT).show()
                        else startActivity(Intent(context, MainActivity::class.java))
                    },
                    onStartTestClick = { showPatientDialog = true },
                    onHistoryClick = { startActivity(Intent(context, HistoryActivity::class.java)) },
                    onLogoutClick = {
                        getSharedPreferences("EstesioPrefs", MODE_PRIVATE).edit().clear().apply()
                        EstesioCloud.logout()
                        BleManager.disconnect()
                        startActivity(Intent(context, LoginActivity::class.java))
                        finish()
                    },
                    onRecentClick = { cpf, name ->
                        selectedPatient = cpf to name
                        showTestSelectionDialog = true
                    }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    userName: String,
    isConnected: Boolean,
    recentPatients: List<Map<String, Any>>,
    onConnectClick: () -> Unit,
    onStartTestClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onRecentClick: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF101820), Color(0xFF000000))))
            .padding(24.dp)
    ) {
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

        Spacer(modifier = Modifier.height(24.dp))

        Text("ÚLTIMOS ATENDIMENTOS", color = Color(0xFF80DEEA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (recentPatients.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Text("Nenhum paciente recente.", color = Color.Gray.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.padding(top = 20.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentPatients) { patient ->
                    val name = patient["name"] as? String ?: "Sem Nome"
                    // AJUSTE DE NOME (1º e 2º apenas)
                    val displayName = name.split(" ").take(2).joinToString(" ")

                    val cpf = patient["cpf"] as String
                    val date = patient["lastExam"] as java.util.Date
                    val dateStr = SimpleDateFormat("dd 'de' MMM, HH:mm", Locale("pt", "BR")).format(date)

                    RecentPatientItem(displayName, dateStr) { onRecentClick(cpf, name) } // Passa nome completo no click, mostra curto
                }
            }
        }
    }
}

@Composable
fun RecentPatientItem(name: String, date: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634)),
        border = BorderStroke(1.dp, Color(0xFF00ACC1).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF00ACC1).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = if(name.isNotEmpty()) name.first().toString() else "?"
                    Text(initial, color = Color(0xFF00ACC1), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(if(name.isEmpty()) "Sem Nome" else name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text(date, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// ... Resto dos diálogos (PatientSearchDialog, etc) mantidos do arquivo anterior ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientSearchDialog(onDismiss: () -> Unit, onPatientSelected: (String, String) -> Unit) {
    var cpf by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newAge by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A242E)),
            border = BorderStroke(1.dp, Color(0xFF00ACC1).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(if (showRegister) "Cadastrar Paciente" else "Identificar Paciente",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                if (!showRegister) {
                    OutlinedTextField(
                        value = cpf,
                        onValueChange = { if(it.length <= 11) cpf = it },
                        label = { Text("CPF (apenas números)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00ACC1),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00ACC1)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (cpf.length >= 1) { // Aceita qualquer tamanho para teste
                                isLoading = true
                                EstesioCloud.checkPatient(cpf,
                                    onFound = { name, _, _ ->
                                        isLoading = false
                                        onPatientSelected(cpf, name)
                                    },
                                    onNotFound = {
                                        isLoading = false
                                        showRegister = true
                                    },
                                    onError = { isLoading = false }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1)),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Buscar")
                    }
                } else {
                    Text("CPF não encontrado. Cadastre abaixo:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    SimpleHomeTextField(newName, { newName = it }, "Nome Completo", Icons.Default.Person)
                    Spacer(Modifier.height(8.dp))
                    SimpleHomeTextField(newAge, { newAge = it }, "Idade", Icons.Default.DateRange, KeyboardType.Number)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (newName.isNotEmpty()) {
                                isLoading = true
                                EstesioCloud.createPatient(cpf, newName, newAge, "",
                                    onSuccess = {
                                        isLoading = false
                                        onPatientSelected(cpf, newName)
                                    },
                                    onError = { isLoading = false }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1)),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Salvar e Continuar")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancelar", color = Color(0xFFCF6679))
                }
            }
        }
    }
}

@Composable
fun SimpleHomeTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, kType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, null, tint = Color(0xFF00ACC1)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = kType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00ACC1),
            unfocusedBorderColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Color(0xFFCF6679)) } },
        containerColor = Color(0xFF1A2634),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun TestOption(name: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    com.code.EstesioTech.ui.theme.EstesioTechTheme {
        HomeScreen(
            userName = "Dr. Glauco",
            isConnected = true,
            recentPatients = listOf(mapOf("name" to "Maria", "cpf" to "123", "lastExam" to java.util.Date())),
            onConnectClick = {}, onStartTestClick = {}, onHistoryClick = {}, onLogoutClick = {}, onRecentClick = {_,_ ->}
        )
    }
}