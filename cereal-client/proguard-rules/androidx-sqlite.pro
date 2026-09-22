# Taken from https://github.com/androidx/androidx/blob/140c775f383da265d572cb5a6a326aa056bf4557/sqlite/sqlite-bundled/src/jvmMain/resources/META-INF/proguard/sqlite-bundled.pro

-keepclasseswithmembernames class androidx.sqlite.driver.bundled.** { native <methods>; }

# NECESSARY: the upstream rule above only pins native-method holders. The bundled driver also
# calls top-level Kotlin functions (e.g. BundledSQLiteDriverKt.nativeThreadSafeMode()) and other
# androidx.sqlite members reflectively / across the JNI bridge. Without this keep, the prod release
# throws NoSuchMethodError: BundledSQLiteDriverKt.nativeThreadSafeMode()I at first DB access.
-keep class androidx.sqlite.** { *; }
