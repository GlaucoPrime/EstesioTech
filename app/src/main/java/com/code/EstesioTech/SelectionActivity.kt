package com.code.EstesioTech

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.code.EstesioTech.ui.theme.EstesioTechTheme

class SelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")

        setContent {
            EstesioTechTheme {
                SelectionScreen(
                    onPartSelected = { part ->
                        val intent = Intent(this, TesteActivity::class.java).apply {
                            putExtra("DEVICE_ADDRESS", deviceAddress)
                            putExtra("BODY_PART", part)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

// CRIEI ESTE COMPONENTE SEPARADO PARA PODERMOS DAR PREVIEW
@Composable
fun SelectionScreen(onPartSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
            .padding(24.dp)
    ) {
        Text("MAPEAMENTO", color = Color(0xFF00ACC1), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Selecione a Região", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(32.dp))

        // Grid Mãos
        Row(Modifier.weight(1f)) {
            AnatomyCard("Mão\nDireita", "R", Color(0xFF2196F3), Modifier.weight(1f)) {
                onPartSelected("mao_direita")
            }
            Spacer(Modifier.width(16.dp))
            AnatomyCard("Mão\nEsquerda", "L", Color(0xFF2196F3), Modifier.weight(1f)) {
                onPartSelected("mao_esquerda")
            }
        }
        Spacer(Modifier.height(16.dp))

        // Grid Pés
        Row(Modifier.weight(1f)) {
            AnatomyCard("Pé\nDireito", "R", Color(0xFFFF9800), Modifier.weight(1f)) {
                onPartSelected("pe_direito")
            }
            Spacer(Modifier.width(16.dp))
            AnatomyCard("Pé\nEsquerdo", "L", Color(0xFFFF9800), Modifier.weight(1f)) {
                onPartSelected("pe_esquerdo")
            }
        }
    }
}

@Composable
fun AnatomyCard(title: String, side: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.fillMaxSize().clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = side,
                fontSize = 100.sp,
                fontWeight = FontWeight.Black,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 10.dp)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(side, color = color, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Iniciar Teste >", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// --- PREVIEW DO SELETOR ---
@Preview(showBackground = true)
@Composable
fun SelectionScreenPreview() {
    EstesioTechTheme {
        SelectionScreen(onPartSelected = {})
    }
}