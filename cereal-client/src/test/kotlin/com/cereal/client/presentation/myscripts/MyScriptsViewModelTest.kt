package com.cereal.client.presentation.myscripts

import com.cereal.client.application.CoroutinesDispatcherProvider
import com.cereal.client.application.exception.ScriptHasRunningTasksException
import com.cereal.client.application.interactor.marketplace.GetPackagesWithRunningTasksInteractor
import com.cereal.client.application.interactor.marketplace.RemoveMarketplaceScriptInteractor
import com.cereal.client.application.interactor.script.GetScriptsInteractor
import com.cereal.client.application.interactor.script.GetSdkVersionInteractor
import com.cereal.client.domain.model.script.Manifest
import com.cereal.client.domain.model.script.ScriptPackage
import com.cereal.client.presentation.error.ErrorResolver
import com.github.kittinunf.result.coroutines.SuspendableResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.swiftzer.semver.SemVer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MyScriptsViewModelTest {
    private val getScriptsInteractor = mockk<GetScriptsInteractor>()
    private val getSdkVersionInteractor = mockk<GetSdkVersionInteractor>(relaxed = true)
    private val getPackagesWithRunningTasksInteractor = mockk<GetPackagesWithRunningTasksInteractor>()
    private val removeInteractor = mockk<RemoveMarketplaceScriptInteractor>(relaxed = true)
    private val errorResolver = mockk<ErrorResolver>(relaxed = true)

    init {
        coEvery { getPackagesWithRunningTasksInteractor(any()) } returns flowOf(SuspendableResult.Success(emptySet()))
    }

    private fun stubSdkVersion(version: SemVer) {
        val callbackSlot = slot<suspend (SuspendableResult<SemVer, Exception>) -> Unit>()
        coEvery { getSdkVersionInteractor(any(), capture(callbackSlot)) } coAnswers {
            callbackSlot.captured.invoke(SuspendableResult.Success(version))
        }
    }

    private fun dispatcherProvider(testDispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        CoroutinesDispatcherProvider(
            main = testDispatcher,
            io = testDispatcher,
            computation = testDispatcher,
        )

    @Test
    fun `populates list from interactor`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scriptA = aScriptPackage("com.a", "Alpha")
            val scriptB = aScriptPackage("com.b", "Beta")
            coEvery { getScriptsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(scriptB, scriptA)))

            val vm =
                MyScriptsViewModel(
                    scope = CoroutineScope(testDispatcher),
                    dispatcherProvider = dispatcherProvider(testDispatcher),
                    getScriptsInteractor = getScriptsInteractor,
                    getSdkVersionInteractor = getSdkVersionInteractor,
                    getPackagesWithRunningTasksInteractor = getPackagesWithRunningTasksInteractor,
                    removeMarketplaceScriptInteractor = removeInteractor,
                    errorResolver = errorResolver,
                )
            advanceUntilIdle()

            // Sorted by name asc.
            assertEquals(listOf("Alpha", "Beta"), vm.installedScripts.value.map { it.manifest.name })
        }

    @Test
    fun `onRemoveConfirmed calls interactor and surfaces error on failure`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val script = aScriptPackage("com.a", "Alpha")
            coEvery { getScriptsInteractor(any()) } returns flowOf(SuspendableResult.Success(listOf(script)))

            val callbackSlot = slot<suspend (SuspendableResult<Unit, Exception>) -> Unit>()
            coEvery { removeInteractor(any(), capture(callbackSlot)) } coAnswers {
                callbackSlot.captured.invoke(SuspendableResult.Failure(ScriptHasRunningTasksException()))
            }

            val vm =
                MyScriptsViewModel(
                    scope = CoroutineScope(testDispatcher),
                    dispatcherProvider = dispatcherProvider(testDispatcher),
                    getScriptsInteractor = getScriptsInteractor,
                    getSdkVersionInteractor = getSdkVersionInteractor,
                    getPackagesWithRunningTasksInteractor = getPackagesWithRunningTasksInteractor,
                    removeMarketplaceScriptInteractor = removeInteractor,
                    errorResolver = errorResolver,
                )
            advanceUntilIdle()

            vm.onRemoveClicked(script)
            assertEquals(script, vm.confirmRemoveTarget.value)

            vm.onRemoveConfirmed()
            advanceUntilIdle()

            coVerify { errorResolver.setError(any<ScriptHasRunningTasksException>()) }
            assertTrue(vm.confirmRemoveTarget.value == null)
        }

    @Test
    fun `flags scripts that require a newer client as update required`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val compatible = aScriptPackage("com.a", "Alpha", sdkVersion = "1.0.0")
            val incompatible = aScriptPackage("com.b", "Beta", sdkVersion = "2.0.0")
            coEvery { getScriptsInteractor(any()) } returns
                flowOf(SuspendableResult.Success(listOf(compatible, incompatible)))
            stubSdkVersion(SemVer(1, 0, 0))

            val vm =
                MyScriptsViewModel(
                    scope = CoroutineScope(testDispatcher),
                    dispatcherProvider = dispatcherProvider(testDispatcher),
                    getScriptsInteractor = getScriptsInteractor,
                    getSdkVersionInteractor = getSdkVersionInteractor,
                    getPackagesWithRunningTasksInteractor = getPackagesWithRunningTasksInteractor,
                    removeMarketplaceScriptInteractor = removeInteractor,
                    errorResolver = errorResolver,
                )
            advanceUntilIdle()

            assertEquals(setOf("com.b"), vm.updateRequiredPackages.value)
        }

    private fun aScriptPackage(
        packageName: String,
        name: String,
        sdkVersion: String? = null,
    ) = ScriptPackage(
        source = File("/tmp/$packageName.jar"),
        manifest =
            Manifest(
                packageName = packageName,
                name = name,
                versionCode = 1L,
                sdkVersion = sdkVersion,
            ),
        mainScript = mockk(),
        childScripts = emptyMap(),
    )
}
