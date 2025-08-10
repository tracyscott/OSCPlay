#!/bin/bash

# OSCSession - Non-interactive session playback
# Usage: ./oscsession.sh <session_name> [additional_args]
# Requires JFX_SDK environment variable to be set to JavaFX SDK lib directory

if [ $# -eq 0 ]; then
    echo "Usage: $0 <session_name> [additional_args]"
    echo "Example: $0 mysession"
    echo "Example: $0 mysession --host 192.168.1.100 --port 9000"
    exit 1
fi

SESSION_NAME="$1"
shift # Remove session name from arguments, pass rest to java

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
if [ -f "target/osc-play-1.1.jar" ]; then
    JAR_FILE="target/osc-play-1.1.jar"
elif [ -f "target/osc-play-1.1-shaded.jar" ]; then
    JAR_FILE="target/osc-play-1.1-shaded.jar"
elif [ -f "osc-play-1.1.jar" ]; then
    JAR_FILE="osc-play-1.1.jar"
else
    echo "Error: Could not find osc-play JAR file"
    echo "Expected one of:"
    echo "  target/osc-play-1.1.jar"
    echo "  target/osc-play-1.1-shaded.jar" 
    echo "  osc-play-1.1.jar"
    exit 1
fi

# Check if session exists
if [ ! -f "recordings/${SESSION_NAME}.json" ]; then
    echo "Error: Session '$SESSION_NAME' not found"
    echo "Expected file: recordings/${SESSION_NAME}.json"
    echo ""
    echo "Available sessions:"
    if [ -d "recordings" ]; then
        for session in recordings/*.json; do
            if [ -f "$session" ]; then
                basename "$session" .json
            fi
        done
    else
        echo "  (no recordings directory found)"
    fi
    exit 1
fi

echo "Playing session: $SESSION_NAME"
echo "Using JAR: $JAR_FILE"
echo "Using JavaFX SDK: $JFX_SDK"

java --module-path "$JFX_SDK" \
     --add-modules javafx.controls,javafx.fxml,javafx.media \
     --add-exports javafx.base/com.sun.javafx=ALL-UNNAMED \
     --add-exports javafx.graphics/com.sun.javafx.util=ALL-UNNAMED \
     --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED \
     --add-exports javafx.media/com.sun.media.jfxmedia=ALL-UNNAMED \
     -jar "$JAR_FILE" \
     --session "$SESSION_NAME" \
     --port 3031 \
     "$@"