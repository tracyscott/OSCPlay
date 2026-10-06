#!/bin/bash


# OSCPlay - Interactive GUI mode
# JavaFX dependencies are bundled in the shaded JAR - no separate SDK installation required

# Find the JAR file. The version lives in pom.xml, so match it with a glob
# rather than hardcoding it here (preferring the shaded JAR, which bundles all
# dependencies).
JAR_FILE=""
for pattern in target/osc-play-*-shaded.jar osc-play-*-shaded.jar target/osc-play-*.jar osc-play-*.jar; do
    for candidate in $pattern; do
        if [ -f "$candidate" ]; then
            JAR_FILE="$candidate"
            break 2
        fi
    done
done

if [ -z "$JAR_FILE" ]; then
    echo "Error: Could not find an osc-play JAR file"
    echo "Run 'mvn package' first to build target/osc-play-<version>-shaded.jar"
    exit 1
fi

echo "Starting OSCPlay GUI..."
echo "Using JAR: $JAR_FILE"

java -Xdock:icon="icons/oscplay.icns" \
     -jar "$JAR_FILE" \
     --port 3030 \
     "$@"