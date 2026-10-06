@echo off

rem OSCPlay - session playback, run from a distribution directory next to the JAR.
rem Usage: oscsession.bat <session> <port>
rem Requires JFX_SDK to point at your JavaFX SDK lib directory.

rem Find the JAR file. The version lives in pom.xml, so match it with a glob
rem rather than hardcoding it here.
set JAR_FILE=
for %%f in (osc-play-*-shaded.jar) do set JAR_FILE=%%f
if "%JAR_FILE%"=="" for %%f in (osc-play-*.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo Error: Could not find an osc-play JAR file next to this script
    exit /b 1
)

java --module-path %JFX_SDK% --add-modules javafx.controls,javafx.fxml,javafx.media --add-exports javafx.base/com.sun.javafx=ALL-UNNAMED --add-exports javafx.graphics/com.sun.javafx.util=ALL-UNNAMED --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-exports javafx.media/com.sun.media.jfxmedia=ALL-UNNAMED -jar "%JAR_FILE%" --session %1 --port %2
