package com.code.EstesioTech

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)

        setContent {
            // Respeita o tema escolhido
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode) {
                var isLoading by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    RegisterScreen(
                        isLoading = isLoading,
                        onRegister = { name, crm, uf, recoveryEmail, pass ->
                            isLoading = true
                            EstesioCloud.register(
                                crm = crm,
                                uf = uf,
                                pass = pass,
                                name = name,
                                recoveryEmail = recoveryEmail,
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
                        onBack = { finish() },
                        onSettingsClick = {
                            startActivity(Intent(this@RegisterActivity, SettingsActivity::class.java))
                        }
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable(enabled = false) {},
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF00ACC1))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Criando conta...", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
fun RegisterScreen(
    isLoading: Boolean,
    onRegister: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var crm by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var ufList by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val list = fetchStatesRegister()
        val fallbackList = listOf("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO")
        ufList = if (list.isNotEmpty()) list else fallbackList
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))),
        contentAlignment = Alignment.Center
    ) {
        // --- BOTÃO DE CONFIGURAÇÕES ---
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Acessibilidade", tint = Color.White)
        }

        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634).copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("CRIAR CONTA", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00ACC1), letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(24.dp))

                TechTextField(name, { name = it }, "Nome Completo", Icons.Default.Person)
                Spacer(modifier = Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth()) {
                    TechTextField(crm, { crm = it }, "CRM", Icons.Default.Badge, KeyboardType.Number, Modifier.weight(0.6f))
                    Spacer(Modifier.width(8.dp))
                    TechDropdownUF(uf, ufList) { uf = it }
                }
                Spacer(modifier = Modifier.height(12.dp))

                TechTextField(email, { email = it }, "E-mail", Icons.Default.Email, KeyboardType.Email)
                Spacer(modifier = Modifier.height(12.dp))

                TechTextField(password, { password = it }, "Senha", Icons.Default.Lock, isPassword = true)
                Spacer(modifier = Modifier.height(12.dp))
                TechTextField(confirmPassword, { confirmPassword = it }, "Confirmar Senha", Icons.Default.Lock, isPassword = true)

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (name.isNotEmpty() && crm.isNotEmpty() && uf.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                            if (password == confirmPassword) {
                                onRegister(name, crm, uf, email, password)
                            } else {
                                // Erro de senha
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, Color(0xFF00ACC1).copy(alpha=0.5f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1).copy(alpha=0.2f), contentColor = Color(0xFF00ACC1)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Text("CADASTRAR", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Já tem conta? Entrar", color = Color(0xFF80DEEA), modifier = Modifier.clickable { onBack() }, fontSize = 14.sp)
            }
        }
    }
}

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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.Gray) },
        leadingIcon = { Icon(icon, null, tint = Color(0xFF00ACC1)) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00ACC1),
            unfocusedBorderColor = Color.Gray.copy(alpha=0.5f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF00ACC1)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechDropdownUF(selected: String, items: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.width(110.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("UF", color = Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00ACC1),
                unfocusedBorderColor = Color.Gray.copy(alpha=0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A2634))
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, color = Color.White) },
                    onClick = { onSelected(item); expanded = false }
                )
            }
        }
    }
}

// --- PREVIEW ---
@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    EstesioTechTheme {
        RegisterScreen(
            isLoading = false,
            onRegister = { _, _, _, _, _ -> },
            onBack = {},
            onSettingsClick = {}
        )
    }
}