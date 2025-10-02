#!/bin/bash

VERSION="2.0.0"

# OSCPlay - Interactive GUI mode
# JavaFX dependencies are bundled in the shaded JAR - no separate SDK installation required

# Find the JAR file (prefer shaded JAR which has all dependencies bundled)
JAR_FILE=""
if [ -f "target/osc-play-${VERSION}-shaded.jar" ]; then
    JAR_FILE="target/osc-play-${VERSION}-shaded.jar"
elif [ -f "osc-play-${VERSION}-shaded.jar" ]; then
    JAR_FILE="osc-play-${VERSION}-shaded.jar"
elif [ -f "target/osc-play-${VERSION}.jar" ]; then
    JAR_FILE="target/osc-play-${VERSION}.jar"
elif [ -f "osc-play-${VERSION}.jar" ]; then
    JAR_FILE="osc-play-${VERSION}.jar"
else
    echo "Error: Could not find osc-play JAR file"
    echo "Expected one of:"
    echo "  target/osc-play-${VERSION}-shaded.jar"
    echo "  osc-play-${VERSION}-shaded.jar"
    echo "  target/osc-play-${VERSION}.jar"
    echo "  osc-play-${VERSION}.jar"
    exit 1
fi

echo "Starting OSCPlay GUI..."
echo "Using JAR: $JAR_FILE"

java -Xdock:icon="icons/oscplay.icns" \
     -jar "$JAR_FILE" \
     --port 3030 \
     "$@"