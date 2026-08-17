@echo off
taskkill /im AGEinwahl.exe /t /f >nul 2>&1
if errorlevel 1 (
    echo AGEinwahl war nicht gestartet.
) else (
    echo AGEinwahl wurde beendet.
)
timeout /t 2 /nobreak >nul
