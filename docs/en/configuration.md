# Configuration

*English · [Deutsch](../de/configuration.md)* · [← Overview](../../README.md)

All settings live in `plugins/VoiceAPI/config.yml`. They are read on start — restart the server after a change.

## `speech` — speaking

| Key | Default | Meaning |
|---|---|---|
| `speech.rate` | `1.0` | speaking rate: `0.8` slower, `1.3` faster |
| `speech.volume` | `1.0` | base volume of all voices (0–1) |
| `speech.category` | `VOICE` | sound category players control the speech with: `VOICE` (slider "Voice/Speech"), `RECORDS`, `PLAYERS`, `NEUTRAL`, `MASTER`. Looked up by name; missing categories fall back to `VOICE` → `PLAYERS` → `MASTER` |
| `speech.require-pack` | `true` | speak only to players with the resource pack. `false` = players without it hear villager and block sounds (rough, but understandable rhythm and melody) |

## `sing` — singing

| Key | Default | Meaning |
|---|---|---|
| `sing.require-pack` | `true` | sing only to players with the pack. The vanilla villager hum is only steady for ~180 ms and the game picks one of three takes at random, so vanilla singing is off-key. `VoiceApi.needsPack(utterance)` tells the integrating plugin to enforce the pack |

## `pack` — resource pack sounds

| Key | Default | Meaning |
|---|---|---|
| `pack.use` | `true` | use the pack sounds for players who have them |
| `pack.file` | `""` | where the integrating plugin's pack is (empty = `plugins/NoteAPI/pack/NoteAPI-Sounds.zip`); VoiceAPI reads from it whether its sounds are included (re-checked at most every 30 s) |
| `pack.download` | `true` | download the base sounds on the first start (release `voice-pack`, SHA-256 checked). `false` = never download, e.g. for a file placed by hand |
| `pack.download-url` | `""` | another address (mirror). `{sha}` = first 12 characters of the checksum, `{sha256}` = all of it. Empty = the GitHub release |

See [Sound pack](sound-pack.md) for the whole story.

## `rvc` — AI villager singing for songs

Only needed for [villager singing](villager-singing.md) of real songs.

| Key | Default | Meaning |
|---|---|---|
| `rvc.home` | `""` | folder of the voice-model environment (empty = `~/.voiceapi/rvc`: venv, Applio, models) |
| `rvc.model` | `villager` | model name: `<home>/models/<model>.pth` (+ `.index`) |
| `rvc.resume` | `true` | continue conversions a server stop interrupted (next start, in the background) |
| `rvc.timbre-follow.strength` | `0.0` | carry the singer's timbre movement over: `0` off … `1` fully. `0.3` moves like the villager videos — livelier, a little less "villager" |
| `rvc.timbre-follow.max-db` | `6.0` | limit per frequency band (0.5–18 dB) |
| `rvc.timbre-follow.window-ms` | `400` | what counts as movement: changes against the average of this many ms (100–2000) |
| `rvc.timbre-follow.smooth-ms` | `40` | smoothing of the movement (0–200 ms) |

## Full default file

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
