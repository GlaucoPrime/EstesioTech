package com.code.EstesioTech

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.R
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val savedColorMode = prefs.getInt("color_blind_mode", 0)
        val savedFontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            var darkTheme by remember { mutableStateOf(isDarkTheme) }
            var colorMode by remember { mutableStateOf(savedColorMode) }
            var fontScale by remember { mutableStateOf(savedFontScale) }

            // O tema da própria tela de configurações muda instantaneamente ao selecionar
            EstesioTechTheme(
                darkTheme = darkTheme,
                colorBlindMode = colorMode,
                fontScale = fontScale // Passa a escala para o tema
            ) {
                SettingsScreen(
                    isDarkTheme = darkTheme,
                    colorBlindMode = colorMode,
                    fontScale = fontScale,
                    onThemeChange = { newTheme ->
                        darkTheme = newTheme
                        prefs.edit().putBoolean("dark_theme", newTheme).apply()
                    },
                    onColorModeChange = { newMode ->
                        colorMode = newMode
                        prefs.edit().putInt("color_blind_mode", newMode).apply()
                    },
                    onFontScaleChange = { newScale ->
                        fontScale = newScale
                        prefs.edit().putFloat("font_scale", newScale).apply()
                    },
                    onLanguageChange = { langCode ->
                        // Lógica básica de troca de idioma (Requer reinício da Activity para efeito total)
                        setLocale(this, langCode)
                        recreate()
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun setLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    colorBlindMode: Int,
    fontScale: Float,
    onThemeChange: (Boolean) -> Unit,
    onColorModeChange: (Int) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // --- APARÊNCIA ---
            if (colorBlindMode == 0) {
                SettingsHeader(stringResource(R.string.appearance_header))
                SettingsCard(colors) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = colors.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(stringResource(R.string.dark_mode), fontWeight = FontWeight.Bold, color = colors.onSurface)
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onThemeChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = colors.primary)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // --- ACESSIBILIDADE ---
            SettingsHeader(stringResource(R.string.accessibility_header))
            SettingsCard(colors) {
                Column(Modifier.padding(16.dp)) {
                    // Tamanho da Fonte
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatSize, null, tint = colors.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("${stringResource(R.string.font_size)}: ${(fontScale * 100).toInt()}%", fontWeight = FontWeight.Bold, color = colors.onSurface)
                    }
                    Slider(
                        value = fontScale,
                        onValueChange = onFontScaleChange,
                        valueRange = 0.8f..1.5f,
                        steps = 6,
                        colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
                    )

                    Divider(Modifier.padding(vertical = 16.dp))

                    // Daltonismo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, null, tint = colors.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.color_blind_mode), fontWeight = FontWeight.Bold, color = colors.onSurface)
                    }
                    Spacer(Modifier.height(12.dp))

                    ColorBlindOption(0, stringResource(R.string.mode_normal), colorBlindMode, onColorModeChange)
                    ColorBlindOption(1, stringResource(R.string.mode_protanopia), colorBlindMode, onColorModeChange)
                    ColorBlindOption(2, stringResource(R.string.mode_deuteranopia), colorBlindMode, onColorModeChange)
                    ColorBlindOption(3, stringResource(R.string.mode_tritanopia), colorBlindMode, onColorModeChange)
                    ColorBlindOption(4, stringResource(R.string.mode_monochromacy), colorBlindMode, onColorModeChange)
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- IDIOMA ---
            SettingsHeader(stringResource(R.string.language_header))
            SettingsCard(colors) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onLanguageChange("pt") }, modifier = Modifier.weight(1f)) { Text("Português") }
                        OutlinedButton(onClick = { onLanguageChange("en") }, modifier = Modifier.weight(1f)) { Text("English") }
                        OutlinedButton(onClick = { onLanguageChange("es") }, modifier = Modifier.weight(1f)) { Text("Español") }
                    }
                }
            }
        }
    }
}

@Composable
fun ColorBlindOption(mode: Int, label: String, currentMode: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (mode == currentMode),
            onClick = { onSelect(mode) },
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsCard(colors: ColorScheme, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    EstesioTechTheme(darkTheme = true) {
        SettingsScreen(true, 0, 1.0f, {}, {}, {}, {}, {})
    }
}