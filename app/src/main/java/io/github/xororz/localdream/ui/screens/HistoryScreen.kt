package io.github.xororz.localdream.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.database.GenerationEntity
import io.github.xororz.localdream.data.database.GenerationRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { GenerationRepository(context) }
    
    val generations by repository.getAllGenerations().collectAsState(initial = emptyList())
    val favoriteGenerations by repository.getFavoriteGenerations().collectAsState(initial = emptyList())
    
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var selectedGeneration by remember { mutableStateOf<GenerationEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var generationToDelete by remember { mutableStateOf<GenerationEntity?>(null) }
    var filterByModel by remember { mutableStateOf<String?>(null) }
    
    val displayedGenerations = remember(generations, favoriteGenerations, showFavoritesOnly, searchQuery, filterByModel) {
        val baseList = if (showFavoritesOnly) favoriteGenerations else generations
        val filteredByModel = if (filterByModel != null) {
            baseList.filter { it.modelId == filterByModel }
        } else {
            baseList
        }
        
        if (searchQuery.isNotBlank()) {
            filteredByModel.filter { 
                it.prompt.contains(searchQuery, ignoreCase = true) ||
                it.negativePrompt.contains(searchQuery, ignoreCase = true) ||
                it.modelName.contains(searchQuery, ignoreCase = true)
            }
        } else {
            filteredByModel
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (showSearchBar) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_history)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    } else {
                        Text(stringResource(R.string.generation_history))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar; if (!showSearchBar) searchQuery = "" }) {
                        Icon(if (showSearchBar) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                        Icon(
                            if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorites",
                            tint = if (showFavoritesOnly) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats card
            if (generations.isNotEmpty() && !showSearchBar) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(
                            icon = Icons.Default.Image,
                            value = generations.size.toString(),
                            label = stringResource(R.string.total_generations)
                        )
                        StatItem(
                            icon = Icons.Default.Favorite,
                            value = favoriteGenerations.size.toString(),
                            label = stringResource(R.string.favorites)
                        )
                    }
                }
            }

            // Filter chips
            if (!showSearchBar && filterByModel == null) {
                LaunchedEffect(generations) {
                    // Could add model filter chips here
                }
            }

            // Grid
            if (displayedGenerations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.ImageSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            stringResource(R.string.no_history),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            stringResource(R.string.no_history_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedGenerations, key = { it.id }) { generation ->
                        GenerationGridItem(
                            generation = generation,
                            onClick = { selectedGeneration = it },
                            onFavoriteClick = { id, isFav ->
                                scope.launch {
                                    repository.updateFavorite(id, isFav)
                                }
                            },
                            onDeleteClick = {
                                generationToDelete = it
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Detail dialog
        selectedGeneration?.let { generation ->
            GenerationDetailDialog(
                generation = generation,
                onDismiss = { selectedGeneration = null },
                onFavoriteClick = { id, isFav ->
                    scope.launch {
                        repository.updateFavorite(id, isFav)
                    }
                },
                onDeleteClick = {
                    generationToDelete = generation
                    showDeleteDialog = true
                    selectedGeneration = null
                },
                onLoadParameters = {
                    // TODO: Navigate back to ModelRunScreen with these parameters
                    selectedGeneration = null
                }
            )
        }

        // Delete confirmation
        if (showDeleteDialog && generationToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete_generation)) },
                text = { Text(stringResource(R.string.delete_generation_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                generationToDelete?.let { repository.deleteGeneration(it.id) }
                                showDeleteDialog = false
                                generationToDelete = null
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; generationToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun GenerationGridItem(
    generation: GenerationEntity,
    onClick: (GenerationEntity) -> Unit,
    onFavoriteClick: (Long, Boolean) -> Unit,
    onDeleteClick: (GenerationEntity) -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick(generation) }
    ) {
        Box {
            // Thumbnail
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(generation.thumbnailPath ?: generation.imagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = generation.prompt,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Favorite indicator
            if (generation.isFavorite) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp),
                    tint = Color.Red
                )
            }
            
            // Runtime badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    generation.runtime,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            // Model name overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    generation.modelName,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GenerationDetailDialog(
    generation: GenerationEntity,
    onDismiss: () -> Unit,
    onFavoriteClick: (Long, Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onLoadParameters: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Image
                Box {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(generation.imagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = generation.prompt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Action buttons
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { onFavoriteClick(generation.id, !generation.isFavorite) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Icon(
                                if (generation.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (generation.isFavorite) Color.Red else LocalContentColor.current
                            )
                        }
                        
                        FilledTonalIconButton(
                            onClick = onDeleteClick,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                        
                        FilledTonalIconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                
                // Details
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Metadata
                    InfoSection(stringResource(R.string.generation_info)) {
                        InfoRow("Model", generation.modelName)
                        InfoRow("Runtime", generation.runtime)
                        InfoRow("Date", dateFormat.format(Date(generation.timestamp)))
                        generation.generationTime?.let { InfoRow("Generation Time", it) }
                    }
                    
                    // Parameters
                    InfoSection(stringResource(R.string.parameters)) {
                        InfoRow("Steps", generation.steps.toString())
                        InfoRow("CFG Scale", "%.1f".format(generation.cfg))
                        InfoRow("Seed", generation.seed.toString())
                        InfoRow("Size", "${generation.width}×${generation.height}")
                        generation.denoiseStrength?.let { InfoRow("Denoise Strength", "%.2f".format(it)) }
                    }
                    
                    // Prompt
                    InfoSection(stringResource(R.string.image_prompt)) {
                        Text(
                            generation.prompt,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Negative prompt
                    InfoSection(stringResource(R.string.negative_prompt)) {
                        Text(
                            generation.negativePrompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    
                    // Action button
                    Button(
                        onClick = onLoadParameters,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.use_these_parameters))
                    }
                    
                    // Share and Copy buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                shareImage(context, generation.imagePath)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.share_image))
                        }
                        
                        OutlinedButton(
                            onClick = {
                                copyParametersToClipboard(context, generation)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.copy_parameters))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// Helper functions for sharing and copying
private fun shareImage(context: Context, imagePath: String) {
    try {
        val imageFile = File(imagePath)
        if (!imageFile.exists()) {
            Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_image)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun copyParametersToClipboard(context: Context, generation: GenerationEntity) {
    val parametersText = buildString {
        appendLine("Prompt: ${generation.prompt}")
        appendLine("Negative Prompt: ${generation.negativePrompt}")
        appendLine("Steps: ${generation.steps}")
        appendLine("CFG Scale: ${generation.cfg}")
        appendLine("Seed: ${generation.seed}")
        appendLine("Size: ${generation.width}×${generation.height}")
        generation.denoiseStrength?.let { appendLine("Denoise Strength: $it") }
        appendLine("Model: ${generation.modelName}")
        appendLine("Runtime: ${generation.runtime}")
    }
    
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Generation Parameters", parametersText)
    clipboardManager.setPrimaryClip(clip)
    
    Toast.makeText(context, context.getString(R.string.parameters_copied), Toast.LENGTH_SHORT).show()
}
