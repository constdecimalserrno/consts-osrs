#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_GRADLE_JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
DEFAULT_RUNELITE_JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home"

export JAVA_HOME="${JAVA_HOME:-$DEFAULT_GRADLE_JAVA_HOME}"
export RUNELITE_JAVA_HOME="${RUNELITE_JAVA_HOME:-$DEFAULT_RUNELITE_JAVA_HOME}"
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$SCRIPT_DIR/.gradle-home}"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Error: JAVA_HOME is invalid: $JAVA_HOME" >&2
  echo "Set JAVA_HOME to a JDK 17+ installation and try again." >&2
  exit 1
fi

if [[ ! -x "$RUNELITE_JAVA_HOME/bin/java" ]]; then
  echo "Error: RUNELITE_JAVA_HOME is invalid: $RUNELITE_JAVA_HOME" >&2
  echo "Set RUNELITE_JAVA_HOME to a compatible JDK (11/17 recommended) and try again." >&2
  exit 1
fi

if [[ $# -eq 0 ]]; then
  set -- runClient
fi

if [[ "$1" == "all" || "$1" == "runAll" ]]; then
  shift
  "$SCRIPT_DIR/gradlew" -Dorg.gradle.java.home="$JAVA_HOME" test "$@"
  "$SCRIPT_DIR/gradlew" -Dorg.gradle.java.home="$JAVA_HOME" clean classes "$@"
  exec "$SCRIPT_DIR/gradlew" -Dorg.gradle.java.home="$JAVA_HOME" runClient "$@"
fi

exec "$SCRIPT_DIR/gradlew" -Dorg.gradle.java.home="$JAVA_HOME" "$@"
