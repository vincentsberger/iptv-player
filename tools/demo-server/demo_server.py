"""Demo-Xtream-Server mit erfundenen Inhalten – für Screenshots und manuelle Tests.

Realistisch angelegt: Kategorie- und Sendernamen im typischen Anbieter-Stil (Länderpräfix, HD/FHD/ᴿᴬᵂ,
Backup-Feeds), generierte Senderlogos (einige fehlen oder sind kaputt, wie bei echten Anbietern),
selbst erzeugte Testvideos (vod.mp4 mit Laufzeit-Einblendung, live.ts mit Testbild). Keine fremden Inhalte.
Bild- und Stream-Adressen nutzen den Host der Anfrage → funktioniert im Emulator (10.0.2.2) und am PC (localhost).
"""
import base64, colorsys, datetime, hashlib, io, json, os, re, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageDraw, ImageFont

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
HERE = os.path.dirname(os.path.abspath(__file__))
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

LIVE = [  # (Kategorie, [Sender …])
    ("DE | NACHRICHTEN & INFO ᴴᴰ", ["DE: Nachrichten 24 HD", "DE: Welt Aktuell FHD", "DE: Info Plus HD", "DE: Börse & Wirtschaft TV HD", "DE: Nachrichten 24 HD (Backup)"]),
    ("DE | SPORT ▸ FUSSBALL & LIVE-EVENTS", ["DE: Sport Arena 1 FHD", "DE: Sport Arena 2 FHD", "DE: Sport Arena 3 HD ᴿᴬᵂ", "DE: Motor TV HD", "DE: Fußball Live Event 1 (nur bei Spielen)", "DE: Golf & Tennis HD", "DE: Kampfsport Channel HD"]),
    ("DE | UNTERHALTUNG & SHOWS", ["DE: Show Kanal HD", "DE: Serien Eins FHD", "DE: Komödie TV HD", "DE: Krimi Zeit HD", "DE: Abenteuer Kanal HD", "DE: Lifestyle & Kochen HD"]),
    ("DE | KINDER & FAMILIE", ["DE: Kinder Kanal HD", "DE: Toon Time", "DE: Junior Wissen HD", "DE: Familien Kino HD"]),
    ("DE | DOKU & WISSEN", ["DE: Planet Doku HD", "DE: Natur & Tier FHD", "DE: Geschichte TV HD", "DE: Technik & Zukunft HD", "DE: Reise Welt HD"]),
    ("DE | MUSIK", ["DE: Musik Plus HD", "DE: Schlager Welle", "DE: Rock Station HD", "DE: Klassik Live"]),
    ("DE | REGIONAL ▸ NORD / OST / SÜD / WEST", ["DE: Region Nord HD", "DE: Region Ost HD", "DE: Region Süd HD", "DE: Region West HD", "DE: Stadtsender Hafenstadt"]),
    ("AT | ÖSTERREICH ALLGEMEIN", ["AT: Alpen TV HD", "AT: Donau Info HD", "AT: Wiener Bühne"]),
    ("CH | SCHWEIZ ALLGEMEIN", ["CH: Helvetia Eins HD", "CH: Bergkanal HD"]),
    ("4K | ULTRA HD SENDER (NUR MIT SCHNELLER LEITUNG)", ["4K: Natur & Tier UHD", "4K: Sport Arena 1 UHD", "4K: Planet Doku UHD HDR"]),
    ("EN | INTERNATIONAL NEWS 24/7", ["EN: World News 24 HD", "EN: Global Business HD", "EN: Breaking Report"]),
    ("DE | 24/7 SERIEN-SENDER", ["24/7 | Hafenwache – alle Staffeln", "24/7 | Nordlicht – Krimi rund um die Uhr", "24/7 | Familienbande Classics"]),
]
LIVE_CATS = [(str(i + 1), name) for i, (name, _) in enumerate(LIVE)]
CHANNELS = [(ch, str(i + 1)) for i, (_, chs) in enumerate(LIVE) for ch in chs]

