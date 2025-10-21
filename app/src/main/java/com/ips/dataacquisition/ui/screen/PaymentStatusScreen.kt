package com.ips.dataacquisition.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ips.dataacquisition.R
import com.ips.dataacquisition.data.model.PaymentStatus
import com.ips.dataacquisition.data.model.Session
import com.ips.dataacquisition.data.model.SessionStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentStatusScreen(
    sessions: List<Session>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            if (!isLoading && !isRefreshing) {
                FloatingActionButton(
                    onClick = onRefresh,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.no_sessions_yet),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.refresh))
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isRefreshing) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    
                    items(sessions) { session ->
                        SessionCard(session)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: Session) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Session ${session.sessionId.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                StatusChip(session.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Date
            val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            Text(
                text = dateFormatter.format(Date(session.startTimestamp)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            
            // Payment Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.payment_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                PaymentStatusChip(session.paymentStatus)
            }
            
            // Bonus Amount (if approved and paid)
            if (session.bonusAmount != null && session.bonusAmount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.bonus_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$${String.format("%.2f", session.bonusAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Remarks (if rejected)
            if (session.status == SessionStatus.REJECTED && session.remarks != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.remarks_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = session.remarks,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: SessionStatus) {
    val textRes = when (status) {
        SessionStatus.APPROVED -> R.string.status_approved
        SessionStatus.REJECTED -> R.string.status_rejected
        SessionStatus.COMPLETED -> R.string.status_pending
        SessionStatus.IN_PROGRESS -> R.string.status_in_progress
    }
    val color = when (status) {
        SessionStatus.APPROVED -> MaterialTheme.colorScheme.primary
        SessionStatus.REJECTED -> MaterialTheme.colorScheme.error
        SessionStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        SessionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    }
    
    AssistChip(
        onClick = { },
        label = { Text(stringResource(textRes), style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                imageVector = if (status == SessionStatus.APPROVED) 
                    Icons.Default.CheckCircle 
                else if (status == SessionStatus.REJECTED)
                    Icons.Default.Error
                else
                    Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.2f),
            labelColor = color,
            leadingIconContentColor = color
        )
    )
}

@Composable
private fun PaymentStatusChip(paymentStatus: PaymentStatus) {
    val textRes = when (paymentStatus) {
        PaymentStatus.PAID -> R.string.payment_paid
        PaymentStatus.UNPAID -> R.string.payment_unpaid
    }
    val color = when (paymentStatus) {
        PaymentStatus.PAID -> MaterialTheme.colorScheme.primary
        PaymentStatus.UNPAID -> MaterialTheme.colorScheme.outline
    }
    
    AssistChip(
        onClick = { },
        label = { Text(stringResource(textRes), style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.2f),
            labelColor = color
        )
    )
}

