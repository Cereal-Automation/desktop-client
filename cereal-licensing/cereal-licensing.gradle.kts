apply(from = "../gradle/publishing.gradle")

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.bundles.kotlinx.serialization)

    // Exclude cereal-sdk in maven pom so that it can easily be excluded in consumers using this lib.
    // Every consumer that wants to use this licensing lib has to include cereal-sdk anyway.
    compileOnly(libs.cereal.sdk)

    testImplementation(libs.cereal.sdk)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.junit5)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}

tasks {
    kotlin {
        jvmToolchain(17)

        compilerOptions {
            freeCompilerArgs.set(listOf("-jvm-default=enable"))
            allWarningsAsErrors.set(true)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform() // This is required for JUnit 5
}
