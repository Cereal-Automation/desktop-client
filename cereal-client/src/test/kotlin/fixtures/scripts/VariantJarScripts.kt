package fixtures.scripts

import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem
import com.cereal.sdk.component.ComponentProvider

/*
 * Script classes a test jar's manifest can name. They live outside `com.cereal` so the jar's ScriptClassLoader
 * falls back to the test classpath for them, which lets a manifest-only jar load real script classes. Each
 * implements Script<T> directly: the jar reader finds the configuration type on the class's own interfaces.
 */

interface DefaultEntryConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(keyName = "goal", name = "Goal", description = "", position = 0)
    fun goal(): String
}

interface CheckoutVariantConfiguration : ScriptConfiguration {
    @ScriptConfigurationItem(keyName = "productUrl", name = "Product URL", description = "", position = 0)
    fun productUrl(): String
}

class DefaultEntryScript : Script<DefaultEntryConfiguration> {
    override suspend fun onStart(
        configuration: DefaultEntryConfiguration,
        provider: ComponentProvider,
    ) = true

    override suspend fun execute(
        configuration: DefaultEntryConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult = throw UnsupportedOperationException()

    override suspend fun onFinish(
        configuration: DefaultEntryConfiguration,
        provider: ComponentProvider,
    ) = Unit
}

class CheckoutVariantScript : Script<CheckoutVariantConfiguration> {
    override suspend fun onStart(
        configuration: CheckoutVariantConfiguration,
        provider: ComponentProvider,
    ) = true

    override suspend fun execute(
        configuration: CheckoutVariantConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult = throw UnsupportedOperationException()

    override suspend fun onFinish(
        configuration: CheckoutVariantConfiguration,
        provider: ComponentProvider,
    ) = Unit
}
