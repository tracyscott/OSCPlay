@echo off
REM OSCPlay - Build a sensor calibration from a recording
REM Run with --help for options

set VERSION=2.0.1

set JAR_FILE=
if exist "target\osc-play-%VERSION%-shaded.jar" (
    set JAR_FILE=target\osc-play-%VERSION%-shaded.jar
) else if exist "osc-play-%VERSION%-shaded.jar" (
    set JAR_FILE=osc-play-%VERSION%-shaded.jar
) else (
    echo Error: Could not find osc-play-%VERSION%-shaded.jar
    echo Please run 'mvn package' first to build the project.
    exit /b 1
)

java -cp "%JAR_FILE%" xyz.theforks.calibration.CalibrationTool %*
