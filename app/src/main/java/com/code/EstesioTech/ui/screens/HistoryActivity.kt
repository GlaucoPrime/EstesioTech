package com.code.EstesioTech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.data.cloud.PatientHistoryData
import com.code.EstesioTech.data.cloud.SessionData
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historico de pacientes e sessoes de exame.
 *
 * Melhorias:
 * - PDF gerado via PdfUtil centralizado (sem duplicar logica)
 * - LoadingOverlay reutilizavel
 * - CPF exibido como hash (LGPD): o CPF real nao fica visivel na tela
 * - Cards com animacao de expansao para ver as sessoes
 */
class HistoryActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p       = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDark  = p.getBoolean("dark_theme", true)
        val cMode   = p.getInt("color_blind_mode", 0)
        val fScale  = p.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDark, colorBlindMode = cMode, fontScale = fScale) {
                var loadingMsg by remember { mutableStateOf<String?>(null) }
                Box(Modifier.fillMaxSize()) {
                    HistoryScreen(
                        onBack          = { finish() },
                        onLoadingChange = { loadingMsg = it },
                        onGeneratePDF   = { sessionId, name, cpfHash, action ->
                            loadingMsg = "Gerando relatorio..."
                            PdfUtil.generateAndShare(this@HistoryActivity, sessionId, name, cpfHash, action) { file ->
                                loadingMsg = null
                                if (action == "download" && file != null) openPdf(file)
                            }
                        }
                    )
                    loadingMsg?.let { LoadingOverlay(it) }
                }
            }
        }
    }

    private fun openPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum visualizador de PDF encontrado.", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun LoadingOverlay(message: String) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onLoadingChange: (String?) -> Unit,
    onGeneratePDF: (String, String, String, String) -> Unit
) {
    var patients    by remember { mutableStateOf<List<PatientHistoryData>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(true) }
    val colors      = MaterialTheme.colorScheme

    fun reload() {
        EstesioCloud.getGroupedHistory(
            onSuccess = { patients = it; isLoading = false },
            onError   = { isLoading = false }
        )
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                patients.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, tint = colors.onSurface.copy(0.3f), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.history_empty), color = colors.onSurface.copy(0.5f))
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(patients, key = { it.cpfHash }) { patient ->
                        PatientHistoryCard(
                            patient         = patient,
                            onGeneratePDF   = onGeneratePDF,
                            onDeleteSession = { sid ->
                                onLoadingChange("Excluindo...")
                                EstesioCloud.deleteSession(sid,
                                    onSuccess = { onLoadingChange(null); reload() },
                                    onError   = { onLoadingChange(null) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PatientHistoryCard(
    patient: PatientHistoryData,
    onGeneratePDF: (String, String, String, String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var expanded        by remember { mutableStateOf(false) }
    var confirmDelete   by remember { mutableStateOf<String?>(null) }
    val colors          = MaterialTheme.colorScheme
    val dateFmt         = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    confirmDelete?.let { sid ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title   = { Text(stringResource(R.string.history_delete_title), fontWeight = FontWeight.Bold) },
            text    = { Text(stringResource(R.string.history_delete_desc)) },
            confirmButton = {
                TextButton(onClick = { onDeleteSession(sid); confirmDelete = null }) {
                    Text(stringResource(R.string.delete), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = colors.surface
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(containerColor = colors.surface),
        elevation= CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            // -- Cabecalho do paciente --
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        patient.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                        color = colors.onPrimaryContainer, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(patient.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.onSurface)
                    // LGPD: exibimos apenas os primeiros 8 chars do hash como identificador anonimizado
                    Text(
                        "ID: ${patient.cpfHash.take(8)}...",
                        fontSize = 12.sp,
                        color    = colors.onSurface.copy(0.5f)
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = colors.onSurface.copy(0.6f)
                )
            }

            // -- Sessoes (expandivel) --
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                Divider(color = colors.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.history_sessions), fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))

                patient.sessions.forEach { session ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape    = RoundedCornerShape(12.dp),
                        color    = colors.surfaceVariant.copy(0.4f)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dateFmt.format(session.date), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.onSurface)
                                RiskBadge(riskLevel = session.maxGif, modifier = Modifier.padding(top = 4.dp))
                            }
                            // Acoes
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(onClick = { onGeneratePDF(session.sessionId, patient.name, patient.cpfHash, "download") }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Download, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onGeneratePDF(session.sessionId, patient.name, patient.cpfHash, "share") }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Share, null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { confirmDelete = session.sessionId }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = colors.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun HistoryPreview() {
    EstesioTechTheme(darkTheme = true) {
        PatientHistoryCard(
            patient = PatientHistoryData("Maria Silva", "abc123def456", listOf(SessionData("s1", Date(), 1))),
            onGeneratePDF = { _, _, _, _ -> }, onDeleteSession = {}
        )
    }
}