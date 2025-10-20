package com.ips.dataacquisition.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ips.dataacquisition.data.model.ButtonAction
import com.ips.dataacquisition.data.model.ButtonPress
import com.ips.dataacquisition.data.model.Session
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    activeSession: Session?,
    buttonPresses: List<ButtonPress>,
    availableActions: List<ButtonAction>,
    isLoading: Boolean,
    isOnline: Boolean,
    isCollectingData: Boolean,
    samplesCollected: Long,
    pendingSyncCount: Int,
    errorMessage: String?,
    showFloorDialog: Boolean,
    pendingAction: ButtonAction?,
    showSuccessMessage: Boolean,
    onButtonPress: (ButtonAction) -> Unit,
    onFloorSelected: (Int) -> Unit,
    onDismissFloorDialog: () -> Unit,
    onToggleOnline: () -> Unit,
    onClearError: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Online/Offline Toggle
            OnlineToggleCard(isOnline, onToggleOnline)

            Spacer(modifier = Modifier.height(12.dp))

            // Sensor Status Indicators (ALWAYS VISIBLE)
            SensorStatusCard(
                isOnline = isOnline,
                isCollectingData = isCollectingData,
                samplesCollected = samplesCollected,
                pendingSyncCount = pendingSyncCount
            )
            Spacer(modifier = Modifier.height(12.dp))

            Spacer(modifier = Modifier.height(4.dp))

            // Session Info
            SessionInfoCard(activeSession, buttonPresses)

            Spacer(modifier = Modifier.height(24.dp))

            // Available Actions
            Text(
                text = "Available Actions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Button Grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(availableActions) { action ->
                        Button(
                            onClick = { onButtonPress(action) },
                            enabled = isOnline && !isLoading,  // Only enabled when online
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOnline)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = action.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Error Message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

        // Floor Selection Dialog (at Box level, not Column)
        if (showFloorDialog && pendingAction != null) {
            com.ips.dataacquisition.ui.components.FloorSelectionDialog(
                action = pendingAction,
                onFloorSelected = onFloorSelected,
                onDismiss = onDismissFloorDialog
            )
        }
        
        // Session Complete Success Dialog
        if (showSuccessMessage) {
            com.ips.dataacquisition.ui.components.SessionCompleteDialog()
        }
    }
}
}

        @Composable
        fun OnlineToggleCard(
            isOnline: Boolean,
            onToggle: () -> Unit
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOnline)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOnline) "You are ONLINE" else "You are OFFLINE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isOnline)
                                "Collecting data when you walk/stand"
                            else
                                "No data collection - tap to go online",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOnline)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Switch(
                        checked = isOnline,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }

        @Composable
        fun SensorStatusCard(
            isOnline: Boolean,
            isCollectingData: Boolean,
            samplesCollected: Long,
            pendingSyncCount: Int
        ) {
            // Determine sensor status
            val sensorStatus = when {
                !isOnline -> "STOPPED"
                isCollectingData -> "RUNNING"
                else -> "IDLE"
            }

            // Determine status color
            val statusColor = when (sensorStatus) {
                "RUNNING" -> MaterialTheme.colorScheme.primary
                "IDLE" -> MaterialTheme.colorScheme.outline
                "STOPPED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }

            // Determine card background color
            val backgroundColor = when (sensorStatus) {
                "RUNNING" -> MaterialTheme.colorScheme.primaryContainer
                "IDLE" -> MaterialTheme.colorScheme.surfaceVariant
                "STOPPED" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = backgroundColor
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(
                        text = "📡 Sensors Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (sensorStatus) {
                            "RUNNING" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "IDLE" -> MaterialTheme.colorScheme.onSurfaceVariant
                            "STOPPED" -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status Badge
                    Text(
                        text = sensorStatus,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status Description
                    Text(
                        text = when (sensorStatus) {
                            "RUNNING" -> "Capturing & syncing data (<10 km/h)"
                            "IDLE" -> "GPS monitoring (speed >15 km/h)"
                            "STOPPED" -> "User offline - no data collection"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (sensorStatus) {
                            "RUNNING" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "IDLE" -> MaterialTheme.colorScheme.onSurfaceVariant
                            "STOPPED" -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    // Show pending sync only when stopped
                    if (sensorStatus == "STOPPED") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (pendingSyncCount > 0) {
                                "$pendingSyncCount records pending sync"
                            } else {
                                "All data synced ✓"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        @Composable
        fun SessionInfoCard(
            activeSession: Session?,
            buttonPresses: List<ButtonPress>
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSession != null)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (activeSession != null) "Active Session" else "No Active Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (activeSession != null)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (activeSession != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        val formatter =
                            SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                        Text(
                            text = "Started: ${formatter.format(Date(activeSession.startTimestamp))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Steps Completed: ${buttonPresses.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (buttonPresses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Recent Steps:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            buttonPresses.takeLast(3).forEach { press ->
                                val timeFormatter =
                                    SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${ButtonAction.valueOf(press.action).displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = timeFormatter.format(Date(press.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                            alpha = 0.7f
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }


