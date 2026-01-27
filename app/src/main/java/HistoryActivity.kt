package com.code.EstesioTech

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EstesioTechTheme {
                HistoryScreen(
                    onBack = { finish() },
                    onGeneratePDF = { sessionId, name, cpf, action ->
                        generateAndSharePDF(sessionId, name, cpf, action)
                    }
                )
            }
        }
    }

    private fun generateAndSharePDF(sessionId: String, name: String, cpf: String, action: String) {
        Toast.makeText(this, "Processando solicitação...", Toast.LENGTH_SHORT).show()
        EstesioCloud.getFullSessionData(sessionId, onSuccess = { data ->
            try {
                // ... (Lógica de desenho do PDF mantida, focando na lógica de salvar) ...
                // Cálculo de GIF
                val maxGif = data.maxOfOrNull { (it["gif"] as? Long)?.toInt() ?: 0 } ?: 0
                val gifText = when(maxGif) {
                    0 -> "Grau 0: Sensibilidade Preservada"
                    1 -> "Grau 1: Perda de Sensibilidade Protetora"
                    else -> "Grau 2: Risco Elevado"
                }

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                val paint = Paint()

                paint.textSize = 18f
                paint.isFakeBoldText = true
                canvas.drawText("Relatório EstesioTech", 50f, 50f, paint)
                paint.textSize = 12f
                paint.isFakeBoldText = false
                canvas.drawText("Paciente: $name", 50f, 80f, paint)
                canvas.drawText("CPF: $cpf", 50f, 100f, paint)
                canvas.drawText("Emissão: ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date())}", 50f, 120f, paint)

                paint.textSize = 14f
                paint.isFakeBoldText = true
                paint.color = if(maxGif > 0) android.graphics.Color.RED else android.graphics.Color.rgb(0, 100, 0)
                canvas.drawText("Avaliação OMS: $gifText", 50f, 160f, paint)

                paint.color = android.graphics.Color.BLACK
                paint.textSize = 12f
                var yPos = 200f

                data.forEach { test ->
                    val part = (test["bodyPart"] as? String)?.replace("_", " ")?.uppercase() ?: "MEMBRO"
                    val points = test["pointsData"] as? Map<String, Any>
                    canvas.drawText("- $part:", 50f, yPos, paint)
                    yPos += 20f
                    points?.forEach { (k, v) ->
                        val pLevel = v.toString().toIntOrNull() ?: 0
                        val pDesc = ClinicalScale.getResult(pLevel).description
                        canvas.drawText("   Ponto $k: Nível $v ($pDesc)", 50f, yPos, paint)
                        yPos += 15f
                    }
                    yPos += 10f
                }
                pdfDocument.finishPage(page)

                // Salva na pasta pública Downloads
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "EstesioTech_${name.split(" ")[0]}_${System.currentTimeMillis()}.pdf"
                val file = File(path, fileName)
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                if (action == "download") {
                    Toast.makeText(this, "Salvo em Downloads: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    // COMPARTILHAMENTO CORRIGIDO COM FILEPROVIDER
                    try {
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider", // Deve bater com o Manifest
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Enviar Relatório"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Erro ao compartilhar: ${e.message}. Arquivo salvo em Downloads.", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }, onError = {
            Toast.makeText(this, "Erro ao baixar dados: $it", Toast.LENGTH_SHORT).show()
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onGeneratePDF: (String, String, String, String) -> Unit) {
    var patientsList by remember { mutableStateOf<List<PatientHistoryData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        EstesioCloud.getGroupedHistory(
            onSuccess = {
                patientsList = it
                isLoading = false
            },
            onError = { isLoading = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Pacientes", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A2634))
            )
        },
        containerColor = Color(0xFF101820)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF00ACC1)) }
            } else if (patientsList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum histórico encontrado.", color = Color.Gray) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(patientsList) { patient ->
                        PatientHistoryCard(patient, onGeneratePDF)
                    }
                }
            }
        }
    }
}

@Composable
fun PatientHistoryCard(patient: PatientHistoryData, onGeneratePDF: (String, String, String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634)),
        border = BorderStroke(1.dp, Color(0xFF00ACC1).copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabeçalho: Nome e CPF
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF00ACC1).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        val initial = if (patient.name.isNotEmpty()) patient.name.first().toString() else "?"
                        Text(initial, color = Color(0xFF00ACC1), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (patient.name.isEmpty()) "Sem Nome" else patient.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("CPF: ${patient.cpf}", color = Color.Gray.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
            }

            // Lista de Sessões (Datas)
            if (expanded) {
                Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.2f))
                Text("Sessões Realizadas:", color = Color(0xFF80DEEA), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

                patient.sessions.forEach { session ->
                    val dateStr = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(session.date)
                    val statusColor = if (session.maxGif > 0) Color(0xFFF44336) else Color(0xFF4CAF50)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                            Spacer(Modifier.width(8.dp))
                            Text(dateStr, color = Color.White, fontSize = 14.sp)
                        }
                        // Botões de Ação Específicos para a Sessão
                        Row {
                            IconButton(onClick = { onGeneratePDF(session.sessionId, patient.name, patient.cpf, "download") }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Download, "Baixar", tint = Color(0xFF00ACC1), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onGeneratePDF(session.sessionId, patient.name, patient.cpf, "share") }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Share, "Compartilhar", tint = Color(0xFF00ACC1), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}