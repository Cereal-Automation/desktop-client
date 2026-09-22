package com.cereal.script.sample

import com.cereal.script.sample.child.TestChildScript
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.component.ComponentProvider
import com.cereal.sdk.component.notification.notification
import com.cereal.sdk.component.script.ScriptParameters
import kotlinx.coroutines.delay

class SampleScript : Script<SampleConfiguration> {
    private fun shouldRun(
        configuration: SampleConfiguration,
        specificFlag: Boolean?,
    ): Boolean {
        // If all flags are null, run everything (default behavior).
        val allNull =
            configuration.testLogger() == null &&
                configuration.testPreferences() == null &&
                configuration.testNotification() == null &&
                configuration.testScriptLauncher() == null &&
                configuration.testUserInteraction() == null &&
                configuration.testLicense() == null &&
                configuration.testArtifacts() == null &&
                configuration.crashWithException() == null

        if (allNull) return true

        // Otherwise, run only if the specific flag is true.
        return specificFlag == true
    }

    override suspend fun onStart(
        configuration: SampleConfiguration,
        provider: ComponentProvider,
    ): Boolean {
        val logger = provider.logger()
        logger.info("Initializing SampleScript...")

        // Complex list: the rows arrive as typed objects, no delimiter parsing needed.
        configuration.targets()?.forEach { target ->
            logger.info(
                "TEST: Target ${target.sku()} x${target.quantity()} size=${target.size()} " +
                    "maxPrice=${target.maxPrice()} notify=${target.notify()}",
            )
        }

        // Test Logger
        if (shouldRun(configuration, configuration.testLogger())) {
            logger.debug("TEST: Debug Message")
            logger.info("TEST: Info Message")
            logger.warn("TEST: Warn Message")
            logger.error("TEST: Error Message", Exception("Test Exception"))
        }

        // Test Preferences
        if (shouldRun(configuration, configuration.testPreferences())) {
            val prefs = provider.preference()
            logger.info("TEST: Testing Preferences...")

            prefs.setString("test_string", "value")
            val str = prefs.getString("test_string")
            logger.info("Preference String: $str")

            prefs.setInt("test_int", TEST_INT_VALUE)
            val intVal = prefs.getInt("test_int")
            logger.info("Preference Int: $intVal")

            prefs.setBoolean("test_bool", true)
            val boolVal = prefs.getBoolean("test_bool")
            logger.info("Preference Boolean: $boolVal")

            prefs.setFloat("test_float", TEST_FLOAT_VALUE)
            val floatVal = prefs.getFloat("test_float")
            logger.info("Preference Float: $floatVal")

            prefs.setLong("test_long", TEST_LONG_VALUE)
            val longVal = prefs.getLong("test_long")
            logger.info("Preference Long: $longVal")

            prefs.delete("test_string")
            if (prefs.getString("test_string") == null) {
                logger.info("Preference Delete: Success")
            } else {
                logger.error("Preference Delete: Failed")
            }
        }

        // Test License (Best effort)
        if (shouldRun(configuration, configuration.testLicense())) {
            try {
                logger.info("TEST: Testing License Component...")
                // Using a dummy script ID and salt, expected to likely fail or return invalid
                provider.license().checkScriptLicense("dummy_id", "dummy_salt")
                logger.info("License check invoked.")
            } catch (e: Exception) {
                logger.warn("License check threw exception (expected with dummy data): ${e.message}")
            }
        }

        return true
    }

