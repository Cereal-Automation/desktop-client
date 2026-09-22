# Package: dev.kdriver:core

# kdriver drives Chrome via CDP (Chrome DevTools Protocol) using kotlinx.serialization.
# CDP request/response models are resolved reflectively by the generated serializers, so
# keep the classes and all their members. (-keep already retains members, so the previous
# extra -keepclassmembers { public *; } block was fully subsumed by this rule.)
-keep class dev.kdriver.** { *; }

# Suppress warnings for optional or platform-specific dependencies
-dontwarn dev.kdriver.**
