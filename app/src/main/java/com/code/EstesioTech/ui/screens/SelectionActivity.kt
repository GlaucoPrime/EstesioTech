package com.code.EstesioTech.ui.screens

import android.content.Context
import android.content.Intent
import com.code.EstesioTech.SessionCache
import com.code.EstesioTech.PdfUtil
import com.code.EstesioTech.R
import com.code.EstesioTech.ClinicalScale
import com.code.EstesioTech.RiskBadge
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils

/**
 * Tela de selecao de membros e finalizacao do exame.
 *
 * Fluxo:
 * 1. Medico seleciona os 4 membros a avaliar
 * 2. Para cada membro, navega para TesteActivity (resultados salvos no SessionCache)
 * 3. Indica deformidades (Grade 2 automatico)
 * 4. Ao finalizar, envia para EstesioCloud e gera PDF
 *
 * Correcao de bug: StrictMode nao e mais desativado aqui.
 * O PdfUtil usa FileProvider corretamente via ACTION_SEND.
 */
class SelectionActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val patientName = intent.getStringExtra("PATIENT_NAME") ?: "Paciente"
        val patientCpf  = intent.getStringExtra("PATIENT_CPF")  ?: ""

        val p       = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDark  = p.getBoolean("dark_theme", true)
        val cMode   = p.getInt("color_blind_mode", 0)
        val fScale  = p.getFloat("font_scale", 1.0f)

        // Limpa cache de sessao anterior
        SessionCache.clear()

        setContent {
            EstesioTechTheme(darkTheme = isDark, colorBlindMode = cMode, fontScale = fScale) {
                SelectionScreen(
                    patientName = patientName,
                    patientCpf  = patientCpf,
                    onNavigateToTest = { bodyPart ->
                        startActivity(Intent(this, TesteActivity::class.java).apply {
                            putExtra("BODY_PART",    bodyPart)
                            putExtra("PATIENT_NAME", patientName)
                            putExtra("PATIENT_CPF",  patientCpf)
                        })
                    },
                    onGoHome = {
                        startActivity(Intent(this, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    }
                )
            }
        }
    }
}

/**
 * SessionCache: armazena os resultados temporarios de uma sessao de exame.
 * Limpo no inicio de cada SelectionActivity para evitar contaminacao
 * entre sessoes diferentes.
 */
object SessionCache {
    var results: MutableMap<String, Map<Int, Int>> = mutableMapOf()
    var hasDeformities: Boolean                    = false
    fun clear() { results.clear(); hasDeformities = false }
}

