#!/bin/bash

VERSION="1.1.1"

# OSCPlay - Interactive GUI mode
# Requires JFX_SDK environment variable to be set to JavaFX SDK lib directory
# Example: export JFX_SDK=/path/to/javafx-sdk-17.0.13/lib

if [ -z "$JFX_SDK" ]; then
    echo "Error: JFX_SDK environment variable not set"
    echo "Please set it to your JavaFX SDK lib directory, e.g.:"
    echo "export JFX_SDK=/path/to/javafx-sdk-17.0.13/lib"
    exit 1
fi

if [ ! -d "$JFX_SDK" ]; then
    echo "Error: JFX_SDK directory does not exist: $JFX_SDK"
    exit 1
fi

# Find the JAR file
JAR_FILE=""
if [ -f "target/osc-play-${VERSION}.jar" ]; then
    JAR_FILE="target/osc-play-${VERSION}.jar"
elif [ -f "target/osc-play-${VERSION}-shaded.jar" ]; then
    JAR_FILE="target/osc-play-${VERSION}-shaded.jar"
elif [ -f "osc-play-${VERSION}.jar" ]; then
    JAR_FILE="osc-play-${VERSION}.jar"
else
    echo "Error: Could not find osc-play JAR file"
    echo "Expected one of:"
    echo "  target/osc-play-${VERSION}.jar"
    echo "  target/osc-play-${VERSION}-shaded.jar" 
    echo "  osc-play-${VERSION}.jar"
    exit 1
fi

echo "Starting OSCPlay GUI..."
echo "Using JAR: $JAR_FILE"
echo "Using JavaFX SDK: $JFX_SDK"

java -Xdock:icon="icons/oscplay.icns" \
     --module-path "$JFX_SDK" \
     --add-modules javafx.controls,javafx.fxml,javafx.media \
     --add-exports javafx.base/com.sun.javafx=ALL-UNNAMED \
     --add-exports javafx.graphics/com.sun.javafx.util=ALL-UNNAMED \
     --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
     --add-exports javafx.media/com.sun.media.jfxmedia=ALL-UNNAMED \
     -jar "$JAR_FILE" \
     --port 3030 \
     "$@"