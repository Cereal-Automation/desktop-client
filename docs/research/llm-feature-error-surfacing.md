# How an error from `provider.llm()` reaches the user and Sentry

Research for [#117](https://github.com/Cereal-Automation/desktop-client/issues/117).

**Question.** When `provider.llm()` throws because the manifest doesn't declare the `llm` script feature, what do
the user and the script author see (task log line, task end state, Sentry)? Is a plain `IllegalStateException`
enough, or should it be a `CerealException` subclass?

**Sources.** desktop-client `origin/master` @ `529c7f5` (paths below are under
`cereal-client/src/main/java/com/cereal/client/` unless they start with `cereal-client/`); SDK `origin/HEAD` @
`80614fa` (paths under `cereal-sdk/src/main/java/com/cereal/sdk/`, marked "SDK"). Line numbers are from those
commits.

## Answer

Use a `CerealException` subclass. A plain `IllegalStateException` sends an author's manifest mistake to Sentry
as a client crash and hides the explanation from the user behind "The script failed without error message."
A `CerealException` subclass shows its message as the task's error status, writes it to the task log, and stays
out of Sentry. `TaskExecutor` already classifies `CerealException` this way, so it needs no change.

## Where the exception goes

Script code calls `provider.llm()` from `Script.onStart`, `Script.execute` or `Script.onFinish` (lifecycle in
SDK `Script.kt:5-58`). `ComponentProviderImpl.llm()` is a plain getter today
(`infrastructure/sdkcomponent/ComponentProviderImpl.kt:37`); the check would throw from there, on the script's
thread, before any `LlmComponent` call.

The SDK sets no contract for exceptions thrown out of a script. `Script.kt` describes outcomes only through
`ExecutionResult` (SDK `ExecutionResult.kt:13-42`), and the one documented "an uncaught exception fails the task
with its message" promise covers `LlmException` (SDK `component/llm/LlmException.kt:5-8`). How a script
exception surfaces is decided entirely by the client.

### onStart / execute

1. `TaskManager.startTask` runs the script in `scope.launch { executor.run() }` and stores the returned status
   (`application/task/TaskManager.kt:156-161`). This is **not** inside an `Interactor`, so `Interactor`'s
   `RuntimeException` to Sentry branch (`application/Interactor.kt:136-142`) never sees a script exception.
2. `TaskExecutor.run` catches every `Exception` from `onStart`/`execute` (`application/task/TaskExecutor.kt:110`).
3. **Task log line**, written whatever the type:
   `componentProvider.logger().error("Error executing task: ${e.message}")` (`TaskExecutor.kt:112`). It goes
   through `LoggerComponentImpl` (`infrastructure/sdkcomponent/LoggerComponentImpl.kt:10-21`) to
   `LoggerProviderImpl.error`, which logs to SLF4J and persists the event to the task log
   (`infrastructure/provider/LoggerProviderImpl.kt:36-37`). SLF4J is not wired to Sentry: `logback.xml` has only
   the `STDOUT` and `FILE` appenders (`cereal-client/src/main/resources/logback.xml:3,9,32-33`), and the build
   applies no Sentry Gradle plugin and depends only on `sentry-kotlin`
   (`cereal-client/cereal-client.gradle.kts:6-13,129`).
4. It then branches on `isExpectedScriptError()`, defined as `this is CerealException || this is LlmException`
   (`TaskExecutor.kt:202`):

| | `IllegalStateException` | `CerealException` subclass |
|---|---|---|
| Task log | `Error executing task: <message>` (`TaskExecutor.kt:112`) | same |
| End state | `TaskStatus.Error("The script failed without error message.", stackTrace)` (`TaskExecutor.kt:117`, constant at `:206`) | `TaskStatus.Error(<message>, stackTrace)` (`TaskExecutor.kt:115`) |
| Sentry | Yes: `CrashReporter.report(e, …)` with `script_name`, `script_package`, `script_version_code`, `task_configuration` (`TaskExecutor.kt:118-126`) | No |

The task row shows `TaskStatus.Error.message` as its status text
(`presentation/tasks/mappers/TaskUiMapper.kt:55,85-89`) and keeps the stack trace for the details view
(`TaskUiMapper.kt:63`). With an `IllegalStateException` the user sees the generic "The script failed without
error message." in the task list; the explanation is only in the task log.

Nothing downstream drops an `IllegalStateException`. `CrashReporter.report` skips only exceptions with an
`EOFException`, `CancellationException` or `SocketTimeoutException` in the cause chain
(`application/exception/CrashReporter.kt:20-22`, `application/exception/ExceptionFilter.kt:8-11`), and the global
`beforeSend` (`infrastructure/crashreporting/CrashReportingClient.kt:43`) drops only those types plus a kdriver
shutdown `IllegalAccessError` (`ExceptionFilter.kt:13-23`). The exception is reported once per failed run.

### onFinish

`safeFinishScript` logs `Script failed to finish: <message>` and reports to Sentry unless
`isExpectedScriptError()` (`TaskExecutor.kt:148-161`). The end state is already set by then; the same type rule
decides Sentry.

## Why not the alternatives

- **`IllegalStateException` (`check`/`error`).** Reported to Sentry as a client error with the author's package
  attached (`TaskExecutor.kt:118-126`), once per run, for a bug that belongs to the script author and is fixed
  in their manifest. The user gets the generic message (`TaskExecutor.kt:117`).
- **Reusing an `LlmException` subtype.** `LlmException` is sealed in the SDK (SDK `LlmException.kt:11`), so the
  client can't add a subtype. Reusing `NotConfigured` would be wrong too: its KDoc invites scripts to catch it
  and "fall back to non-LLM behaviour" (SDK `LlmException.kt:15-18`), which would hide the author's mistake
  instead of failing the task.
- **`CerealException` subclass.** Already the "expected, script-originated" type in `TaskExecutor`
  (`TaskExecutor.kt:113-115,160,202`) and in the `Interactor` pipeline (`Interactor.kt:102-104`). The closest
  precedent is `ClientUpdateRequiredException`, the user-readable `CerealException` for a manifest
  `sdk_version` the client can't satisfy (`application/exception/ClientUpdateRequiredException.kt:3-7`). The
  class belongs in `application/exception/`, which `infrastructure/sdkcomponent/` may import.

## Notes for the implementation

- The message is the task's status text, so make it actionable for both readers: name the feature and the
  manifest key (e.g. that the script uses the `llm` feature without declaring it in `manifest.json`), so a
  user can forward it to the author.
- A script that wraps its LLM calls in `catch (e: LlmException)` won't catch this (it isn't an `LlmException`),
  so the task fails as intended. A broad `catch (e: Exception)` in the script would swallow it; the client
  can't prevent that.
- Because the classification already exists, one test at the `TaskExecutor` seam (a script that calls
  `provider.llm()` without declaring the feature ends in `TaskStatus.Error(<that message>)`) covers it.
