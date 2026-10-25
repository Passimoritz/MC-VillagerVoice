# Entwicklung

*[English](../en/development.md) · Deutsch* · [← Übersicht](../../README.de.md)

## Bauen und testen

```bash
mvn verify                                                                     # Spigot-API 1.17.1, Ziel Java 17
mvn -P paper-latest -Dbukkit.version=$(bash ci/latest-api.sh paper) verify    # gegen die neueste Paper-API
mvn package -P bundled-pack                                                    # großes Jar mit den Lauten
python ci/smoke.py --project paper --version latest --jar target/voiceapi-*.jar   # echten Server mit dem Jar starten
```

Das Jar landet in `target/voiceapi-<Version>.jar` (≈ 0,2 MB).

## Aufbau des Projekts

| Pfad | Inhalt |
|---|---|
| `src/main/java/de/passimoritz/voiceapi/` | `VoiceApi` (die Schnittstelle), `VoiceApiPlugin`, `Speaker`, `Style`, `Utterance`, `VoiceLibrary` |
| `…/lang` | Phonemisierer (Deutsch, Englisch), Silben, Betonung |
| `…/prosody` | Dauern, Melodie, Lautstärke |
| `…/render` | Vanilla- und Paket-Renderer, `Cue` |
| `…/play` | Sprechuhr, `Speech` |
| `…/sing` | `MelodyNote`, `SingOptions`, `Singer` |
| `…/song` | KI-Villager-Gesang: Umwandlung, Abschnitte, Paket schreiben |
| `…/pack` | Brücke zum Ressourcenpaket, Lautnamen, `SoundDownload` |
| `…/tool` | Kommandozeilen-Werkzeug |
| `src/main/resources/pack/VoiceAPI-Sounds.zip` | die Laute (im Repository, **nicht** im normalen Jar; Release-Asset `voice-pack`) |
| `tools/` | Python-Werkzeuge (im Jar enthalten) |
| `ci/` | Smoke-Test, neueste API-Versionen, Stimmmodell-Test |
| `docs/` | diese Doku; `docs/images` Grafiken, `docs/audio` Hörbeispiele |

## CI

| Workflow | Wann | Was |
|---|---|---|
| *Java CI with Maven* | jeder Push/PR, jeden Montag | Bauen + Tests (Spigot 1.17.1), dasselbe gegen die neueste Paper- und Spigot-API, echte Server: Paper 1.17.1, 1.20.4, neueste, Folia neueste, Spigot neueste (BuildTools). Ein Tag `v<Version>` erzeugt ein Release mit dem Jar |
| *Voice model (CPU, Linux + Windows Server)* | wöchentlich und wenn sich die Werkzeuge ändern | richtet die Stimmmodell-Umgebung auf Runnern ohne GPU neu ein und wandelt eine Melodie wie das Plugin um |

## Release

1. `<revision>` in `pom.xml` setzen, einen Abschnitt in `CHANGELOG.md` ergänzen.
2. Haben sich die Laute geändert: `SoundDownload.SHA256` anpassen und das Asset hochladen ([Lautpaket → Betreuer](sound-pack.md#für-betreuer-neue-laute-veröffentlichen)).
3. Tag `v<Version>` setzen und pushen — die CI baut und veröffentlicht das Release.

## Doku

- Grafiken: `python tools/doc_diagrams.py` (englische und deutsche SVGs, hell und dunkel).
- Hörbeispiele der Live-Sprache: `python tools/doc_samples.py --jar target/voiceapi-<Version>.jar --gson <gson.jar>`.
- Villager-Reden: `python tools/doc_speeches.py`.
- Gemessene Bilder: `python tools/readme_images.py --orig … --video … --ours …`.
