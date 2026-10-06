@echo off
REM OSCPlay - Build a sensor calibration from a recording
REM Run with --help for options


rem Find the JAR file. The version lives in pom.xml, so match it with a glob
rem rather than hardcoding it here (preferring the shaded JAR, which bundles all
rem dependencies).
set JAR_FILE=
for %%f in (target\osc-play-*-shaded.jar) do set JAR_FILE=%%f
if "%JAR_FILE%"=="" for %%f in (osc-play-*-shaded.jar) do set JAR_FILE=%%f
if "%JAR_FILE%"=="" for %%f in (target\osc-play-*.jar) do set JAR_FILE=%%f
if "%JAR_FILE%"=="" for %%f in (osc-play-*.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo Error: Could not find an osc-play JAR file
    echo Run 'mvn package' first to build target\osc-play-^<version^>-shaded.jar
    exit /b 1
)

java -cp "%JAR_FILE%" xyz.theforks.calibration.CalibrationTool %*
