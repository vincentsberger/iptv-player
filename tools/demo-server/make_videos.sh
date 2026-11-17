#!/usr/bin/env bash
# Erzeugt die Testvideos für den Demo-Server (keine fremden Inhalte):
#   vod.mp4 – 8-Minuten-Film mit eingeblendeter Laufzeit (Spulen, Weiterschauen, nächste Folge testen),
#             zwei Tonspuren und zwei Untertitelspuren (Spurauswahl testen)
#   live.ts – 3-Minuten-Testbild als MPEG-TS (Live-Wiedergabe)
# Benötigt ffmpeg mit drawtext (libfreetype).
set -euo pipefail
cd "$(dirname "$0")"
FONT="${FONT:-/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf}"
# Hinweis: In zsh wäre "$FONT:text" ein Pfad-Modifikator (:t) – darum ${FONT} und ein bash-Skript.
VOD="drawtext=fontfile=${FONT}:text=DEMO-FILM:x=24:y=24:fontsize=32:fontcolor=white:box=1:boxcolor=black@0.6,drawtext=fontfile=${FONT}:text='%{pts\:hms}':x=24:y=h-64:fontsize=36:fontcolor=white:box=1:boxcolor=black@0.6"
LIVE="drawtext=fontfile=${FONT}:text=LIVE-DEMO:x=24:y=24:fontsize=32:fontcolor=white:box=1:boxcolor=red@0.8,drawtext=fontfile=${FONT}:text='%{pts\:hms}':x=w-tw-24:y=h-64:fontsize=36:fontcolor=white:box=1:boxcolor=black@0.6"
# Zwei Tonspuren (Deutsch 330 Hz, Englisch 550 Hz) und zwei Untertitelspuren – zum Testen der Spurauswahl im Player.
subs() { # $1 = Präfix; alle 20 s eine Zeile mit Laufzeit
  for i in $(seq 0 23); do s=$((i * 20)); printf '%d\n00:%02d:%02d,000 --> 00:%02d:%02d,000\n%s – Minute %d, Sekunde %d\n\n' \
    $((i + 1)) $((s / 60)) $((s % 60)) $(((s + 8) / 60)) $(((s + 8) % 60)) "$1" $((s / 60)) $((s % 60)); done
}
subs "Deutscher Untertitel" > /tmp/demo-de.srt
subs "English subtitle" > /tmp/demo-en.srt
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "testsrc2=size=640x360:rate=15" -f lavfi -i "sine=frequency=330:sample_rate=44100" \
  -f lavfi -i "sine=frequency=550:sample_rate=44100" -i /tmp/demo-de.srt -i /tmp/demo-en.srt -t 480 \
  -map 0:v -map 1:a -map 2:a -map 3:s -map 4:s \
  -vf "$VOD" -c:v libx264 -preset veryfast -crf 32 -pix_fmt yuv420p -c:a aac -b:a 48k -filter:a volume=0.15 -c:s mov_text \
  -metadata:s:a:0 language=deu -metadata:s:a:1 language=eng -metadata:s:s:0 language=deu -metadata:s:s:1 language=eng \
  -movflags +faststart vod.mp4
rm -f /tmp/demo-de.srt /tmp/demo-en.srt
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "smptehdbars=size=640x360:rate=25" -f lavfi -i "sine=frequency=1000:sample_rate=44100" -t 180 \
  -vf "$LIVE" -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac -b:a 48k -af volume=0.08 -f mpegts live.ts
echo "Fertig: vod.mp4, live.ts"
