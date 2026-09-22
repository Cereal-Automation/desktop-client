# Presentation Layer

**Purpose:** Delivery mechanism translating user interactions (Compose UI events) into application layer calls, and rendering application output as Compose UI state.

---

## Contents

| Type | Naming Convention | Example |
|---|---|---|
| Screen composables | `[Feature]Screen` | `TasksScreen.kt`, `LoginScreen.kt` |
| Window composables | `[Feature]Window` | `MainWindow.kt`, `BootstrapWindow.kt` |
| Dialog composables | `[Subject]Dialog` | `ScriptDetailDialog.kt`, `ImportFromFileDialog.kt` |
| Form content composables | `[Feature]FormContent` | `LoginFormContent.kt` |
| ViewModels | `[Feature]ViewModel` | `TasksViewModel.kt`, `LoginViewModel.kt` |
| ViewState (sealed classes) | `[Feature]ViewState` | `TaskViewState.kt`, `ProxyViewState.kt` |
| Field state classes | `[Type]FieldState` / `[Type]TextFieldState` | `StringTextFieldState.kt`, `DropDownFieldState.kt` |
| Field validators | `[Type]FieldValidator` | `RequiredStringFieldValidator.kt` |
| UiModels | `[Domain]UiModel` | `TaskUiModel.kt`, `ScriptInstanceUiModel.kt` |
| Mappers | `[Domain]UiMapper` | `TaskUiMapper.kt`, `GroupListItemContentMapper.kt` |
| Shared components | `Cereal[Component]` | `CerealOutlinedTextField.kt`, `CerealSnackbar.kt` |
| Navigation | — | `Router.kt`, `BackStack.kt`, `Root.kt` |
| Theme | — | `CerealTheme.kt`, `Colors.kt`, `Typography.kt` |
| Utilities | — | `Extensions.kt`, `ImageUtil.kt` |

---

## Responsibilities

- **Input validation** via field state classes (`FormFieldState<T, V>`) and validator objects — not in composables directly.
- **Auth/identity extraction** — extract identity from state and pass to interactors as parameters; never enforce auth rules here.
- **Map interactor output → UiModel → ViewState** — domain objects must not leak into composables.
- **Invoke interactors** — one interactor call per user action; orchestration belongs in the application layer.
- **Manage UI state** — expose `mutableStateOf` / `State<T>` from ViewModels; composables observe and render.
- **Error presentation** — observe `ErrorResolver.errorAction` and render `ErrorView` / `ErrorDialog` accordingly.

---

## Disallowed

- Business rule enforcement (belongs in domain).
- Direct access to repositories, DAOs, or any infrastructure class.
- Orchestrating multiple interactors in a single user action (belongs in the application layer).
- Domain entities or value objects referenced directly in composables — always map to UiModels first.
- Business decisions inside composables (e.g., conditional logic based on domain rules).

---

## Design Notes

### ViewModels
- Plain Kotlin classes — **no framework base class**.
- Constructor receives `CoroutineScope` and `CoroutinesDispatcherProvider`.
- State is held as `mutableStateOf<T>` and exposed as `val state: State<T>`.
- Side-effect events use `MutableSharedFlow`.
- Interactors are called on `dispatcherProvider.io`; switch back to main with `withContext(dispatcherProvider.main)`.
- Error results handled with `handleFailureOrElse(errorResolver)` on `SuspendableResult`.

### Composables
- Keep composables thin — **no business logic**.
- Receive ViewModel as a parameter (defaulting to Koin `get()`) to stay testable.
- Hoist state up; use unidirectional data flow.
- Large features decompose into: ViewModel + ActionHandler + DialogManager + ListObserver + ViewStateBuilder.

### ViewState
- Model all possible UI states as a `sealed class` hierarchy.
- States carry all data the composable needs: `NoSelection`, `Loading`, `Filled(data)`, `Empty`, `Error`.

### Navigation
- Custom `BackStack<T>` driven by `mutableStateOf` — no third-party nav library.
- Use `Router` composable with `push`, `pop`, `replace`, `newRoot`.
- `Root.kt` defines the top-level `Routing` sealed class and renders screens via `when (backStack.last())`.

### Error Handling
- `ErrorResolver` is injected into the ViewModel and exposes `errorAction: State<ErrorAction>`.
- Composables observe `errorAction` and render the appropriate error UI.
- Never catch and silently discard errors in composables.

---

## Testing

- **Unit tests** for ViewModels using JUnit 5 + Mockk + `runTest`.
- Mock interactors with `mockk<T>(relaxed = true)`; use `coEvery` / `coVerify` for suspending calls.
- Use `slot<T>()` to capture interactor arguments for assertion.
- Test ViewState transitions: assert correct state emitted for each interactor outcome.
- Field validators are pure functions — test them directly without mocking.

```kotlin
@Test
fun `login should emit error state when credentials are invalid`() = runTest {
    coEvery { loginInteractor.run(any()) } throws InvalidCredentialsException()

    viewModel.login(email = "a@b.com", password = "wrong")

    assertEquals(LoginViewState.Error, viewModel.viewState.value)
}
```

---

## PR Checklist

- [ ] No business logic in composables or ViewModels
- [ ] Domain objects mapped to UiModels before reaching composables
- [ ] ViewState sealed class covers all UI states (loading, empty, filled, error)
- [ ] Interactor invoked with validated parameters; result mapped to ViewState
- [ ] Errors handled via `handleFailureOrElse(errorResolver)`
- [ ] ViewModel is a plain Kotlin class (no framework base class)
- [ ] Composable receives ViewModel as a parameter (testable)

---

## Anti-Patterns

- **Thick composables** — composable functions containing conditional business logic or calling interactors directly.
- **Domain leakage** — passing domain entities or value objects into composables without mapping to UiModels.
- **Duplicating validation** — re-implementing domain invariants in field validators; transport-level syntactic validation (types, format, required) is fine.
- **Fat ViewModels** — a ViewModel doing state management, side effects, dialog orchestration, and flow observation all at once; extract `ActionHandler`, `DialogManager`, `ListObserver` for complex features.
- **Hardcoded error strings** — error messages embedded in ViewModels; use `ErrorResolver` and resource references.

---

## Example Skeletons

### ViewModel

```kotlin
class LoginViewModel(
    private val loginInteractor: LoginInteractor,
    private val errorResolver: ErrorResolver,
    coroutineScope: CoroutineScope,
    dispatcherProvider: CoroutinesDispatcherProvider,
) {
    private val _viewState = mutableStateOf<LoginViewState>(LoginViewState.Idle)
    val viewState: State<LoginViewState> = _viewState

    val loginSuccess = MutableSharedFlow<Unit>()

    fun login(email: String, password: String) {
        coroutineScope.launch(dispatcherProvider.io) {
            _viewState.value = LoginViewState.Loading
            loginInteractor.run(LoginInteractor.Params(email = email, password = password))
                .handleFailureOrElse(errorResolver) {
                    withContext(dispatcherProvider.main) {
                        loginSuccess.emit(Unit)
                    }
                }
        }
    }
}
```

### Screen Composable

```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel = get()) {
    val viewState by viewModel.viewState
    val errorAction by viewModel.errorResolver.errorAction

    LoginFormContent(
        viewState = viewState,
        onLogin = { email, password -> viewModel.login(email, password) },
    )

    ErrorHandler(errorAction = errorAction)
}
```
