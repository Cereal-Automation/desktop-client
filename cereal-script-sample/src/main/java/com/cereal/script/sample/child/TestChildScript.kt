package com.cereal.script.sample.child

import com.cereal.sdk.ChildScript
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.component.ComponentProvider
import com.cereal.sdk.component.script.ScriptParameters
import kotlinx.coroutines.delay

@ChildScript(
    id = "CHILD_SCRIPT_ID",
    name = "My child script",
)
class TestChildScript(
    private val parameters: ScriptParameters,
) : Script<TestChildConfiguration> {
    override suspend fun onStart(
        configuration: TestChildConfiguration,
        provider: ComponentProvider,
    ): Boolean {
        provider.logger().info("Value for TEST is:" + parameters.getString("TEST"))

        return true
    }

    override suspend fun execute(
        configuration: TestChildConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult {
        statusUpdate("Waiting for 5 seconds")
        delay(WAIT_DELAY_MS)

        statusUpdate("Launching child script with ${configuration.keyString()}")

        val parameters = ScriptParameters()
        parameters.putString("TEST", "Test string")
        provider.scriptLauncher().start(TestChildScript::class.java, parameters)

        return ExecutionResult.Success("${TestChildScript::class.java.simpleName} succeeded!")
    }

    override suspend fun onFinish(
        configuration: TestChildConfiguration,
        provider: ComponentProvider,
    ) {
        // No-op
    }

    private companion object {
        const val WAIT_DELAY_MS = 5000L
    }
}
