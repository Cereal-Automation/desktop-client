# Taken from https://github.com/ktorio/ktor/blob/main/ktor-utils/jvm/resources/META-INF/proguard/ktor.pro

# Most of volatile fields are updated with AtomicFU and should not be mangled/removed
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

-keepclassmembernames class io.ktor.** {
    volatile <fields>;
}

# client engines are loaded using ServiceLoader so we need to keep them
-keep interface io.ktor.client.HttpClientEngineContainer
-keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer
-keepclassmembers class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer {
    <init>();
}
