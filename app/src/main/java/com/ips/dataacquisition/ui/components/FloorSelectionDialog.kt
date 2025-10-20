package com.ips.dataacquisition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ips.dataacquisition.data.model.ButtonAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloorSelectionDialog(
    action: ButtonAction,
    onFloorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFloor by remember { mutableStateOf<Int?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Select Floor Number")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "For: ${action.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Floor selection dropdown
                var expanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedFloor?.toString() ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Floor Number") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        // Basement floors (-2 to -1)
                        (-2..-1).forEach { floor ->
                            DropdownMenuItem(
                                text = { Text("B${-floor}") },
                                onClick = {
                                    selectedFloor = floor
                                    expanded = false
                                }
                            )
                        }
                        
                        // Ground floor
                        DropdownMenuItem(
                            text = { Text("Ground (0)") },
                            onClick = {
                                selectedFloor = 0
                                expanded = false
                            }
                        )
                        
                        // Upper floors (1 to 50)
                        (1..50).forEach { floor ->
                            DropdownMenuItem(
                                text = { Text("Floor $floor") },
                                onClick = {
                                    selectedFloor = floor
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedFloor?.let { onFloorSelected(it) }
                },
                enabled = selectedFloor != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

