package io.github.xororz.localdream.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.AppTheme
import io.github.xororz.localdream.data.ThemePreferences
import kotlinx.coroutines.launch

@Composable
fun ThemeSettingsDialog(
    themePreferences: ThemePreferences,
    currentTheme: AppTheme,
    useDynamicColor: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
                Text(stringResource(R.string.theme_settings))
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // Theme selection
                Text(
                    stringResource(R.string.theme_mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeOption(
                        theme = AppTheme.SYSTEM,
                        currentTheme = currentTheme,
                        icon = Icons.Default.SettingsBrightness,
                        label = stringResource(R.string.theme_system),
                        description = stringResource(R.string.theme_system_desc),
                        onSelect = {
                            scope.launch {
                                themePreferences.setTheme(AppTheme.SYSTEM)
                            }
                        }
                    )
                    
                    ThemeOption(
                        theme = AppTheme.LIGHT,
                        currentTheme = currentTheme,
                        icon = Icons.Default.LightMode,
                        label = stringResource(R.string.theme_light),
                        description = stringResource(R.string.theme_light_desc),
                        onSelect = {
                            scope.launch {
                                themePreferences.setTheme(AppTheme.LIGHT)
                            }
                        }
                    )
                    
                    ThemeOption(
                        theme = AppTheme.DARK,
                        currentTheme = currentTheme,
                        icon = Icons.Default.DarkMode,
                        label = stringResource(R.string.theme_dark),
                        description = stringResource(R.string.theme_dark_desc),
                        onSelect = {
                            scope.launch {
                                themePreferences.setTheme(AppTheme.DARK)
                            }
                        }
                    )
                    
                    ThemeOption(
                        theme = AppTheme.AMOLED,
                        currentTheme = currentTheme,
                        icon = Icons.Default.Brightness2,
                        label = stringResource(R.string.theme_amoled),
                        description = stringResource(R.string.theme_amoled_desc),
                        onSelect = {
                            scope.launch {
                                themePreferences.setTheme(AppTheme.AMOLED)
                            }
                        }
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Dynamic color toggle (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.dynamic_color),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    themePreferences.setDynamicColor(enabled)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    currentTheme: AppTheme,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onSelect: () -> Unit
) {
    val isSelected = currentTheme == theme
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
            
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
