# PC-App im Microsoft Store (MSIX)

**Stand:** 26.11.2026 · Größe: kleines Projekt · Plattform: PC (Windows) · Branch `feat/microsoft-store`  
**Plattformen:** PC (Windows) ✅ · TV ➖ · iPhone ➖

**Umgesetzt** am 26.11.2026 (PR #42, Plan `docs/superpowers/plans/2026-11-26-microsoft-store-msix.md`). Offen: Konto, Namensreservierung und Repo-Variablen `MSIX_*` (Vincent); erst dann hängt ein Release das MSIX an.

## Warum

Zwei Ziele:
- **Mehr Nutzer erreichen:** Viele Windows-Nutzer suchen Apps im Store statt auf GitHub.
- **Die SmartScreen-Warnung loswerden:** Die GitHub-.msi ist unsigniert, deshalb warnt Windows beim ersten Start.
  Store-Installationen zeigen die Warnung nie, weil Microsoft MSIX-Pakete selbst signiert.

## Entschieden

- **Weg C:** MSIX für den Store, dazu später eine signierte .msi auf GitHub.
- **Diese Spec umfasst nur den Store-Teil (MSIX).** Die Signatur der .msi folgt später als eigenes Vorhaben
  (Kandidaten: SignPath Foundation kostenlos für Open Source, Azure Artifact Signing etwa 10 $ im Monat).
- **Einreichen zuerst von Hand:** Der Release-Lauf baut das MSIX und hängt es ans GitHub-Release, Vincent lädt es
  im Partner Center hoch. Automatisch einreichen (Store-Schnittstelle, `msstore`-CLI) erst, wenn der erste Eintrag
  freigegeben ist.
- **Konto:** Vincent legt das Einzelentwickler-Konto an (kostenlos über storedeveloper.microsoft.com, Ausweis und
  Selfie) und reserviert den App-Namen. Die Anleitung steht schon in `docs/PUBLISHING.md`, Abschnitt „Windows: Microsoft Store“.

## Ansätze (gewählt: C)

- **A. Signierte .msi für beide Kanäle:** ein einziges Paket, der eigene Updater bleibt. Braucht ein kostenpflichtiges
  oder beantragtes Zertifikat. Nicht gewählt.
- **B. Nur MSIX für den Store:** kostenlos signiert, der Store liefert die Updates. Die GitHub-.msi warnt aber weiter.
  Nicht gewählt.
- **C. MSIX im Store und signierte .msi auf GitHub (gewählt):** das beste Ergebnis in beiden Kanälen, dafür zwei Pakete.
  Begonnen wird mit dem MSIX-Teil.

## Bestand (schon im Repo)

- `desktopApp/packaging/build-msix.ps1`: Das Skript baut `createReleaseDistributable` und kopiert den App-Ordner,
  das Manifest und die Assets in einen Staging-Ordner. Danach packt `makeappx` daraus `build/msix/IPTVPlayer.msix`.
- `desktopApp/packaging/msix/AppxManifest.xml` (Ausschnitt):
  - `runFullTrust` und `internetClient` sind eingetragen, Sprache ist `de-de`.
  - Name, Publisher und PublisherDisplayName sind Platzhalter („ANPASSEN“).
  - Die Version ist fest `1.2.0.0`.
- Die Assets (Square44/150, Wide310, StoreLogo) erzeugt `tools/brand/make_icons.py`.
- `docs/PUBLISHING.md`: Anleitung für Konto, Namensreservierung, Einreichung und Hinweise zur Zertifizierung.
- `tools/fetch-vlc.sh` legt libVLC für Windows bereit. Der Release-Job `windows` baut damit .msi und portable Zip.

## Was zu bauen ist

- **1. Version aus `appVersion`:**
  - Das Skript setzt `Identity/@Version` beim Bauen auf `X.Y.Z.0`. Die vierte Stelle muss 0 sein, sonst lehnt der Store ab.
  - Gelesen wird `appVersion` aus `gradle.properties`.
  - Die feste `1.2.0.0` im Manifest bleibt als Vorgabe stehen und wird beim Bauen überschrieben.
- **2. Store-Identität ohne Handarbeit im Manifest:**
  - Name, Publisher und PublisherDisplayName kommen als Parameter bzw. Umgebungsvariablen ins Skript.
  - Im Release-Lauf stammen sie aus Repository-Variablen (`vars.MSIX_IDENTITY_NAME`, `vars.MSIX_PUBLISHER`,
    `vars.MSIX_PUBLISHER_DISPLAY_NAME`). Sie sind nicht geheim, erscheinen aber erst nach der Reservierung.
  - *(Meine Entscheidung)* Eine vierte, optionale Variable `vars.MSIX_DISPLAY_NAME` setzt `Properties/DisplayName`
    und `uap:VisualElements/@DisplayName`. Ohne sie gilt „IPTV Player“. Der Anzeigename muss zum reservierten Namen
    passen, und „IPTV Player“ ist im Store womöglich schon vergeben.
  - Fehlen sie, baut der Lauf **kein** MSIX und schreibt einen Hinweis. Das Release scheitert nicht daran.
- **3. Store-Fassung ohne eigenen Updater:**
  - Das Skript schreibt beim Bauen `-Diptvplayer.store=true` in die JVM-Optionen (`app/IPTVPlayer.cfg`, Abschnitt
    `[JavaOptions]`). *(Meine Entscheidung)* Das geschieht nur in der Kopie im Staging-Ordner: Der gemeinsame App-Ordner
    für .msi und portable Zip bleibt unverändert, die Reihenfolge der Schritte im Workflow spielt keine Rolle.
  - Die PC-App liest die Eigenschaft und schaltet in dieser Fassung ab:
    - die tägliche Update-Prüfung,
    - „Nach Updates suchen“,
    - den Update-Hinweis auf der Startseite,
    - das Häkchen „Einmal am Tag automatisch nachsehen“.
  - Stattdessen steht unter „Updates“: „Updates kommen über den Microsoft Store.“
  - `shared` bleibt unverändert: Die Store-Fassung ruft `checkForUpdate` schlicht nicht auf.
- **4. Release-Lauf:**
  - Im Job `windows` packt ein zusätzlicher Schritt nach dem Bau das MSIX, und zwar aus **demselben App-Ordner**,
    aus dem die portable Zip entsteht (mit VLC). So wird nichts zweimal gebaut.
  - Das Skript bekommt dafür einen Schalter, mit dem es einen schon gebauten App-Ordner übernimmt
    (`-AppDir`, ohne erneuten Gradle-Lauf). *(Meine Entscheidung)* Im Release zeigt `-AppDir` auf
    `binaries/main/app/IPTVPlayer` aus `createDistributable`, also auf denselben Ordner wie .msi und Zip. Ohne `-AppDir`
    baut das Skript lokal wie bisher `createReleaseDistributable`. Weil ProGuard aus ist, sind beide inhaltlich gleich.
  - Prüfungen vor dem Upload:
    - Liegt `libvlc.dll` im Staging-Ordner?
    - Gibt `makeappx unpack` das Paket fehlerfrei wieder aus?
    - Hat das Manifest die richtige Version?
  - Das Ergebnis heißt `IPTVPlayer-X.Y.Z.msix` und wird mit den anderen Windows-Dateien hochgeladen und ans Release
    gehängt. Betroffen sind die Upload-Pfade in `release.yml`.
  - Prüfung der Version: nach `makeappx unpack` `Identity/@Version` mit `X.Y.Z.0` vergleichen.
  - *(Meine Entscheidung)* Im Job `release` ist das MSIX **optional** und wird **nicht** aus dem vorigen Release
    übernommen, genau wie seit PR #40 die .ipa. Es hängt also nur an Releases, die es frisch gebaut haben (Desktop
    geändert, Variablen gesetzt). Sonst läge ein MSIX mit alter Version an einem neuen Release, und beim Hochladen
    wäre unklar, welches gilt.
