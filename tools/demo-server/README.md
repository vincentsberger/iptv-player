# Demo-Server

Erfundener Xtream-Codes-Anbieter für Screenshots und manuelle Tests: 50 Sender mit generierten Logos
(einige fehlen absichtlich), Kategorien im typischen Anbieter-Stil („DE | NACHRICHTEN & INFO ᴴᴰ“),
31 Filme, 12 Serien, Programmvorschau und echte, selbst erzeugte Testvideos.

```bash
pip install pillow              # einmalig
./make_videos.sh                # einmalig, braucht ffmpeg
python3 demo_server.py 8099
```

Anmelden mit Benutzer `demo`, Passwort `demo` und als Server:

| Wo | Server |
|---|---|
| Desktop-App | `localhost:8099` |
| Android-Emulator | `10.0.2.2:8099` |
| Fire TV / TV im WLAN | `<IP des PCs>:8099` |
