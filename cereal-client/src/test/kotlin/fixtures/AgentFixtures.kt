package fixtures

import com.cereal.client.domain.model.agent.AI_BROWSER_AGENT_PACKAGE
import com.cereal.client.domain.model.marketplace.MarketplaceScript
import com.cereal.client.domain.model.script.Release
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationDefinition
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.domain.provider.ScriptInstallProvider
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptRepository
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.statemodifier.ScriptConfig
import com.cereal.sdk.statemodifier.ScriptConfigValue
import com.cereal.sdk.statemodifier.StateModifier
import com.cereal.sdk.statemodifier.Visibility
import kotlin.reflect.KClass

/** Mirrors the agent script's own `RunMode`, which lives in the script jar the client can't reference. */
enum class FakeRunMode { Once, Repeat }

/** Mirrors the agent's `GoalState`: the script's own validator for the goal. */
private object GoalRequired : StateModifier {
    override fun getVisibility(scriptConfig: ScriptConfig): Visibility = Visibility.VisibleRequired

    override fun getError(scriptConfig: ScriptConfig): String? {
        val goal = (scriptConfig.valueForKey("goal") as? ScriptConfigValue.StringScriptConfigValue)?.value
        return if (goal.isNullOrBlank()) "Goal is required" else null
    }
}

fun anAgentItem(
    key: String,
    type: ConfigItemType,
    position: Int,
    defaultValue: ConfigValue? = null,
    stateModifier: StateModifier? = null,
) = ScriptConfigurationItemDefinition(
    name = key,
    description = "",
    key = key,
    position = position,
    type = type,
    isNullable = false,
    stateModifier = stateModifier,
    isScriptIdentifier = false,
    defaultValue = defaultValue,
)

@Suppress("UNCHECKED_CAST")
val fakeRunModeType = ConfigItemType.EnumConfigItem(FakeRunMode::class as KClass<Enum<*>>)

/** The agent's definition as the client sees it: the composer's four keys plus one hidden item with a default. */
fun agentConfigurationItems(): List<ScriptConfigurationItemDefinition> =
    listOf(
        anAgentItem("goal", ConfigItemType.StringConfigItem, 0, stateModifier = GoalRequired),
        anAgentItem("startUrl", ConfigItemType.StringConfigItem, 1),
        anAgentItem("runMode", fakeRunModeType, 2, defaultValue = ConfigValue.EnumValue(FakeRunMode.Once)),
        anAgentItem("repeatInterval", ConfigItemType.IntConfigItem, 3, defaultValue = ConfigValue.IntValue(300)),
        anAgentItem("maxSteps", ConfigItemType.IntConfigItem, 31, defaultValue = ConfigValue.IntValue(40)),
    )

/** An installed AI browser agent whose definition has the given [items] (the real four keys by default). */
fun anAgentPackage(items: List<ScriptConfigurationItemDefinition> = agentConfigurationItems()): ScriptPackage {
    val base = aScriptPackage(AI_BROWSER_AGENT_PACKAGE)
    return base.copy(
        manifest = base.manifest.copy(name = "AI Browser Agent"),
        mainScript =
            base.mainScript.copy(
                configuration = ScriptConfigurationDefinition(ScriptConfiguration::class, items),
            ),
    )
}

/** An installed agent that declares an SDK newer than the client's (the in-memory client SDK is 1.0.0). */
fun anAgentRequiringNewerClient(): ScriptPackage {
    val agent = anAgentPackage()
    return agent.copy(manifest = agent.manifest.copy(sdkVersion = "9.0.0"))
}

/** The agent's marketplace listing, installable. */
fun anAgentListing(maintenanceMode: Boolean = false) =
    MarketplaceScript(
        id = "agent",
        publicIdentifier = AI_BROWSER_AGENT_PACKAGE,
        title = "AI Browser Agent",
        isFree = true,
        maintenanceMode = maintenanceMode,
        latestRelease = Release(versionName = "1.0.0", versionCode = 1, releaseNotes = null),
    )

/**
 * [ScriptInstallProvider] whose install lands in [scripts] (like a real install landing on disk), or
 * throws [failWith] when set.
 */
class FakeScriptInstallProvider(
    private val scripts: InMemoryScriptRepository,
    private val installs: ScriptPackage = anAgentPackage(),
) : ScriptInstallProvider {
    var failWith: Exception? = null

    override suspend fun updateScript(
        scriptPackage: ScriptPackage,
        release: Release,
    ): ScriptPackage = scriptPackage

    override suspend fun installScript(
        packageName: String,
        release: Release,
    ): ScriptPackage {
        failWith?.let { throw it }
        scripts.seed(listOf(installs))
        return installs
    }
}
