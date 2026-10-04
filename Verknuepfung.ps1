# Legt auf dem Desktop die Verknuepfung "Kneiphof 1910" mit dem Programmsymbol an.
# Aufruf ueber Verknuepfung.bat (Doppelklick).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar  = Join-Path $root 'dist\Kneiphof.jar'
$ico  = Join-Path $root 'kneiphof.ico'
if (-not (Test-Path $jar)) { throw "dist\Kneiphof.jar fehlt - zuerst in NetBeans bauen (Clean and Build)." }
if (-not (Test-Path $ico)) { throw "kneiphof.ico fehlt." }

$javaw = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) {
    $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
} else {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaw = $cmd.Source }
}
if (-not $javaw) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java in den PATH aufnehmen." }

# Dieselben Optionen wie Kneiphof.bat: JOGL braucht unter Java 21 Zugriff auf einige AWT-Interna.
$opts = '-Xmx3g --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED ' +
        '--add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED'

$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop 'Kneiphof 1910.lnk'
$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath       = $javaw
$s.Arguments        = $opts + ' -jar "' + $jar + '"'
$s.WorkingDirectory = $root
$s.IconLocation     = $ico
$s.Description      = 'Die sieben Bruecken von Koenigsberg um 1910'
$s.Save()
Write-Host "Verknuepfung angelegt: $lnk"