PROGRAMS = ["Tagesüberblick", "Weltspiegel kompakt", "Spieltag live – Konferenz", "Die große Quizshow", "Expedition Arktis",
            "Abenteuer im Wald", "Wetter & Verkehr", "Talk am Abend", "Börse am Mittag", "Kochduell regional",
            "Musik der 90er – die Hits", "Reportage: Hafen bei Nacht", "Kinderkino am Nachmittag", "Spätnachrichten"]

VOD = [
    ("DE | KINO-HIGHLIGHTS 2024/2025", ["Signal aus dem Nebel (2024) [FHD]", "Die letzte Station – Extended Edition (2023)", "Orbit 9 (2025)", "Unter dem Eis (2024) [4K]", "Der Brief (2024)"]),
    ("DE | ACTION & THRILLER", ["Nachtschicht in Sektor 7 (2021)", "Der falsche Zeuge (2019)", "Kaltfront (2022) [FHD]", "Gegenwind – Die Jagd beginnt (2020)", "Brücke der Spione von nebenan (2018)"]),
    ("DE | KOMÖDIE", ["Chaos im Ferienhaus (2022)", "Der falsche Onkel (2020)", "Hochzeit mit Hindernissen 2: Jetzt erst recht (2023)", "Opa fährt Rennrad (2021)"]),
    ("DE | DRAMA & ROMANZE", ["Das stille Haus (2019)", "Zwischen den Wellen (2022)", "Ein Sommer in Lissabon (2021) [OmU]", "Was vom Winter bleibt (2020)"]),
    ("DE | SCIENCE-FICTION & FANTASY", ["Echo der Sterne (2023)", "Die Stadt unter Glas (2024)", "Zeitfenster 2049 (2022) [4K HDR]", "Der Hüter des Nordlichts (2021)"]),
    ("DE | DOKUMENTATIONEN", ["Wildes Island – Eine Reise ans Ende der Welt (2023)", "Die Bienen von Berlin (2022)", "Tiefsee: Leben im Dunkeln (2024) [4K]"]),
    ("DE | KINDER- & FAMILIENFILME", ["Pixel und die verlorene Insel (2023)", "Das Geheimnis der Drachenhöhle (2021)", "Ferien auf dem Kuhhof (2022)"]),
    ("EN | ORIGINAL MIT UNTERTITEL", ["The Long Way Home (2022) [EN]", "Midnight at the Lighthouse (2023) [EN/OmU]", "Paper Planes Over Paris (2021) [EN]"]),
]
VOD_CATS = [(str(10 + i), name) for i, (name, _) in enumerate(VOD)]
MOVIES = [(m, str(10 + i)) for i, (_, ms) in enumerate(VOD) for m in ms]

SER = [
    ("DE | SERIEN ▸ KRIMI & THRILLER", ["Hafenwache", "Nordlicht", "Die Akte Brandt", "Schatten über Lübeck"]),
    ("DE | SERIEN ▸ DRAMA", ["Die Werft", "Familienbande", "Station 7 – Notaufnahme"]),
    ("DE | SERIEN ▸ COMEDY", ["WG Sonnenallee", "Der Hausmeister", "Büro Büro reloaded"]),
    ("DE | DOKU-SERIEN", ["Abenteuer Alpen", "Megastädte der Zukunft"]),
]
SER_CATS = [(str(40 + i), name) for i, (name, _) in enumerate(SER)]
SERIES = [(s, str(40 + i)) for i, (_, ss) in enumerate(SER) for s in ss]

_cache = {}


def font(size):
    try:
        return ImageFont.truetype(FONT, size)
    except OSError:
        return ImageFont.load_default()


def png(img):
    buf = io.BytesIO(); img.save(buf, "PNG"); return buf.getvalue()


