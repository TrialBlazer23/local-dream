package io.github.xororz.localdream.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.database.PromptPresetEntity
import io.github.xororz.localdream.data.database.PromptPresetRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryScreen(
    navController: NavController,
    onApplyPreset: ((PromptPresetEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { PromptPresetRepository(context) }
    
    val allPresets by repository.getAllPresets().collectAsState(initial = emptyList())
    val favoritePresets by repository.getFavoritePresets().collectAsState(initial = emptyList())
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf<PromptPresetEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<PromptPresetEntity?>(null) }
    
    val displayedPresets = remember(allPresets, favoritePresets, showFavoritesOnly, searchQuery, selectedCategory) {
        val baseList = if (showFavoritesOnly) favoritePresets else allPresets
        val filteredByCategory = if (selectedCategory != null) {
            baseList.filter { it.category == selectedCategory }
        } else {
            baseList
        }
        
        if (searchQuery.isNotBlank()) {
            filteredByCategory.filter { 
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.prompt.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        } else {
            filteredByCategory
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
                            placeholder = { Text(stringResource(R.string.search_presets)) },
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
                        Text(stringResource(R.string.prompt_library))
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Preset")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category chips
            if (categories.isNotEmpty() && !showSearchBar) {
                ScrollableTabRow(
                    selectedTabIndex = if (selectedCategory == null) 0 else categories.indexOf(selectedCategory) + 1,
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 16.dp
                ) {
                    Tab(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        text = { Text("All") }
                    )
                    categories.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            text = { Text(category) }
                        )
                    }
                }
            }

            // Presets list
            if (displayedPresets.isEmpty()) {
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
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            stringResource(R.string.no_presets),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            stringResource(R.string.no_presets_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedPresets, key = { it.id }) { preset ->
                        PresetListItem(
                            preset = preset,
                            onClick = { selectedPreset = it },
                            onFavoriteClick = { id, isFav ->
                                scope.launch {
                                    repository.updateFavorite(id, isFav)
                                }
                            },
                            onApplyClick = { 
                                scope.launch {
                                    repository.incrementUsageCount(it.id)
                                }
                                onApplyPreset?.invoke(it) 
                            },
                            onDeleteClick = {
                                presetToDelete = it
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Create/Edit dialog
        if (showCreateDialog) {
            CreatePresetDialog(
                onDismiss = { showCreateDialog = false },
                onSave = { name, prompt, negPrompt, category, steps, cfg, denoise ->
                    scope.launch {
                        repository.savePreset(name, prompt, negPrompt, category, steps, cfg, denoise)
                        showCreateDialog = false
                    }
                }
            )
        }

        // Detail dialog
        selectedPreset?.let { preset ->
            PresetDetailDialog(
                preset = preset,
                onDismiss = { selectedPreset = null },
                onFavoriteClick = { id, isFav ->
                    scope.launch {
                        repository.updateFavorite(id, isFav)
                    }
                },
                onApplyClick = {
                    scope.launch {
                        repository.incrementUsageCount(preset.id)
                    }
                    onApplyPreset?.invoke(preset)
                    selectedPreset = null
                },
                onEditClick = {
                    // TODO: Open edit dialog
                },
                onDeleteClick = {
                    presetToDelete = preset
                    showDeleteDialog = true
                    selectedPreset = null
                }
            )
        }

        // Delete confirmation
        if (showDeleteDialog && presetToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.delete_preset)) },
                text = { Text(stringResource(R.string.delete_preset_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                presetToDelete?.let { repository.deletePreset(it) }
                                showDeleteDialog = false
                                presetToDelete = null
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
                    TextButton(onClick = { showDeleteDialog = false; presetToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun PresetListItem(
    preset: PromptPresetEntity,
    onClick: (PromptPresetEntity) -> Unit,
    onFavoriteClick: (Long, Boolean) -> Unit,
    onApplyClick: (PromptPresetEntity) -> Unit,
    onDeleteClick: (PromptPresetEntity) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(preset) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (preset.isFavorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Red
                        )
                    }
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        preset.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Text(
                    preset.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Steps: ${preset.steps}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "CFG: %.1f".format(preset.cfg),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (preset.usageCount > 0) {
                        Text(
                            "Used: ${preset.usageCount}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { onFavoriteClick(preset.id, !preset.isFavorite) }) {
                    Icon(
                        if (preset.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (preset.isFavorite) Color.Red else LocalContentColor.current
                    )
                }
                
                FilledTonalIconButton(onClick = { onApplyClick(preset) }) {
                    Icon(Icons.Default.Check, contentDescription = "Apply")
                }
            }
        }
    }
}

@Composable
fun CreatePresetDialog(
    preset: PromptPresetEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int, Float, Float) -> Unit
) {
    var name by remember { mutableStateOf(preset?.name ?: "") }
    var prompt by remember { mutableStateOf(preset?.prompt ?: "") }
    var negativePrompt by remember { mutableStateOf(preset?.negativePrompt ?: "") }
    var category by remember { mutableStateOf(preset?.category ?: "General") }
    var steps by remember { mutableStateOf(preset?.steps?.toFloat() ?: 20f) }
    var cfg by remember { mutableStateOf(preset?.cfg ?: 7f) }
    var denoiseStrength by remember { mutableStateOf(preset?.denoiseStrength ?: 0.6f) }
    
    val categories = listOf("General", "Anime", "Realistic", "Portrait", "Landscape", "Abstract", "Style", "Custom")
    
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (preset == null) stringResource(R.string.create_preset) else stringResource(R.string.edit_preset),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preset_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.image_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                
                OutlinedTextField(
                    value = negativePrompt,
                    onValueChange = { negativePrompt = it },
                    label = { Text(stringResource(R.string.negative_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                
                Column {
                    Text("Steps: ${steps.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = steps,
                        onValueChange = { steps = it },
                        valueRange = 1f..50f,
                        steps = 48
                    )
                }
                
                Column {
                    Text("CFG Scale: %.1f".format(cfg), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = cfg,
                        onValueChange = { cfg = it },
                        valueRange = 1f..30f,
                        steps = 57
                    )
                }
                
                Column {
                    Text("Denoise Strength: %.2f".format(denoiseStrength), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = denoiseStrength,
                        onValueChange = { denoiseStrength = it },
                        valueRange = 0f..1f,
                        steps = 99
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank() && prompt.isNotBlank()) {
                                onSave(name, prompt, negativePrompt, category, steps.toInt(), cfg, denoiseStrength)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && prompt.isNotBlank()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
fun PresetDetailDialog(
    preset: PromptPresetEntity,
    onDismiss: () -> Unit,
    onFavoriteClick: (Long, Boolean) -> Unit,
    onApplyClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row {
                        IconButton(onClick = { onFavoriteClick(preset.id, !preset.isFavorite) }) {
                            Icon(
                                if (preset.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (preset.isFavorite) Color.Red else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            preset.category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    if (preset.usageCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Used ${preset.usageCount}×",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                InfoSection(stringResource(R.string.parameters)) {
                    InfoRow("Steps", preset.steps.toString())
                    InfoRow("CFG Scale", "%.1f".format(preset.cfg))
                    InfoRow("Denoise Strength", "%.2f".format(preset.denoiseStrength))
                    InfoRow("Created", dateFormat.format(Date(preset.timestamp)))
                }
                
                InfoSection(stringResource(R.string.image_prompt)) {
                    Text(
                        preset.prompt,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                InfoSection(stringResource(R.string.negative_prompt)) {
                    Text(
                        preset.negativePrompt.ifBlank { "None" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                Button(
                    onClick = onApplyClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.apply_preset))
                }
            }
        }
    }
}
