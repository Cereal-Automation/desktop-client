package com.cereal.client.application.task

import com.cereal.client.application.exception.CerealException
import com.cereal.client.application.exception.CrashReporter
import com.cereal.client.domain.model.script.ChildScriptInstance
import com.cereal.client.domain.model.script.getScriptPackageInstance
import com.cereal.client.domain.model.task.Task
import com.cereal.client.domain.model.task.TaskStatus
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.component.ComponentProvider
import com.cereal.sdk.component.script.ScriptParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.cereal.client.domain.model.script.Script as DomainScript

class TaskExecutor(
    private val task: Task,
    private val onStatusChange: suspend (message: String) -> Unit,
) : KoinComponent {
    @Volatile
    private var isTaskActive = true

    // Sequential lifecycle (start, execute-loop, error handling, cleanup) is clearest kept inline.
    @OptIn(ExperimentalTime::class)
    @Suppress("LongMethod", "LoopWithTooManyJumpStatements")
    suspend fun run(): TaskStatus {
        val manifest = task.scriptInstance.packageInstance.definition.manifest

        // Dedicated single-thread executor wrapped as a dispatcher so the script body
        // runs on a thread we can Thread.interrupt() if cooperative cancellation fails.
        val scriptExecutor = Executors.newSingleThreadExecutor()
        val scriptDispatcher = scriptExecutor.asCoroutineDispatcher()
        var script: Script<ScriptConfiguration>? = null
        var config: ScriptConfiguration? = null
        var componentProvider: ComponentProvider? = null

        var result: TaskStatus
        try {
            val scriptParameters = (task.scriptInstance as? ChildScriptInstance)?.params
            script = newInstance(task.scriptInstance.definition, scriptParameters)

            val taskScope =
                getKoin().getOrCreateScope(
                    task.id,
                    named<Task>(),
                    source = task,
                )

            componentProvider = taskScope.get<ComponentProvider>(ComponentProvider::class)
            config = createConfigurationProxy<ScriptConfiguration>()

            val start =
                runInterruptibleScript(scriptDispatcher) {
                    script.onStart(config, componentProvider)
                }

            if (!start) {
                componentProvider.logger().error("Failed to start script.")
                return TaskStatus.Error("Failed to initialize task.", timestamp = Clock.System.now())
            }

            while (true) {
                val executionResult =
                    runInterruptibleScript(scriptDispatcher) {
                        script.execute(config, componentProvider) {
                            if (isTaskActive) {
                                onStatusChange(it)
                            }
                        }
                    }

                when (executionResult) {
                    is ExecutionResult.Error -> {
                        result = TaskStatus.Error(executionResult.message, timestamp = Clock.System.now())
                        break
                    }

                    is ExecutionResult.Loop -> {
                        onStatusChange(executionResult.message)
                        delay(max(executionResult.delay, MIN_LOOP_DELAY_MS))
                    }

                    is ExecutionResult.Success -> {
                        val message = executionResult.message.ifBlank { "Script executed successfully." }
                        result = TaskStatus.Success(message, timestamp = Clock.System.now())
                        break
                    }
                }
            }
        } catch (cancellationException: CancellationException) {
            // Job was canceled
            isTaskActive = false
            result = TaskStatus.Idle(cancellationException.message, timestamp = Clock.System.now())
        } catch (cerealException: CerealException) {
            // Expected, script-originated error: report it without sending to Sentry.
            isTaskActive = false
            val message = cerealException.message ?: DEFAULT_ERROR_MESSAGE
            result = TaskStatus.Error(message, cerealException.stackTraceToString(), timestamp = Clock.System.now())
            componentProvider?.logger()?.error("Error executing task: ${cerealException.message}")
        } catch (e: Exception) {
            isTaskActive = false
            result = TaskStatus.Error(DEFAULT_ERROR_MESSAGE, e.stackTraceToString(), timestamp = Clock.System.now())
            componentProvider?.logger()?.error("Error executing task: ${e.message}")

            CrashReporter.report(
                e,
                mapOf(
                    "script_name" to manifest.name,
                    "script_package" to manifest.packageName,
                    "script_version_code" to manifest.versionCode,
                    "task_configuration" to task.configuration,
                ),
            )
        } finally {
            isTaskActive = false
            scriptDispatcher.close()
            scriptExecutor.shutdownNow()
            if (script != null && config != null && componentProvider != null) {
                safeFinishScript(script, config, componentProvider)
            }
        }

        return result
    }

    private suspend fun <T> runInterruptibleScript(
        dispatcher: CoroutineDispatcher,
        block: suspend () -> T,
    ): T =
        runInterruptible(dispatcher) {
            runBlocking { block() }
        }

    private suspend fun safeFinishScript(
        script: Script<ScriptConfiguration>,
        config: ScriptConfiguration,
        componentProvider: ComponentProvider,
    ) {
        try {
            script.onFinish(config, componentProvider)
        } catch (ce: CancellationException) {
            throw ce
        } catch (cerealException: CerealException) {
            // Expected, script-originated error: surface it without sending to Sentry.
            componentProvider.logger().error("Script failed to finish: ${cerealException.message}")
        } catch (exception: Exception) {
            componentProvider.logger().error("Script failed to finish: ${exception.message}")
            CrashReporter.report(exception)
        }
    }

    private fun <T> createConfigurationProxy(): T {
        val clazz = task.scriptInstance.definition.configuration.scriptConfigurationClass

        if (!Modifier.isPublic(clazz.java.modifiers)) {
            throw RuntimeException("Non-public configuration classes can't have default methods invoked")
        }

        val invocationHandler =
            ConfigInvocationHandler(
                task.scriptInstance.getScriptPackageInstance(),
                task.scriptInstance.definition.configuration,
                task.configuration,
            )

        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(clazz.java.classLoader, arrayOf(clazz.java), invocationHandler) as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun newInstance(
        script: DomainScript,
        parameters: ScriptParameters?,
    ): Script<ScriptConfiguration> {
        val constructor = script.clazz.constructors.first()
        constructor.isAccessible = true

        return if (constructor.parameters.size == 1 &&
            constructor.parameters
                .first()
                .type.classifier == ScriptParameters::class
        ) {
            constructor.call(parameters) as Script<ScriptConfiguration>
        } else {
            constructor.call() as Script<ScriptConfiguration>
        }
    }

    companion object {
        const val MIN_LOOP_DELAY_MS = 50L
        private const val DEFAULT_ERROR_MESSAGE = "The script failed without error message."
    }
}
