package com.code.EstesioTech

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EstesioTechTheme { HistoryScreen(onBack = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    var historyList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Carrega dados ao iniciar
    LaunchedEffect(Unit) {
        EstesioCloud.getDoctorHistory(
            onSuccess = { list ->
                historyList = list
                isLoading = false
            },
            onError = { error ->
                errorMessage = error
                isLoading = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Testes", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF101820))
            )
        },
        containerColor = Color(0xFF101820)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00ACC1))
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMessage!!, color = Color.Red)
                }
            } else if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nenhum teste realizado ainda.", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(historyList) { test ->
                        HistoryItem(test)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(test: Map<String, Any>) {
    val patientName = test["patientName"] as? String ?: "Desconhecido"
    val bodyPart = test["bodyPart"] as? String ?: "Corpo"
    val dateTimestamp = test["date"] as? com.google.firebase.Timestamp
    val date = dateTimestamp?.toDate()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateString = if (date != null) dateFormat.format(date) else "Data N/A"

    // Calcula GIF
    val gif = (test["gif"] as? Long)?.toInt() ?: 0
    val gifColor = if (gif == 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val gifText = "Grau $gif"

    // Formata bodyPart
    val bodyPartLabel = bodyPart.replace("_", " ").replaceFirstChar { it.uppercase() }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone do Grau
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(gifColor.copy(alpha = 0.2f))
                    .border(2.dp, gifColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(gif.toString(), color = gifColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(patientName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(bodyPartLabel, color = Color(0xFF80DEEA), fontSize = 14.sp)
                Text(dateString, color = Color.Gray, fontSize = 12.sp)
            }

            // Tag GIF
            Surface(
                color = gifColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if(gif == 0) "Normal" else "Risco",
                    color = gifColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}