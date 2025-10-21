package com.ips.dataacquisition.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ips.dataacquisition.R
import com.ips.dataacquisition.data.model.ButtonAction
import com.ips.dataacquisition.data.model.localizedName

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
            Text(text = stringResource(R.string.select_floor))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.floor_for, action.localizedName()),
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
                        label = { Text(stringResource(R.string.floor_number)) },
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
                                text = { Text(stringResource(R.string.basement_floor, -floor)) },
                                onClick = {
                                    selectedFloor = floor
                                    expanded = false
                                }
                            )
                        }
                        
                        // Ground floor
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ground_floor)) },
                            onClick = {
                                selectedFloor = 0
                                expanded = false
                            }
                        )
                        
                        // Upper floors (1 to 50)
                        (1..50).forEach { floor ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.floor, floor)) },
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
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

