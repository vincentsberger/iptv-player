#!/usr/bin/env bash
# Welche Plattformen baut ein Release neu? Vergleicht den Tag mit dem vorigen Versions-Tag:
#   android  – TV-App (APK)            desktop – PC-App (Linux .deb und Windows .msi)      ios – iPhone (.ipa)
# Gemeinsamer Code (shared/, Build-Dateien, Release-Workflow) baut alles neu. Die Zeile appVersion in gradle.properties
# ändert sich bei jedem Release und zählt nicht. Unveränderte Plattformen übernimmt der Release-Workflow aus dem
# vorigen Release (die Datei behält ihre Version im Namen – die Apps melden dann kein Update, siehe Updates.parse).
#
# Aufruf:  tools/release/plan-platforms.sh <tag> [auswahl]
#   auswahl: leer oder „auto“ = nach Änderungen · „all“ = alles · sonst Liste, z. B. „android ios“
# Ausgabe (für $GITHUB_OUTPUT): previous=<voriger Tag> android=true|false desktop=true|false ios=true|false
#
# Lokal ausprobieren:  tools/release/plan-platforms.sh v1.8.0
set -euo pipefail

tag="${1:?Tag fehlt, z. B. v1.8.0}"
choice="${2:-auto}"
[ -z "$choice" ] && choice=auto

prev=$(git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null || true)

emit() { # android desktop ios Grund
  echo "previous=$prev"
  echo "android=$1"
  echo "desktop=$2"
  echo "ios=$3"
  echo "Plan: TV=$1 PC=$2 iPhone=$3 – $4 (voriger Tag: ${prev:-keiner})" >&2
}

has() { [[ " $choice " == *" $1 "* ]] && echo true || echo false; }

if [ "$choice" = all ]; then emit true true true "alles gewählt"; exit 0; fi
if [ "$choice" != auto ]; then emit "$(has android)" "$(has desktop)" "$(has ios)" "von Hand gewählt"; exit 0; fi
if [ -z "$prev" ]; then emit true true true "erstes Release"; exit 0; fi

changed=$(git diff --name-only "$prev" "$tag")
# gradle.properties ohne die appVersion-Zeile: geänderte Build-Einstellungen betreffen alle Apps.
props=$(git diff "$prev" "$tag" -- gradle.properties | grep -E '^[+-][^+-]' | grep -v -E '^[+-]appVersion=' || true)

touches() { grep -qE "$1" <<<"$changed"; }
common='^(shared/|testing/|build\.gradle\.kts$|settings\.gradle\.kts$|gradle/|\.github/workflows/release\.yml$|tools/release/)'
if [ -n "$props" ] || touches "$common"; then emit true true true "gemeinsamer Code oder Build geändert"; exit 0; fi

flag() { touches "$1" && echo true || echo false; }
android=$(flag '^androidApp/')
desktop=$(flag '^(desktopApp/|tools/fetch-vlc\.sh$|tools/vlc-licenses/)')
ios=$(flag '^(iosApp/|iosUi/|tools/ios/|tools/vlc-licenses/)')

# Fehlt im vorigen Release eine Datei (z. B. iPhone-Bau damals gescheitert), gibt es nichts zu übernehmen → neu bauen.
if command -v gh > /dev/null && assets=$(gh release view "$prev" --json assets -q '.assets[].name' 2>/dev/null); then
  grep -qE '^IPTV-Player-[0-9.]+\.apk$' <<<"$assets" || android=true
  { grep -qE '\.deb$' <<<"$assets" && grep -qE '\.msi$' <<<"$assets"; } || desktop=true
  grep -qE '\.ipa$' <<<"$assets" || ios=true
else
  emit true true true "voriges Release $prev nicht lesbar"; exit 0
fi

emit "$android" "$desktop" "$ios" "nach Änderungen seit $prev"
