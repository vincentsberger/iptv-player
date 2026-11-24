#!/usr/bin/env python3
"""Logo „IPTV Player“: erzeugt aus einer Beschreibung alle Icons (SVG-Quellen, Android-Vektoren, PC- und MSIX-PNGs, iOS).

Aufruf (aus dem Repo-Wurzelordner):  python3 tools/brand/make_icons.py [--ios ZIEL.png] [--launch-logo ZIEL.png]
Braucht: fontTools (Schriftzug als Pfad), google-chrome (Rendern), ImageMagick `magick` (ICO, iOS ohne Alpha).
Stil: .tastemaker/style-lock.md – Anthrazit, Bernstein nur als Signal (hier: Fortschrittsbalken), keine Verläufe.
Farben: shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/Palette.kt (wird hier eingelesen).
"""
import os, subprocess, sys, tempfile
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def palette():
    """Farben aus shared/Palette.kt – dieselbe Quelle wie alle Oberflächen (`const val Name = 0xAARRGGBB`)."""
    import re
    src = open(os.path.join(ROOT, "shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/Palette.kt")).read()
    return {n: "#" + v for n, v in re.findall(r"const val (\w+) = 0x[0-9A-Fa-f]{2}([0-9A-Fa-f]{6})", src)}


_P = palette()
BG, SURFACE, LINE, TEXT, SIGNAL = _P["Bg"], _P["Surface1"], _P["Line"], _P["Text"], _P["Signal"]
FOCUS_BG, SURFACE2 = _P["FocusBg"], _P["Surface2"]
NAME = "IPTV Player"  # Schriftzug im Banner


def rrect(x, y, w, h, r):
    """Abgerundetes Rechteck als Pfad (Android-Vektoren kennen kein <rect>)."""
    return (f"M{x + r},{y}H{x + w - r}A{r},{r} 0 0 1 {x + w},{y + r}V{y + h - r}A{r},{r} 0 0 1 {x + w - r},{y + h}"
            f"H{x + r}A{r},{r} 0 0 1 {x},{y + h - r}V{y + r}A{r},{r} 0 0 1 {x + r},{y}Z")


# Das Zeichen im 512er-Raster: Antennen-„V“, Bildschirm, Fortschrittsbalken (Bernstein gespielt, Rest in Linienfarbe).
GLYPH = [  # (Pfad, Füllung, Linie, Linienbreite)
    ("M196,92L256,152L316,92", None, TEXT, 28),
    (rrect(96, 152, 320, 236, 28), None, TEXT, 28),
    (rrect(140, 318, 150, 22, 4), SIGNAL, None, 0),
    (rrect(290, 318, 82, 22, 4), LINE, None, 0),
]


def wordmark(x, baseline, size, tracking=-0.02):
    """Schriftzug NAME in Archivo SemiBold als Pfad (Ortszeit-unabhängig, kein Schrift-Font nötig beim Anzeigen)."""
    font = TTFont(os.path.join(ROOT, "androidApp/src/main/res/font/archivo_600.ttf"))
    gs, cmap, upm = font.getGlyphSet(), font.getBestCmap(), font["head"].unitsPerEm
    s = size / upm
    pen = SVGPathPen(gs)
    cx = x
    for ch in NAME:
        name = cmap[ord(ch)]
        gs[name].draw(TransformPen(pen, (s, 0, 0, -s, cx, baseline)))
        cx += gs[name].width * s + tracking * size
    return pen.getCommands(), cx - x


def svg_paths(items, indent="  "):
    out = []
    for d, fill, stroke, width in items:
        a = f'{indent}<path d="{d}" fill="{fill or "none"}"'
        if stroke:
            a += f' stroke="{stroke}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"'
        out.append(a + "/>")
    return "\n".join(out)


def icon_svg(corner=96):
    bg = f'  <path d="{rrect(0, 0, 512, 512, corner)}" fill="{BG}"/>' if corner else f'  <rect width="512" height="512" fill="{BG}"/>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">\n{bg}\n{svg_paths(GLYPH)}\n</svg>\n'


def launch_logo_svg():
    """Statisches Logo im ViewBox 240 × 200 (Gehäuse, Antenne, Bild aus, Balken) – wie `BRAND_MARK_STATIC` in der App."""
    body = rrect(24, 56, 192, 132, 16)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 200">\n'
            f'  <rect x="30" y="62" width="180" height="120" fill="{FOCUS_BG}" fill-opacity="0.06"/>\n'
            f'  <path d="M84 20 L120 52 L156 20" fill="none" stroke="{TEXT}" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>\n'
            f'  <path d="{body}" fill="none" stroke="{TEXT}" stroke-width="12" stroke-linejoin="round"/>\n'
            f'  <rect x="52" y="140" width="81.6" height="18" fill="{SIGNAL}"/>\n'
            f'  <rect x="133.6" y="140" width="54.4" height="18" fill="{SURFACE2}"/>\n</svg>\n')


def banner_parts(w, h):
    """Zeichen links, Schriftzug rechts, zusammen mittig – für TV-Banner (320×180) und MSIX-Kachel (310×150)."""
    def measure(gh):
        gs = gh / (402 - 78)           # 512er-Raster: sichtbar von y=78 bis 402, x=82 bis 430
        size = gh * 0.62
        return gs, size, (430 - 82) * gs, gh * 0.28, wordmark(0, 0, size)[1]

    gh = h * 0.46                      # Höhe des Zeichens (Antennenspitze bis Unterkante)
    gs, size, gw, gap, tw = measure(gh)
    fit = min(1.0, w * 0.82 / (gw + gap + tw))  # langer Schriftzug: Zeichen + Schrift gemeinsam verkleinern
    gh *= fit
    gs, size, gw, gap, tw = measure(gh)
    x0 = (w - (gw + gap + tw)) / 2
    gx, gy = x0 - 82 * gs, (h - gh) / 2 - 78 * gs
    baseline = h / 2 + size * 0.36
    text, _ = wordmark(x0 + gw + gap, baseline, size)
    return gx, gy, gs, text


