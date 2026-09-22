# Dependency Updates

This project uses the [gradle-versions-plugin](https://github.com/ben-manes/gradle-versions-plugin) to check for available dependency updates. The plugin is configured to only show stable versions, filtering out alpha, beta, release candidate, and snapshot versions.

## Usage

To check for dependency updates, run:

```bash
./gradlew dependencyUpdates
```

This will generate a report showing:
- Dependencies that are using the latest stable version
- Dependencies that have newer stable versions available
- Dependencies where the latest version check failed

## Report Output

The plugin generates two types of reports:

1. **Console Output**: Displays the results directly in the terminal
2. **Text Report**: Saves a detailed report to `build/dependencyUpdates/report.txt`

## Configuration

The plugin is configured in `build.gradle.kts` with the following settings:

- **Stable Versions Only**: The plugin filters out non-stable versions using a custom `rejectVersionIf` configuration
- **Version Stability Detection**: Versions are considered stable if they:
  - Contain keywords like "RELEASE", "FINAL", or "GA"
  - Match the pattern `^[0-9,.v-]+(-r)?$` (numeric versions with optional release suffix)

## Example Output

```
The following dependencies have later milestone versions:
 - com.google.guava:guava [33.0.0-jre -> 33.4.8-jre]
 - org.jetbrains.kotlin:kotlin-stdlib [2.0.21 -> 2.1.21]
```

## Updating Dependencies

After reviewing the report, you can update dependencies by:

1. Updating version numbers in `gradle/libs.versions.toml`
2. Running tests to ensure compatibility
3. Committing the changes

## Notes

- The plugin respects the current version catalog structure in `gradle/libs.versions.toml`
- Some plugin dependencies may show as "Failed to determine" - this is normal for Gradle plugins
- The filtering ensures you only see production-ready versions, avoiding unstable releases