def poster(title, w, h, seed):
    hue = (seed * 0.137) % 1.0
    top = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.55, 0.55))
    bottom = tuple(int(c * 255) for c in colorsys.hsv_to_rgb((hue + 0.08) % 1, 0.7, 0.18))
    img = Image.new("RGB", (w, h)); d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(3)))
    title = re.sub(r"\s*[\(\[].*$", "", title)  # Jahr/Qualität nicht aufs Cover
    words, lines, cur = title.split(), [], ""
    for wd in words:
        if len(cur + " " + wd) > 14 and cur:
            lines.append(cur); cur = wd
        else:
            cur = (cur + " " + wd).strip()
    lines.append(cur)
    f = font(max(18, w // 11)); y = h - 30 - len(lines) * (w // 9)
    for ln in lines:
        d.text((20, y), ln, font=f, fill=(255, 255, 255)); y += w // 9
    return png(img)


def logo(name, seed):
    """Erfundenes Senderlogo: Form + Kürzel/Wortmarke in Senderfarbe, transparenter Hintergrund."""
    base = re.sub(r"^[A-Z0-9/]+\s*[:|]\s*", "", name)
    base = re.sub(r"\s*(\(.*\)|HD|FHD|UHD|HDR|ᴿᴬᵂ|–.*)\s*", " ", base).strip()
    words = [w for w in re.split(r"[\s&]+", base) if w]
    short = "".join(w[0] for w in words[:3]).upper() if len(words) > 1 else base[:6].upper()
    hue = (int(hashlib.md5(base.encode()).hexdigest()[:6], 16) % 360) / 360
    col = tuple(int(c * 255) for c in colorsys.hsv_to_rgb(hue, 0.75, 0.95))
    W, H = 320, 180
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    style = seed % 4
    if style == 0:   # Kreis mit Kürzel
        d.ellipse((W / 2 - 70, H / 2 - 70, W / 2 + 70, H / 2 + 70), fill=col)
        f = font(64 if len(short) <= 2 else 48); d.text((W / 2, H / 2), short, font=f, fill="white", anchor="mm")
    elif style == 1:  # Abgerundetes Rechteck mit Kürzel
        d.rounded_rectangle((40, 30, W - 40, H - 30), 22, fill=col)
        d.text((W / 2, H / 2), short, font=font(70 if len(short) <= 3 else 52), fill="white", anchor="mm")
    elif style == 2:  # Wortmarke mit Farbbalken
        d.rectangle((20, H / 2 + 30, W - 20, H / 2 + 40), fill=col)
        d.text((W / 2, H / 2 - 4), base[:14], font=font(40 if len(base) <= 10 else 30), fill="white", anchor="mm")
    else:             # Schräges Band
        d.polygon([(30, 130), (90, 40), (W - 30, 40), (W - 90, 130)], fill=col)
        d.text((W / 2, 86), short, font=font(60), fill="white", anchor="mm")
    return png(img)


def epg(stream_id):
    now = datetime.datetime.now().replace(second=0, microsecond=0)
    start = now - datetime.timedelta(minutes=(now.minute % 30) + 10)
    out = []
    for i in range(2):
        s = start + datetime.timedelta(minutes=45 * i); e = s + datetime.timedelta(minutes=45)
        title = PROGRAMS[(int(stream_id) * 3 + i) % len(PROGRAMS)]
        out.append({"title": base64.b64encode(title.encode()).decode(),
                    "start": s.strftime("%Y-%m-%d %H:%M:%S"), "end": e.strftime("%Y-%m-%d %H:%M:%S")})
    return {"epg_listings": out}


def channel_icon(host, k):
    if k % 9 == 4: return None                       # manche Sender haben kein Logo …
    if k % 13 == 7: return f"{host}/img/missing.png"  # … und manche ein kaputtes
    return f"{host}/img/l{k}.png"


def api(host, action, q):
    if action is None:
        return {"user_info": {"auth": 1, "status": "Active", "allowed_output_formats": ["ts"]}}
    if action == "get_live_categories":
        return [{"category_id": i, "category_name": n} for i, n in LIVE_CATS]
    if action == "get_live_streams":
        return [{"stream_id": k + 1, "name": n, "category_id": c, "stream_icon": channel_icon(host, k)} for k, (n, c) in enumerate(CHANNELS)]
    if action == "get_vod_categories":
        return [{"category_id": i, "category_name": n} for i, n in VOD_CATS]
    if action == "get_vod_streams":
        return [{"stream_id": 1000 + k, "name": n, "category_id": c,
                 "stream_icon": None if k % 11 == 5 else f"{host}/img/m{k}.png", "container_extension": "mp4"} for k, (n, c) in enumerate(MOVIES)]
    if action == "get_series_categories":
        return [{"category_id": i, "category_name": n} for i, n in SER_CATS]
    if action == "get_series":
        return [{"series_id": 2000 + k, "name": n, "category_id": c, "cover": f"{host}/img/s{k}.png"} for k, (n, c) in enumerate(SERIES)]
    if action == "get_series_info":
        sid = int(q.get("series_id", ["2000"])[0])
        eps = {}
        for season in (1, 2, 3) if sid % 2 else (1, 2):
            eps[str(season)] = [{"id": f"{sid}{season}{e:02d}", "episode_num": e,
                                 "title": f"Folge {e}: " + PROGRAMS[(e + season) % len(PROGRAMS)],
                                 "container_extension": "mp4", "info": {"movie_image": f"{host}/img/e{(e + season) % 6}.png"}}
                                for e in range(1, 9 if season == 1 else 7)]
        return {"episodes": eps}
    if action == "get_short_epg":
        return epg(q.get("stream_id", ["1"])[0])
    return []


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def send_file(self, path, ctype):
        size = os.path.getsize(path)
        rng = re.match(r"bytes=(\d*)-(\d*)", self.headers.get("Range", ""))
        start, end = 0, size - 1
        if rng:
            if rng[1]: start = int(rng[1])
            if rng[2]: end = min(int(rng[2]), size - 1)
            self.send_response(206); self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        else:
            self.send_response(200)
        self.send_header("Content-Type", ctype); self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1)); self.end_headers()
        with open(path, "rb") as f:
            f.seek(start); left = end - start + 1
            try:
                while left > 0:
                    chunk = f.read(min(65536, left))
                    if not chunk: break
                    self.wfile.write(chunk); left -= len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def send_body(self, code, body, ctype):
        self.send_response(code); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path); host = f"http://{self.headers.get('Host', f'localhost:{PORT}')}"
        if u.path.startswith(("/live/", "/movie/", "/series/")):
            name, ctype = ("live.ts", "video/mp2t") if u.path.startswith("/live/") else ("vod.mp4", "video/mp4")
            path = os.path.join(HERE, name)
            if not os.path.exists(path):
                return self.send_body(404, b"Testvideo fehlt: ./make_videos.sh ausfuehren", "text/plain")
            return self.send_file(path, ctype)
        if u.path.startswith("/img/"):
            name = u.path[5:-4]
            if name == "missing" or not name[1:].isdigit():
                return self.send_body(404, b"", "text/plain")
            kind, idx = name[0], int(name[1:])
            if name not in _cache:
                if kind == "m": _cache[name] = poster(MOVIES[idx][0], 300, 450, idx)
                elif kind == "s": _cache[name] = poster(SERIES[idx][0], 300, 450, idx + 20)
                elif kind == "l": _cache[name] = logo(CHANNELS[idx][0], idx)
                else: _cache[name] = poster("", 480, 270, idx + 40)
            return self.send_body(200, _cache[name], "image/png")
        if u.path.endswith("player_api.php"):
            q = parse_qs(u.query)
            return self.send_body(200, json.dumps(api(host, q.get("action", [None])[0], q)).encode(), "application/json")
        self.send_body(404, b"", "text/plain")

    def log_message(self, *a):
        pass


if not all(os.path.exists(os.path.join(HERE, f)) for f in ("vod.mp4", "live.ts")):
    print("Hinweis: Testvideos fehlen – erst ./make_videos.sh ausführen, sonst schlägt die Wiedergabe fehl.")
print(f"Demo-Server läuft auf Port {PORT} (Benutzer demo / Passwort demo)")
ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
