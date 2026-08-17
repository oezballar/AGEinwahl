@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~dp0.."
set "JAR_NAME=AGEinwahl-0.0.1-SNAPSHOT.jar"
set "STAGING_DIR=%TEMP%\AGEinwahl-jpackage-input"
set "OUTPUT_DIR=%PROJECT_DIR%\dist\AGEinwahl-USB"

cd /d "%PROJECT_DIR%"

where java >nul 2>&1
if errorlevel 1 (
    echo FEHLER: Java wurde nicht gefunden.
    echo Bitte ein JDK 19 installieren und JAVA_HOME/PATH pruefen.
    pause
    exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
    echo FEHLER: jpackage wurde nicht gefunden.
    echo Bitte ein JDK 19 installieren. Eine reine JRE reicht nicht aus.
    pause
    exit /b 1
)

call mvnw.cmd -DskipTests package
if errorlevel 1 (
    echo FEHLER: Die Anwendung konnte nicht gebaut werden.
    pause
    exit /b 1
)

if not exist "target\%JAR_NAME%" (
    echo FEHLER: target\%JAR_NAME% wurde nicht erzeugt.
    pause
    exit /b 1
)

if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"
mkdir "%STAGING_DIR%"
copy /y "target\%JAR_NAME%" "%STAGING_DIR%\%JAR_NAME%" >nul

if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
mkdir "%OUTPUT_DIR%"

jpackage ^
    --type app-image ^
    --name AGEinwahl ^
    --app-version 0.0.1 ^
    --vendor "AGEinwahl" ^
    --input "%STAGING_DIR%" ^
    --main-jar "%JAR_NAME%" ^
    --dest "%OUTPUT_DIR%" ^
    --java-options "-Dserver.address=127.0.0.1" ^
    --java-options "-Dserver.port=8080"
if errorlevel 1 (
    echo FEHLER: jpackage konnte die Windows-App nicht erstellen.
    pause
    exit /b 1
)

copy /y "scripts\start-ageinwahl.bat" "%OUTPUT_DIR%\Start-AGEinwahl.bat" >nul
copy /y "scripts\stop-ageinwahl.bat" "%OUTPUT_DIR%\Stop-AGEinwahl.bat" >nul
copy /y "docs\AGEinwahl-Nutzung.md" "%OUTPUT_DIR%\Anleitung-fuer-die-Nutzung.md" >nul
copy /y "docs\AGEinwahl-Windows-USB-Installation.md" "%OUTPUT_DIR%\Anleitung-Installation.md" >nul

if exist "%STAGING_DIR%" rmdir /s /q "%STAGING_DIR%"

echo.
echo Fertig. Der portable Ordner liegt hier:
echo %OUTPUT_DIR%
echo Diesen gesamten Ordner auf den USB-Stick kopieren.
pause
