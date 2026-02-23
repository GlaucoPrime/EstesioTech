package com.code.EstesioTech.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// IMPORTS DA NOVA ARQUITETURA
import com.code.EstesioTech.R
import com.code.EstesioTech.ui.theme.EstesioTechTheme

class SettingsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val langCode = prefs.getString("language", "pt") ?: "pt"
        val locale = Locale(langCode)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val savedColorMode = prefs.getInt("color_blind_mode", 0)
        val savedFontScale = prefs.getFloat("font_scale", 1.0f)
        val currentLanguage = prefs.getString("language", "pt") ?: "pt"

        setContent {
            var darkTheme by remember { mutableStateOf(isDarkTheme) }
            var colorMode by remember { mutableStateOf(savedColorMode) }
            var fontScale by remember { mutableStateOf(savedFontScale) }
            var activeLanguage by remember { mutableStateOf(currentLanguage) }

            EstesioTechTheme(darkTheme = darkTheme, colorBlindMode = colorMode, fontScale = fontScale) {
                DivineSettingsScreen(
                    isDarkTheme = darkTheme,
                    colorBlindMode = colorMode,
                    fontScale = fontScale,
                    activeLanguage = activeLanguage,
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
                        activeLanguage = langCode
                        prefs.edit().putString("language", langCode).apply()
                        recreate()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DivineSettingsScreen(
    isDarkTheme: Boolean, colorBlindMode: Int, fontScale: Float, activeLanguage: String,
    onThemeChange: (Boolean) -> Unit, onColorModeChange: (Int) -> Unit,
    onFontScaleChange: (Float) -> Unit, onLanguageChange: (String) -> Unit, onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel)) } },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = colors.background, scrolledContainerColor = colors.surface, titleContentColor = colors.primary)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            if (colorBlindMode == 0) {
                SectionWrapper(title = stringResource(R.string.appearance_header), icon = Icons.Default.Palette) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onThemeChange(!isDarkTheme) }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = colors.primary)
                            Spacer(Modifier.width(16.dp))
                            Text(if(isDarkTheme) stringResource(R.string.dark_mode) else stringResource(R.string.light_mode), fontWeight = FontWeight.Bold, color = colors.onSurface, fontSize = 16.sp)
                        }
                        Switch(checked = isDarkTheme, onCheckedChange = onThemeChange, colors = SwitchDefaults.colors(checkedThumbColor = colors.primary, checkedTrackColor = colors.primaryContainer))
                    }
                }
            }

            SectionWrapper(title = stringResource(R.string.accessibility_header), icon = Icons.Default.AccessibilityNew) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(Icons.Default.FormatSize, null, tint = colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.font_size), fontWeight = FontWeight.Bold, color = colors.onSurface, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${(fontScale * 100).toInt()}%", color = colors.primary, fontWeight = FontWeight.ExtraBold)
                    }
                    Slider(value = fontScale, onValueChange = onFontScaleChange, valueRange = 0.8f..1.5f, steps = 6, colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary))

                    Divider(Modifier.padding(vertical = 24.dp), color = colors.outlineVariant.copy(alpha = 0.5f))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Icon(Icons.Default.Visibility, null, tint = colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.color_blind_mode), fontWeight = FontWeight.Bold, color = colors.onSurface, fontSize = 16.sp)
                    }

                    val blindModes = listOf(stringResource(R.string.mode_normal), stringResource(R.string.mode_protanopia), stringResource(R.string.mode_deuteranopia), stringResource(R.string.mode_tritanopia), stringResource(R.string.mode_monochromacy))
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = blindModes[colorBlindMode], onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.primary, unfocusedBorderColor = colors.outline, focusedContainerColor = colors.surfaceVariant.copy(alpha=0.3f), unfocusedContainerColor = colors.surfaceVariant.copy(alpha=0.3f)),
                            textStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(colors.surface).clip(RoundedCornerShape(16.dp))) {
                            blindModes.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(text = label, color = if (colorBlindMode == index) colors.primary else colors.onSurface, fontWeight = if (colorBlindMode == index) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { onColorModeChange(index); expanded = false },
                                    trailingIcon = { if (colorBlindMode == index) Icon(Icons.Default.CheckCircle, null, tint = colors.primary) }
                                )
                            }
                        }
                    }
                }
            }

            SectionWrapper(title = stringResource(R.string.language_header), icon = Icons.Default.Translate) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val ptColors = listOf(Color(0xFF009B3A), Color(0xFFFEDF00), Color(0xFF002776), Color(0xFFFF0000), Color(0xFF009B3A))
                    val enColors = listOf(Color(0xFFB22234), Color(0xFFFFFFFF), Color(0xFF3C3B6E), Color(0xFFB22234))
                    val esColors = listOf(Color(0xFFAA151B), Color(0xFFF1BF00), Color(0xFFAA151B), Color(0xFF006847), Color(0xFFAA151B))

                    DivineFlagButton(text = "Português", flagEmoji = "🇧🇷 🇵🇹", isSelected = activeLanguage == "pt", flagColors = ptColors) { onLanguageChange("pt") }
                    DivineFlagButton(text = "English", flagEmoji = "🇺🇸 🇬🇧", isSelected = activeLanguage == "en", flagColors = enColors) { onLanguageChange("en") }
                    DivineFlagButton(text = "Español", flagEmoji = "🇪🇸 🇲🇽", isSelected = activeLanguage == "es", flagColors = esColors) { onLanguageChange("es") }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun SectionWrapper(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = title.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

// O CÓDIGO DA ANIMAÇÃO RÁPIDA (REVERSE) SOLICITADO
@Composable
fun DivineFlagButton(
    text: String,
    flagEmoji: String,
    isSelected: Boolean,
    flagColors: List<Color>,
    onClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // 1. Animação Infinita do Gradiente (Rápida e Contínua)
    val infiniteTransition = rememberInfiniteTransition(label = "gradient_anim_$text")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing), // 3 segundos para rodar bem rápido
            repeatMode = RepeatMode.Restart // Cria o efeito de esteira que nunca acaba
        ),
        label = "offset_$text"
    )

    // 2. Transição suave de cor se for selecionado ou não
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
        label = "bgColor"
    )

    // O pincel (brush) só vira o arco-íris da bandeira se o idioma estiver selecionado
    val backgroundModifier = if (isSelected) {
        Modifier.background(
            Brush.linearGradient(
                colors = flagColors,
                start = Offset(offset, offset),
                end = Offset(offset + 600f, offset + 600f)
            )
        )
    } else {
        Modifier.background(bgColor)
    }

    // 3. Física de "Pressionar" (Spring Animation)
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale_bounce"
    )

    // 4. Sombras para garantir leitura por cima do gradiente
    val textStyle = if (isSelected) {
        TextStyle(
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(2f, 2f), blurRadius = 4f)
        )
    } else {
        TextStyle(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .then(backgroundModifier)
            .border(
                BorderStroke(2.dp, if (isSelected) Color.White.copy(alpha = 0.5f) else Color.Transparent),
                RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        // Pequeno atraso visual para a pessoa ver o botão afundar antes de atualizar
                        coroutineScope.launch {
                            delay(150)
                            onClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(text, style = textStyle)
            }
            Text(flagEmoji, fontSize = 28.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    EstesioTechTheme(darkTheme = true) {
        DivineSettingsScreen(
            isDarkTheme = true, colorBlindMode = 0, fontScale = 1.0f, activeLanguage = "pt",
            onThemeChange = {}, onColorModeChange = {}, onFontScaleChange = {}, onLanguageChange = {}, onBack = {}
        )
    }
}