# CodeVibrator (MVP)

Desktop-MVP als Java‑Swing App (Java 21, Maven). Fokus: lauffähig, klar getrennte Komponenten, schnell erweiterbar.

## Build / Run

Voraussetzungen: JDK 21, Maven 3.9+

```bash
mvn -q clean package
java -jar target/codevibrator-0.1.0-shaded.jar
```

Projekt‑Root ist **das aktuelle Arbeitsverzeichnis**, in dem die App gestartet wird (`Paths.get("").toAbsolutePath()`).

## Projektstruktur

- `de.ralfrosenkranz.codevibrator.app` – App‑Start, MainFrame
- `de.ralfrosenkranz.codevibrator.ui` – Swing‑UI (Tree, Selektor‑Panel, File‑Tabelle, Dialoge)
- `de.ralfrosenkranz.codevibrator.config` – JSON‑Modelle für `.code.vibrator`
- `de.ralfrosenkranz.codevibrator.persist` – Laden/Speichern Home/Projekt/Verzeichnis‑Configs
- `de.ralfrosenkranz.codevibrator.selectors` – Selektorauflösung, Glob‑Match, Konfliktlogik
- `de.ralfrosenkranz.codevibrator.zip` – Export‑Zip Erstellung
- `de.ralfrosenkranz.codevibrator.importer` – Import/Diff/Checks (human‑driven Zip Auswahl)
- `de.ralfrosenkranz.codevibrator.git` – Git add/commit Wrapper (nur vor „Send to ChatGPT“)
- `de.ralfrosenkranz.codevibrator.logging` – Result‑Logs (Datei + UI)

## `.code.vibrator` JSON (Kurz)

Es gibt drei Ebenen:

1) **Home‑Defaults**: `~/.code.vibrator`
```json
{
  "textFileExtensions": ".java;.xml;...",
  "textFileExactNames": "Makefile;Dockerfile;..."
}
```
Wenn die Datei fehlt oder Listen leer sind, initialisiert die App sinnvolle Default‑Werte und schreibt die Datei.

2) **Projekt‑Root**: `./.code.vibrator`
```json
{
  "activeProfile": "default",
  "promptBase": "…",
  "promptAddOns": ["…"],
  "promptHistory": ["… (MVP: capped)"]
}
```

3) **Verzeichnis‑Konfig**: pro Verzeichnis optional `./path/.code.vibrator`
```json
{
  "profiles": {
    "default": {
      "excludeFromZip": false,
      "readonlyDir": false,
      "selectorsText": "*.java;*.md",
      "readonlyFilePatterns": "*.pem;*.key",
      "selectorStates": {
        "*.java": { "force": true, "active": true }
      }
    }
  }
}
```
Alle Profile teilen sich die gleiche Datei im jeweiligen Verzeichnis (`profiles`‑Map).

### Vererbungs-/Selektorentscheidung (konservativ)
- „selectorsText“ wird als **FORCE‑Liste** interpretiert: alle dort genannten Patterns werden auf dieser Ebene `force=true` und `active=true`.
- Beim Multiplikations‑UI werden Patterns aus Parent‑Ebenen als Zeilen angezeigt. Child kann per FORCE/AKTIV übersteuern; ohne FORCE wird der Parent‑Aktivwert übernommen.
- **Konflikt**: Datei matched sowohl effektive aktive als auch effektive inaktive Patterns ⇒ Warnsymbol; Entscheidung im MVP: **OR‑Logik** (mind. ein aktives Match ⇒ im Zip), wie gefordert.

### Zip‑Root‑Ordner Check (konservativ)
„Zusätzlicher Root‑Ordner im Zip“ wird **fatal**, wenn _alle_ Zip‑Einträge denselben Top‑Level‑Ordner haben **und** dieser Ordner dem Projekt‑Root‑Verzeichnisnamen entspricht (typischer Fall `projectname/...`).

## Human‑in‑the‑loop (wichtig)
Es gibt **keine** Automatisierung der ChatGPT‑Weboberfläche (kein DOM/Scraping). Der Button „Send to ChatGPT“:
1) erzeugt Export‑Zip
2) optional Git add+commit
3) baut Prompt (Basis + AddOns + JSON‑Kontext)
4) kopiert Prompt in Zwischenablage
5) öffnet ChatGPT im Browser

## MVP‑Stand / Nächste Schritte
MVP ist lauffähig und deckt Kernlogik ab (Selektoren/Vererbung, Exclude/Readonly, Export, Import‑Checks+Diff, Logs).
Als nächstes ausbaubar:
- Feingranulare UI‑Editierung für readonlyFilePatterns und Prompt‑AddOns
- Bessere Executable‑Erkennung (POSIX Mode Bits, platform‑aware)
- Performance: Caching der aufgelösten Konfiguration pro Verzeichnis
- Mehr Profile UI (Anlegen/Löschen)
- Import‑Dialog: „neueste Zip im Tagesverzeichnis vorschlagen“ (teilweise schon vorhanden)

## UI-Änderungen (MVP-Iteration)
- FlatLaf als modernes Look&Feel; Auswahl über Menü „Look&Feel“.
- Global-Panel oben: Profil + „Send to ChatGPT“ + „Import Result Zip“.
- ChatGPT kann lokale Zip-Dateien nicht automatisch lesen: die App öffnet das Tagesverzeichnis für manuelles Upload.
