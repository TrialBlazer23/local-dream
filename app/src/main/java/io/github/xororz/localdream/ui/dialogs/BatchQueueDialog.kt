package io.github.xororz.localdream.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.BatchItemStatus
import io.github.xororz.localdream.data.BatchQueueItem
import io.github.xororz.localdream.data.BatchQueueManager
import io.github.xororz.localdream.data.BatchQueueState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchQueueDialog(
    queueState: BatchQueueState,
    onDismiss: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearQueue: () -> Unit,
    onStartBatch: () -> Unit,
    onStopBatch: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.batch_queue),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Progress
                if (queueState.items.isNotEmpty()) {
                    val (completed, total) = queueState.items.count { 
                        it.status == BatchItemStatus.COMPLETED || it.status == BatchItemStatus.FAILED 
                    } to queueState.items.size
                    
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.batch_progress, completed, total),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${(completed.toFloat() / total * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { completed.toFloat() / total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                
                Divider()
                
                // Queue items
                if (queueState.items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.QueuePlayNext,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                stringResource(R.string.no_batch_items),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                stringResource(R.string.no_batch_items_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queueState.items) { index, item ->
                            BatchQueueItemCard(
                                item = item,
                                index = index + 1,
                                isProcessing = queueState.currentIndex == index,
                                onRemove = { onRemoveItem(item.id) }
                            )
                        }
                    }
                }
                
                Divider()
                
                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (queueState.isProcessing) {
                        Button(
                            onClick = onStopBatch,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.stop_batch))
                        }
                    } else {
                        Button(
                            onClick = onStartBatch,
                            modifier = Modifier.weight(1f),
                            enabled = queueState.items.any { it.status == BatchItemStatus.PENDING }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.start_batch))
                        }
                        OutlinedButton(
                            onClick = onClearQueue,
                            modifier = Modifier.weight(1f),
                            enabled = queueState.items.isNotEmpty() && !queueState.isProcessing
                        ) {
                            Icon(Icons.Default.ClearAll, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.clear_queue))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchQueueItemCard(
    item: BatchQueueItem,
    index: Int,
    isProcessing: Boolean,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isProcessing -> MaterialTheme.colorScheme.primaryContainer
                item.status == BatchItemStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                item.status == BatchItemStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                when (item.status) {
                    BatchItemStatus.PENDING -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "#$index",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    BatchItemStatus.PROCESSING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    BatchItemStatus.COMPLETED -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    BatchItemStatus.FAILED -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Item details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        item.prompt.take(50) + if (item.prompt.length > 50) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        "${item.width}×${item.height} • Steps: ${item.steps} • CFG: ${item.cfg}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Remove button
            if (item.status == BatchItemStatus.PENDING) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
