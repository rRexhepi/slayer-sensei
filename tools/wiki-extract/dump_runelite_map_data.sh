#!/usr/bin/env bash
# Re-dumps RuneLite's world map location enums (dungeon icons, transports, fairy rings, teleports) to
# runelite_map_data.tsv. Only needed after a RuneLite update. Reads the client jar from the Gradle cache.
#
# Paths come from the environment (see README.md):
#   JAVA_HOME          JDK 11 or newer. When unset, java and javac are taken from PATH.
#   GRADLE_USER_HOME   Gradle's home with the RuneLite jars in its cache. Default ~/.gradle.
#   RL_VERSION         RuneLite client version to read from that cache. Default 1.13.1.
set -euo pipefail
cd "$(dirname "$0")"

if [ -n "${JAVA_HOME:-}" ]; then
	JAVA="$JAVA_HOME/bin/java"
	JAVAC="$JAVA_HOME/bin/javac"
else
	JAVA=java
	JAVAC=javac
fi
GRADLE_CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
RL_VERSION="${RL_VERSION:-1.13.1}"
# Java's classpath separator: ';' on Windows (Git Bash), ':' elsewhere
case "$(uname -s)" in
	MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
	*) SEP=':' ;;
esac

win() { cygpath -m "$1" 2>/dev/null || echo "$1"; }
first() { ls $1 2>/dev/null | grep -v sources | head -1; }

CLIENT="$(first "$GRADLE_CACHE/net.runelite/client/$RL_VERSION/*/client-$RL_VERSION.jar")"
API="$(first "$GRADLE_CACHE/net.runelite/runelite-api/$RL_VERSION/*/runelite-api-$RL_VERSION.jar")"
GUAVA="$(first "$GRADLE_CACHE/com.google.guava/guava/*/*/guava-*.jar")"
SLF4J="$(first "$GRADLE_CACHE/org.slf4j/slf4j-api/*/*/slf4j-api-*.jar")"
for jar in "$CLIENT" "$API" "$GUAVA" "$SLF4J"; do
	[ -n "$jar" ] || { echo "a required jar is missing, see README.md" >&2; exit 1; }
done

mkdir -p work/classes
"$JAVAC" -d work/classes DumpRuneLiteMapData.java
"$JAVA" -cp "work/classes$SEP$(win "$CLIENT")$SEP$(win "$API")$SEP$(win "$GUAVA")$SEP$(win "$SLF4J")" \
	DumpRuneLiteMapData runelite_map_data.tsv
rm -rf work/classes
echo "wrote runelite_map_data.tsv for RuneLite $RL_VERSION"
