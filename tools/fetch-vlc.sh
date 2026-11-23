#!/usr/bin/env bash
# Holt die VLC-Engine (libVLC) für den Windows-Installer nach desktopApp/resources/windows/vlc/.
# Fest eingetragene Version + SHA-256 von VideoLAN: Ein anderes oder verändertes Archiv bricht den Build ab.
# Läuft in der CI (Git Bash auf dem Windows-Runner) und lokal; auf Windows wird zusätzlich der Plugin-Index erzeugt.
#
#   tools/fetch-vlc.sh                     # Standardversion
#   VLC_VERSION=… VLC_SHA256=… tools/fetch-vlc.sh   # andere Version (Prüfsumme aus der .sha256-Datei von VideoLAN)
set -euo pipefail

VLC_VERSION="${VLC_VERSION:-3.0.24}"
VLC_SHA256="${VLC_SHA256:-fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323}"

root="$(cd "$(dirname "$0")/.." && pwd)"
dest="$root/desktopApp/resources/windows/vlc"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

url="https://download.videolan.org/pub/videolan/vlc/$VLC_VERSION/win64/vlc-$VLC_VERSION-win64.zip"
echo "Lade VLC $VLC_VERSION …"
curl -fsSL --retry 3 -o "$work/vlc.zip" "$url"
echo "$VLC_SHA256  $work/vlc.zip" | sha256sum -c -

unzip -q "$work/vlc.zip" -d "$work"
src="$work/vlc-$VLC_VERSION"

rm -rf "$dest"
mkdir -p "$dest"
cp "$src/libvlc.dll" "$src/libvlccore.dll" "$dest/"
# Lizenzen, Urheber und Quellcode-Hinweis gehören mitgeliefert (libVLC: LGPL 2.1, Plugins teils GPL).
# Das VLC-Archiv enthält nur den GPL-Text – der LGPL-Text und der Quellcode-Hinweis liegen im Repository.
cp "$src/COPYING.txt" "$src/AUTHORS.txt" "$src/README.txt" "$dest/"
cp "$root/tools/vlc-licenses/COPYING.LIB.txt" "$root/tools/vlc-licenses/QUELLCODE.txt" "$dest/"
cp -r "$src/plugins" "$dest/plugins"

# Nicht zum Abspielen nötig: VLC-Oberfläche, Senden/Aufnehmen, Visualisierung, Netzwerk-Suche, Fernsteuerung, Lua.
for dir in gui access_output stream_out mux visualization services_discovery control lua video_splitter; do
    rm -rf "$dest/plugins/$dir"
done

# Kein Plugin-Index (plugins.dat): VLC prüft Zeitstempel und Größe jedes Plugins, und Build/Installation erhalten
# die Zeitstempel nicht – ein mitgelieferter Index wäre beim Nutzer immer veraltet. VLC baut ihn beim Start selbst auf.
rm -f "$dest/plugins/plugins.dat"

# Dateigröße, nicht belegter Platz (komprimierende Dateisysteme wie ZFS zeigen sonst zu wenig)
size="$( (du -sh --apparent-size "$dest" 2>/dev/null || du -sh "$dest") | cut -f1)"
echo "VLC $VLC_VERSION liegt in $dest ($size)"
