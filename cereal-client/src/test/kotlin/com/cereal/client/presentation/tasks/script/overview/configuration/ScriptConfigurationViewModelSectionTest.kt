package com.cereal.client.presentation.tasks.script.overview.configuration

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.interactor.script.GetScriptConfigDefinitionInteractor
import com.cereal.client.application.interactor.script.GetScriptPackageInstancesByPackageNameInteractor
import com.cereal.client.domain.model.script.ScriptPackageInstance
import com.cereal.client.domain.model.script.configuration.ConfigItemType
import com.cereal.client.domain.model.script.configuration.ConfigSection
import com.cereal.client.domain.model.script.configuration.ConfigValue
import com.cereal.client.domain.model.script.configuration.ConfigurationItem
import com.cereal.client.domain.model.script.configuration.ScriptConfigurationItemDefinition
import com.cereal.client.presentation.error.ErrorResolver
import com.cereal.sdk.statemodifier.DefaultStateModifier
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Seam: [ScriptConfigurationViewModel] fed a definition whose fields declare sections. The definition
 * interactor is the edge (stubbed); grouping, collapse and Save are observed on the view model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScriptConfigurationViewModelSectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val login = ConfigSection("login", "Login", collapsed = false, order = 0)
    private val advanced = ConfigSection("adv", "Advanced", collapsed = true, order = 1)

    private fun item(
        key: String,
        section: ConfigSection? = null,
        value: String? = "value",
        isNullable: Boolean = false,
    ) = ConfigurationItem(
        definition =
            ScriptConfigurationItemDefinition(
                name = key,
                description = "",
                key = key,
                position = Int.MAX_VALUE,
                type = ConfigItemType.StringConfigItem,
                isNullable = isNullable,
                stateModifier = DefaultStateModifier,
                isScriptIdentifier = false,
                section = section,
            ),
        value = value?.let { ConfigValue.StringValue(it) },
    )

    private fun createViewModel(items: List<ConfigurationItem>): ScriptConfigurationViewModel {
        val errorResolver: ErrorResolver = mockk(relaxed = true)
        every { errorResolver.errorAction } returns mockk(relaxed = true)
        val getDefinition: GetScriptConfigDefinitionInteractor = mockk()
        coEvery { getDefinition(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<GetScriptConfigDefinitionInteractor.Result, Exception>) -> Unit>()(
                SuspendableResult.Success(GetScriptConfigDefinitionInteractor.Result(items, emptyMap())),
            )
        }
        val getInstances: GetScriptPackageInstancesByPackageNameInteractor = mockk()
        coEvery { getInstances(any(), any()) } coAnswers {
            secondArg<suspend (SuspendableResult<List<ScriptPackageInstance>, Exception>) -> Unit>()(
                SuspendableResult.Success(emptyList()),
            )
        }
        return ScriptConfigurationViewModel(
            scriptPackage = mockk(relaxed = true),
            initialScriptPackageInstance = null,
            scope = CoroutineScope(dispatcher),
            errorResolver = errorResolver,
            dispatcherProvider = CoroutinesDispatcherProvider(dispatcher, dispatcher, dispatcher),
            getScriptConfigDefinitionInteractor = getDefinition,
            getScriptPackageInstancesByPackageNameInteractor = getInstances,
            readCustomDatasetFileInteractor = mockk(relaxed = true),
            readProxyFileInteractor = mockk(relaxed = true),
            readListFileInteractor = mockk(relaxed = true),
        ).also { dispatcher.scheduler.advanceUntilIdle() }
    }

    private fun ScriptConfigurationViewModel.layout() = fieldSections().map { group -> group.section?.key to group.fields.keys.map { it.definition.key } }

    @Test
    fun `unsectioned fields come first, then sections in declared order, fields keeping their order`() {
        val viewModel =
            createViewModel(
                listOf(
                    item("retries", advanced),
                    item("user", login),
                    item("plain"),
                    item("password", login),
                    item("other"),
                ),
            )

        assertEquals(
            listOf(
                null to listOf("plain", "other"),
                "login" to listOf("user", "password"),
                "adv" to listOf("retries"),
            ),
            viewModel.layout(),
        )
    }

    @Test
    fun `a collapsed section starts closed and an expanded one open`() {
        val viewModel = createViewModel(listOf(item("user", login), item("retries", advanced)))

        assertTrue(viewModel.isSectionOpen(login))
        assertFalse(viewModel.isSectionOpen(advanced))
    }

    @Test
    fun `a collapsed section starts closed on every open, even after it was opened before`() {
        val items = listOf(item("retries", advanced))
        createViewModel(items).toggleSection(advanced)

        assertFalse(createViewModel(items).isSectionOpen(advanced))
    }

    @Test
    fun `opening and closing a collapsed section never changes values`() {
        val viewModel = createViewModel(listOf(item("retries", advanced, value = "3")))
        val form = viewModel.scriptConfigurationForm.value!!.mainConfigurationFormSection
        val before = form.getScriptConfigurationValues()

        viewModel.toggleSection(advanced)
        viewModel.toggleSection(advanced)

        assertEquals(before, form.getScriptConfigurationValues())
        assertEquals(ConfigValue.StringValue("3"), before["retries"])
    }

    @Test
    fun `save with an error inside a collapsed section opens it and counts the errors`() {
        val viewModel =
            createViewModel(
                listOf(
                    item("plain"),
                    item("retries", advanced, value = null),
                    item("timeout", advanced, value = null),
                    item("note", advanced, value = null, isNullable = true),
                ),
            )

        assertFalse(viewModel.validate())

        assertTrue(viewModel.isSectionOpen(advanced))
        assertEquals(2, viewModel.sectionErrorCounts["adv"])
    }

    @Test
    fun `save without errors leaves a collapsed section closed with no badge`() {
        val viewModel = createViewModel(listOf(item("retries", advanced)))

        assertTrue(viewModel.validate())

        assertFalse(viewModel.isSectionOpen(advanced))
        assertEquals(null, viewModel.sectionErrorCounts["adv"])
    }
}
