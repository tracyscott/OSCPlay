@echo off
rem ============================================================================
rem OSCPlay - unattended start for an installation machine.
rem
rem Brings OSCPlay up with a project already loaded, so the proxy is listening
rem and every output is running without anyone touching the UI.
rem
rem Setup:
rem   1. Leave this script in the OSCPlay directory, next to target\.
rem   2. Press Win+R, run  shell:startup
rem   3. Put a SHORTCUT to this script in the folder that opens.
rem      Use a shortcut, not a copy: the script locates the JAR relative to its
rem      own directory, which only works while it lives beside target\.
rem
rem If you would rather copy the script somewhere else, set OSCPLAY_DIR below to
rem the OSCPlay directory.
rem
rem Run it by hand once before trusting it to a power cycle. A wrong project name
rem does not fall back to the project picker: OSCPlay reports "Project file not
rem found" and the proxy never starts.
rem ============================================================================

rem --- Settings ---------------------------------------------------------------

rem Project to load. Must match a folder in Documents\OSCPlay\Projects.
set PROJECT=interlacetest1

rem OSCPlay directory. Defaults to wherever this script lives.
if "%OSCPLAY_DIR%"=="" set OSCPLAY_DIR=%~dp0
rem %~dp0 ends in a backslash; a hand-set OSCPLAY_DIR might not.
if not "%OSCPLAY_DIR:~-1%"=="\" set OSCPLAY_DIR=%OSCPLAY_DIR%\

rem Extra options, e.g. --mcp to allow agents to connect on port 7770.
set EXTRA_OPTS=

rem Where to record what happened, so a failed boot can be diagnosed.
set LOG=%OSCPLAY_DIR%startup.log

rem --- Launch -----------------------------------------------------------------

cd /d "%OSCPLAY_DIR%" 2>nul
if errorlevel 1 (
    echo [%DATE% %TIME%] Error: cannot enter "%OSCPLAY_DIR%" >> "%LOG%"
    exit /b 1
)

rem Match the JAR with a glob; the version lives in pom.xml, not in here.
set JAR_FILE=
for %%f in (target\osc-play-*-shaded.jar) do set JAR_FILE=%%f
if "%JAR_FILE%"=="" for %%f in (osc-play-*-shaded.jar) do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo [%DATE% %TIME%] Error: no osc-play shaded JAR under "%OSCPLAY_DIR%" >> "%LOG%"
    exit /b 1
)

echo [%DATE% %TIME%] Starting %JAR_FILE% with project %PROJECT% >> "%LOG%"

rem javaw, not java, so no console window stays open for the life of the show.
rem JavaFX is bundled in the shaded JAR, so JFX_SDK is not needed.
start "" javaw -jar "%JAR_FILE%" --project "%PROJECT%" %EXTRA_OPTS%
