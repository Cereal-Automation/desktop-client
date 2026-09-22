# Most of volatile fields are updated with AtomicFU and should not be mangled/removed
-keepclassmembers class kotlinx.io.** {
    volatile <fields>;
}

-keepclassmembernames class kotlinx.io.** {
    volatile <fields>;
}
