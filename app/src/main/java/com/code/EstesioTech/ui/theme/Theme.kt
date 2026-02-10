package com.code.EstesioTech.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- DEFINIÇÃO DAS CORES (Reutilizando Color.kt) ---

private val NormalDark = darkColorScheme(primary = DarkPrimary, secondary = DarkPrimary, background = DarkBackground, surface = DarkSurface, onPrimary = DarkText, onSurface = DarkText)
private val NormalLight = lightColorScheme(primary = LightPrimary, secondary = LightSecondary, background = LightBackground, surface = LightSurface, onPrimary = Color.White, onSurface = LightText)

// Esquemas Daltônicos (Alto Contraste)
private val ProtanopiaScheme = lightColorScheme(primary = ProtPrimary, secondary = ProtSecondary, background = ProtBackground, surface = ProtSurface, onPrimary = Color.White, onSurface = ProtText)
private val DeuteranopiaScheme = lightColorScheme(primary = DeutPrimary, secondary = DeutSecondary, background = DeutBackground, surface = DeutSurface, onPrimary = Color.White, onSurface = DeutText)
private val TritanopiaScheme = lightColorScheme(primary = TritPrimary, secondary = TritSecondary, background = TritBackground, surface = TritSurface, onPrimary = Color.White, onSurface = TritText)
private val MonoScheme = lightColorScheme(primary = MonoPrimary, secondary = MonoSecondary, background = MonoBackground, surface = MonoSurface, onPrimary = Color.White, onSurface = MonoText)

@Composable
fun EstesioTechTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorBlindMode: Int = 0, // 0: Nenhum, 1: Prot, 2: Deut, 3: Trit, 4: Mono
    fontScale: Float = 1.0f, // Fator de escala da fonte (Padrão 1.0)
    content: @Composable () -> Unit
) {
    // 1. Escolhe a Paleta de Cores
    val colorScheme = when (colorBlindMode) {
        1 -> ProtanopiaScheme
        2 -> DeuteranopiaScheme
        3 -> TritanopiaScheme
        4 -> MonoScheme
        else -> if (darkTheme) NormalDark else NormalLight
    }

    // 2. Configura a Barra de Status do Android
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // Se for modo daltônico (geralmente fundo claro) ou Light Mode, ícones escuros
            val isLightStatus = colorBlindMode != 0 || !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatus
        }
    }

    // 3. Aplica o MaterialTheme com a Tipografia Dinâmica
    MaterialTheme(
        colorScheme = colorScheme,
        typography = getTypography(fontScale), // CHAMA A FUNÇÃO NOVA
        content = content
    )
}