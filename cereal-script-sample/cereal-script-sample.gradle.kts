apply(from = "../gradle/proguard.gradle")

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.cereal.sdk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.cereal.test.utils)
    testImplementation(libs.mockk)
    implementation(libs.coroutines.core)
}

tasks {
    jar {
        archiveFileName.set("release.jar")
    }

    kotlin {
        jvmToolchain(21)

        compilerOptions {
            freeCompilerArgs.set(listOf("-jvm-default=enable"))
            allWarningsAsErrors.set(true)
        }
    }
}
