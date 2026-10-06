#!/bin/bash


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