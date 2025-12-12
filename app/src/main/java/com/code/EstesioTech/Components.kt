package com.code.EstesioTech

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val techColor = Color(0xFF00ACC1)
    val glassColor = Color.Black.copy(alpha = 0.2f)

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            // TRAVA DE SEGURANÇA: Se for campo numérico, só aceita dígitos
            if (keyboardType == KeyboardType.Number) {
                if (newValue.all { it.isDigit() }) {
                    onValueChange(newValue)
                }
            } else {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = techColor) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = techColor,
            unfocusedBorderColor = Color.Gray,
            focusedLabelColor = techColor,
            unfocusedLabelColor = Color.Gray,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = techColor,
            focusedContainerColor = glassColor,
            unfocusedContainerColor = glassColor
        ),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechStateDropdown(
    selectedState: String,
    onStateSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var statesList by remember { mutableStateOf<List<IbgeState>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        statesList = IbgeProvider.getBrazilianStates()
        isLoading = false
    }

    val techColor = Color(0xFF00ACC1)
    val glassColor = Color.Black.copy(alpha = 0.2f)
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow")

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (selectedState.isEmpty()) "" else selectedState,
            onValueChange = {},
            readOnly = true,
            label = { Text("UF") },
            placeholder = { Text("UF") },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = techColor, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expandir",
                        tint = if (expanded) techColor else Color.Gray,
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = techColor,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = techColor,
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = glassColor,
                unfocusedContainerColor = glassColor
            )
        )

        Box(modifier = Modifier.matchParentSize().clickable { if (!isLoading) expanded = !expanded })

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A2634)).fillMaxWidth(0.9f).heightIn(max = 250.dp)
        ) {
            statesList.forEach { state ->
                DropdownMenuItem(
                    text = { Text("${state.sigla} - ${state.nome}", color = Color.White) },
                    onClick = {
                        onStateSelected(state.sigla)
                        expanded = false
                    },
                    colors = MenuDefaults.itemColors(textColor = Color.White, leadingIconColor = techColor)
                )
            }
        }
    }
}