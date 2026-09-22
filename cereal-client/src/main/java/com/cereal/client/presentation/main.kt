package com.cereal.client.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import com.cereal.client.App
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.application.exception.ExceptionFilter
import com.cereal.client.presentation.bootstrap.BootstrapWindow
import com.cereal.client.presentation.main.MainWindow
import com.cereal.client.smoke.SmokeTest
import io.kamel.core.config.DefaultCacheSize
import io.kamel.core.config.DefaultHttpCacheSize
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.fileFetcher
import io.kamel.core.config.fileUrlFetcher
import io.kamel.core.config.httpUrlFetcher
import io.kamel.core.config.stringMapper
import io.kamel.core.config.uriMapper
import io.kamel.core.config.urlMapper
import io.kamel.image.config.LocalKamelConfig
import io.kamel.image.config.animatedImageDecoder
import io.kamel.image.config.imageBitmapDecoder
import io.kamel.image.config.imageVectorDecoder
import io.kamel.image.config.svgDecoder
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.awt.GraphicsEnvironment
import kotlin.system.exitProcess

// Custom Kamel config that uses OkHttp instead of Apache HttpClient.
// We build from scratch (rather than takeFrom KamelConfig.Default) to avoid
// KamelConfig.Core constructing an Apache-backed HttpClient via ServiceLoader,
// which fails at runtime because commons-logging is stripped by ProGuard.
private val kamelConfig =
    KamelConfig {
        imageBitmapCacheSize = DefaultCacheSize
        imageVectorCacheSize = DefaultCacheSize
        svgCacheSize = DefaultCacheSize
        animatedImageCacheSize = DefaultCacheSize
        stringMapper()
        urlMapper()
        uriMapper()
        fileFetcher()
        fileUrlFetcher()
        httpUrlFetcher(
            HttpClient(OkHttp) {
                httpCache(DefaultHttpCacheSize)
            },
        )
        imageBitmapDecoder()
        imageVectorDecoder()
        svgDecoder()
        animatedImageDecoder()
    }

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ensureSafeWindowsTempDir()

    // Filter out unavailable assistive technologies to prevent AWTError on Windows when accessibility
    // is configured but the AccessBridge JAR is not available, while keeping working ones enabled.
    // This must run before any AWT/Swing code initializes.
    ensureLoadableAssistiveTechnologies()

    // Obfuscation gate: when launched with CEREAL_SMOKE_TEST=1, boot the real App.initialize() and
    // exercise the obfuscation-fragile subsystems headlessly, then exit with a pass/fail code. Runs
    // before the headless guard below so it works on headless CI runners, and never opens a window.
    if (SmokeTest.isRequested()) {
        exitProcess(SmokeTest.run())
    }

    // Detect headless environments (e.g. Linux without an X11/Wayland display server) before
    // Compose tries to access the screen device, which would throw HeadlessException and crash.
    if (GraphicsEnvironment.isHeadless()) {
        System.err.println(
            "Cereal requires a graphical display to run. " +
                "No display was detected (DISPLAY environment variable is not set or the system is headless). " +
                "Please run Cereal on a machine with a graphical environment.",
        )
        return
    }

    App.initialize()

    application(exitProcessOnExit = true) {
        val bootstrapping = remember { mutableStateOf(true) }
        CompositionLocalProvider(
            // Custom exception handler just to log the exception with Sentry and give the user a general error message
            // instead of a technical one. Based on https://github.com/JetBrains/compose-multiplatform/issues/1764#issuecomment-1174249825.
            LocalWindowExceptionHandlerFactory provides
                WindowExceptionHandlerFactory { window ->
                    WindowExceptionHandler {
                        // Ignore EOFException as it often occurs when the application is running and the user shuts down their PC.
                        // This causes network connections to close abruptly, which is not an error we need to track.
                        if (ExceptionFilter.shouldIgnoreException(it)) {
                            return@WindowExceptionHandler
                        }

                        CrashReporter.report(it)
                        DefaultWindowExceptionHandlerFactory
                            .exceptionHandler(window)
                            .onException(
                                RuntimeException(
                                    "An unexpected error occurred, we are notified about this and will fix it asap.",
                                    it,
                                ),
                            )
                    }
                },
            LocalKamelConfig provides kamelConfig,
        ) {
            if (bootstrapping.value) {
                BootstrapWindow {
                    bootstrapping.value = false
                }
            } else {
                MainWindow(onCloseRequest = ::exitApplication)
            }
        }
    }
}
