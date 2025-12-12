package com.code.EstesioTech

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

// Modelo de dados local
data class TestResult(
    val id: String = "",
    val bodyPart: String = "",
    val averageLevel: Double = 0.0,
    val date: Date? = null
)

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EstesioTechTheme {
                HistoryScreen(onBackClick = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBackClick: () -> Unit) {
    val isPreview = LocalInspectionMode.current
    var testsList by remember { mutableStateOf<List<TestResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            val db = FirebaseFirestore.getInstance()
            val userId = EstesioCloud.getCurrentUserId()

            if (userId == null) {
                errorMessage = "Usuário não logado."
                isLoading = false
                return@LaunchedEffect
            }

            db.collection("tests")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { result ->
                    val list = result.documents.map { doc ->
                        // Pega os dados com segurança
                        val avg = doc.get("averageLevel") // Tenta pegar genérico
                        val avgDouble = when(avg) {
                            is Double -> avg
                            is Long -> avg.toDouble()
                            is String -> avg.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }

                        TestResult(
                            id = doc.id,
                            bodyPart = doc.getString("bodyPart") ?: "Desconhecido",
                            averageLevel = avgDouble,
                            date = doc.getDate("date")
                        )
                    }
                    testsList = list.sortedByDescending { it.date }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    errorMessage = "Erro: ${e.message}"
                    isLoading = false
                }
        } else {
            // Mock para preview
            testsList = listOf(TestResult("1", "mao_direita", 2.5, Date()))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico Clínico", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF101820))
            )
        },
        containerColor = Color(0xFF101820)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(colors = listOf(Color(0xFF101820), Color(0xFF000000))))
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF00ACC1)) }
            } else if (errorMessage != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(errorMessage!!, color = Color.Red) }
            } else if (testsList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Text("Nenhum teste encontrado.", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(testsList) { test -> TestHistoryCard(test) }
                }
            }
        }
    }
}

@Composable
fun TestHistoryCard(test: TestResult) {
    val dateString = test.date?.let { DateFormat.format("dd/MM/yyyy 'às' HH:mm", it).toString() } ?: "Data desconhecida"
    val formattedPart = test.bodyPart.replace("_", " ").replaceFirstChar { it.uppercase() }

    val statusColor = when {
        test.averageLevel < 2.0 -> Color(0xFF4CAF50)
        test.averageLevel < 4.0 -> Color(0xFFFF9800)
        else -> Color(0xFFE91E63)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634).copy(alpha = 0.8f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.2f)).border(2.dp, statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(String.format("%.1f", test.averageLevel), color = statusColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(formattedPart, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF00ACC1), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(dateString, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryPreview() {
    EstesioTechTheme { HistoryScreen({}) }
}