private val BODY_PARTS = listOf("mao_direita", "mao_esquerda", "pe_direito", "pe_esquerdo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    patientName: String,
    patientCpf: String,
    onNavigateToTest: (String) -> Unit,
    onGoHome: () -> Unit
) {
    var hasDeformities  by remember { mutableStateOf(false) }
    var showSaveDialog  by remember { mutableStateOf(false) }
    var isSaving        by remember { mutableStateOf(false) }
    val context         = LocalContext.current
    val colors          = MaterialTheme.colorScheme

    val completedParts = BODY_PARTS.filter { SessionCache.results.containsKey(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.evaluation_title), fontWeight = FontWeight.Bold)
                        Text(patientName, color = colors.primary, fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onGoHome) {
                        Icon(Icons.Default.Home, null, tint = colors.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progresso da sessao
            LinearProgressIndicator(
                progress = completedParts.size / 4f,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color    = colors.primary
            )
            Text(
                "${completedParts.size}/4 membros avaliados",
                fontSize = 13.sp, color = colors.onBackground.copy(0.6f)
            )

            // Cards dos membros
            Text(stringResource(R.string.select_area), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val bodyLabels = mapOf(
                "mao_direita"   to stringResource(R.string.right_hand),
                "mao_esquerda"  to stringResource(R.string.left_hand),
                "pe_direito"    to stringResource(R.string.right_foot),
                "pe_esquerdo"   to stringResource(R.string.left_foot)
            )
            BODY_PARTS.forEach { part ->
                val isDone   = SessionCache.results.containsKey(part)
                val maxLevel = SessionCache.results[part]?.values?.maxOrNull() ?: 0
                BodyPartCard(
                    label    = bodyLabels[part] ?: part,
                    isDone   = isDone,
                    maxLevel = maxLevel,
                    colors   = colors,
                    onClick  = {
                        if (isDone) {
                            // Pergunta se quer refazer
                            Toast.makeText(context, "Segure para refazer este membro.", Toast.LENGTH_SHORT).show()
                        } else {
                            onNavigateToTest(part)
                        }
                    },
                    onLongClick = { onNavigateToTest(part) }
                )
            }

            // Deformidades
            Divider(color = colors.outlineVariant)
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasDeformities) colors.error.copy(0.1f) else colors.surface
                ),
                border = if (hasDeformities) BorderStroke(1.dp, colors.error.copy(0.5f)) else null
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (hasDeformities) Icons.Default.Warning else Icons.Default.Info,
                        null,
                        tint = if (hasDeformities) colors.error else colors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.deformities_question), fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(stringResource(R.string.deformities_desc), fontSize = 12.sp, color = colors.onSurface.copy(0.6f))
                    }
                    Switch(
                        checked         = hasDeformities,
                        onCheckedChange = { hasDeformities = it; SessionCache.hasDeformities = it },
                        colors          = SwitchDefaults.colors(checkedThumbColor = colors.error, checkedTrackColor = colors.error.copy(0.3f))
                    )
                }
            }

            // Botao de finalizar
            Button(
                onClick  = { if (completedParts.size < 4) Toast.makeText(context, "Complete os 4 testes", Toast.LENGTH_SHORT).show() else showSaveDialog = true },
                enabled  = completedParts.isNotEmpty() && !isSaving,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (completedParts.size == 4) colors.primary else colors.outline
                )
            ) {
                if (isSaving) CircularProgressIndicator(color = colors.onPrimary, modifier = Modifier.size(22.dp))
                else Text(
                    if (completedParts.size == 4) stringResource(R.string.finish_evaluation)
                    else "${stringResource(R.string.complete_all_tests)} (${completedParts.size}/4)",
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // Dialog de confirmacao de finalizacao
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            icon    = { Icon(Icons.Default.Save, null, tint = colors.primary, modifier = Modifier.size(36.dp)) },
            title   = { Text("Finalizar Avaliacao", fontWeight = FontWeight.Bold) },
            text    = {
                Column {
                    Text("Isso salva os resultados definitivamente.")
                    if (hasDeformities) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.errorContainer) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = colors.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Deformidades marcadas — Grau 2 registrado automaticamente.", color = colors.onErrorContainer, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            containerColor = colors.surface,
            confirmButton  = {
                Button(onClick = {
                    showSaveDialog = false
                    isSaving = true
                    // Gera um ID unico para esta sessao completa
                    val sessionId = java.util.UUID.randomUUID().toString()
                    EstesioCloud.saveCompleteSession(
                        sessionId      = sessionId,
                        patientCpf     = patientCpf,
                        patientName    = patientName,
                        allResults     = SessionCache.results,
                        hasDeformities = SessionCache.hasDeformities,
                        onSuccess      = {
                            isSaving = false
                            SessionCache.clear()
                            PdfUtil.generateAndShare(context, sessionId, patientName, patientCpf, "share") {}
                        },
                        onError        = { err ->
                            isSaving = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                }) { Text("CONFIRMAR", fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BodyPartCard(
    label: String, isDone: Boolean, maxLevel: Int, colors: ColorScheme,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    val riskResult = ClinicalScale.getResult(maxLevel)
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = if (isDone) colors.surface else colors.surfaceVariant.copy(0.5f)),
        border   = if (isDone) BorderStroke(1.dp, riskResult.color.copy(0.5f)) else null,
        elevation= CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (isDone) riskResult.color.copy(0.15f) else colors.surface),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) Icon(Icons.Default.CheckCircle, null, tint = riskResult.color, modifier = Modifier.size(28.dp))
                else Icon(Icons.Default.RadioButtonUnchecked, null, tint = colors.primary.copy(0.5f), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, color = colors.onSurface)
                if (isDone) {
                    Text(riskResult.description, fontSize = 12.sp, color = riskResult.color)
                    Text("Segure para refazer", fontSize = 11.sp, color = colors.onSurface.copy(0.4f))
                }
            }
            if (!isDone) Icon(Icons.Default.ChevronRight, null, tint = colors.onSurface.copy(0.4f))
        }
    }
}

@Preview
@Composable
fun SelectionPreview() {
    EstesioTechTheme(darkTheme = true) {
        SelectionScreen("Maria Silva", "00000000000", {}, {})
    }
}