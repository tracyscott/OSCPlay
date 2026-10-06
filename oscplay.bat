@echo off

rem OSCPlay - Interactive GUI mode
rem JavaFX is bundled in the shaded JAR, so no separate SDK installation is
rem needed and JFX_SDK does not have to be set.

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

echo Starting OSCPlay GUI...
echo Using JAR: %JAR_FILE%

java -jar "%JAR_FILE%" --port 3030 %*
