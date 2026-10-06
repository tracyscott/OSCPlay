#!/bin/bash


# OSCPlay - Build a sensor calibration from a recording
# Run with --help for options

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

java -cp "$JAR_FILE" xyz.theforks.calibration.CalibrationTool "$@"
