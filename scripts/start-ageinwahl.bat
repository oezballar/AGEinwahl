@echo off
setlocal

set "APP_EXE=%~dp0AGEinwahl\AGEinwahl.exe"
set "APP_URL=http://127.0.0.1:8080"

if not exist "%APP_EXE%" (
    echo Die AGEinwahl-App wurde nicht gefunden.
    echo Bitte diese Datei aus dem Ordner AGEinwahl-USB starten.
    pause
    exit /b 1
)

start "AGEinwahl" "%APP_EXE%"
timeout /t 3 /nobreak >nul
start "" "%APP_URL%"
exit /b 0
