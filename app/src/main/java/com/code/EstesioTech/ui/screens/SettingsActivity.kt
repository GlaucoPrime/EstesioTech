package com.code.EstesioTech.ui.screens

import android.content.Context
import com.code.EstesioTech.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils

/**
 * Tela de configuracoes do app.
 *
 * Salva preferencias no SharedPreferences "EstesioPrefs".
 * Ao fechar, o HomeActivity.onResume detecta mudancas e executa recreate().
 *
 * Secoes:
 * - Aparencia: tema escuro/claro
 * - Acessibilidade: escala de fonte, modo de daltonismo
 * - Idioma: PT, EN, ES
 */
class SettingsActivity : ComponentActivity() {

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
                SettingsScreen(
                    onBack = { finish() },
                    onChanged = { /* HomeActivity.onResume vai detectar */ }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context   = androidx.compose.ui.platform.LocalContext.current
    val prefs     = context.getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
    var darkTheme   by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
    var colorMode   by remember { mutableIntStateOf(prefs.getInt("color_blind_mode", 0)) }
    var fontScale   by remember { mutableFloatStateOf(prefs.getFloat("font_scale", 1.0f)) }
    var language    by remember { mutableStateOf(prefs.getString("language", "pt") ?: "pt") }
    val colors      = MaterialTheme.colorScheme

    val colorModes = listOf(
        stringResource(R.string.mode_normal),
        stringResource(R.string.mode_protanopia),
        stringResource(R.string.mode_deuteranopia),
        stringResource(R.string.mode_tritanopia),
        stringResource(R.string.mode_monochromacy)
    )

    fun save() {
        prefs.edit()
            .putBoolean("dark_theme",      darkTheme)
            .putInt("color_blind_mode",    colorMode)
            .putFloat("font_scale",        fontScale)
            .putString("language",         language)
            .apply()
        onChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Aparencia ----
            SettingsSection(stringResource(R.string.appearance_header), Icons.Default.Palette)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(colors.surface)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (darkTheme) stringResource(R.string.dark_mode) else stringResource(R.string.light_mode),
                        Modifier.weight(1f), color = colors.onSurface
                    )
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = { darkTheme = it; save() }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Acessibilidade ----
            SettingsSection(stringResource(R.string.accessibility_header), Icons.Default.Accessibility)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(colors.surface)) {
                Column(Modifier.padding(20.dp)) {
                    // Escala de fonte
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatSize, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.font_size), Modifier.weight(1f), color = colors.onSurface)
                        Text("${(fontScale * 100).toInt()}%", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Slider(
                        value         = fontScale,
                        onValueChange = { fontScale = it; save() },
                        valueRange    = 0.8f..1.4f,
                        steps         = 5,
                        colors        = SliderDefaults.colors(activeTrackColor = colors.primary)
                    )

                    Divider(color = colors.outlineVariant)
                    Spacer(Modifier.height(12.dp))

                    // Modo de daltonismo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RemoveRedEye, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.color_blind_mode), color = colors.onSurface)
                    }
                    Spacer(Modifier.height(10.dp))
                    colorModes.forEachIndexed { idx, mode ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = colorMode == idx,
                                onClick  = { colorMode = idx; save() },
                                colors   = RadioButtonDefaults.colors(selectedColor = colors.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(mode, color = colors.onSurface)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Idioma ----
            SettingsSection(stringResource(R.string.language_header), Icons.Default.Language)
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(colors.surface)) {
                Column(Modifier.padding(20.dp)) {
                    listOf("pt" to "Portugues (PT-BR)", "en" to "English", "es" to "Espanol").forEach { (code, label) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = language == code,
                                onClick  = { language = code; LocaleUtils.saveAndApply(context, code); save() },
                                colors   = RadioButtonDefaults.colors(selectedColor = colors.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = colors.onSurface)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Preview
@Composable
fun SettingsPreview() {
    EstesioTechTheme(darkTheme = true) { SettingsScreen({}, {}) }
}