package com.code.EstesioTech.ui.screens

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.R
import com.code.EstesioTech.TechTextField
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class RegisterActivity : ComponentActivity() {

    // Variáveis de controlo para impedir o ciclo infinito do recreate()
    private var lastLocale: String? = null
    private var lastTheme: Boolean = true
    private var lastColorMode: Int = 0
    private var lastFontScale: Float = 1.0f

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

        // Guarda o estado atual ao iniciar para não recarregar à toa
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        lastLocale = prefs.getString("language", "pt")
        lastTheme = prefs.getBoolean("dark_theme", true)
        lastColorMode = prefs.getInt("color_blind_mode", 0)
        lastFontScale = prefs.getFloat("font_scale", 1.0f)

        loadUI()
    }

    override fun onResume() {
        super.onResume()
        // Verifica se o utilizador voltou das configurações com alguma alteração real
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        if (lastLocale != prefs.getString("language", "pt") ||
            lastTheme != prefs.getBoolean("dark_theme", true) ||
            lastColorMode != prefs.getInt("color_blind_mode", 0) ||
            lastFontScale != prefs.getFloat("font_scale", 1.0f)
        ) {
            recreate() // Apenas recria se algo mudou! Evita a tela a piscar.
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
                    RegisterScreen(
                        isLoading = isLoading,
                        onRegister = { name, crm, uf, recoveryEmail, pass ->
                            isLoading = true
                            EstesioCloud.register(
                                crm = crm, uf = uf, pass = pass, name = name, recoveryEmail = recoveryEmail,
                                onSuccess = {
                                    isLoading = false
                                    Toast.makeText(this@RegisterActivity, "Conta criada! Faça login.", Toast.LENGTH_LONG).show()
                                    finish()
                                },
                                onError = { error ->
                                    isLoading = false
                                    Toast.makeText(this@RegisterActivity, error, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        onBack = { finish() }
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
                                Text("Criando conta...", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun fetchStatesRegister(): List<String> = withContext(Dispatchers.IO) {
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
fun RegisterScreen(isLoading: Boolean, onRegister: (String, String, String, String, String) -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var crm by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val context = LocalContext.current

    // Estados UF com Fallback Completo
    var ufList by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val list = fetchStatesRegister()
        val fallbackList = listOf("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO")
        ufList = if (list.isNotEmpty()) list else fallbackList
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(colors.background, colors.surface))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.register_button), style = typography.headlineSmall, fontWeight = FontWeight.Bold, color = colors.primary, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(24.dp))

                TechTextField(
                    name,
                    { name = it },
                    stringResource(R.string.name_hint),
                    Icons.Default.Person
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth()) {
                    TechTextField(crm, { crm = it }, stringResource(R.string.crm_hint), Icons.Default.Badge, KeyboardType.Number, Modifier.weight(0.6f))
                    Spacer(Modifier.width(8.dp))
                    TechDropdownUF(uf, ufList) { uf = it }
                }
                Spacer(modifier = Modifier.height(12.dp))

                TechTextField(email, { email = it }, stringResource(R.string.email_hint), Icons.Default.Email, KeyboardType.Email)
                Spacer(modifier = Modifier.height(12.dp))

                TechTextField(
                    password,
                    { password = it },
                    stringResource(R.string.password_hint),
                    Icons.Default.Lock,
                    isPassword = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                TechTextField(
                    confirmPassword,
                    { confirmPassword = it },
                    stringResource(R.string.confirm_password_hint),
                    Icons.Default.Lock,
                    isPassword = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (name.isNotEmpty() && crm.isNotEmpty() && uf.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                            if (password == confirmPassword) {
                                onRegister(name, crm, uf, email, password)
                            } else {
                                Toast.makeText(context, "As senhas não coincidem.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, colors.primary.copy(alpha=0.5f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary.copy(alpha=0.2f), contentColor = colors.primary),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.register_button), style = typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.login_button), color = colors.primary, modifier = Modifier.clickable { onBack() }, style = typography.bodyMedium)
            }
        }
    }
}

// --- Componentes Reutilizáveis Locais ---
@Composable
private fun TechTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isPassword: Boolean = false
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
private fun TechDropdownUF(selected: String, items: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.width(110.dp)
    ) {
        OutlinedTextField(
            value = selected,
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
            modifier = Modifier.menuAnchor(),
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
                    onClick = { onSelected(item); expanded = false }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    EstesioTechTheme {
        RegisterScreen(isLoading = false, onRegister = { _, _, _, _, _ -> }, onBack = {})
    }
}