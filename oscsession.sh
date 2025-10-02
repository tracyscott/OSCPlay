#!/bin/bash

VERSION="1.1.1"

# OSCSession - Non-interactive session playback
# Usage: ./oscsession.sh <session_name> [additional_args]
# JavaFX dependencies are bundled in the shaded JAR - no separate SDK installation required

if [ $# -eq 0 ]; then
    echo "Usage: $0 <session_name> [additional_args]"
    echo "Example: $0 mysession"
    echo "Example: $0 mysession --host 192.168.1.100 --port 9000"
    exit 1
fi

SESSION_NAME="$1"
shift # Remove session name from arguments, pass rest to java

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

java -jar "$JAR_FILE" \
     --session "$SESSION_NAME" \
     --port 3031 \
     "$@"