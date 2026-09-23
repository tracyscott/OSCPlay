#!/bin/bash

VERSION="2.0.1"

# OSCPlay - Build a sensor calibration from a recording
# Run with --help for options

# Find the JAR file (prefer shaded JAR which has all dependencies bundled)
JAR_FILE=""
if [ -f "target/osc-play-${VERSION}-shaded.jar" ]; then
    JAR_FILE="target/osc-play-${VERSION}-shaded.jar"
elif [ -f "osc-play-${VERSION}-shaded.jar" ]; then
    JAR_FILE="osc-play-${VERSION}-shaded.jar"
else
    echo "Error: Could not find osc-play JAR file"
    echo "Expected one of:"
    echo "  target/osc-play-${VERSION}-shaded.jar"
    echo "  osc-play-${VERSION}-shaded.jar"
    exit 1
fi

java -cp "$JAR_FILE" xyz.theforks.calibration.CalibrationTool "$@"
