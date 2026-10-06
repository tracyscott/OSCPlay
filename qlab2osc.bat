@echo off
REM QLab2OSC Converter Batch Script
REM Converts OSC Play recordings to QLab Network OSC cues

REM Check if JFX_SDK is set
if "%JFX_SDK%"=="" (
    echo Error: JFX_SDK environment variable is not set.
    echo Please set JFX_SDK to the path of your JavaFX SDK lib directory.
    echo Example: set JFX_SDK=C:\path\to\javafx-sdk-17.0.13\lib
    exit /b 1
)

REM Find the JAR file
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

REM Check if session name is provided
if "%1"=="" (
    echo Usage: %0 ^<session-name^> [--config config.json]
    echo.
    echo Available recordings:
    if exist "recordings" (
        dir recordings\*.json /b 2>nul | findstr /v "^$"
        if errorlevel 1 echo No recordings found
    ) else (
        echo No recordings directory found
    )
    exit /b 1
)

set SESSION_NAME=%1

REM Check if the recording exists
if not exist "recordings\%SESSION_NAME%.json" (
    echo Error: Recording '%SESSION_NAME%' not found in recordings\ directory
    echo.
    echo Available recordings:
    dir recordings\*.json /b 2>nul | findstr /v "^$"
    if errorlevel 1 echo No recordings found
    exit /b 1
)

echo Starting QLab2OSC Converter...
echo Session: %SESSION_NAME%
echo JAR file: %JAR_FILE%

REM Run the converter
java --module-path "%JFX_SDK%" --add-modules javafx.controls,javafx.fxml,javafx.media -cp "%JAR_FILE%" xyz.theforks.QLab2OSCConverter %*