# Einstellungen

*[English](../en/configuration.md) · Deutsch* · [← Übersicht](../../README.de.md)

Alle Einstellungen stehen in `plugins/VoiceAPI/config.yml`. Sie werden beim Start gelesen — nach einer Änderung den Server neu starten.

## `speech` — Sprechen

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `speech.rate` | `1.0` | Sprechtempo: `0.8` langsamer, `1.3` schneller |
| `speech.volume` | `1.0` | Grundlautstärke aller Stimmen (0–1) |
| `speech.category` | `VOICE` | Soundkategorie, mit der Spieler die Sprache regeln: `VOICE` (Regler „Stimme/Sprache“), `RECORDS`, `PLAYERS`, `NEUTRAL`, `MASTER`. Nach Namen gesucht; fehlt sie, gilt `VOICE` → `PLAYERS` → `MASTER` |
| `speech.require-pack` | `true` | nur zu Spielern mit Ressourcenpaket sprechen. `false` = Spieler ohne Paket hören Villager- und Blockgeräusche (rau, aber mit verständlichem Rhythmus und Melodie) |

## `sing` — Singen

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `sing.require-pack` | `true` | nur zu Spielern mit Paket singen. Das Vanilla-Summen ist nur ~180 ms stabil und das Spiel wählt zufällig einen von drei Takes, Vanilla-Gesang klingt also schief. `VoiceApi.needsPack(utterance)` sagt dem einbindenden Plugin, dass es das Paket verlangen soll |

## `pack` — Laute des Ressourcenpakets

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `pack.use` | `true` | Paket-Laute für Spieler nutzen, die sie haben |
| `pack.file` | `""` | wo das Paket des einbindenden Plugins liegt (leer = `plugins/NoteAPI/pack/NoteAPI-Sounds.zip`); VoiceAPI liest daraus, ob seine Laute enthalten sind (höchstens alle 30 s neu geprüft) |
| `pack.download` | `true` | die Grundlaute beim ersten Start laden (Release `voice-pack`, SHA-256 geprüft). `false` = nie laden, z. B. für eine von Hand abgelegte Datei |
| `pack.download-url` | `""` | andere Adresse (Spiegel). `{sha}` = erste 12 Zeichen der Prüfsumme, `{sha256}` = alle. Leer = das GitHub-Release |

Die ganze Geschichte steht unter [Lautpaket](sound-pack.md).

## `rvc` — KI-Villager-Gesang für Lieder

Nur für den [Villager-Gesang](villager-singing.md) echter Lieder nötig.

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `rvc.home` | `""` | Ordner der Stimmmodell-Umgebung (leer = `~/.voiceapi/rvc`: venv, Applio, Modelle) |
| `rvc.model` | `villager` | Modellname: `<home>/models/<model>.pth` (+ `.index`) |
| `rvc.resume` | `true` | durch einen Serverstopp unterbrochene Umwandlungen fortsetzen (nächster Start, im Hintergrund) |
| `rvc.timbre-follow.strength` | `0.0` | Klangfarbenbewegung des Sängers übernehmen: `0` aus … `1` ganz. `0.3` bewegt sich wie die Villager-Videos — lebendiger, etwas weniger „Villager“ |
| `rvc.timbre-follow.max-db` | `6.0` | Grenze je Frequenzband (0,5–18 dB) |
| `rvc.timbre-follow.window-ms` | `400` | was als Bewegung zählt: Änderung gegen den Mittelwert so vieler ms (100–2000) |
| `rvc.timbre-follow.smooth-ms` | `40` | Glättung der Bewegung (0–200 ms) |

## Vollständige Standarddatei

```yaml
speech:
  rate: 1.0
  volume: 1.0
  category: VOICE
  require-pack: true

sing:
  require-pack: true

rvc:
  home: ""
  model: villager
  resume: true
  timbre-follow:
    strength: 0.0
    max-db: 6.0
    window-ms: 400
    smooth-ms: 40

pack:
  use: true
  file: ""
  download: true
  download-url: ""
```
