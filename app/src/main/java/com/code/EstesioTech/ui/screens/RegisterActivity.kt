package com.code.EstesioTech.ui.screens

import android.content.Context
import com.code.EstesioTech.R
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.TechStateDropdown
import com.code.EstesioTech.TechTextField
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils

class RegisterActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        setContent {
            EstesioTechTheme(darkTheme = p.getBoolean("dark_theme", true), colorBlindMode = p.getInt("color_blind_mode", 0)) {
                RegisterScreen(onSuccess = { finish() }, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onSuccess: () -> Unit, onBack: () -> Unit) {
    var name       by remember { mutableStateOf("") }
    var crm        by remember { mutableStateOf("") }
    var uf         by remember { mutableStateOf("") }
    var email      by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var confirm    by remember { mutableStateOf("") }
    var isLoading  by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    val colors     = MaterialTheme.colorScheme
    val context    = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.register_button), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.PersonAdd, null, tint = colors.primary, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
            Text("Dados Profissionais", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onBackground)

            TechTextField(name, { name = it }, stringResource(R.string.name_hint), Icons.Default.Person)
            TechTextField(email, { email = it }, "E-mail de recuperação", Icons.Default.Email, keyboardType = KeyboardType.Email)
            TechTextField(crm, { crm = it }, stringResource(R.string.crm_hint), Icons.Default.Badge, keyboardType = KeyboardType.Number, maxLength = 7)
            TechStateDropdown(selectedState = uf, onStateSelected = { uf = it })

            Divider(color = colors.outlineVariant)
            Text("Senha de Acesso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onBackground)

            TechTextField(password, { password = it }, stringResource(R.string.password_hint), Icons.Default.Lock, isPassword = true)
            TechTextField(confirm, { confirm = it }, stringResource(R.string.confirm_password_hint), Icons.Default.Lock, isPassword = true)

            errorMsg?.let {
                Surface(shape = RoundedCornerShape(8.dp), color = colors.errorContainer) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = colors.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = colors.onErrorContainer, fontSize = 13.sp)
                    }
                }
            }

            Button(
                onClick = {
                    if (password != confirm) { errorMsg = "Senhas nao coincidem."; return@Button }
                    if (crm.isBlank() || uf.isBlank() || name.isBlank()) { errorMsg = "Preencha todos os campos."; return@Button }
                    errorMsg  = null
                    isLoading = true
                    EstesioCloud.register(
                        crm           = crm.trim(),
                        uf            = uf,
                        pass          = password,
                        name          = name.trim(),
                        recoveryEmail = email.trim(),
                        onSuccess     = { isLoading = false; Toast.makeText(context, "Conta criada!", Toast.LENGTH_SHORT).show(); onSuccess() },
                        onError       = { isLoading = false; errorMsg = it }
                    )
                },
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) CircularProgressIndicator(color = colors.onPrimary, modifier = Modifier.size(22.dp))
                else Text(stringResource(R.string.register_button), fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview
@Composable
fun RegisterPreview() {
    EstesioTechTheme(darkTheme = true) { RegisterScreen({}, {}) }
}