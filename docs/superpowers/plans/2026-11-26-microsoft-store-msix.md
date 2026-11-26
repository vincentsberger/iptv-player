# PC-App im Microsoft Store (MSIX) – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jedes Release mit Desktop-Änderungen hängt ein Store-taugliches `IPTVPlayer-X.Y.Z.msix` (mit libVLC, ohne eigenen Updater) ans GitHub-Release.

**Architecture:** `build-msix.ps1` wird parametrisierbar: Es übernimmt einen fertigen App-Ordner, kopiert ihn in einen Staging-Ordner, schreibt
Version/Identität ins Manifest und die Store-Kennung in die `.cfg` der Kopie und packt mit `makeappx`. Die PC-App liest
`-Diptvplayer.store=true` und blendet alles rund um GitHub-Updates aus. Ein CI-Job prüft das Packen in PRs, der Release-Job baut das MSIX,
sobald die Store-Identität als Repo-Variablen da ist.

**Tech Stack:** PowerShell 7 (Windows-Runner), Windows SDK `makeappx.exe`, GitHub Actions, Kotlin/Compose Desktop.

**Spec:** `docs/superpowers/specs/2026-11-26-microsoft-store-msix-design.md`

## Global Constraints

- `shared/` bleibt unverändert; die Store-Fassung ruft `checkForUpdate` nur nicht auf.
- MSIX-Version = `appVersion` + `.0` (vierte Stelle immer 0).
- Fehlende Store-Identität (Repo-Variablen) → kein MSIX, Hinweis im Lauf, Release läuft weiter.
- Ohne `vlc\libvlc.dll` im Staging-Ordner bricht das Skript ab (kein MSIX ohne Engine).
- Der gemeinsame App-Ordner (.msi, portable Zip) wird nie verändert – nur die Kopie im Staging-Ordner.
- Das MSIX wird nie aus einem vorigen Release übernommen (`nur-diese-version`).
- Lokal gibt es kein `pwsh`: Das Skript wird über den CI-Job aus Task 3 geprüft (läuft in PRs).
- Konventionen: Kommentare deutsch, Bezeichner englisch, Zeilen ≤ 160, Commits englisch ohne Co-Authored-By.

## Review Focus

- **Store-Fassung zeigt nie GitHub-Updates:** auch nicht über einen alten, gespeicherten Update-Hinweis. Test: Task 1 `storeBuildShowsNoUpdateHintOnStart`.
- **Release ohne Repo-Variablen:** Der Windows-Job bleibt grün, das Release erscheint ohne MSIX. Prüfung: Task 4, Schritt-Bedingung + `nur-diese-version`.
- **Pfade mit Leerzeichen / `.cfg` ohne `[JavaOptions]`:** Das Skript legt den Abschnitt an, statt ins Leere zu schreiben. Prüfung: Task 2 Selbstcheck nach dem Schreiben.
- **Manifest-Version falsch** (z. B. `2.4.0` statt `2.4.0.0`, oder die feste `1.2.0.0` bleibt): Der Store lehnt ab. Prüfung: Task 2 Vergleich nach `makeappx unpack`.
- **`.msix` gerät in den App-Updater oder in `take '*IPTVPlayer-*.msi'`:** Die Muster enden auf `.msi`, `.msix` passt nicht. Prüfung: Task 4 Schritt 1 (gezielte Kontrolle der Muster).

---

### Task 1: Store-Fassung ohne eigenen Updater (PC-App)

**Files:**
- Modify: `desktopApp/src/main/kotlin/de/vincentsberger/iptvplayer/desktop/ui/HomeScreen.kt` (`appVersion` bei :916, `UpdateSettings` :925, Update-Prüfung :1014, `UpdateHint`-Aufruf :1030)
- Test: Create `desktopApp/src/test/kotlin/de/vincentsberger/iptvplayer/desktop/ui/StoreBuildTest.kt`

**Interfaces:**
- Produces: `internal val isStoreBuild: Boolean` – `System.getProperty("iptvplayer.store") == "true"`; `internal const val STORE_UPDATES_TEXT = "Updates kommen über den Microsoft Store."`

- [ ] **Step 1: Failing Tests** – `StoreBuildTest` (setzt `System.setProperty("iptvplayer.store", "true")` in `@BeforeTest`, entfernt sie in `@AfterTest`):
  - `storeBuildShowsStoreTextInsteadOfUpdateButton`: Einstellungen → Text `STORE_UPDATES_TEXT` existiert; kein Knoten mit Text „Nach Updates suchen“ und keiner mit „Einmal am Tag automatisch nachsehen“
  - `storeBuildShowsNoUpdateHintOnStart`: `app.checkForUpdate("1.0.0", force = true, http = FakeHttp { release })` vorab (Release `v9.9.0`), Startseite → `onAllNodesWithTag("update-hint").assertCountEquals(0)`
  - `regularBuildStillOffersUpdates` (Property entfernt): Einstellungen → „Nach Updates suchen“ existiert
