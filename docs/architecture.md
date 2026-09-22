# Cereal Client - Clean Architecture

## Overview

The `cereal-client` module follows **Clean Architecture** principles to ensure separation of concerns, maintainability, and testability. The architecture is organized into four distinct layers, each with specific responsibilities and clear boundaries.

## Architecture Layers

```
cereal-client/
└── src/main/java/com/cereal/client/
    ├── application/              # Application Layer (Use Cases)
    ├── domain/                   # Domain Layer (Business Logic)
    ├── infrastructure/           # Infrastructure Layer (Technical Details)
    └── presentation/             # Presentation Layer (UI)
```

### 1. Application Layer (`application/`)

**Purpose**: Orchestrates application use cases and coordinates between the domain and infrastructure layers.

**Responsibilities**:
- Implement use cases (interactors) that represent business operations
- Coordinate data flow between domain and infrastructure layers
- Handle application configuration and environment settings
- Manage cross-cutting concerns like authentication and error handling

**Key Concepts**:
- **Interactors**: Single-responsibility use cases that orchestrate business operations
- Each interactor depends only on domain interfaces (repositories), never concrete implementations
- Interactors are organized by feature domain (e.g., `account/`, `task/`, `script/`)
- Returns domain models or application-specific DTOs

**Naming Convention**: `[Verb][Noun]Interactor` (e.g., `CreateTaskInteractor`, `GetUserProfileInteractor`)

### 2. Domain Layer (`domain/`)

**Purpose**: Contains core business logic, entities, and contracts. This is the heart of the application with zero dependencies on external frameworks or other layers.

**Responsibilities**:
- Define business entities and domain models (in `model/`)
- Declare repository interfaces that define data access contracts (in `repository/`)
- Contain pure business rules and domain logic
- Define domain-specific exceptions
- Remain framework-agnostic and highly testable

**Structure**:
```
domain/
├── model/                          # Business entities and value objects
│   └── [feature]/                  # Organized by business domain
└── repository/                     # Repository interface contracts
    ├── [Feature]Repository.kt      # Data access interfaces
    └── datasource/                 # Data source interfaces
```

**Key Concepts**:
- **Entities**: Core business objects with identity and lifecycle
- **Value Objects**: Immutable objects representing domain concepts
- **Repository Interfaces**: Define data access contracts without revealing implementation details
- **Domain Exceptions**: Represent business rule violations

**Dependency Rule**: The domain layer has **zero outward dependencies**. All other layers depend on the domain.

**Naming Convention**: `[Noun]Repository` for repository interfaces (e.g., `TaskRepository`, `UserRepository`)

### 3. Infrastructure Layer (`infrastructure/`)

**Purpose**: Provides concrete implementations of domain interfaces and handles all technical and framework-specific details.

**Responsibilities**:
- Implement repository interfaces defined in the domain layer
- Manage data persistence (database, files, caching)
- Handle external service integrations (network, APIs)
- Configure dependency injection
- Provide technical utilities and platform-specific implementations

**Structure**:
```
infrastructure/
├── data/
│   ├── datasource/                 # Raw data access implementations
│   └── repository/                 # Repository implementations
├── di/                             # Dependency injection configuration
└── [technical-concerns]/           # Other infrastructure needs
```

**Key Concepts**:
- **Repository Implementations**: Concrete classes fulfilling domain repository contracts
- **Data Sources**: Handle raw data access (database DAOs, file system, network clients)
- **Adapters**: Transform between external formats and domain models
- **Dependency Injection**: Wire up implementations to interfaces

**Dependency Direction**: Infrastructure depends on domain (implements domain interfaces), but domain never depends on infrastructure.

### 4. Presentation Layer (`presentation/`)

**Purpose**: Handles all user interface concerns, user interactions, and presentation logic.

**Responsibilities**:
- Render UI components and screens
- Handle user input and events
- Manage UI state and navigation
- Transform domain data into user-friendly formats
- Implement ViewModels that coordinate with application layer interactors

**Structure**:
```
presentation/
├── [feature]/                      # Feature-specific UI screens
├── model/                          # UI-specific models (separate from domain)
├── navigation/                     # Screen navigation logic
├── theme/                          # Design system and styling
├── util/                           # UI utilities
└── view/                           # Reusable UI components
```

**Key Concepts**:
- **Composables**: Declarative UI components
- **ViewModels**: Manage UI state and coordinate with interactors
- **UI Models**: Presentation-specific data structures optimized for display
- **Navigation**: Screen routing and flow management
- **Separation**: Presentation models are separate from domain models

**Dependency Direction**: Presentation depends on application layer (calls interactors), never directly accesses infrastructure or domain repositories.

## Clean Architecture Principles

