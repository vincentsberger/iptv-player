#!/usr/bin/env bash
# Baut die iPhone-App unsigniert als .ipa (nur macOS mit Xcode + XcodeGen).
# Aufruf: tools/ios/build-ipa.sh <ziel.ipa>   – signiert wird erst beim Installieren (Sideloading).
set -euo pipefail
[ $# -eq 1 ] || { echo "Aufruf: $0 <ziel.ipa>" >&2; exit 2; }
out="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
root="$(cd "$(dirname "$0")/../.." && pwd)"
version="$(grep '^appVersion=' "$root/gradle.properties" | cut -d= -f2)"

cd "$root/iosApp/xcode"
xcodegen generate --quiet
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath build \
  MARKETING_VERSION="$version" CURRENT_PROJECT_VERSION="${GITHUB_RUN_NUMBER:-1}" \
  CODE_SIGNING_ALLOWED=NO build

app="build/Build/Products/Release-iphoneos/IPTVPlayer.app"
[ -d "$app" ] || { echo "App fehlt: $app" >&2; exit 1; }
rm -rf build/ipa "$out"
mkdir -p build/ipa/Payload
cp -R "$app" build/ipa/Payload/
(cd build/ipa && zip -qry "$out" Payload)
echo "$out"
