package com.code.EstesioTech

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EstesioTechTheme {
                RegisterScreen(
                    onRegisterClick = { name, crm, uf, pass, confirm ->
                        if (name.isEmpty() || crm.isEmpty() || uf.isEmpty() || pass.isEmpty()) {
                            Toast.makeText(this, "Preencha todos os campos!", Toast.LENGTH_SHORT).show()
                        } else if (pass != confirm) {
                            Toast.makeText(this, "As senhas não conferem.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Criando conta...", Toast.LENGTH_SHORT).show()

                            // Agora passamos o UF também
                            EstesioCloud.register(crm, uf, pass, name,
                                onSuccess = {
                                    Toast.makeText(this, "Bem-vindo(a), $name!", Toast.LENGTH_LONG).show()
                                    finish()
                                },
                                onError = { msg ->
                                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }
}

@Composable
fun RegisterScreen(
    onRegisterClick: (String, String, String, String, String) -> Unit,
    onBackClick: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var crm by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") } // Estado selecionado
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF0F2027), Color(0xFF2C5364)))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634).copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("NOVO USUÁRIO", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(24.dp))

                TechTextField(name, { name = it }, "Nome Completo", Icons.Default.Person)
                Spacer(modifier = Modifier.height(12.dp))

                // Linha com CRM e UF lado a lado
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1.5f)) {
                        TechTextField(crm, { crm = it }, "CRM", Icons.Default.Badge, keyboardType = KeyboardType.Number)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        TechStateDropdown(selectedState = uf, onStateSelected = { uf = it })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TechTextField(password, { password = it }, "Senha", Icons.Default.Lock, isPassword = true)
                Spacer(modifier = Modifier.height(12.dp))

                TechTextField(confirmPassword, { confirmPassword = it }, "Confirmar Senha", Icons.Default.CheckCircle, isPassword = true)
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { onRegisterClick(name, crm, uf, password, confirmPassword) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CRIAR CONTA", fontWeight = FontWeight.Bold, color = Color.White)
                }

                TextButton(onClick = onBackClick, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun RegisterScreenPreview() {
    com.code.EstesioTech.ui.theme.EstesioTechTheme {
        RegisterScreen(onRegisterClick = { _, _, _, _, _ -> }, onBackClick = {})
    }
}