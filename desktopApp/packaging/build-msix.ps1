# Baut ein MSIX-Paket für den Microsoft Store (unsigniert – Microsoft signiert es bei der Zertifizierung).
# Voraussetzungen: JDK 17, Windows 10/11 SDK (makeappx.exe), libVLC unter desktopApp\resources\windows\vlc (tools/fetch-vlc.sh).
#
# Lokal (baut die App selbst):
#   pwsh -File desktopApp\packaging\build-msix.ps1 -IdentityName <Name> -Publisher "CN=…" -PublisherDisplayName <Name>
# Release/CI (übernimmt einen fertigen App-Ordner, siehe release.yml / ci.yml):
#   pwsh -File desktopApp\packaging\build-msix.ps1 -AppDir desktopApp\build\compose\binaries\main\app\IPTVPlayer -Version 2.4.0 …
#
# Die Store-Identität (Name, Publisher, PublisherDisplayName) steht im Partner Center unter Produktverwaltung →
# Produktidentität. Das Skript schreibt sie zusammen mit der Version ins Manifest; die Werte im Manifest sind nur Vorgaben.
param(
    [string]$AppDir,
    [string]$Version,
    [Parameter(Mandatory = $true)][string]$IdentityName,
    [Parameter(Mandatory = $true)][string]$Publisher,
    [Parameter(Mandatory = $true)][string]$PublisherDisplayName,
    [string]$DisplayName = "IPTV Player",
    [string]$OutFile
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot      # desktopApp
$repo = Split-Path -Parent $root              # Hauptordner mit gradlew.bat

# Store-Versionen haben vier Stellen, die letzte muss 0 sein: "2.4.0" → "2.4.0.0".
function ConvertTo-MsixVersion([string]$v) {
    if ($v -notmatch '^\d+\.\d+\.\d+$') { throw "Version '$v' hat nicht die Form X.Y.Z" }
    return "$v.0"
}

if (-not $Version) {
    $line = Get-Content (Join-Path $repo "gradle.properties") | Where-Object { $_ -match '^appVersion=' } | Select-Object -First 1
    if (-not $line) { throw "appVersion fehlt in gradle.properties" }
    $Version = ($line -replace '^appVersion=', '').Trim()
}
$msixVersion = ConvertTo-MsixVersion $Version
if (-not $OutFile) { $OutFile = Join-Path $root "build\msix\IPTVPlayer-$Version.msix" }
$OutFile = [System.IO.Path]::GetFullPath($OutFile)

if (-not $AppDir) {
    Write-Host "1/4  App bauen ..."
    & (Join-Path $repo "gradlew.bat") :desktopApp:createReleaseDistributable
    if ($LASTEXITCODE -ne 0) { throw "Gradle-Build fehlgeschlagen" }
    $AppDir = Join-Path $root "build\compose\binaries\main-release\app\IPTVPlayer"
} else {
    Write-Host "1/4  Fertigen App-Ordner übernehmen: $AppDir"
}
if (-not (Test-Path (Join-Path $AppDir "IPTVPlayer.exe"))) { throw "IPTVPlayer.exe fehlt in $AppDir" }

# Alles Weitere nur in der Kopie: Der App-Ordner gehört auch zur .msi und zur portablen Zip und bleibt unverändert.
Write-Host "2/4  Dateien zusammenstellen ..."
$stage = Join-Path $root "build\msix\stage"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item (Join-Path $AppDir "*") $stage -Recurse
Copy-Item (Join-Path $PSScriptRoot "msix\Assets") (Join-Path $stage "Assets") -Recurse

$vlc = Get-ChildItem (Join-Path $stage "app") -Recurse -Filter libvlc.dll | Where-Object { $_.FullName -like '*\vlc\libvlc.dll' }
if (-not $vlc) { throw "libVLC fehlt im App-Ordner (tools/fetch-vlc.sh ausführen) – ohne Engine gibt es kein MSIX" }
Write-Host "     VLC gefunden: $($vlc[0].FullName)"

# Manifest: Identität, Version und Anzeigenamen setzen, als UTF-8 ohne BOM speichern.
[xml]$manifest = Get-Content (Join-Path $PSScriptRoot "msix\AppxManifest.xml") -Raw -Encoding UTF8
$manifest.Package.Identity.Name = $IdentityName
$manifest.Package.Identity.Publisher = $Publisher
$manifest.Package.Identity.Version = $msixVersion
$manifest.Package.Properties.DisplayName = $DisplayName
$manifest.Package.Properties.PublisherDisplayName = $PublisherDisplayName
$manifest.Package.Applications.Application.VisualElements.DisplayName = $DisplayName
$settings = New-Object System.Xml.XmlWriterSettings
$settings.Encoding = New-Object System.Text.UTF8Encoding($false)
$settings.Indent = $true
$writer = [System.Xml.XmlWriter]::Create((Join-Path $stage "AppxManifest.xml"), $settings)
$manifest.Save($writer)
$writer.Close()

# Store-Kennung für die App (isStoreBuild): keine eigene Update-Suche bei GitHub.
$cfg = Join-Path $stage "app\IPTVPlayer.cfg"
if (-not (Test-Path $cfg)) { throw "app\IPTVPlayer.cfg fehlt im App-Ordner" }
$lines = [System.Collections.Generic.List[string]](Get-Content $cfg)
$storeOption = "java-options=-Diptvplayer.store=true"
$section = $lines.IndexOf("[JavaOptions]")
if ($section -ge 0) { $lines.Insert($section + 1, $storeOption) } else { $lines.Add(""); $lines.Add("[JavaOptions]"); $lines.Add($storeOption) }
[System.IO.File]::WriteAllLines($cfg, $lines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "3/4  MSIX packen ..."
$makeappx = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\makeappx.exe" | Sort-Object FullName -Descending | Select-Object -First 1
if (-not $makeappx) { throw "makeappx.exe nicht gefunden. Bitte das Windows 10/11 SDK installieren." }
New-Item -ItemType Directory -Path (Split-Path -Parent $OutFile) -Force | Out-Null
if (Test-Path $OutFile) { Remove-Item $OutFile }
& $makeappx.FullName pack /d $stage /p $OutFile /o
if ($LASTEXITCODE -ne 0) { throw "makeappx pack fehlgeschlagen" }

# Selbstprüfung am fertigen Paket: entpacken und nachsehen, ob drin ist, was der Store und die App brauchen.
Write-Host "4/4  Paket prüfen ..."
$check = Join-Path $root "build\msix\check"
if (Test-Path $check) { Remove-Item $check -Recurse -Force }
& $makeappx.FullName unpack /p $OutFile /d $check /o
if ($LASTEXITCODE -ne 0) { throw "makeappx unpack fehlgeschlagen" }
[xml]$packed = Get-Content (Join-Path $check "AppxManifest.xml") -Raw -Encoding UTF8
if ($packed.Package.Identity.Version -ne $msixVersion) { throw "Version im Paket: $($packed.Package.Identity.Version), erwartet $msixVersion" }
if ($packed.Package.Identity.Name -ne $IdentityName) { throw "Identity/Name im Paket: $($packed.Package.Identity.Name), erwartet $IdentityName" }
if (-not (Select-String -Path (Join-Path $check "app\IPTVPlayer.cfg") -SimpleMatch $storeOption -Quiet)) { throw "Store-Kennung fehlt in app\IPTVPlayer.cfg" }
if (-not (Get-ChildItem (Join-Path $check "app") -Recurse -Filter libvlc.dll)) { throw "libVLC fehlt im fertigen Paket" }
if (Select-String -Path (Join-Path $AppDir "app\IPTVPlayer.cfg") -SimpleMatch $storeOption -Quiet) { throw "Store-Kennung ist im gemeinsamen App-Ordner gelandet" }
Write-Host "     Version $msixVersion, Identität $IdentityName, Store-Kennung und VLC im Paket."

Write-Host ""
Write-Host "Fertig: $OutFile"
Write-Host "Dieses Paket im Partner Center hochladen. Microsoft signiert es bei der Zertifizierung selbst."
