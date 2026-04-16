package com.code.EstesioTech

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// TechTextField - campo de texto padrao do app
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int = Int.MAX_VALUE
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val filtered = when (keyboardType) {
                KeyboardType.Number, KeyboardType.Phone -> newValue.filter { it.isDigit() }
                else -> newValue
            }
            if (filtered.length <= maxLength) onValueChange(filtered)
        },
        label = { Text(label, color = colors.onSurfaceVariant) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = colors.primary) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline.copy(alpha = 0.5f),
            focusedLabelColor = colors.primary,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            cursorColor = colors.primary,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface
        )
    )
}

// TechStateDropdown - dropdown de UF com IBGE e animacao
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechStateDropdown(
    selectedState: String,
    onStateSelected: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by remember { mutableStateOf(false) }
    var statesList by remember { mutableStateOf<List<IbgeState>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        statesList = IbgeProvider.getBrazilianStates()
        isLoading = false
    }

    val colors = MaterialTheme.colorScheme
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "arrow"
    )

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!isLoading) expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = selectedState,
            onValueChange = {},
            readOnly = true,
            label = { Text("UF", color = colors.onSurfaceVariant) },
            trailingIcon = {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                else Icon(Icons.Default.ArrowDropDown, null, tint = if (expanded) colors.primary else colors.onSurfaceVariant, modifier = Modifier.rotate(arrowRotation))
            },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outline.copy(alpha = 0.5f),
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface
            )
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(colors.surface).heightIn(max = 280.dp)) {
            statesList.forEach { state ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.sigla, color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(36.dp))
                            Text(state.nome, color = colors.onSurface, fontSize = 13.sp)
                        }
                    },
                    onClick = { onStateSelected(state.sigla); expanded = false },
                    modifier = if (state.sigla == selectedState) Modifier.background(colors.primaryContainer.copy(alpha = 0.3f)) else Modifier
                )
            }
        }
    }
}

// RiskBadge - badge colorido de nivel de risco
@Composable
fun RiskBadge(riskLevel: Int, modifier: Modifier = Modifier) {
    val (label, bgColor) = when (riskLevel) {
        0 -> "Sem Risco" to Color(0xFF1B5E20)
        1 -> "Risco Mod." to Color(0xFFE65100)
        else -> "Alto Risco" to Color(0xFFB71C1C)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = 0.15f))
            .border(1.dp, bgColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = bgColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
