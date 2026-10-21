#!/usr/bin/env bash
# Starts RuneLite with every consts-osrs plugin, from the play kit's jar beside this script, on Java 11.
# The jar is `./gradlew playKit`'s, and holds the RuneLite of the day it was built: after a RuneLite update, build it
# again, or the client is out of date.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_11="${RUNELITE_JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home}"

exec "$JAVA_11/bin/java" -ea --add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED -jar "$DIR/consts-osrs.jar" "$@"
