# Baut ein MSIX-Paket für den Microsoft Store.
# Voraussetzungen: JDK 17, Windows 10/11 SDK (für makeappx.exe).
# Aufruf im Hauptordner:  powershell -ExecutionPolicy Bypass -File desktopApp\packaging\build-msix.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot      # desktopApp
$repo = Split-Path -Parent $root              # Hauptordner mit gradlew.bat

Write-Host "1/3  App bauen ..."
& "$repo\gradlew.bat" :desktopApp:createReleaseDistributable
if ($LASTEXITCODE -ne 0) { throw "Gradle-Build fehlgeschlagen" }

$app = Join-Path $root "build\compose\binaries\main-release\app\TVPlayer"
$stage = Join-Path $root "build\msix\stage"
$out = Join-Path $root "build\msix\TVPlayer.msix"

Write-Host "2/3  Dateien zusammenstellen ..."
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item "$app\*" $stage -Recurse
Copy-Item "$PSScriptRoot\msix\AppxManifest.xml" $stage
Copy-Item "$PSScriptRoot\msix\Assets" "$stage\Assets" -Recurse

if (-not (Test-Path "$stage\app\resources\vlc\libvlc.dll")) {
    Write-Warning "VLC-Dateien fehlen (resources\windows\vlc). Die Store-Version braucht sie, siehe README."
}

Write-Host "3/3  MSIX packen ..."
$makeappx = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\makeappx.exe" | Sort-Object FullName -Descending | Select-Object -First 1
if (-not $makeappx) { throw "makeappx.exe nicht gefunden. Bitte das Windows 10/11 SDK installieren." }
if (Test-Path $out) { Remove-Item $out }
& $makeappx.FullName pack /d $stage /p $out /o
if ($LASTEXITCODE -ne 0) { throw "makeappx fehlgeschlagen" }

Write-Host ""
Write-Host "Fertig: $out"
Write-Host "Dieses Paket in Partner Center hochladen. Microsoft signiert es bei der Zertifizierung selbst."
