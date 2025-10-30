package io.github.xororz.localdream.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.xororz.localdream.R

@Composable
fun AddVariationsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var count by remember { mutableStateOf("5") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Add, contentDescription = null)
        },
        title = {
            Text(stringResource(R.string.add_variations))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.variation_count_hint))
                
                OutlinedTextField(
                    value = count,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() } && it.length <= 3) {
                            count = it
                        }
                    },
                    label = { Text(stringResource(R.string.variation_count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val num = count.toIntOrNull() ?: 1
                    if (num in 1..100) {
                        onConfirm(num)
                        onDismiss()
                    }
                },
                enabled = count.toIntOrNull()?.let { it in 1..100 } == true
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
