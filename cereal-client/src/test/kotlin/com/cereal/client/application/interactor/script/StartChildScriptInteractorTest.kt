package com.cereal.client.application.interactor.script

import com.cereal.client.application.script.ScriptInstanceFactory
import com.cereal.client.application.task.TaskManager
import com.cereal.client.domain.model.ScopeLinker
import com.cereal.client.domain.model.script.ScriptInstance
import com.cereal.client.domain.model.task.JobTask
import com.cereal.client.infrastructure.data.repository.inmemory.InMemoryScriptInstanceRepository
import com.cereal.sdk.Script
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class StartChildScriptInteractorTest {
    private lateinit var taskManager: TaskManager
    private lateinit var interactor: StartChildScriptInteractor

    private val noOpScopeLinker =
        object : ScopeLinker {
            override fun linkScriptInstanceToPackage(scriptInstance: ScriptInstance) = Unit

            override fun linkTaskToScriptInstance(task: JobTask) = Unit
        }

    @BeforeEach
    fun setUp() {
        taskManager = mockk(relaxed = true)
        interactor =
            StartChildScriptInteractor(
                scriptInstanceRepository = InMemoryScriptInstanceRepository(),
                taskManager = taskManager,
                scriptInstanceFactory = ScriptInstanceFactory(noOpScopeLinker),
            )
    }

    @Test
    fun `run throws ScriptNotFoundException when the requested child script is not in the package`() =
        runTest {
            // The fixture package has no child scripts, so any requested child class is unknown.
            val launchedBy = fixtures.aScriptInstance(fixtures.aScriptPackageInstance("instance-1", "com.cereal.test"))

            assertFailsWith<ScriptNotFoundException> {
                interactor.run(
                    StartChildScriptInteractor.Params(
                        launchedBy = launchedBy,
                        scriptCls = Script::class,
                        parameters = null,
                    ),
                )
            }
        }
}