- [ ] **Step 2: Laufen lassen** – `./gradlew :desktopApp:test --tests '*StoreBuildTest'` → FAIL (unresolved `STORE_UPDATES_TEXT`)
- [ ] **Step 3: Umsetzen** – `isStoreBuild` neben `appVersion`. `UpdateSettings`: in der Store-Fassung nur Versionszeile + `STORE_UPDATES_TEXT` (Muted). Update-Prüfung auf der Startseite nur `if (!isStoreBuild && appVersion.isNotEmpty())`. Update-Hinweis nur `!isStoreBuild`. KDoc deutsch.
- [ ] **Step 4: Tests grün** – `./gradlew :desktopApp:test detekt`
- [ ] **Step 5: Commit** `Hide GitHub updates in the Microsoft Store build`

### Task 2: `build-msix.ps1` parametrisieren und absichern

**Files:**
- Modify: `desktopApp/packaging/build-msix.ps1`
- Modify: `desktopApp/packaging/msix/AppxManifest.xml` (Kommentar: Werte kommen vom Skript; Platzhalter bleiben als Vorgabe)

**Interfaces:**
- Produces (Aufruf, von Task 3 und 4 genutzt):
  ```
  build-msix.ps1 [-AppDir <Ordner>] [-Version <X.Y.Z>] -IdentityName <s> -Publisher <s> -PublisherDisplayName <s>
                 [-DisplayName <s> = "IPTV Player"] [-OutFile <Pfad> = desktopApp\build\msix\IPTVPlayer-<Version>.msix]
  ```
  - ohne `-AppDir`: baut wie bisher `createReleaseDistributable` und nimmt `build\compose\binaries\main-release\app\IPTVPlayer`
  - ohne `-Version`: liest `appVersion=` aus `gradle.properties`
  - Funktion `ConvertTo-MsixVersion([string]$v)`: `"2.4.0"` → `"2.4.0.0"`; wirft bei allem, was nicht `^\d+\.\d+\.\d+$` ist
  - Exit ≠ 0 bei: fehlender `IPTVPlayer.exe`, fehlender `vlc\libvlc.dll` (rekursiv unter `app\resources`), `makeappx`-Fehler, Versionsabweichung nach `unpack`

- [ ] **Step 1: Selbstcheck ins Skript schreiben (zuerst)** – am Skriptende: `makeappx unpack /p $out /d $check /o`, `[xml]` aus `$check\AppxManifest.xml` lesen, `Identity.Version -eq (ConvertTo-MsixVersion $Version)` und `Identity.Name -eq $IdentityName` prüfen, sonst `throw`; außerdem `$check\app\IPTVPlayer.cfg` enthält `-Diptvplayer.store=true`. Diese Prüfung ist der Test des Skripts (läuft in Task 3 im CI).
- [ ] **Step 2: Umsetzen** – `param(...)` wie oben; Staging: App-Ordner kopieren, Manifest per `[xml]` laden, `Identity.Name/Publisher/Version`, `Properties.DisplayName`, `Properties.PublisherDisplayName`, `uap:VisualElements/@DisplayName` setzen, mit UTF-8 ohne BOM speichern; in `stage\app\IPTVPlayer.cfg` die Zeile `java-options=-Diptvplayer.store=true` unter `[JavaOptions]` anhängen (Abschnitt anlegen, falls er fehlt); VLC-Prüfung als harter Fehler; `makeappx pack /d $stage /p $out /o`.
- [ ] **Step 3: Statisch prüfen** – `grep -n 'ANPASSEN\|1.2.0.0' desktopApp/packaging/build-msix.ps1` → keine Treffer (Werte nur über Parameter); Manifest-Kommentar aktualisiert.
- [ ] **Step 4: Commit** `Parameterise the MSIX build: version, identity, store flag, self-check`

### Task 3: CI prüft das Packen in PRs

**Files:**
- Modify: `.github/workflows/ci.yml` (`changes`: neuer Filter `msix`; neuer Job `package-msix`)

**Interfaces:**
- Consumes: Aufruf aus Task 2
- Produces: Job `package-msix` (Windows), läuft bei PRs und Pushes, wenn sich `desktopApp/packaging/**`, `tools/fetch-vlc.sh`, `.github/workflows/release.yml` oder `.github/workflows/ci.yml` ändern

- [ ] **Step 1: Job schreiben** – `changes` bekommt Ausgabe `msix` (eigener `paths-filter`-Eintrag, unabhängig von `desktop`); Job `package-msix`: `needs: changes`, `if: needs.changes.outputs.msix == 'true'`, `runs-on: windows-latest`, `timeout-minutes: 30`; Schritte: checkout, Java 17, Gradle, `bash tools/fetch-vlc.sh`, `./gradlew.bat :desktopApp:createDistributable`, dann
  `pwsh -File desktopApp/packaging/build-msix.ps1 -AppDir desktopApp/build/compose/binaries/main/app/IPTVPlayer -IdentityName CI.IPTVPlayer -Publisher "CN=CI" -PublisherDisplayName CI`.
  Dummy-Identität reicht: geprüft wird das Packen, nicht die Store-Zulassung.
