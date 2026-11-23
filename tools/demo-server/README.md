# Demo-Server

Erfundener Xtream-Codes-Anbieter für Screenshots und manuelle Tests: 50 Sender mit generierten Logos
(einige fehlen absichtlich), Kategorien im typischen Anbieter-Stil („DE | NACHRICHTEN & INFO ᴴᴰ“),
31 Filme, 12 Serien, Programmvorschau und echte, selbst erzeugte Testvideos. Jeder dritte Film und jede dritte
Serie gilt als „neu“ (hinzugefügt vor 1–3 Tagen), dazu Abo-Daten (läuft in 30 Tagen ab, 1 von 2 Verbindungen) –
so lassen sich Startseite und Abo-Zeile ausprobieren.

```bash
pip install pillow              # einmalig
./make_videos.sh                # einmalig, braucht ffmpeg
python3 demo_server.py 8099
DEMO_DELAY=3 python3 demo_server.py 8099   # langsamer Anbieter: Ladebildschirm prüfen
```

Anmelden mit Benutzer `demo`, Passwort `demo` und als Server:

| Wo | Server |
|---|---|
| Desktop-App | `localhost:8099` |
| Android-Emulator | `10.0.2.2:8099` |
| Fire TV / TV im WLAN | `<IP des PCs>:8099` |
