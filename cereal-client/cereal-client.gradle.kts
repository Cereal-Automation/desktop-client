import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.ksp)
    alias(libs.plugins.sekret)
    alias(libs.plugins.kotlin.serialization)
}

// White-label Brand descriptor (see docs/adr/0003 for the mechanism, docs/adr/0008
// for why the descriptors are not in this repository).
// `-Pbrand=<id>` selects cereal-client/brands/<id>/brand.properties; unset = stock Cereal.
// `brands/` is gitignored and supplied by the private overlay repository, so a branded
// build needs that checkout in place. Brand is a build axis orthogonal to the mock/prod flavor.
val brandId =
    project.findProperty("brand")?.toString()?.takeIf { it.isNotBlank() }
val brandProperties =
    Properties().apply {
        if (brandId != null) {
            val file = project.file("brands/$brandId/brand.properties")
            require(file.exists()) {
                "Unknown brand '$brandId': no descriptor at ${file.path}. Brand descriptors are " +
                    "gitignored — check out the private overlay repository into cereal-client/brands/ " +
                    "(see docs/adr/0008)."
            }
            file.inputStream().use { load(it) }
        }
    }

// Read a Brand value, falling back to the stock default when no brand is selected
// (or the key is absent from the descriptor).
fun brand(
    key: String,
    default: String,
): String = brandProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: default

// Resolve a Brand-specific asset file, falling back to the stock asset when the
// Brand does not override it (e.g. icons). Lets a Brand ship without its own icons.
fun brandAsset(relativePath: String): File {
    val branded = brandId?.let { project.file("brands/$it/$relativePath") }
    return if (branded != null && branded.exists()) branded else project.file(relativePath)
}

buildConfig {
    buildConfigField("APP_NAME", project.properties["name"].toString())
    buildConfigField("APP_VERSION", project.properties["version"].toString())

    // Brand identity. BRAND_ID is empty for stock Cereal and acts as the runtime
    // "is this a branded build" signal (consumed by CerealConfiguration in Phase 1).
    buildConfigField("BRAND_ID", brandId ?: "")
    buildConfigField("BRAND_NAME", brand("brand.name", "Cereal"))
    buildConfigField("BRAND_HOME_DIR", brand("brand.homeDir", "Cereal"))
    buildConfigField("BRAND_SCRIPT_IDS", brand("brand.scriptIds", ""))
    buildConfigField("BRAND_PAYWALL_URL", brand("brand.paywallUrl", ""))
    buildConfigField("BRAND_UPDATE_FEED_PATH", brand("brand.updateFeedPath", ""))
    buildConfigField("BRAND_COLOR_PRIMARY", brand("brand.color.primary", ""))
    buildConfigField(
        "SDK_VERSION",
        libs.versions.cereal.sdk
            .get(),
    )
    buildConfigField("IS_DEBUG", project.properties["debug"].toString().toBoolean())
    buildConfigField("IS_STORE_BUILD", project.properties["store_build"].toString().toBoolean())
    buildConfigField("FLAVOR", project.properties["flavor"].toString())
    buildConfigField("SENTRY_DSN", project.properties["sentry_dsn"].toString())
    buildConfigField("DOWNLOADS_BASE_URL", "https://downloads.cereal-automation.com/")
    buildConfigField("SEKRET_KEY", project.properties["sekret_key"].toString())
    buildConfigField(
        "com.cereal.client.application.Environment",
        "ENVIRONMENT",
        "Environment.fromValue(\"${project.properties["environment"]}\")",
    )

    // Copied from jetbrains compose "currentOS" (org.jetbrains.compose.internal.utils.osUtils.kt).
    val os = System.getProperty("os.name")
    val osIdentifier =
        when {
            os.equals("Mac OS X", ignoreCase = true) -> "macos"
            os.startsWith("Win", ignoreCase = true) -> "windows"
            os.startsWith("Linux", ignoreCase = true) -> "linux"
            else -> error("Unknown OS name: $os")
        }
    buildConfigField("String", "OPERATING_SYSTEM", "\"${osIdentifier}\"")
}

