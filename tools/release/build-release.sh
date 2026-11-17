#!/usr/bin/env bash
# Baut die signierte Release-Version der TV-App (APK zum Sideloaden / Amazon, AAB für Google Play).
# Der Upload-Schlüssel kommt aus Proton Pass (Vault „App-Signierung“, Item „FreeIPTVPlayer Signierung“),
# liegt nur während des Builds in einem privaten Temp-Ordner und wird danach überschrieben und gelöscht.
#
#   pass-cli login            # falls nötig
#   tools/release/build-release.sh
#
# Ergebnis: dist/IPTV-Player-<version>.apk und dist/IPTV-Player-<version>.aab
set -euo pipefail
cd "$(dirname "$0")/../.."
PC="${PASS_CLI:-$HOME/.local/bin/pass-cli}"
ITEM=(--vault-name "App-Signierung" --item-title "FreeIPTVPlayer Signierung")
VERSION=$(grep '^appVersion=' gradle.properties | cut -d= -f2)

"$PC" vault list >/dev/null 2>&1 || { echo "Proton Pass nicht angemeldet: erst 'pass-cli login'." >&2; exit 1; }

TMP=$(mktemp -d); chmod 700 "$TMP"
cleanup() { shred -u "$TMP/upload.jks" 2>/dev/null || rm -f "$TMP/upload.jks"; rm -rf "$TMP"; }
trap cleanup EXIT

"$PC" item view "${ITEM[@]}" --field KEYSTORE_BASE64 | base64 -d > "$TMP/upload.jks"
export IPTV_KEYSTORE="$TMP/upload.jks"
export IPTV_STORE_PASSWORD="$("$PC" item view "${ITEM[@]}" --field STORE_PASSWORD)"
export IPTV_KEY_ALIAS="$("$PC" item view "${ITEM[@]}" --field KEY_ALIAS)"
export IPTV_KEY_PASSWORD="$("$PC" item view "${ITEM[@]}" --field KEY_PASSWORD)"

export JAVA_HOME="${JAVA_HOME_17:-/usr/lib/jvm/java-17-openjdk-amd64}"
./gradlew -q clean :androidApp:assembleRelease :androidApp:bundleRelease

mkdir -p dist
cp androidApp/build/outputs/apk/release/androidApp-release.apk "dist/IPTV-Player-$VERSION.apk"
cp androidApp/build/outputs/bundle/release/androidApp-release.aab "dist/IPTV-Player-$VERSION.aab"
echo "Fertig:"; ls -l dist/IPTV-Player-"$VERSION".*
