package com.cereal.client.infrastructure.sdkcomponent

import com.cereal.client.application.exception.ScriptFeatureNotDeclaredException
import com.cereal.client.domain.model.script.ScriptFeature
import com.cereal.sdk.component.llm.LlmComponent
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ComponentProviderImplTest {
    // A stub that fails if anything is called on it: an undeclared llm() must not get this far.
    private val llmComponent: LlmComponent = mockk()

    @Test
    fun `llm throws when the script doesn't declare the llm feature`() {
        val provider = aProvider(features = emptySet())

        val exception = assertFailsWith<ScriptFeatureNotDeclaredException> { provider.llm() }

        assertEquals(ScriptFeature.LLM, exception.feature)
        assertContains(exception.message!!, "\"llm\"")
        assertContains(exception.message!!, "manifest.json")
    }

    @Test
    fun `llm returns the component when the script declares the llm feature`() {
        val provider = aProvider(features = setOf(ScriptFeature.LLM))

        assertSame(llmComponent, provider.llm())
    }

    private fun aProvider(features: Set<ScriptFeature>) =
        ComponentProviderImpl(
            loggerComponentImpl = mockk(),
            licenseComponent = mockk(),
            notificationComponent = mockk(),
            preferenceComponent = mockk(),
            scriptLauncherComponent = mockk(),
            userInteractionComponent = mockk(),
            artifactComponent = mockk(),
            llmComponent = llmComponent,
            declaredFeatures = features,
        )
}
