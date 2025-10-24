package ai.indoorbrain.ui.components


import ai.indoorbrain.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.indoorbrain.data.model.ButtonAction
import ai.indoorbrain.data.model.localizedName
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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

