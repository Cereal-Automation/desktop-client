# Base ProGuard configuration for the Cereal client release build.
# Library-specific rules live in the sibling *.pro files wired up in cereal-client.gradle.kts.
-verbose

## Include java runtime classes
-libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.desktop.jmod(!**.jar;!module-info.class)

# Write out an obfuscation mapping file, for de-obfuscating any stack traces
# later on, or for incremental obfuscation of extensions.

-printmapping ../proguard.map

# Don't print notes about reflection in GSON code, the Kotlin runtime, and
# our own optionally injected code.

-dontnote kotlin.**
-dontnote kotlinx.**
-dontnote proguard.configuration.ConfigurationLogger

# NOTE: `-overloadaggressively` is intentionally NOT enabled. It lets fields that
# differ only by type share one obfuscated name (e.g. every field of
# okhttp3.internal.connection.RealCall collapsing to `a`). OkHttp 5.4.0's RealCall
# static initializer calls
#   AtomicReferenceFieldUpdater.newUpdater(RealCall, EventListener, "eventListener")
# ProGuard adapts the "eventListener" field-name string to the obfuscated name, but
# AtomicReferenceFieldUpdaterImpl resolves the field reflectively by name only. With
# name collisions it picks the first field of that name, whose type != EventListener,
# throwing ClassCastException in <clinit> — surfacing as
# "NoClassDefFoundError: Could not initialize class RealCall" on any HTTP call.

# Put all obfuscated classes into the nameless root package.

-repackageclasses ''

# Allow classes and class members to be made public.

-allowaccessmodification

# Save the obfuscation mapping to a file, so you can de-obfuscate any stack
# traces later on. Keep a fixed source file attribute and all line number
# tables to get line numbers in the stack traces.
# You can comment this out if you're not interested in stack traces.

-renamesourcefileattribute SourceFile

# Preserve all annotations.

-keepattributes *Annotation*

# Preserve the special static methods that are required in all enumeration
# classes.

-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Explicitly preserve all serialization members. The Serializable interface
# is only a marker interface, so it wouldn't save them.
# You can comment this out if your application doesn't use serialization.
# If your code contains serializable classes that have to be backward
# compatible, please refer to the manual.

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# When editing this file, update the following files as well:
# - META-INF/com.android.tools/proguard/kotlin-reflect.pro
# - META-INF/com.android.tools/r8-from-1.6.0/kotlin-reflect.pro
# - META-INF/com.android.tools/r8-upto-1.6.0/kotlin-reflect.pro
# Keep Metadata annotations so they can be parsed at runtime.
-keep class kotlin.Metadata { *; }

# Keep implementations of service loaded interfaces
# R8 will automatically handle these these in 1.6+
-keep interface kotlin.reflect.jvm.internal.impl.builtins.BuiltInsLoader
-keep class * implements kotlin.reflect.jvm.internal.impl.builtins.BuiltInsLoader { public protected *; }
-keep interface kotlin.reflect.jvm.internal.impl.resolve.ExternalOverridabilityCondition
-keep class * implements kotlin.reflect.jvm.internal.impl.resolve.ExternalOverridabilityCondition { public protected *; }

# Keep generic signatures and annotations at runtime.
# R8 requires InnerClasses and EnclosingMethod if you keepattributes Signature.
-keepattributes InnerClasses,Signature,RuntimeVisible*Annotations,EnclosingMethod

# Don't note on API calls from different JVM versions as they're gated properly at runtime.
-dontnote kotlin.internal.PlatformImplementationsKt

# Don't note on internal APIs, as there is some class relocating that shrinkers may unnecessarily find suspicious.
-dontwarn kotlin.reflect.jvm.internal.**

### Cereal Application ###
# Keep entry point of application
-keepclasseswithmembers public class com.cereal.* {
    public static void main(java.lang.String[]);
}

# Keep the Windows self-update helper entry point. It is launched reflectively — its class name is
# passed on the `javaw -cp ... com.cereal.client.updater.UpdaterMain` command line by
# WindowsUpdateInstaller — so the shrinker cannot trace it from a static call and would otherwise
# rename/remove it, breaking the silent update. Keeping the entry retains its reachable graph
# (UpdateApplier, FileSha256).
-keep class com.cereal.client.updater.UpdaterMain {
    public static void main(java.lang.String[]);
}

# Keep SDK
-keep class com.cereal.sdk.** { *; }
-keep interface com.cereal.sdk.** { *; }

-keep class com.cereal.client.Sekret { *; }

# Suppress warnings for missing optional classes referenced by dependencies on the classpath.
-dontwarn org.apache.commons.**
-dontwarn org.koin.compose.**
-dontwarn kotlin.concurrent.atomics.**
-dontwarn kotlin.jvm.internal.EnhancedNullability
# markdown-renderer (com.mikepenz) was compiled against a Compose build whose
# androidx.compose.ui.graphics.painter.Painter carried a synthetic `int $stable`
# stability marker. Compose 1.10.1's Painter has no such field, so its image/text
# composables reference a field that no longer exists. The reference is inert at
# runtime (a non-obfuscated debug build behaves identically), so silence the
# resulting "can't find referenced field 'int $stable'" warnings.
-dontwarn com.mikepenz.markdown.**

# Pin kotlinx.coroutines wholesale. The canonical coroutines.pro (ServiceLoader factories +
# AtomicFU volatile fields) is NOT sufficient on its own here: letting ProGuard optimize the
# coroutines classes produces invalid bytecode (java.lang.VerifyError: Bad return type at
# MainKt.<clinit>) on the obfuscated release build. Keeping them disables that optimization.
-keep class kotlinx.coroutines.** { *; }

# Compose classes are kept by NAME (`-keepnames` = `-keep,allowshrinking`) rather than
# pinned wholesale. This preserves the names of every Compose class that survives shrinking
# — guarding against the name-based reflection that the original blanket `-keep` was added
# to protect — while still allowing ProGuard to remove unreachable classes. In practice that
# strips ~23,500 unused material-icons-extended icons (~24 MB) that the old `-keep` forced
# into the build, without inlining icon sources or dropping the dependency. Reachable icons
# (the ones actually referenced in code) are retained automatically.
-keepnames class androidx.compose.** { *; }

# NECESSARY (concurrency primitives, same category as the coroutines keep above): letting ProGuard
# optimize okio / atomicfu produces invalid bytecode — "java.lang.VerifyError: Bad return type" on
# the prod release, surfacing on coroutine dispatcher threads. Keeping them disables that optimization.
-keep class okio.** { *; }
-keep class kotlinx.atomicfu.** { *; }