### The Dependency Rule

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│    (UI, ViewModels, Composables)        │
└────────────────┬────────────────────────┘
                 │ depends on
                 ▼
┌─────────────────────────────────────────┐
│        Application Layer                │
│  (Use Cases/Interactors, Orchestration) │
└────────────┬───────────────┬────────────┘
             │               │
   depends on│               │depends on
             ▼               ▼
┌─────────────────────────────────────────┐
│          Domain Layer                   │
│  (Entities, Repository Interfaces)      │  ◄── No Dependencies
└─────────────────▲───────────────────────┘
                  │
        implements│
                  │
┌─────────────────────────────────────────┐
│       Infrastructure Layer              │
│  (Repository Impls, Database, Network)  │
└─────────────────────────────────────────┘
```

**Core Principle**: Dependencies point **inward** toward the domain. The domain layer has zero dependencies on outer layers.

### Benefits

- **Testability**: Test each layer in isolation using mocks/stubs for dependencies
- **Maintainability**: Clear boundaries make changes predictable and localized
- **Flexibility**: Swap implementations without affecting business logic
- **Framework Independence**: Business rules don't depend on UI or database frameworks
- **Understandability**: Clear separation of "what" (domain) from "how" (infrastructure)

## Data Flow Example

Here's how a typical operation flows through the layers:

### Example: User Creates a Task

```
User clicks button → ViewModel → Interactor → Repository Interface → Repository Implementation → Database
                                      ↓              ↓                         ↓
                                 Application      Domain                Infrastructure
```

1. **Presentation**: User interacts with UI
   ```kotlin
   viewModel.onCreateTask(name = "My Task")
   ```

2. **Application**: ViewModel calls interactor
   ```kotlin
   createTaskInteractor.execute(name)
   ```

3. **Application**: Interactor uses domain repository interface
   ```kotlin
   val task = Task(name = name)
   taskRepository.save(task)
   ```

4. **Infrastructure**: Repository implementation persists data
   ```kotlin
   database.taskDao().insert(task.toEntity())
   ```

**Key Points**:
- Each layer only knows about its immediate dependencies
- Domain entities flow through all layers
- Infrastructure details are hidden behind interfaces
- Testing is easy: mock the repository interface in tests

## Development Guidelines

### Adding a New Feature

Follow this sequence to maintain architectural integrity:

1. **Domain First**: Define your entities and repository interface in `domain/`
   - Create domain models that represent business concepts
   - Define repository interface methods for data access

2. **Application Layer**: Create the interactor in `application/interactor/[feature]/`
   - Implement the use case logic
   - Depend only on domain repository interfaces
   - Name it `[Verb][Noun]Interactor`

3. **Infrastructure Layer**: Implement the repository in `infrastructure/data/repository/`
   - Provide concrete implementation of the domain repository interface
   - Handle data persistence, caching, or external service calls

4. **Presentation Layer**: Build the UI in `presentation/[feature]/`
   - Create composables for the UI
   - Create ViewModel that calls the interactor
   - Transform domain models to UI models as needed

5. **Test**: Write tests for each layer independently
   - Domain: Test business logic
   - Application: Test interactor with mocked repositories
   - Infrastructure: Test repository implementation
   - Presentation: Test ViewModel with mocked interactors

### Naming Conventions

- **Interactors**: `[Verb][Noun]Interactor` (e.g., `CreateTaskInteractor`, `GetUserProfileInteractor`)
- **Repository Interfaces**: `[Noun]Repository` (e.g., `TaskRepository`, `UserRepository`)
- **Repository Implementations**: `[Noun]RepositoryImpl` (e.g., `TaskRepositoryImpl`)
- **Domain Models**: Business-focused names (e.g., `Task`, `User`, `Script`)
- **ViewModels**: `[Feature]ViewModel` (e.g., `TasksViewModel`, `ProfileViewModel`)

### Package Organization

- **By Feature**: Organize packages by business feature/domain, not technical function
- **Cohesion**: Keep related classes together within their layer
- **One Class Per File**: Maintain clarity with single-class files

## Testing Strategy

Each layer should be independently testable:

- **Domain Layer**: Pure business logic tests with no mocks needed
- **Application Layer**: Test interactors by mocking repository interfaces
- **Infrastructure Layer**: Test repository implementations with test databases or mock data sources
- **Presentation Layer**: Test ViewModels by mocking interactors

## Key Takeaways

✅ **Do**:
- Keep domain layer free of framework dependencies
- Use repository interfaces defined in domain layer
- Make interactors single-purpose and focused
- Separate UI models from domain models
- Test each layer in isolation

❌ **Don't**:
- Let domain depend on infrastructure or presentation
- Access repositories directly from ViewModels
- Mix business logic into infrastructure or presentation
- Use framework-specific types in domain layer
- Create circular dependencies between layers
