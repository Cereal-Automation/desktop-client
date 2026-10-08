// PROTOTYPE — throwaway spike for wayfinder ticket #59. Not production code; lives only on prototype/agent-loop-spike.
apply(from = "../gradle/proguard.gradle")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(21) }

application { mainClass.set("spike.SpikeKt") }

// Host provides these (ScriptClassLoader parent-first): never bundled.
val hostProvided: Configuration by configurations.creating
configurations.compileClasspath { extendsFrom(hostProvided) }
configurations.runtimeClasspath { extendsFrom(hostProvided) }

dependencies {
    hostProvided(libs.cereal.sdk)
    hostProvided(libs.coroutines.core)
    hostProvided(libs.kotlin.stdlib) // host supplies kotlin.* (parent fallback); bundling it gets it renamed by ProGuard
    implementation("dev.kdriver:core:0.6.1")
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    standardInput = System.`in`
}

// Fat jar = what a marketplace script JAR would ship: everything except host-provided classes.
tasks.jar {
    archiveFileName.set("release.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("**/*.so", "**/*.dylib", "**/*.dll", "**/*.jnilib")
    val bundled = configurations.runtimeClasspath.get().minus(hostProvided.resolve())
    from(bundled.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("**/*.so", "**/*.dylib", "**/*.dll", "**/*.jnilib", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**", "module-info.class")
    }
}


// Prints the host-provided jars to put next to the shrunk JAR: java -cp build/obfuscated/release.jar:<these> spike.SpikeKt ...
tasks.register("printHostCp") { doLast { println(hostProvided.resolve().joinToString(":")) } }
