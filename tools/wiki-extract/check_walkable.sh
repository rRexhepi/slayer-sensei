#!/usr/bin/env bash
# Checks work/candidates.tsv against the installed Shortest Path plugin jar and writes
# work/walkable.tsv (tile is walkable in its collision map) and work/reachable.tsv (its transport
# tables lead there at all). The jar is only read. Run build_slayer_areas.py before and after.
#
# Paths come from the environment (see README.md):
#   JAVA_HOME          JDK 11 or newer. When unset, java and javac are taken from PATH.
#   GRADLE_USER_HOME   Gradle's home with the RuneLite jars in its cache. Default ~/.gradle.
#   RL_VERSION         RuneLite client version to read from that cache. Default 1.13.1.
#   SHORTEST_PATH_JAR  Shortest Path plugin jar. Default the first ~/.runelite/plugins/shortest-path_*.jar.
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

SHORTEST_PATH="${SHORTEST_PATH_JAR:-$(first "$HOME/.runelite/plugins/shortest-path_*.jar")}"
CLIENT="$(first "$GRADLE_CACHE/net.runelite/client/$RL_VERSION/*/client-$RL_VERSION.jar")"
API="$(first "$GRADLE_CACHE/net.runelite/runelite-api/$RL_VERSION/*/runelite-api-$RL_VERSION.jar")"
GUAVA="$(first "$GRADLE_CACHE/com.google.guava/guava/*/*/guava-*.jar")"
GUICE="$(first "$GRADLE_CACHE/com.google.inject/guice/*/*/guice-*.jar")"
INJECT="$(first "$GRADLE_CACHE/javax.inject/javax.inject/*/*/javax.inject-1.jar")"
for jar in "$SHORTEST_PATH" "$CLIENT" "$API" "$GUAVA" "$GUICE" "$INJECT"; do
	[ -n "$jar" ] || { echo "a required jar is missing, see README.md" >&2; exit 1; }
done

CP="work/classes"
for jar in "$SHORTEST_PATH" "$CLIENT" "$API" "$GUAVA" "$GUICE" "$INJECT"; do CP="$CP$SEP$(win "$jar")"; done

mkdir -p work/classes
"$JAVAC" -cp "$CP" -d work/classes WalkableCheck.java ReachabilityCheck.java
"$JAVA" -cp "$CP" WalkableCheck work/candidates.tsv work/walkable.tsv
"$JAVA" -Xmx2g -cp "$CP" ReachabilityCheck work/candidates.tsv work/reachable.tsv
rm -rf work/classes
