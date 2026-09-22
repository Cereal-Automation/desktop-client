package com.cereal.client.presentation.view

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun CerealSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    actionColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        actionColor = actionColor,
    )
}

@Composable
fun CerealSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    actionColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        CerealSnackbar(
            snackbarData = data,
            containerColor = containerColor,
            contentColor = contentColor,
            actionColor = actionColor,
        )
    }
}
