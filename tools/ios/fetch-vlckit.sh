#!/usr/bin/env bash
# Holt MobileVLCKit (dynamisches Framework, LGPL 2.1) nach iosApp/vlckit/ – für cinterop und zum Einbetten in die App.
# Fest eingetragene Version + SHA-256: Ein anderes oder verändertes Archiv bricht den Build ab.
# Auf macOS wird das Geräte-Framework auf arm64 verkleinert (armv7/armv7s braucht kein unterstütztes iPhone).
set -euo pipefail

VLCKIT_FILE="${VLCKIT_FILE:-MobileVLCKit-3.7.5-a213fd11-6de05adc.tar.xz}"
VLCKIT_SHA256="${VLCKIT_SHA256:-a76f7a6cfcde4209b56f943e6e18400659d8a7a93ff482f8b1d059468d11e6be}"

root="$(cd "$(dirname "$0")/../.." && pwd)"
dest="$root/iosApp/vlckit"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "Lade $VLCKIT_FILE …"
curl -fsSL --retry 3 -o "$work/vlckit.tar.xz" "https://download.videolan.org/pub/cocoapods/prod/$VLCKIT_FILE"
echo "$VLCKIT_SHA256  $work/vlckit.tar.xz" | shasum -a 256 -c -

tar -xJf "$work/vlckit.tar.xz" -C "$work" MobileVLCKit-binary/MobileVLCKit.xcframework MobileVLCKit-binary/COPYING.txt
rm -rf "$dest"
mkdir -p "$dest"
mv "$work/MobileVLCKit-binary/MobileVLCKit.xcframework" "$work/MobileVLCKit-binary/COPYING.txt" "$dest/"

device="$dest/MobileVLCKit.xcframework/ios-arm64_armv7_armv7s/MobileVLCKit.framework/MobileVLCKit"
# LGPL: Die App bettet VLCKit als austauschbare, dynamische Bibliothek ein – ein statisches Archiv wäre ein Bruch.
file "$device" | grep -q "dynamically linked shared library" || { echo "MobileVLCKit ist nicht dynamisch gelinkt" >&2; exit 1; }
if command -v lipo >/dev/null; then
    lipo "$device" -thin arm64 -output "$device.arm64" && mv "$device.arm64" "$device"
fi
echo "MobileVLCKit liegt in $dest ($(du -sh "$dest" | cut -f1))"