sekret {
    properties {
        enabled.set(true)
        packageName.set("com.cereal.client")
        encryptionKey.set(project.properties["sekret_key"].toString())
        propertiesFile.set(project.layout.projectDirectory.file("sekret.properties"))

        nativeCopy {
            desktopComposeResourcesFolder.set(project.layout.projectDirectory.dir("resources"))
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.cereal.sdk)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlin.reflect)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.result)
    implementation(libs.logback.classic)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.compose.components.resources)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    // Needed because we use Dispatchers.Main as described here: https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.1.1
    // We must replace this somewhere in the future with the solution proposed in the release.
    implementation(libs.coroutines.swing)
    implementation(libs.cache4k)
    implementation(libs.sentry.kotlin)
    // Needed for Discord RPC native bindings
    implementation(libs.java.native.`interface`)

    // Miscellaneous
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.semver)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.csv)
    implementation(libs.kamel)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kdriver)
    implementation(libs.markdown.renderer)

    // Jakarta Mail
    implementation(libs.jakarta.mail)
    implementation(libs.angus.mail)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.room.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit5)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)

    testImplementation(libs.compose.ui.test.junit4)
}

tasks {
    kotlin {
        jvmToolchain(21)

        compilerOptions {
            freeCompilerArgs.set(listOf("-jvm-default=enable", "-opt-in=kotlin.time.ExperimentalTime"))
            allWarningsAsErrors.set(true)
        }
    }

    kotlin {
        jvmToolchain(21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform() // This is required for JUnit 5
}

// The sandboxed `mock` flavor bundles script JARs so runnable scripts are available out-of-the-box
// without depending on the developer's filesystem (see SandboxScriptSeeder):
//   - the sample script built by :cereal-script-sample;
//   - any *.jar dropped into cereal-client/sandbox-scripts/ (local-only, git-ignored).
// Assumes the LOCAL environment, where script JARs are read as plaintext. This wiring lives inside
// the flavor gate, so production builds (-Pflavor=prod) never copy any of it. Bundled into
// resources only (not the compile classpath) so each script keeps its own isolated classloader.
if (project.properties["flavor"] == "mock") {
    tasks.named<ProcessResources>("processResources") {
        from(project(":cereal-script-sample").tasks.named("jar")) {
            into("sandbox-scripts")
        }
        from(layout.projectDirectory.dir("sandbox-scripts")) {
            into("sandbox-scripts")
            include("*.jar")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.cereal.client.presentation.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(
                "proguard-rules/cereal-client.pro",
                "proguard-rules/coroutines.pro",
                "proguard-rules/logback.pro",
                "proguard-rules/okhttp.pro",
                "proguard-rules/ktor.pro",
                "proguard-rules/kotlinx-serialization.pro",
                "proguard-rules/kotlinx-io.pro",
                "proguard-rules/room.pro",
                "proguard-rules/androidx-sqlite.pro",
                "proguard-rules/jakarta.pro",
                "proguard-rules/kdriver.pro",
                "proguard-rules/commons-logging.pro",
                "proguard-rules/jna.pro",
            )
            obfuscate.set(true)
            joinOutputJars.set(true)
            // 7.9.x fails on Kotlin 2.3 metadata ("can't find referenced class kotlin.RequiresOptIn.Level"
            // for the stdlib opt-in markers, NPE in KotlinReferenceFixer during obfuscation). Re-test on
            // the next ProGuard release before bumping — it also blocks the Kotlin 2.4 upgrade.
            version.set("7.8.2")
        }

        nativeDistributions {
            // Brand-driven (see top-of-file `brand()`); defaults to stock Cereal when unbranded.
            packageName = brand("brand.packageName", "Cereal")
            packageVersion = "${project.properties["version"]}"
            description = brand("brand.name", "Cereal") + " - Desktop Automation Client"
            vendor = "Cereal Automation"
            copyright = "Cereal Automation"

            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.security.jgss",
                "java.sql",
                // Loopback OAuth listener for brokered Google sign-in (SystemBrowserGoogleOAuthDataSource)
                // uses com.sun.net.httpserver.HttpServer, which lives in jdk.httpserver. Without it the
                // packaged (jlink-trimmed) runtime throws NoClassDefFoundError at sign-in time.
                "jdk.httpserver",
                "jdk.unsupported",
            )

            // Linux ships as a self-updating AppImage (built in CI from createReleaseDistributable),
            // so no .deb target is produced. See cereal-client/docs/linux-self-installing-update-plan.md.
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Exe,
                TargetFormat.Msi,
            )
            macOS {
                iconFile.set(brandAsset("icon-mac.icns"))
                // Mac requires version to be atleast 1.0.0 so force it as long as we have pre releases.
                if (project.properties["version"].toString().first() == '0') {
                    packageVersion = "1.0.0"
                }

                // Code signing configuration (used when signing properties are provided)
                val macSigningEnabled = project.findProperty("mac_signing_enabled")?.toString()?.toBoolean() ?: false
                if (macSigningEnabled) {
                    signing {
                        sign.set(true)
                        identity.set(project.findProperty("mac_signing_identity")?.toString() ?: "")
                        keychain.set(project.findProperty("mac_signing_keychain")?.toString() ?: "")
                    }

                    // Notarization configuration
                    notarization {
                        appleID.set(project.findProperty("mac_notarization_apple_id")?.toString() ?: "")
                        password.set(project.findProperty("mac_notarization_password")?.toString() ?: "")
                        teamID.set(project.findProperty("mac_notarization_team_id")?.toString() ?: "")
                    }
                }

                // Entitlements file for sandboxing (required for App Store)
                val entitlementsPath = project.findProperty("mac_entitlements_path")?.toString()
                if (!entitlementsPath.isNullOrBlank()) {
                    val rootFile = project.rootProject.file(entitlementsPath)
                    if (rootFile.exists()) {
                        entitlementsFile.set(rootFile)
                    } else {
                        val file = project.file(entitlementsPath)
                        entitlementsFile.set(file)
                    }
                }

                // Bundle ID required for signing. Brand-unique so builds install side-by-side.
                bundleID = brand("brand.bundleId", "com.cereal-automation.client")
            }
            windows {
                iconFile.set(brandAsset("icon-windows.ico"))
                dirChooser = true
                perUserInstall = true
                menu = true
                menuGroup = "Cereal Automation"
            }
            linux {
                iconFile.set(brandAsset("icon-linux.png"))
            }

            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
        }

        // Workaround for a crash in androidx.sqlite:sqlite-bundled native library on Ubuntu 24.04+
        // (glibc 2.39). The bundled SQLite JNI library uses std::ctype<wchar_t> locale facets
        // that are incompatible with the newer glibc ABI. Forcing the JDK's built-in COMPAT locale
        // provider prevents the JVM from passing an invalid locale pointer into the native code.
        // This is safe on all platforms and has no negative side effects.
        // See: https://issuetracker.google.com/issues/376570464
        jvmArgs("-Djava.locale.providers=COMPAT,SPI")

        // Set log level for Logback based on build type.
        val isDebugBuild = project.properties["debug"].toString().toBoolean()
        if (isDebugBuild) {
            jvmArgs("-DLOG_LEVEL=trace")
        } else {
            jvmArgs("-DLOG_LEVEL=info")
        }

        // Pass the environment home-directory suffix so Logback writes logs to the
        // correct environment-specific folder (e.g. ~/Cereal-Local/Logs, ~/Cereal-Acc/Logs,
        // ~/Cereal/Logs) — mirrors the suffix applied in CerealConfiguration.
        val homeSuffix =
            when (project.properties["environment"].toString()) {
                "acceptance" -> "-Acc"
                "local" -> "-Local"
                else -> ""
            }
        jvmArgs("-DCEREAL_HOME_SUFFIX=$homeSuffix")
    }
}

