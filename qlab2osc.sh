#!/bin/bash

# QLab2OSC Converter Shell Script
# Converts OSC Play recordings to QLab Network OSC cues

# Check if JFX_SDK is set
if [ -z "$JFX_SDK" ]; then
    echo "Error: JFX_SDK environment variable is not set."
    echo "Please set JFX_SDK to the path of your JavaFX SDK lib directory."
    echo "Example: export JFX_SDK=/path/to/javafx-sdk-17.0.13/lib"
    exit 1
fi

# Find the JAR file
JAR_FILE=""
if [ -f "target/osc-play-1.1.1.jar" ]; then
    JAR_FILE="target/osc-play-1.1.1.jar"
elif [ -f "target/osc-play-1.1.1-jar-with-dependencies.jar" ]; then
    JAR_FILE="target/osc-play-1.1.1-jar-with-dependencies.jar"
elif [ -f "osc-play-1.1.1.jar" ]; then
    JAR_FILE="osc-play-1.1.1.jar"
elif [ -f "target/osc-play-1.1-shaded.jar" ]; then
    JAR_FILE="target/osc-play-1.1-shaded.jar"
elif [ -f "osc-play-1.1-shaded.jar" ]; then
    JAR_FILE="osc-play-1.1-shaded.jar"
else
    echo "Error: Could not find OSC Play JAR file"
    echo "Please run 'mvn package' first to build the project."
    exit 1
fi

# Check if session name is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <session-name> [--config config.json]"
    echo ""
    echo "Available recordings:"
    if [ -d "recordings" ]; then
        ls recordings/*.json 2>/dev/null | sed 's/recordings\///g' | sed 's/\.json//g' || echo "No recordings found"
    else
        echo "No recordings directory found"
    fi
    exit 1
fi

SESSION_NAME="$1"
shift

# Check if the recording exists
if [ ! -f "recordings/${SESSION_NAME}.json" ]; then
    echo "Error: Recording '${SESSION_NAME}' not found in recordings/ directory"
    echo ""
    echo "Available recordings:"
    ls recordings/*.json 2>/dev/null | sed 's/recordings\///g' | sed 's/\.json//g' || echo "No recordings found"
    exit 1
fi

echo "Starting QLab2OSC Converter..."
echo "Session: $SESSION_NAME"
echo "JAR file: $JAR_FILE"

# Run the converter
java --module-path "$JFX_SDK" \
     --add-modules javafx.controls,javafx.fxml,javafx.media \
     -cp "$JAR_FILE" \
     xyz.theforks.QLab2OSCConverter \
     "$SESSION_NAME" \
     "$@"