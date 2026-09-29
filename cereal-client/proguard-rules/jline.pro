# JLine 3 (org.jline:jline-terminal*) — the terminal layer under Kotter, used by headless mode.
#
# JLine picks its terminal provider (JNI / FFM / exec) by name through
# META-INF/services and Class.forName, and the JNI provider binds native
# methods by class and member name. Obfuscating or stripping them leaves
# TerminalBuilder unable to find a provider ("Unable to create a system terminal").
-keep class org.jline.** { *; }
-dontwarn org.jline.**
