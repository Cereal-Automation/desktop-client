# Kotter (com.varabyte.kotter) — the headless-mode TUI library.
#
# Kotter is small and has no reflection of its own, but it is only reached from
# the `--headless` path, which the desktop smoke run does not exercise. Keep it
# whole so a shrinker miss can't surface only on a headless server.
-keep class com.varabyte.kotter.** { *; }
-keep class com.varabyte.kotterx.** { *; }
-dontwarn com.varabyte.**