- **5. Doku:**
  - `docs/PUBLISHING.md` bekommt die Repo-Variablen und den Weg „MSIX aus dem Release laden → im Partner Center hochladen“.
  - Dort steht auch der Hinweis: Das MSIX am Release ist unsigniert und nur für den Store gedacht, lokal lässt es sich
    nicht installieren.
  - In `DATENSCHUTZ.md` kommt der Satz: Die Store-Fassung fragt nicht bei GitHub nach Updates.
  - Ins README kommt der Store-Link, sobald der Eintrag live ist (nicht Teil dieses Vorhabens).

## Ziele, Nicht-Ziele, Anti-Ziele

- **Ziele:** Jedes Release enthält ein gültiges, Store-taugliches MSIX mit libVLC und korrekter Version. Die
  Store-Fassung sucht nie selbst nach Updates.
- **Nicht-Ziele:**
  - Signatur der .msi.
  - Automatisches Einreichen.
  - Englischer Store-Eintrag (Texte auf Englisch sind zurückgestellt, siehe CLAUDE.md).
  - arm64-Paket.
- **Anti-Ziele (Fehlschlag, auch wenn es technisch läuft):**
  - Die Store-Fassung bietet ein GitHub-Update an. Sie würde damit eine .msi neben die Store-App installieren,
    und der Store lehnt Selbst-Updates ab.
  - Ein fehlendes MSIX lässt das ganze Release scheitern.
  - Das MSIX läuft ohne libVLC und zeigt nur „Wiedergabe-Engine fehlt“.

## Rahmen und bekannte Folgen

- **MSIX virtualisiert `%APPDATA%`:** Die Store-Fassung speichert ihre Daten getrennt von einer .msi-Installation.
  Wer wechselt, muss sich neu anmelden. Das ist akzeptiert und wird in `PUBLISHING.md` erwähnt.
- `windows-latest` bringt das Windows SDK mit `makeappx.exe` mit; das Skript sucht es schon dort.
- Konventionen aus CLAUDE.md gelten: Commits englisch, Kommentare deutsch, Zeilen bis 160.

## Dem Builder überlassen

- Wie das Skript die Werte ins Manifest schreibt (XML über PowerShell oder Platzhalter ersetzen).
- Wo in der PC-App die Store-Erkennung liegt (z. B. `isStoreBuild` neben `appVersion` in `HomeScreen.kt`).
- Tests:
  - reine Funktion für die MSIX-Version (`"2.4.0" → "2.4.0.0"`), falls in Kotlin, sonst Prüfung im Skript;
  - UI-Test: In der Store-Fassung gibt es keinen Knopf „Nach Updates suchen“ und keinen Update-Hinweis.

## Manuell (Vincent)

- **1.** Konto anlegen, App-Namen reservieren, die drei Identitätswerte als Repo-Variablen eintragen.
- **2.** Nach dem nächsten Release das MSIX im Partner Center hochladen und die Einreichung ausfüllen (siehe `PUBLISHING.md`).
