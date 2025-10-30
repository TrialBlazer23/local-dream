package io.github.xororz.localdream.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class BatchQueueItem(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val negativePrompt: String,
    val steps: Int,
    val cfg: Float,
    val seed: Long,
    val width: Int,
    val height: Int,
    val denoiseStrength: Float? = null,
    val status: BatchItemStatus = BatchItemStatus.PENDING
)

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class BatchQueueState(
    val items: List<BatchQueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val isProcessing: Boolean = false
)

class BatchQueueManager {
    private val _queueState = MutableStateFlow(BatchQueueState())
    val queueState: StateFlow<BatchQueueState> = _queueState.asStateFlow()
    
    fun addItem(item: BatchQueueItem) {
        val currentState = _queueState.value
        _queueState.value = currentState.copy(
            items = currentState.items + item
        )
    }
    
    fun addItems(items: List<BatchQueueItem>) {
        val currentState = _queueState.value
        _queueState.value = currentState.copy(
            items = currentState.items + items
        )
    }
    
    fun removeItem(itemId: String) {
        val currentState = _queueState.value
        _queueState.value = currentState.copy(
            items = currentState.items.filter { it.id != itemId }
        )
    }
    
    fun clearQueue() {
        _queueState.value = BatchQueueState()
    }
    
    fun getNextPendingItem(): BatchQueueItem? {
        val currentState = _queueState.value
        return currentState.items.firstOrNull { it.status == BatchItemStatus.PENDING }
    }
    
    fun updateItemStatus(itemId: String, status: BatchItemStatus) {
        val currentState = _queueState.value
        val updatedItems = currentState.items.map { item ->
            if (item.id == itemId) item.copy(status = status) else item
        }
        val newIndex = if (status == BatchItemStatus.PROCESSING) {
            updatedItems.indexOfFirst { it.id == itemId }
        } else {
            currentState.currentIndex
        }
        _queueState.value = currentState.copy(
            items = updatedItems,
            currentIndex = newIndex,
            isProcessing = status == BatchItemStatus.PROCESSING
        )
    }
    
    fun setProcessing(processing: Boolean) {
        val currentState = _queueState.value
        _queueState.value = currentState.copy(isProcessing = processing)
    }
    
    fun getProgress(): Pair<Int, Int> {
        val currentState = _queueState.value
        val completed = currentState.items.count { 
            it.status == BatchItemStatus.COMPLETED || it.status == BatchItemStatus.FAILED 
        }
        val total = currentState.items.size
        return Pair(completed, total)
    }
}