- [ ] **Step 2: Push und CI abwarten** – Branch pushen, PR als Entwurf öffnen; `gh pr checks <N> --watch` → `package-msix` pass. Bei Fehlern Log lesen (`gh run view --log-failed`), Task-2-Skript korrigieren, erneut.
- [ ] **Step 3: Gegenprobe** – im Log des Jobs stehen die Selbstcheck-Ausgaben (Version `X.Y.Z.0`, `-Diptvplayer.store=true` in der `.cfg`, VLC gefunden).
- [ ] **Step 4: Commit** `Check MSIX packaging in CI` (Fixes aus Step 2 jeweils mit eigenem Commit)

### Task 4: Release-Lauf baut und hängt das MSIX an

**Files:**
- Modify: `.github/workflows/release.yml` (Job `windows` nach „Portable Version packen“; Upload-Pfade; Job `release`: `take`, Aufräum-`case`; Kopfkommentar)

**Interfaces:**
- Consumes: Aufruf aus Task 2; Repo-Variablen `vars.MSIX_IDENTITY_NAME`, `vars.MSIX_PUBLISHER`, `vars.MSIX_PUBLISHER_DISPLAY_NAME`, optional `vars.MSIX_DISPLAY_NAME`

- [ ] **Step 1: Muster kontrollieren** – `case "IPTVPlayer-2.4.0.msix" in *IPTVPlayer-*.msi) echo match;; *) echo no;; esac` in bash → `no`; in `shared/src/commonMain/kotlin/de/vincentsberger/iptvplayer/shared/Updates.kt` die Auswahl der Windows-Datei prüfen (Endung `.msi` per `endsWith`) → `.msix` wird nicht gewählt. Ergebnis im Commit-Text festhalten.
- [ ] **Step 2: Schritt „MSIX für den Microsoft Store packen“** im Job `windows`:
  - `if: ${{ vars.MSIX_IDENTITY_NAME != '' && vars.MSIX_PUBLISHER != '' && vars.MSIX_PUBLISHER_DISPLAY_NAME != '' }}`
  - Aufruf mit `-AppDir desktopApp/build/compose/binaries/main/app/IPTVPlayer -Version $env:VERSION -OutFile desktopApp/build/compose/binaries/main/msi/IPTVPlayer-$env:VERSION.msix`, `-DisplayName` nur wenn `vars.MSIX_DISPLAY_NAME` gesetzt
  - Zusatzschritt mit umgekehrter Bedingung: `echo "::notice::Kein MSIX – Store-Identität (Repo-Variablen MSIX_*) fehlt"`
  - Upload-Pfad ergänzen: `desktopApp/build/compose/binaries/main/msi/IPTVPlayer-*.msix` (`if-no-files-found: error` bleibt, .msi ist immer da)
- [ ] **Step 3: Job `release`** – `take 'IPTVPlayer-*.msix' 'Microsoft Store (MSIX)' nur-diese-version`; Aufräum-`case` um `*.msix` ergänzen; Kopfkommentar: MSIX nur frisch gebaut, nie übernommen.
- [ ] **Step 4: Prüfen** – `actionlint .github/workflows/release.yml .github/workflows/ci.yml` (falls nicht installiert: `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest`) → keine Fehler; CI des PRs grün.
- [ ] **Step 5: Commit** `Build the MSIX in the release run when the Store identity is set`

### Task 5: Doku

**Files:**
- Modify: `docs/PUBLISHING.md` (Tabelle „Pakete bauen“, Abschnitt „Windows: Microsoft Store“), `DATENSCHUTZ.md` (Abschnitt „Suche nach Updates“)

- [ ] **Step 1:** `PUBLISHING.md`:
  - Tabelle: neuer Aufruf mit Parametern.
  - Schritt 3 „Identität übernehmen“ → die drei Werte (plus optional Anzeigename) als **Repository-Variablen** eintragen (Settings → Secrets and variables → Actions → Variables), nicht ins Manifest.
  - Neuer Schritt: „Das nächste Release mit PC-Änderungen hängt `IPTVPlayer-X.Y.Z.msix` an. Diese Datei im Partner Center hochladen. Sie ist unsigniert und nur für den Store gedacht, Microsoft signiert sie.“
  - Hinweis: Die Store-Fassung speichert ihre Daten getrennt von einer .msi-Installation; wer wechselt, meldet sich neu an.
- [ ] **Step 2:** `DATENSCHUTZ.md`: Satz „Die Version aus dem Microsoft Store fragt nicht bei GitHub nach; Updates kommen über den Store.“
- [ ] **Step 3: Commit** `Document the MSIX release path and the Store build's update behaviour`

### Task 6: Abschluss

- [ ] **Step 1:** `./gradlew :shared:desktopTest :desktopApp:test detekt` → BUILD SUCCESSFUL; PR-Checks inklusive `package-msix` grün.
- [ ] **Step 2:** Spec-Kopf ergänzen: `**Umgesetzt:** …`; Commit `Mark Microsoft Store design as implemented`.
- [ ] **Step 3:** PR aus dem Entwurf in „bereit“ setzen; Merge und Release nur auf Anweisung.
