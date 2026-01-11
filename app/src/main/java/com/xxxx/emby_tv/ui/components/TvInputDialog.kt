package com.xxxx.emby_tv.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.tv.material3.*

import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.R

/**
 * TV输入对话框组件 - 对应Flutter中的tv_input_dialog.dart
 * Dart转换Kotlin说明：
 * 1. 使用Jetpack Compose的@Composable函数替代Flutter的Widget
 * 2. 使用androidx.tv.material3组件替代Flutter的Material组件，避免与标准Material组件冲突
 * 3. 使用State和MutableState替代Dart中的State类
 */
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@Composable
fun TvInputDialog(
    title: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf(initialValue) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface, // Ensure dark background
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(text = title)
        },
        text = {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text(stringResource(R.string.input_label)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onConfirm(inputText)
                        onDismiss()
                    }
                ),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(inputText)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}