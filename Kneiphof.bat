@echo off
rem Kneiphof starten. JOGL braucht unter Java 21 Zugriff auf einige AWT-Interna.
cd /d "%~dp0"
if not exist "dist\Kneiphof.jar" (
    echo Fehler: dist\Kneiphof.jar nicht gefunden. Bitte in NetBeans zuerst "Clean and Build".
    timeout /t 6
    exit /b 1
)
set OPTS=-Xmx3g --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED
rem Falls das Bild schwarz bleibt: die naechste Zeile aktivieren (leichtes GLJPanel statt GLCanvas)
rem set OPTS=%OPTS% -Dkneiphof.gljpanel=true
start "" javaw %OPTS% -jar "dist\Kneiphof.jar"
exit