    override suspend fun execute(
        configuration: SampleConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult {
        val logger = provider.logger()
        logger.info("Executing SampleScript...")

        // Test Artifacts (emits downloadable files; each emit appends a new artifact to this task)
        testArtifacts(configuration, provider, statusUpdate)

        // Test Notification
        if (shouldRun(configuration, configuration.testNotification())) {
            statusUpdate("Testing Notification...")
            delay(DEMO_DELAY_MS)
            provider.notification().sendNotification(
                notification("Test Notification") {
                    title = "Sample Script Test"
                    discordMessage {
                        content = "This is a test notification from the sample script."
                    }
                },
            )
            logger.info("Notification sent.")
        }

        // Test Child Script Launcher
        if (shouldRun(configuration, configuration.testScriptLauncher())) {
            statusUpdate("Testing Child Script Launcher...")
            delay(DEMO_DELAY_MS)
            val params = ScriptParameters()
            params.putString("TEST", "Hello from Parent")
            try {
                provider.scriptLauncher().start(TestChildScript::class.java, params)
                logger.info("Child script started.")
            } catch (e: Exception) {
                logger.error("Failed to start child script", e)
            }
        }

        // Test User Interaction
        if (shouldRun(configuration, configuration.testUserInteraction())) {
            val interaction = provider.userInteraction()

            // 1. Request Input
            statusUpdate("Testing Input Request...")
            val userInput = interaction.requestInput("Input Test", "Please enter some text to continue:")
            logger.info("User input received: $userInput")
            statusUpdate("User entered: $userInput")
            delay(DEMO_DELAY_MS)

            // 2. Show Continue Button
            statusUpdate("Testing Continue Button...")
            interaction.showContinueButton()
            logger.info("User pressed continue.")

            // 3. Show HTML
            statusUpdate("Testing Show HTML...")
            val html =
                """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Test HTML</title>
                    <style>
                        body { font-family: sans-serif; padding: 20px; text-align: center; }
                        button { padding: 10px 20px; font-size: 16px; cursor: pointer; }
                    </style>
                </head>
                <body>
                    <h1>Test HTML Interaction</h1>
                    <p>Click the button below to finish this test step.</p>
                    <button onclick="window.location.href='https://cereal.com/continue'">Continue</button>
                </body>
                </html>
                """.trimIndent()

            interaction.showHtml("HTML Test", html) { request ->
                request.url.contains("cereal.com/continue")
            }
            logger.info("HTML interaction completed.")

            // 4. Show URL
            statusUpdate("Testing Show URL...")
            // Using example.com which has a "More information..." link to iana.org
            interaction.showUrl("URL Test - Please click 'More information...'", "https://example.com") { request ->
                request.url.contains("iana.org")
            }
            logger.info("URL interaction completed.")
        }

        // Test Crash With Exception
        if (shouldRun(configuration, configuration.crashWithException())) {
            statusUpdate("Crashing with exception for demo purposes...")
            delay(DEMO_DELAY_MS)
            throw RuntimeException("This is a demo exception thrown by SampleScript!")
        }

        statusUpdate("All tests completed successfully!")
        delay(COMPLETION_DELAY_MS)

        return ExecutionResult.Success("Test Script Completed")
    }

    private suspend fun testArtifacts(
        configuration: SampleConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ) {
        if (!shouldRun(configuration, configuration.testArtifacts())) return

        statusUpdate("Producing artifacts...")
        val artifacts = provider.artifact()

        val csv =
            buildString {
                appendLine("sku,price")
                appendLine("ABC-1,19.99")
                appendLine("ABC-2,24.50")
            }.toByteArray()
        artifacts.emit("results.csv", csv, "text/csv")

        artifacts.emit("report.json", """{"status":"ok","items":2}""".toByteArray(), "application/json")

        // mimeType omitted on purpose — the host infers it from the .txt extension.
        artifacts.emit("notes.txt", "Generated by SampleScript.".toByteArray())

        provider.logger().info("Emitted 3 artifacts.")
        delay(DEMO_DELAY_MS)
    }

    override suspend fun onFinish(
        configuration: SampleConfiguration,
        provider: ComponentProvider,
    ) {
        provider.logger().info("SampleScript finished.")
    }

    private companion object {
        const val TEST_INT_VALUE = 123
        const val TEST_FLOAT_VALUE = 123.45f
        const val TEST_LONG_VALUE = 123456789L
        const val DEMO_DELAY_MS = 1000L
        const val COMPLETION_DELAY_MS = 2000L
    }
}
