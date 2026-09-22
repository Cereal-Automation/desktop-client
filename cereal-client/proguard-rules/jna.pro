# JNA (net.java.dev.jna:jna) — used by the Discord RPC native bridge.
#
# JNA reaches its own classes reflectively and from native callbacks, so the
# shrinker sees no reference to several of them (notably the anonymous inner
# classes of com.sun.jna.Native, e.g. Native$5) and strips them. Their absence
# surfaces at runtime as `NoClassDefFoundError: com/sun/jna/Native$5` thrown
# from NativeLibrary.<clinit> when DiscordRpcDataSource touches JNA. Keep the
# whole library plus the JNA-mapped types so the native bridge loads on
# obfuscated release builds.

-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }

# JNA-mapped Discord RPC interface, native structs, and callbacks.
-keep class * implements com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Callback { *; }

-dontwarn java.awt.*

# JNA's fast-path invokers call java.lang.invoke.MethodHandle.invoke(...), a
# signature-polymorphic method the JVM synthesises per call site. ProGuard can't
# resolve those synthetic descriptors and emits "can't find referenced method
# 'invoke' in library class java.lang.invoke.MethodHandle" for com.sun.jna.Native
# (and its inner classes). The references are valid at runtime; silence them.
-dontwarn com.sun.jna.**