// Obfuscation smoke gate (local): build the obfuscated distributable and boot it headlessly with
// CEREAL_SMOKE_TEST=1 so it exercises the ProGuard-fragile paths and exits non-zero if any are
// broken. This is the local, one-command form of the CI release gate (.github/actions/smoke-test),
// and replaces the manual "build the prod distributable, launch it, and read session.log" ritual.
//
// Must be invoked with the same flags as a release build so it actually obfuscates the prod stack —
// the default `mock` flavor swaps in in-memory fakes and would hide the very regressions this
// catches:
//   ./gradlew :cereal-client:smokeTest -Pflavor=prod -Pdebug=false -Penvironment=production \
//     -Psekret_key=<key> -Psentry_dsn=<dsn>
tasks.register<Exec>("smokeTest") {
    group = "verification"
    description = "Boots the obfuscated distributable headlessly and fails if an obfuscation-fragile path is broken."
    dependsOn("createReleaseDistributable")

    val os =
        org.gradle.internal.os.OperatingSystem
            .current()
    val packageName = brand("brand.packageName", "Cereal")
    val appDir = layout.buildDirectory.dir("compose/binaries/main-release/app")
    val launcher =
        when {
            os.isMacOsX -> appDir.map { it.file("$packageName.app/Contents/MacOS/$packageName") }
            os.isWindows -> appDir.map { it.file("$packageName/$packageName.exe") }
            else -> appDir.map { it.file("$packageName/bin/$packageName") }
        }

    environment("CEREAL_SMOKE_TEST", "1")
    doFirst {
        if (project.properties["flavor"].toString() != "prod") {
            logger.warn(
                "smokeTest is running with flavor='${project.properties["flavor"]}'. The obfuscation gate " +
                    "is only meaningful against the prod flavor — re-run with -Pflavor=prod -Pdebug=false.",
            )
        }
        commandLine(launcher.get().asFile.absolutePath)
    }
}
