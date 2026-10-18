#!/bin/bash
# Defaults match the measured run in ../child-jvm-overhead.md; override any of them via the environment.
set -euo pipefail
G=~/.gradle/caches/modules-2/files-2.1
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export STDLIB_JAR="${STDLIB_JAR:-$(ls $G/org.jetbrains.kotlin/kotlin-stdlib/2.4.21/*/kotlin-stdlib-2.4.21.jar)}"
export SDK_JAR="${SDK_JAR:-$(ls $G/com.cereal-automation/cereal-sdk/1.14.0/*/cereal-sdk-1.14.0.jar)}"
export COROUTINES_JAR="${COROUTINES_JAR:-$(ls $G/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.11.0/*/kotlinx-coroutines-core-jvm-1.11.0.jar)}"
export SCRIPT_JAR="${SCRIPT_JAR:-$HOME/PProjects/Script-Monitoring/script-tgtg/build/libs/release.jar}"
export SCRIPT_CLASS="${SCRIPT_CLASS:-com.cereal.tgtg.TgtgScript}"
exec python3 "$(dirname "$0")/run.py" "$@"
