# Getting started

*English · [Deutsch](../de/getting-started.md)* · [← Overview](../../README.md)

VoiceAPI is a **library plugin**: it has no commands of its own. Another plugin (for example
[NoteAPI](https://github.com/Passimoritz/MC-MusicAPI), a minigame or an NPC plugin) calls it to speak or sing. This page gets it running
on a server.

![How VoiceAPI fits into a server](../images/architecture.svg)

## Requirements

| | |
|---|---|
| Server | Spigot, Paper, Purpur, Folia or a fork — **one and the same jar** |
| Minecraft | 1.17 or newer (tested up to 26.3, future versions are checked weekly by CI) |
| Java | 17 or newer |
| Internet | once, on the first start (6 MB of sounds). Not needed afterwards — see [offline servers](#offline-servers) |

## Install

1. Download `voiceapi-<version>.jar` from the [releases](https://github.com/Passimoritz/MC-VillagerVoice/releases) and put it into
   `plugins/`.
2. Start (or restart) the server. The log shows:

   ```text
   [VoiceAPI] Enabling VoiceAPI v1.9.0
   [VoiceAPI] Downloading the VoiceAPI sounds in the background from https://github.com/Passimoritz/MC-VillagerVoice/releases/download/voice-pack/VoiceAPI-Sounds-81d420b13543.zip
   [VoiceAPI] VoiceAPI 1.9.0 ready: 1 voice, 0 saved utterances, 0 villager songs, resource pack without VoiceAPI sounds, …
   Done (10.986s)! For help, type "help"
   [VoiceAPI] Downloaded the VoiceAPI sounds (5917 KB) from https://github.com/…
   ```

   The server start never waits for the download. Until the sounds are there, VoiceAPI already works with vanilla sounds.
3. Install the plugin that uses VoiceAPI (e.g. NoteAPI). It lists `VoiceAPI` under `softdepend` or `depend` in its `plugin.yml`.

That is all. With NoteAPI the sounds land in its resource pack by themselves; players who accept the pack hear real vowels and
consonants, everyone else hears villager and block sounds.

![Where the sounds come from](../images/sound-download.svg)

## What lands in `plugins/VoiceAPI/`

| Path | What it is |
|---|---|
| `config.yml` | settings — see [Configuration](configuration.md) |
| `pack/VoiceAPI-Sounds-base.zip` | the downloaded base sounds (checked against a SHA-256 built into the plugin) |
| `VoiceAPI-Sounds.zip` | the finished sound pack: base sounds + converted villager songs; NoteAPI mixes it into its resource pack |
| `voices/*.voice` | saved utterances (readable text, see [Developer guide](developer-guide.md#voice-files)) |
| `songs/<id>/` | villager singing of converted songs (sections as OGG + `voice.json`) |
| `tools/` | the Python tools, unpacked from the jar on every start (always the jar's version) |

## Listen first

**The villager voice model** (AI, the same one that sings songs) speaking famous speeches — 10–20 s each:

| | |
|---|---|
| 🚀 Kennedy — *"We choose to go to the Moon"* (1962) | [▶ villager](../audio/villager-moon.mp3) · [original](../audio/original-moon.mp3) |
| 🌕 Armstrong — *"That's one small step for man"* (1969) | [▶ villager](../audio/villager-small-step.mp3) · [original](../audio/original-small-step.mp3) |
| 🇩🇪 Kennedy — *"Ich bin ein Berliner"* (1963) | [▶ villager](../audio/villager-berliner.mp3) · [original](../audio/original-berliner.mp3) |
| 🇩🇪 Reuter — *„Schaut auf diese Stadt“* (1948, text read by speech synthesis) | [▶ villager](../audio/villager-reuter.mp3) |

**Live in-game speech** — what `VoiceApi.speak` plays on the server, rendered the way the Minecraft client plays the sound commands:

| | |
|---|---|
| 🗣️ English, villager, with the pack | [▶ listen](../audio/speech-en-pack.mp3) |
| 🗣️ German, villager, with the pack | [▶ listen](../audio/speech-de-pack.mp3) |
| 🗣️ English, vanilla sounds only (no pack) | [▶ listen](../audio/speech-en-vanilla.mp3) |
| 😢 Villager, sad | [▶ listen](../audio/speech-en-sad.mp3) |
| 🎵 Singing on notes (hum) | [▶ listen](../audio/sing-en-pack.mp3) |

## Offline servers

A server without internet access runs without the resource pack sounds until the file is there. Three ways to provide it:

1. **By hand:** download `VoiceAPI-Sounds-<sha>.zip` from the release
   [`voice-pack`](https://github.com/Passimoritz/MC-VillagerVoice/releases/tag/voice-pack), save it as
   `plugins/VoiceAPI/pack/VoiceAPI-Sounds-base.zip` and restart. Set `pack.download: false` so VoiceAPI never tries.
2. **Mirror:** host the file yourself and set `pack.download-url` (`{sha}` = first 12 characters of the checksum, `{sha256}` = all of it).
3. **Jar with the sounds inside:** `mvn package -P bundled-pack` (≈ 3.7 MB, never downloads).

## Updating

Replace the jar and restart. If the new version needs other sounds, the plugin knows their checksum and downloads them once; otherwise
nothing is downloaded. Saved utterances and converted songs stay.

## Next steps

- [Configuration](configuration.md) — every setting explained
- [Developer guide](developer-guide.md) — let your plugin speak and sing
- [Villager singing](villager-singing.md) — AI villager voice for real songs (CPU only)
- [Troubleshooting](troubleshooting.md)
