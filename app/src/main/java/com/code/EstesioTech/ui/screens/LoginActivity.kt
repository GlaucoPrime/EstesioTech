package com.code.EstesioTech.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.code.EstesioTech.R
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class LoginActivity : ComponentActivity() {

    // Variáveis de controle para impedir o loop infinito do recreate()
    private var lastLocale: String? = null
    private var lastTheme: Boolean = true
    private var lastColorMode: Int = 0
    private var lastFontScale: Float = 1.0f

    // Força o idioma nativamente antes da tela nascer
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val langCode = prefs.getString("language", "pt") ?: "pt"
        val locale = Locale(langCode)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val rememberMe = prefs.getBoolean("remember_me", false)

        // Salva o estado atual ao iniciar para não recarregar atoa
        lastLocale = prefs.getString("language", "pt")
        lastTheme = prefs.getBoolean("dark_theme", true)
        lastColorMode = prefs.getInt("color_blind_mode", 0)
        lastFontScale = prefs.getFloat("font_scale", 1.0f)

        if (EstesioCloud.isUserLoggedIn() && rememberMe) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        loadUI()
    }

    override fun onResume() {
        super.onResume()
        // Checa se o usuário voltou das configurações com alguma mudança real
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        if (lastLocale != prefs.getString("language", "pt") ||
            lastTheme != prefs.getBoolean("dark_theme", true) ||
            lastColorMode != prefs.getInt("color_blind_mode", 0) ||
            lastFontScale != prefs.getFloat("font_scale", 1.0f)
        ) {
            recreate() // Apenas recria se algo mudou! Evita a tela piscando em loop.
        }
    }

    private fun loadUI() {
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode, fontScale = fontScale) {
                var isLoading by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        isLoading = isLoading,
                        onLoginClick = { crm, uf, password, isRemember ->
                            if (crm.isNotEmpty() && uf.isNotEmpty() && password.isNotEmpty()) {
                                isLoading = true
                                EstesioCloud.login(crm, uf, password,
                                    onSuccess = {
                                        with (prefs.edit()) {
                                            putBoolean("remember_me", isRemember)
                                            apply()
                                        }
                                        isLoading = false
                                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                                        finish()
                                    },
                                    onError = { msg ->
                                        isLoading = false
                                        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                Toast.makeText(this@LoginActivity, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRegisterClick = {
                            startActivity(Intent(this@LoginActivity, RegisterActivity::class.java))
                        },
                        onSettingsClick = {
                            startActivity(Intent(this@LoginActivity, SettingsActivity::class.java))
                        }
                    )

                    // Overlay de Carregamento
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable(enabled = false) {},
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(stringResource(R.string.authenticating), color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun fetchStatesLogin(): List<String> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://servicodados.ibge.gov.br/api/v1/localidades/estados?orderBy=nome")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000

        if (connection.responseCode == 200) {
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val ufs = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) ufs.add(jsonArray.getJSONObject(i).getString("sigla"))
            ufs.sorted()
        } else { emptyList() }
    } catch (e: Exception) { emptyList() }
}

@Composable
fun LoginScreen(isLoading: Boolean, onLoginClick: (String, String, String, Boolean) -> Unit, onRegisterClick: () -> Unit, onSettingsClick: () -> Unit) {
    var crm by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    var ufList by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val list = fetchStatesLogin()
        val fallbackList = listOf("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO")
        ufList = if (list.isNotEmpty()) list else fallbackList
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(colors.background, colors.surface))),
        contentAlignment = Alignment.Center
    ) {
        // Ícone de Configurações no topo
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = colors.onBackground)
        }

        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.app_name), style = typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.primary)
                Text(stringResource(R.string.login_title), style = typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 32.dp))

                LoginTextField(crm, { crm = it }, stringResource(R.string.crm_hint), Icons.Default.Badge, keyboardType = KeyboardType.Number)
                Spacer(modifier = Modifier.height(12.dp))

                LoginStateDropdown(selectedState = uf, items = ufList, onStateSelected = { uf = it })

                Spacer(modifier = Modifier.height(12.dp))
                LoginTextField(password, { password = it }, stringResource(R.string.password_hint), Icons.Default.Lock, isPassword = true)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primary)
                        )
                        Text(stringResource(R.string.remember_me), color = colors.onSurfaceVariant, style = typography.labelSmall)
                    }
                    Text(
                        stringResource(R.string.forgot_password),
                        color = colors.primary,
                        style = typography.labelSmall,
                        modifier = Modifier.clickable { showForgotPassword = true }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onLoginClick(crm, uf, password, rememberMe) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha = 0.2f), contentColor = colors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.login_button), style = typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.new_here), color = colors.primary, modifier = Modifier.clickable { onRegisterClick() }, style = typography.bodyMedium)
            }
        }
    }

    if (showForgotPassword) {
        ForgotPasswordDialog(ufList = ufList, onDismiss = { showForgotPassword = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordDialog(ufList: List<String>, onDismiss: () -> Unit) {
    var crm by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.forgot_password), color = colors.onSurface, style = typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (sent) {
                    Text("Solicitação enviada!", color = Color.Green, style = typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) { Text(stringResource(
                        R.string.cancel)) }
                } else {
                    LoginTextField(crm, { crm = it }, stringResource(R.string.crm_hint), Icons.Default.Badge, keyboardType = KeyboardType.Number)
                    Spacer(modifier = Modifier.height(8.dp))
                    LoginStateDropdown(selectedState = uf, items = ufList, onStateSelected = { uf = it })
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if(crm.isNotEmpty() && uf.isNotEmpty()) {
                                isLoading = true
                                EstesioCloud.sendPasswordResetByCrm(crm, uf,
                                    onSuccess = {
                                        isLoading = false
                                        sent = true
                                    },
                                    onError = { error ->
                                        isLoading = false
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        if (isLoading) CircularProgressIndicator(color = colors.onPrimary, modifier = Modifier.size(24.dp))
                        else Text("Enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginTextField(
    value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth(), isPassword: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.onSurfaceVariant) },
        leadingIcon = { Icon(icon, null, tint = colors.primary) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            cursorColor = colors.primary
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginStateDropdown(selectedState: String, items: List<String>, onStateSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedState,
            onValueChange = {},
            readOnly = true,
            label = { Text("UF", color = colors.onSurfaceVariant) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface
            ),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colors.surface)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, color = colors.onSurface) },
                    onClick = { onStateSelected(item); expanded = false }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    EstesioTechTheme {
        LoginScreen(isLoading = false, onLoginClick = { _, _, _, _ -> }, onRegisterClick = {}, onSettingsClick = {})
    }
}