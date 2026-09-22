# ===== Jakarta Mail API (jakarta.mail:jakarta.mail-api) =====

# Keep the public API surface and all interface members (the latter are resolved via
# reflection during dynamic provider loading).
-keep public class jakarta.mail.** {
    public *;
}
-keep interface jakarta.mail.** {
    *;
}

# ===== Angus Mail implementation (org.eclipse.angus:angus-mail) =====

# Angus instantiates its Store/Transport providers and MIME content handlers reflectively
# (from META-INF/javamail.* and META-INF/mailcap), so keep the whole implementation package.
# This already covers the smtp/imap/pop3/util/handlers subpackages — separate keeps for
# those would be redundant.
-keep class org.eclipse.angus.mail.** { *; }

# ===== Activation / MIME content handling =====

# The activation framework loads DataContentHandlers reflectively for attachments/MIME.
-keep class jakarta.activation.** { *; }

# ===== Suppress warnings =====

-dontwarn jakarta.mail.**
-dontwarn org.eclipse.angus.mail.**

# GraalVM native-image hooks referenced by Angus are absent unless building a native image.
-dontwarn org.graalvm.nativeimage.hosted.Feature
-dontwarn org.graalvm.nativeimage.hosted.Feature$*
-dontwarn org.graalvm.nativeimage.hosted.RuntimeReflection
