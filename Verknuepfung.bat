@echo off
rem Legt auf dem Desktop eine Verknuepfung "Kneiphof 1910" mit dem Programmsymbol an.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Verknuepfung.ps1"
if errorlevel 1 (
  echo.
  echo Die Verknuepfung konnte nicht angelegt werden - siehe Meldung oben.
)
pause
