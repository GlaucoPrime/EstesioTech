package com.code.EstesioTech.ui.screens

import android.content.Context
import com.code.EstesioTech.ui.screens.HomeActivity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.code.EstesioTech.R
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.TechStateDropdown
import com.code.EstesioTech.TechTextField
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils

/**
 * Tela de login do EstesioTech.
 * Design: fundo escuro com efeito glassmorphism no card de login.
 */
class LoginActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login se usuario ja estiver autenticado
        if (EstesioCloud.isUserLoggedIn()) {
            goToHome(); return
        }

        val p = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        setContent {
            EstesioTechTheme(darkTheme = p.getBoolean("dark_theme", true), colorBlindMode = p.getInt("color_blind_mode", 0)) {
                LoginScreen(
                    onLoginSuccess  = { goToHome() },
                    onNavigateToReg = { startActivity(Intent(this, RegisterActivity::class.java)) }
                )
            }
        }
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToReg: () -> Unit
) {
    var crm        by remember { mutableStateOf("") }
    var uf         by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var isLoading  by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }

    val colors    = MaterialTheme.colorScheme

    val infiniteAnim = rememberInfiniteTransition(label = "bg")
    val bgAlpha by infiniteAnim.animateFloat(0.04f, 0.1f,
        infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "bg")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(
                colors  = listOf(colors.primary.copy(bgAlpha), colors.background),
                radius  = 800f
            )),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth(0.88f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // Logo / Titulo
            Icon(
                Icons.Default.MedicalServices,
                null,
                tint = colors.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "EstesioTech",
                fontSize   = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = colors.onBackground
            )
            Text(
                stringResource(R.string.login_title),
                fontSize = 15.sp,
                color    = colors.onBackground.copy(0.55f)
            )

            Spacer(Modifier.height(40.dp))

            // Card de login
            Card(
                shape  = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TechTextField(crm, { crm = it }, stringResource(R.string.crm_hint), Icons.Default.Badge, keyboardType = KeyboardType.Number, maxLength = 7)
                    TechStateDropdown(selectedState = uf, onStateSelected = { uf = it })
                    TechTextField(password, { password = it }, stringResource(R.string.password_hint), Icons.Default.Lock, isPassword = true)

                    AnimatedVisibility(errorMsg != null) {
                        Surface(shape = RoundedCornerShape(8.dp), color = colors.errorContainer) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = colors.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(errorMsg ?: "", color = colors.onErrorContainer, fontSize = 13.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            errorMsg  = null
                            isLoading = true
                            EstesioCloud.login(crm.trim(), uf, password,
                                onSuccess = { isLoading = false; onLoginSuccess() },
                                onError   = { isLoading = false; errorMsg = it }
                            )
                        },
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        if (isLoading) CircularProgressIndicator(color = colors.onPrimary, modifier = Modifier.size(22.dp))
                        else Text(stringResource(R.string.login_button), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }

                    TextButton(onClick = onNavigateToReg, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.new_here), color = colors.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    EstesioTechTheme(darkTheme = true) {
        LoginScreen({}, {})
    }
}