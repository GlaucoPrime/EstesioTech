package com.code.EstesioTech.ui.theme

import androidx.compose.ui.graphics.Color

// --- PADRÃO (Normal) ---
val DarkPrimary = Color(0xFF00ACC1) // Cyan
val DarkBackground = Color(0xFF101820)
val DarkSurface = Color(0xFF1A2634)
val DarkText = Color(0xFFFFFFFF)

val LightPrimary = Color(0xFFD32F2F) // Vermelho
val LightSecondary = Color(0xFFFBC02D) // Amarelo
val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightText = Color(0xFF212121)

// --- PROTANOPIA (Deficiência no Vermelho) ---
// Evita vermelhos/verdes. Usa Azuis e Amarelos fortes.
val ProtPrimary = Color(0xFF4B0092) // Roxo/Azul Escuro (substitui vermelho)
val ProtSecondary = Color(0xFFFFC20A) // Amarelo Ouro
val ProtBackground = Color(0xFFE6E6E6)
val ProtSurface = Color(0xFFFFFFFF)
val ProtText = Color(0xFF000000)

// --- DEUTERANOPIA (Deficiência no Verde) ---
// Foco em Azul Profundo e Laranja/Dourado (Paleta Okabe-Ito adaptada)
val DeutPrimary = Color(0xFF004D40) // Teal Escuro (bem distinto)
val DeutSecondary = Color(0xFFFFC107) // Amber
val DeutBackground = Color(0xFFF0F0F0)
val DeutSurface = Color(0xFFFFFFFF)
val DeutText = Color(0xFF000000)

// --- TRITANOPIA (Deficiência no Azul) ---
// Foco em Vermelho/Rosa e Turquesa/Ciano (evita azul puro e amarelo puro misturados)
val TritPrimary = Color(0xFFD55E00) // Vermillion (Laranja Avermelhado)
val TritSecondary = Color(0xFF009E73) // Verde Azulado
val TritBackground = Color(0xFFEEEEEE)
val TritSurface = Color(0xFFFFFFFF)
val TritText = Color(0xFF000000)

// --- MONOCROMACIA (Total) ---
// Contraste puro
val MonoPrimary = Color(0xFF000000) // Preto
val MonoSecondary = Color(0xFF757575) // Cinza
val MonoBackground = Color(0xFFE0E0E0)
val MonoSurface = Color(0xFFFFFFFF)
val MonoText = Color(0xFF000000)