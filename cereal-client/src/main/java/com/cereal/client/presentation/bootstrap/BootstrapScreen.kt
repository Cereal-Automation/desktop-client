package com.cereal.client.presentation.bootstrap

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cereal.client.application.interactor.bootstrap.UserAction
import com.cereal.client.presentation.error.errorView
import com.cereal.client.presentation.view.CerealCircularProgressIndicator
import com.cereal.client.presentation.view.CerealText
import com.cereal.client.presentation.view.CerealTextButton
import com.cereal.client.presentation.view.LinearProgressBar
import com.cereal_automation.cereal_client.generated.resources.Res
import com.cereal_automation.cereal_client.generated.resources.app_icon
import com.cereal_automation.cereal_client.generated.resources.application
import com.cereal_automation.cereal_client.generated.resources.close
import com.cereal_automation.cereal_client.generated.resources.ignore
import com.cereal_automation.cereal_client.generated.resources.installer_manual_message
import com.cereal_automation.cereal_client.generated.resources.installer_manual_title
import com.cereal_automation.cereal_client.generated.resources.installer_timer
import com.cereal_automation.cereal_client.generated.resources.update
import com.cereal_automation.cereal_client.generated.resources.update_available_message
import com.cereal_automation.cereal_client.generated.resources.update_available_title
import com.cereal_automation.cereal_client.generated.resources.update_required_message
import com.cereal_automation.cereal_client.generated.resources.update_required_title
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent
import kotlin.system.exitProcess

@Composable
fun BootstrapScreen(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    bootstrapViewModel: BootstrapViewModel =
        remember {
            KoinJavaComponent.get(
                BootstrapViewModel::class.java,
                parameters = { parametersOf(coroutineScope) },
            )
        },
    onCompleted: () -> Unit,
) {
    val windowClosed = bootstrapViewModel.windowClosed.value
    LaunchedEffect(windowClosed) {
        if (windowClosed) {
            onCompleted()
        }
    }

    val shouldExitApp = bootstrapViewModel.shouldExitApp.value
    LaunchedEffect(shouldExitApp) {
        if (shouldExitApp) {
            exitProcess(0)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painterResource(Res.drawable.application),
                        contentDescription = stringResource(Res.string.app_icon),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(150.dp).height(150.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CerealText(
                            text = bootstrapViewModel.progressStatus.value,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Reserve space for the loading indicator to prevent text shifting
                        if (bootstrapViewModel.showLoadingIndicator.value) {
                            CerealCircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            // Invisible spacer to maintain layout consistency
                            Spacer(modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressBar(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        progress = bootstrapViewModel.progress.value,
                    )
                }
            }
        }

        errorView(bootstrapViewModel.errorAction)

        bootstrapViewModel.showUserAction.value?.let { userAction ->
            when (userAction) {
                UserAction.AppUpdateRequired -> {
                    AlertDialog(
                        modifier = Modifier.widthIn(min = 400.dp),
                        onDismissRequest = { /* No-op */ },
                        title = { CerealText(stringResource(Res.string.update_required_title)) },
                        text = { CerealText(stringResource(Res.string.update_required_message)) },
                        confirmButton = {
                            CerealTextButton(onClick = { bootstrapViewModel.onConfirmUserAction() }) {
                                CerealText(stringResource(Res.string.update).uppercase())
                            }
                        },
                    )
                }

                UserAction.AppUpdateAdvised -> {
                    AlertDialog(
                        modifier = Modifier.widthIn(min = 400.dp),
                        onDismissRequest = { /* No-op */ },
                        title = { CerealText(stringResource(Res.string.update_available_title)) },
                        text = { CerealText(stringResource(Res.string.update_available_message)) },
                        confirmButton = {
                            CerealTextButton(onClick = { bootstrapViewModel.onConfirmUserAction() }) {
                                CerealText(stringResource(Res.string.update).uppercase())
                            }
                        },
                        dismissButton = {
                            CerealTextButton(onClick = { bootstrapViewModel.onDismissUserAction() }) {
                                CerealText(stringResource(Res.string.ignore).uppercase())
                            }
                        },
                    )
                }
            }
        }

        bootstrapViewModel.openInstaller.value?.let {
            var timer by remember { mutableStateOf(5) }

            LaunchedEffect(timer) {
                if (timer > 0) {
                    delay(1000)
                    timer -= 1
                } else {
                    // installUpdate self-installs the AppImage (Linux) or opens the installer and
                    // quits the app once it launches; if it can't be launched it surfaces the
                    // download location instead, so don't exit here.
                    bootstrapViewModel.installUpdate(it)
                }
            }

            AlertDialog(
                modifier = Modifier.widthIn(min = 400.dp),
                onDismissRequest = { /* No-op */ },
                title = { CerealText(stringResource(Res.string.installer_timer, timer)) },
                confirmButton = {
                    CerealTextButton(onClick = {
                        bootstrapViewModel.installUpdate(it)
                    }) {
                        CerealText(stringResource(Res.string.update).uppercase())
                    }
                },
            )
        }

        bootstrapViewModel.installerLocation.value?.let { file ->
            AlertDialog(
                modifier = Modifier.widthIn(min = 400.dp),
                onDismissRequest = { /* No-op */ },
                title = { CerealText(stringResource(Res.string.installer_manual_title)) },
                text = { CerealText(stringResource(Res.string.installer_manual_message, file.absolutePath)) },
                confirmButton = {
                    CerealTextButton(onClick = { exitProcess(0) }) {
                        CerealText(stringResource(Res.string.close).uppercase())
                    }
                },
            )
        }
    }
}
