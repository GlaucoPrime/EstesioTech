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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val rememberMe = sharedPref.getBoolean("remember_me", false)

        if (EstesioCloud.isUserLoggedIn() && rememberMe) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContent {
            EstesioTechTheme {
                LoginScreen(
                    onLoginClick = { crm, uf, password, isRemember ->
                        if (crm.isNotEmpty() && uf.isNotEmpty() && password.isNotEmpty()) {
                            Toast.makeText(this, "Autenticando...", Toast.LENGTH_SHORT).show()

                            EstesioCloud.login(crm, uf, password,
                                onSuccess = {
                                    with (sharedPref.edit()) {
                                        putBoolean("remember_me", isRemember)
                                        apply()
                                    }
                                    startActivity(Intent(this, HomeActivity::class.java))
                                    finish()
                                },
                                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                            )
                        } else {
                            Toast.makeText(this, "Preencha todos os campos.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRegisterClick = { startActivity(Intent(this, RegisterActivity::class.java)) }
                )
            }
        }
    }
}

// --- API IBGE (Versão Privada para Login) ---
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
fun LoginScreen(onLoginClick: (String, String, String, Boolean) -> Unit, onRegisterClick: () -> Unit) {
    var crm by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    // Estados UF
    var ufList by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val list = fetchStatesLogin()
        val fallbackList = listOf("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO")
        ufList = if (list.isNotEmpty()) list else fallbackList
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634).copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ESTESIOTECH", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00ACC1), letterSpacing = 2.sp)
                Text("Acesso Profissional", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 32.dp))

                LoginTextField(crm, { crm = it }, "CRM", Icons.Default.Badge, keyboardType = KeyboardType.Number)
                Spacer(modifier = Modifier.height(12.dp))

                LoginStateDropdown(selectedState = uf, items = ufList, onStateSelected = { uf = it })

                Spacer(modifier = Modifier.height(12.dp))
                LoginTextField(password, { password = it }, "Senha", Icons.Default.Lock, isPassword = true)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00ACC1))
                        )
                        Text("Lembrar", color = Color.Gray, fontSize = 12.sp)
                    }
                    Text(
                        "Esqueci a senha",
                        color = Color(0xFF80DEEA),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { showForgotPassword = true }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onLoginClick(crm, uf, password, rememberMe) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, Color(0xFF00ACC1).copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1).copy(alpha = 0.2f), contentColor = Color(0xFF00ACC1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ACESSAR", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Novo por aqui? Criar conta", color = Color(0xFF80DEEA), modifier = Modifier.clickable { onRegisterClick() }, fontSize = 14.sp)
            }
        }
    }

    // Passa a lista de UFs para o Dialog
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

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A242E))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Recuperar Senha", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (sent) {
                    Text("Solicitação enviada! Se o CRM estiver correto, você receberá um e-mail.", color = Color.Green, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismiss) { Text("Fechar") }
                } else {
                    Text("Informe seus dados para buscar o cadastro.", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    LoginTextField(crm, { crm = it }, "CRM", Icons.Default.Badge, keyboardType = KeyboardType.Number)
                    Spacer(modifier = Modifier.height(8.dp))
                    LoginStateDropdown(selectedState = uf, items = ufList, onStateSelected = { uf = it })

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if(crm.isNotEmpty() && uf.isNotEmpty()) {
                                EstesioCloud.sendPasswordResetByCrm(crm, uf, onSuccess = { sent = true }, onError = {})
                                sent = true // Feedback visual imediato
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                    ) {
                        Text("Buscar e Enviar")
                    }
                }
            }
        }
    }
}

// Componentes Privados para Login
@Composable
private fun LoginTextField(
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
private fun LoginStateDropdown(selectedState: String, items: List<String>, onStateSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedState,
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
            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                    onClick = { onStateSelected(item); expanded = false }
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    com.code.EstesioTech.ui.theme.EstesioTechTheme {
        LoginScreen(onLoginClick = { _, _, _, _ -> }, onRegisterClick = {})
    }
}