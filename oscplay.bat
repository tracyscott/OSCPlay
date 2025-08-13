@echo off

set VERSION=1.1.1

rem OSCPlay - Interactive GUI mode
rem Requires JFX_SDK environment variable to be set to JavaFX SDK lib directory
rem Example: set JFX_SDK=C:\path\to\javafx-sdk-17.0.13\lib

if "%JFX_SDK%"=="" (
    echo Error: JFX_SDK environment variable not set
    echo Please set it to your JavaFX SDK lib directory, e.g.:
    echo set JFX_SDK=C:\path\to\javafx-sdk-17.0.13\lib
    exit /b 1
)

if not exist "%JFX_SDK%" (
    echo Error: JFX_SDK directory does not exist: %JFX_SDK%
    exit /b 1
)

rem Find the JAR file
set JAR_FILE=
if exist "target\osc-play-%VERSION%.jar" (
    set JAR_FILE=target\osc-play-%VERSION%.jar
) else if exist "target\osc-play-%VERSION%-shaded.jar" (
    set JAR_FILE=target\osc-play-%VERSION%-shaded.jar
) else if exist "osc-play-%VERSION%.jar" (
    set JAR_FILE=osc-play-%VERSION%.jar
) else (
    echo Error: Could not find osc-play JAR file
    echo Expected one of:
    echo   target\osc-play-%VERSION%.jar
    echo   target\osc-play-%VERSION%-shaded.jar
    echo   osc-play-%VERSION%.jar
    exit /b 1
)

echo Starting OSCPlay GUI...
echo Using JAR: %JAR_FILE%
echo Using JavaFX SDK: %JFX_SDK%

java --module-path "%JFX_SDK%" ^
     --add-modules javafx.controls,javafx.fxml,javafx.media ^
     --add-exports javafx.base/com.sun.javafx=ALL-UNNAMED ^
     --add-exports javafx.graphics/com.sun.javafx.util=ALL-UNNAMED ^
     --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED ^
     --add-exports javafx.media/com.sun.media.jfxmedia=ALL-UNNAMED ^
     -jar "%JAR_FILE%" ^
     --port 3030 ^
     %*