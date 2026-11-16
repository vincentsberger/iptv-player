#!/usr/bin/env bash
# Erzeugt die Testvideos für den Demo-Server (keine fremden Inhalte):
#   vod.mp4 – 8-Minuten-Film mit eingeblendeter Laufzeit (Spulen, Weiterschauen, nächste Folge testen)
#   live.ts – 3-Minuten-Testbild als MPEG-TS (Live-Wiedergabe)
# Benötigt ffmpeg mit drawtext (libfreetype).
set -euo pipefail
cd "$(dirname "$0")"
FONT="${FONT:-/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf}"
# Hinweis: In zsh wäre "$FONT:text" ein Pfad-Modifikator (:t) – darum ${FONT} und ein bash-Skript.
VOD="drawtext=fontfile=${FONT}:text=DEMO-FILM:x=24:y=24:fontsize=32:fontcolor=white:box=1:boxcolor=black@0.6,drawtext=fontfile=${FONT}:text='%{pts\:hms}':x=24:y=h-64:fontsize=36:fontcolor=white:box=1:boxcolor=black@0.6"
LIVE="drawtext=fontfile=${FONT}:text=LIVE-DEMO:x=24:y=24:fontsize=32:fontcolor=white:box=1:boxcolor=red@0.8,drawtext=fontfile=${FONT}:text='%{pts\:hms}':x=w-tw-24:y=h-64:fontsize=36:fontcolor=white:box=1:boxcolor=black@0.6"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "testsrc2=size=640x360:rate=15" -f lavfi -i "sine=frequency=330:sample_rate=44100" -t 480 \
  -vf "$VOD" -c:v libx264 -preset veryfast -crf 32 -pix_fmt yuv420p -c:a aac -b:a 48k -af volume=0.15 -movflags +faststart vod.mp4
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "smptehdbars=size=640x360:rate=25" -f lavfi -i "sine=frequency=1000:sample_rate=44100" -t 180 \
  -vf "$LIVE" -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -c:a aac -b:a 48k -af volume=0.08 -f mpegts live.ts
echo "Fertig: vod.mp4, live.ts"
