# VoiceAPI

*English · [Deutsch](README.de.md)*

Speech and singing from villager sounds for **every Bukkit server from Minecraft 1.17** – Spigot, Paper, Purpur, Folia, today and in the
future (Java 17+) – as a plugin of its own that other plugins (e.g. [NoteAPI](https://github.com/Passimoritz/MC-MusicAPI)) use. A text
(German or English) becomes sounds, syllables, stress and intonation and is then spoken with sounds every player already has: the villager's
"Hmm" carries the vowels, sand, gravel, a button click and a lava pop are the consonants. Players who loaded the NoteAPI resource pack with
the VoiceAPI sounds hear real vowels and consonants instead (formant synthesis).

**No commands.** NoteAPI (or any other plugin) integrates VoiceAPI and offers it.

- **Speaking** – `VoiceApi.speak("Welcome to the village!", Language.ENGLISH, Speaker.VILLAGER, Style.of(Mood.HAPPY))`: pronunciation by the
  rules of German spelling or dictionary + rules (English), numbers as words, stress, questions rise, full stops fall, commas stay open,
  `CAPS` and `*like this*` emphasise, `…` makes a long pause
- **Singing** – `VoiceApi.sing(text, lang, speaker, melody, style)`: syllables on notes (melismas, notes per word), one octave register for the
  whole melody, legato. It sings like the well-known villager videos: **only "Hmm"**, exactly on the notes – that needs the resource pack
  (the real villager hum per root, cleanly cut and pulled onto the semitone). With vanilla sounds the villager does not hit the notes (its hum
  is only stable for ~180 ms, the game randomly picks one of three takes), so `sing.require-pack: true` applies: players without the pack hear
  no singing, and `VoiceApi.needsPack(utterance)` tells the integrating plugin to enforce the pack
- **9 voices** – villager, wandering trader, witch, allay, piglin, zombie villager, evoker (wololo), screaming goat, cat – each sings with its
  own hum
- **Adjustable smooth singing** – `SingOptions`: volume, dynamics, tying/overlapping, gliding on jumps, shortest note
- **10 moods** – neutral, happy, sad, angry, scared, calm, excited, whisper, robot, tired; plus tempo, pitch, range, power
- **Speech clock** – its own thread with 5 ms steps: sounds start and stop to the millisecond (the server tick would be too coarse)
- **`.voice` files** – readable, editable by hand, library in `plugins/VoiceAPI/voices/`
- **Tools** – `java -jar voiceapi.jar speak …` (without a server), `tools/voicelab.py` (render the way it sounds in the game),
  `tools/build_voice_pack.py` (build the pack sounds and mix them into the NoteAPI pack), `tools/compare.py` and `tools/sing_test.py` (real
  singing vs. villager)
- **Villager voice model (AI)** – `tools/villager_dataset.py` + `tools/villager_sing.py`: an RVC voice model of its own, trained only on the
  game's villager sounds, turns real singing into the villager voice – text, melody and tempo stay. Runs **without a GPU** (see below)

## Compatibility – without maintenance

| | |
|---|---|
| Minecraft | **1.17 to today (26.x) and future versions** |
| Server | Spigot, Paper, Purpur, Folia (and forks) – one and the same jar |
| Java | 17 or newer (Minecraft 1.17–1.20.4 run on 17, newer ones on 21/25 – all run the jar) |
| Resource pack | `pack.mcmeta` is valid for every pack format from 1.17 with an open upper end; the sounds (`sounds.json`, OGG) are unchanged since 1.9 |

How it stays valid without maintenance:
- **Lowest common denominator**: compiled against the *oldest* supported Spigot API (1.17.1) with Java 17 – what compiles here exists on
  every server. Only a small part of the Bukkit API that has been stable for years is used (players, locations, playing/stopping sounds,
  scheduler) plus Gson; no server-internal code (NMS), no version queries, `api-version: '1.17'`.
- **Detected at runtime instead of hard-wired**: Folia (its own scheduler, via reflection), the sound category (by name, with fallbacks),
  the Gson version (`Compat.json`). The same jar runs everywhere.
- **CI checks by itself** – on every push and **every Monday**: compiles and tests against the oldest *and* the newest Paper and Spigot API
  (`ci/latest-api.sh` reads them from the Maven metadata) and starts real servers – Paper 1.17.1, 1.20.4, the newest Paper version, the
  newest Folia and the newest Spigot (BuildTools) – with the jar (`ci/smoke.py`, versions and Java from the PaperMC API). If a new Minecraft
  or server version breaks something, GitHub sends an email – otherwise there is nothing to do.

## Installation

Put `voiceapi-<version>.jar` into the `plugins` folder and restart the server. The plugin that uses it lists `VoiceAPI` under `softdepend`
(or `depend`). The real sounds are bundled with the jar (`plugins/VoiceAPI/VoiceAPI-Sounds.zip`); **NoteAPI mixes them into its resource
pack by itself**. With NoteAPI a villager sings along with the vocal melody of converted songs (question when converting, mixer → 🎤 voice),
and `/music villager <piece>` turns the real vocal track into AI villager singing.
Building it yourself still works with `python plugins/VoiceAPI/tools/build_voice_pack.py --merge plugins/NoteAPI/pack/NoteAPI-Sounds.zip`.

## For developers (interface 3)

```java
if (VoiceApi.isAvailable()) {
    Utterance greeting = VoiceApi.speak("Welcome to the village, traveler!", Language.ENGLISH, Speaker.VILLAGER, Style.of(Style.Mood.HAPPY));
    Speech speech = VoiceApi.play(greeting, () -> villager.getLocation(), null, 1.0, () -> log("done"));
    speech.onWord((index, word) -> showSubtitle(word));   // for subtitles
    speech.stop();                                         // cancel
}
```

| What | How |
|---|---|
| read text | `VoiceApi.read(text, lang)` → words with syllables, stress, sentence boundaries |
| speak | `VoiceApi.speak(text, lang, speaker, style)` → `Utterance` (no server needed) |
| sing | `VoiceApi.sing(text, lang, speaker, List<MelodyNote>, style[, SingOptions])` |
| villager singing (AI) | `VoiceApi.songVoice(id)` → `SongVoice.cues()` / `cuesFrom(ms)`; `convertSong(id, vocalsWav, progress)`, `onSoundPackChanged(…)`, `songVoiceMissing()` |
| play | `VoiceApi.play(utterance, place, listeners, volume, whenDone)` → `Speech`; `say(…)` at a place, `tell(player, …)` for one player only |
| sound commands | `VoiceApi.cues(utterance, withPack)` → `Cue` (sound, speed, volume, start, stop) |
| library | `VoiceApi.library().save(utterance)` / `get(id)` / `all()` – files `voices/<id>.voice` |
| pack | `VoiceApi.packAvailable()`, `hearsPack(player)`, `packProbe(predicate)` (instead of asking NoteAPI), `needsPack(utterance)` (singing), `soundPack()` (the bundled sounds) |

- **Language**: `Language.GERMAN`, `Language.ENGLISH`, `Language.guess(text)`.
- **Style**: `Style.parse("rate=1.2; pitch=-3; range=1.4; energy=1.1; mood=happy")` or `Style.of(Mood.SAD).withRate(0.8)`.
- **Voice**: `Speaker.VILLAGER` … each has a natural pitch and a range of ±12 semitones (half to double speed).
- **Threads**: call from the main thread. Places and listeners are queried per server tick on the main thread; observers (`onWord`,
  `onSegment`) and `whenDone` run on the main thread.
- **Tune the singing**: `SingOptions.DEFAULT.withLegato(200, 40).withGlide(2, 50).withDynamics(0.2).withVolume(0.7)` or
  `SingOptions.parse("legato=200; overlap=40; glide=2; glideMs=50; dynamics=0.2; volume=0.7; minNote=70")`; `SingOptions.DETACHED` = every
  note attacked (the old sound).
- **Settings** (`config.yml`): `speech.rate`, `speech.volume`, `speech.category` (default: Voice/Speech), `sing.require-pack`, `pack.use`,
  `pack.file`, `rvc.home`, `rvc.model`.
- **Former German input** still works: German voice names (`dorfbewohner`, `hexe` …), moods (`traurig`) and keys (`stimmung=`, `binden=`).

## How the speech is made

1. **Sounds** ([`lang`](src/main/java/de/passimoritz/voiceapi/lang)): German by the rules of spelling (vowel length from the lengthening h,
   double vowels and following consonants; sch, ch as "ach"/"ich" sound, st/sp at the start, final devoicing, schwa, vocalic r, prefixes,
   endings), English with a dictionary of frequent words plus rules (silent e, digraphs, -tion, -ed, -s …). Syllables by the maximal onset
   principle, stress from prefixes and endings.
2. **Prosody** ([`prosody`](src/main/java/de/passimoritz/voiceapi/prosody)): duration per sound (stressed longer, unstressed shorter, phrase
   ends stretched), melody (declination, accent peaks, the end of a sentence falls/rises/stays open), volume, mood; small variations derived
   from the text.
3. **Rendering** ([`render`](src/main/java/de/passimoritz/voiceapi/render)): **Vanilla** – the voice's held "Hmm" is started at the pitch of
   the syllable (earlier by its onset) and stopped at the end of the vowel; bright vowels a little higher, dark ones lower (the formants move
   along); open stressed vowels start with the "Uh!", exclamations with the scream; consonants from block and animal noises, voiced ones with
   a quiet voicing tone. **Pack** – speech: vowels as formant samples per root (every 4 semitones, stretched by at most 2), consonants as
   samples; singing: the real hum of each voice (`voiceapi:hum.<voice>.<root>.<length>`), extended seamlessly and pulled onto the semitone.
4. **Playback** ([`play`](src/main/java/de/passimoritz/voiceapi/play)): speech clock, stops, listeners, pack per player.

The voice numbers (onset, length, pitch, loudness of every sound) come from measuring all sounds of Minecraft 26.3 (`tools/voicelab.py atlas`).

## Song tests

`tools/sing_test.py` takes a song split up by the NoteAPI converter (NCS titles), gets the text word by word from the vocal track with
Whisper and the melody from the detected vocal notes, lets the villager sing the same text on the same melody and stores: original,
villager (vanilla) and villager with the pack, each alone and with the backing, plus a comparison file to listen to one after the other and
`report.json` with metrics (`tools/compare.py`: melody contour and semitone error, rhythm, timbre, how much a classifier hears singing).
The yardstick are three well-known "villager sings" videos. Measured (video singing against the singing of the original song, both separated
with demucs): the villager sings **in the register of the original** (middle 54.0 → 54.1 and 62.6 → 58.7), 0.1–0.2 semitones off the grid,
98 % tonal, 120–500 Hz lowered by 8–23 dB, mids around 700–1400 Hz forward and above 4 kHz damped by 9–12 dB. Neither hum notes nor a
classic vocoder (`tools/villager_voice.py`, sounds robotic) manage that – the videos use an AI voice model, hence the approach below.

## Villager voice model (AI, CPU only)

Like the videos: an **RVC model** (retrieval-based voice conversion, toolchain [Applio](https://github.com/IAHispano/Applio)) that has learned
the villager voice and turns any singing into it. Everything runs on the **CPU** – also on Linux servers without a graphics card.

1. **Set up** (once): `python tools/rvc_setup.py --model villager.pth` – a Python 3.12 environment in `~/.voiceapi/rvc/venv` with `torch`
   (CPU build) and the packages from Applio's `requirements-engine.txt` without the NVIDIA packages; Applio at a fixed commit in
   `~/.voiceapi/rvc/Applio`, the base models (HiFi-GAN 40k, RMVPE, ContentVec; loaded with `weights_only=True`); `--check` only checks.
2. **Data**: `python tools/villager_dataset.py --out ~/.voiceapi/rvc/dataset/villager` – all 17 villager takes at 10 pitches
   (−7 … +12 semitones, the way the game plays them via speed) and 3 tempi: 510 files, 4.2 min.
3. **Train** (in `Applio/`): `core.py preprocess --model-name villager --sample-rate 40000 …`, `core.py extract … --f0-method rmvpe --gpu -`,
   `core.py train --model-name villager --sample-rate 40000 --batch-size 8 --gpu - --total-epoch 200 --save-every-epoch 5`, `core.py index …`.
   On a Ryzen 7 7800X3D one epoch takes about 4 minutes (200 epochs ≈ 13 hours, once). Copy the result to
   `~/.voiceapi/rvc/models/villager.pth` (+ `.index`).
4. **Let it sing**: `python tools/villager_sing.py --cache <NoteAPI cache folder> --out <folder> --title "Genre - Title"` – takes the
   separated vocal track, converts it with the model (`~/.voiceapi/rvc/models/villager.pth` or the newest training weights) and mixes it with
   the original backing. Files: `<Title> - 1 Original - with backing.wav`, `<Title> - 2 Villager - with backing.wav`,
   `<Title> - 2 Villager - vocals only.wav`, `<Title> - Comparison (original first, then Villager).wav`.
   After the conversion the singing follows the performance of the original (`follow`): offset corrected sample-exactly, level every 25 ms
   like the original (silence stays silent), the original's sharp consonants mixed in quietly (`--consonants 0.5`, `0` = off; `--no-follow`
   switches it all off). NoteAPI Likeness on 8 NCS songs: harmony/rhythm/dynamics 91.3 (before 85.3 on 6 songs; dynamics 90 → 100,
   rhythm 72 → 80).
5. **Reproducible**: conversions run through `tools/rvc_infer.py` – fixed randomness, the model's noise is always made on the CPU. The same
   input gives the same result on every machine (two CPU runs checked: bit-identical), and a GPU would get the same random numbers.

**In the plugin** (interface 3): `VoiceApi.convertSong(id, vocalsWav, progress)` calls `villager_sing.py --chunks` with the environment from
`rvc.home`, stores the singing as 4 s sections with 60 ms crossfades in `plugins/VoiceAPI/songs/<id>/` and rewrites the sound pack;
`onSoundPackChanged` tells the integrating plugin (NoteAPI remixes and sends). Playback goes through `VoiceApi.songVoice(id).cues()` on the
plugin's music clock. On a server without a GPU: `python plugins/VoiceAPI/tools/rvc_setup.py --model villager.pth` (Linux and Windows).

## Build

```
mvn verify                                                          # against Spigot API 1.17.1 (Java 17)
mvn -P paper-latest -Dbukkit.version=$(bash ci/latest-api.sh paper) verify    # cross-check against the newest Paper API
python ci/smoke.py --project paper --version latest --jar target/voiceapi-*.jar   # real server
```

The Python tools in `tools/` are packed into the jar and placed in `plugins/VoiceAPI/tools/` on start; they need the NoteAPI environment
(`~/.noteapi/venv`: numpy, scipy, librosa, soundfile) or a Python of your own with these packages.

## Icon in Hostwerk

The jar contains `hostwerk-icon.svg` (in `src/main/resources/`, registered via `hostwerk-icon:` in `plugin.yml`). Hostwerk shows the icon in
its plugin list – see the Hostwerk README.
