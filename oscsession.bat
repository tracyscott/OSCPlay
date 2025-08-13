@echo off
setlocal enabledelayedexpansion

set VERSION=1.1.1

rem OSCSession - Non-interactive session playback
rem Usage: oscsession.bat <session_name> [additional_args]
rem Example: oscsession.bat mysession
rem Example: oscsession.bat mysession --host 192.168.1.100 --port 9000
rem Requires JFX_SDK environment variable to be set to JavaFX SDK lib directory

if "%1"=="" (
    echo Usage: %0 ^<session_name^> [additional_args]
    echo Example: %0 mysession
    echo Example: %0 mysession --host 192.168.1.100 --port 9000
    exit /b 1
)

set SESSION_NAME=%1

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

rem Check if session exists
if not exist "recordings\%SESSION_NAME%.json" (
    echo Error: Session '%SESSION_NAME%' not found
    echo Expected file: recordings\%SESSION_NAME%.json
    echo.
    echo Available sessions:
    if exist "recordings" (
        for %%f in (recordings\*.json) do (
            set "filename=%%~nf"
            echo   !filename!
        )
    ) else (
        echo   ^(no recordings directory found^)
    )
    exit /b 1
)

echo Playing session: %SESSION_NAME%
echo Using JAR: %JAR_FILE%
echo Using JavaFX SDK: %JFX_SDK%

rem Shift to remove session name from arguments, pass rest to java
shift
set ARGS=
:loop
if "%1"=="" goto done
set ARGS=%ARGS% %1
shift
goto loop
:done

java --module-path "%JFX_SDK%" ^
     --add-modules javafx.controls,javafx.fxml,javafx.media ^
     --add-exports javafx.base/com.sun.javafx=ALL-UNNAMED ^
     --add-exports javafx.graphics/com.sun.javafx.util=ALL-UNNAMED ^
     --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED ^
     --add-exports javafx.media/com.sun.media.jfxmedia=ALL-UNNAMED ^
     -jar "%JAR_FILE%" ^
     --session "%SESSION_NAME%" ^
     --port 3031 ^
     %ARGS%