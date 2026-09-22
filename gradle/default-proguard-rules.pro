#
# This ProGuard configuration file illustrates how to process ProGuard itself.
# Configuration files for typical applications will be very similar.
# Usage:
#     java -jar proguard.jar @proguard.pro
#
-verbose

## Include java runtime classes
-libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)
-libraryjars <java.home>/jmods/java.desktop.jmod(!**.jar;!module-info.class)

# Don't print notes about reflection in the Kotlin runtime, and
# our own optionally injected code.
-dontnote kotlin.**
-dontnote kotlinx.**
-dontnote proguard.configuration.ConfigurationLogger



# Put all obfuscated classes into the nameless root package.
-repackageclasses ''

# Allow classes and class members to be made public.
-allowaccessmodification

-renamesourcefileattribute SourceFile

# Preserve all annotations.
-keepattributes *Annotation*

# Preserve all public applications.
-keepclasseswithmembers public class * {
    public static void main(java.lang.String[]);
}

# Preserve the special static methods that are required in all enumeration
# classes.
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}