def banner_svg(w, h):
    gx, gy, gs, text = banner_parts(w, h)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">\n  <rect width="{w}" height="{h}" fill="{BG}"/>\n'
            f'  <g transform="translate({gx:.2f} {gy:.2f}) scale({gs:.5f})">\n{svg_paths(GLYPH, "    ")}\n  </g>\n'
            f'  <path d="{text}" fill="{TEXT}"/>\n</svg>\n')


def android_paths(items, indent="        "):
    out = []
    for d, fill, stroke, width in items:
        a = f'{indent}<path android:pathData="{d}"'
        if fill: a += f' android:fillColor="{fill}"'
        if stroke:
            a += (f' android:strokeColor="{stroke}" android:strokeWidth="{width}"'
                  ' android:strokeLineCap="round" android:strokeLineJoin="round"')
        out.append(a + " />")
    return "\n".join(out)


HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Erzeugt von tools/brand/make_icons.py – nicht von Hand ändern. -->\n'


def android_icon():
    # Wie bisher: Inhalt im mittleren 72-dp-Feld eines 108-dp-Icons.
    s = 72 / 512
    return (HEAD + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'    <group android:translateX="18" android:translateY="18" android:scaleX="{s:.6f}" android:scaleY="{s:.6f}">\n'
            f'        <path android:pathData="{rrect(0, 0, 512, 512, 96)}" android:fillColor="{BG}" />\n'
            f'{android_paths(GLYPH)}\n    </group>\n</vector>\n')


def android_banner():
    gx, gy, gs, text = banner_parts(320, 180)
    return (HEAD + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="320dp" android:height="180dp"\n    android:viewportWidth="320" android:viewportHeight="180">\n'
            f'    <path android:pathData="M0,0h320v180h-320z" android:fillColor="{BG}" />\n'
            f'    <group android:translateX="{gx:.2f}" android:translateY="{gy:.2f}" android:scaleX="{gs:.5f}" android:scaleY="{gs:.5f}">\n'
            f'{android_paths(GLYPH, "        ")}\n    </group>\n'
            f'    <path android:pathData="{text}" android:fillColor="{TEXT}" />\n</vector>\n')


def render(svg, w, h, out):
    """SVG pixelgenau mit Chrome rendern (transparenter Hintergrund)."""
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "p.html")
        with open(page, "w") as f:
            f.write(f'<html><body style="margin:0;background:transparent"><div style="width:{w}px;height:{h}px">'
                    f'{svg.replace("<svg ", f"<svg width=\"{w}\" height=\"{h}\" ", 1)}</div></body></html>')
        subprocess.run(["google-chrome", "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        "--default-background-color=00000000", f"--window-size={w},{h}",
                        f"--screenshot={out}", f"file://{page}"], check=True, capture_output=True)


def write(rel, text):
    with open(os.path.join(ROOT, rel), "w") as f:
        f.write(text)


def main():
    write("docs/brand/icon.svg", icon_svg())
    write("docs/brand/banner.svg", banner_svg(320, 180))
    write("androidApp/src/main/res/drawable/ic_launcher.xml", android_icon())
    write("androidApp/src/main/res/drawable/banner.xml", android_banner())

    pkg = os.path.join(ROOT, "desktopApp/packaging")
    with tempfile.TemporaryDirectory() as tmp:
        sizes = [16, 32, 48, 64, 128, 256]
        for n in sizes:
            render(icon_svg(), n, n, os.path.join(tmp, f"{n}.png"))
        subprocess.run(["cp", os.path.join(tmp, "256.png"), os.path.join(pkg, "icon.png")], check=True)
        subprocess.run(["magick", *[os.path.join(tmp, f"{n}.png") for n in sizes], os.path.join(pkg, "icon.ico")], check=True)
    assets = os.path.join(pkg, "msix/Assets")
    render(icon_svg(), 44, 44, os.path.join(assets, "Square44x44Logo.png"))
    render(icon_svg(), 50, 50, os.path.join(assets, "StoreLogo.png"))
    render(icon_svg(), 150, 150, os.path.join(assets, "Square150x150Logo.png"))
    render(banner_svg(310, 150), 310, 150, os.path.join(assets, "Wide310x150Logo.png"))

    if "--ios" in sys.argv:  # iOS: volle Fläche ohne Ecken (iOS rundet selbst), keine Transparenz
        target = sys.argv[sys.argv.index("--ios") + 1]
        with tempfile.TemporaryDirectory() as tmp:
            png = os.path.join(tmp, "ios.png")
            render(icon_svg(corner=0), 1024, 1024, png)
            subprocess.run(["magick", png, "-background", BG, "-alpha", "remove", "-alpha", "off", target], check=True)
    if "--launch-logo" in sys.argv:  # iOS-LaunchScreen: statisches Logo (Zustand „fertig“) mit Alpha, 480 × 400 px (@2x von 240 × 200 pt)
        render(launch_logo_svg(), 480, 400, sys.argv[sys.argv.index("--launch-logo") + 1])
    print("Icons erzeugt.")


if __name__ == "__main__":
    main()
