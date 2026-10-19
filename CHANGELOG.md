# Changelog

## 1.4.0 (2026-10-20)

- **English throughout**: code comments, log lines, error and progress messages, config comments, tools, CI and README are English;
  the German README lives on as `README.de.md`.
- Display names in English: voices (`Speaker.label()`: "Villager", "Wandering trader", "Witch" …) and languages (`Language.label()`:
  "German", "English"). Ids, sound names, file formats and the API are unchanged – NoteAPI keeps working without changes.
- Former German input still works: voice names (`dorfbewohner`, `hexe`, `fahrender händler` …), moods (`traurig`), style keys (`tonlage`,
  `stimmung` …) and singing keys (`lautstaerke`, `binden` …).
- Progress of `convertSong`: "Villager is singing", "Writing sections" (before: German). Output files of `villager_sing.py` and
  `sing_test.py`: `… - vocals only.wav`, `… - with backing.wav`, `… - Comparison (…).wav`, `report.json`.
- `.voice` files now also store `kind: sung`, so sung utterances stay sung after saving and loading.
- `ci/smoke.py` accepts the English ready line (and the old German one).

## 1.3.2 (2026-10-19)

- **Villager singing follows the performance of the original** (`villager_sing.py`, `follow`): the model's offset (3–10 ms) corrected
  sample-exactly, level curve every 25 ms like the original (±18 dB, smoothed; where the original is silent, the villager is silent too – no
  breathing/noise), sharp consonants of the original mixed in quietly (`--consonants`, default 0.5) – text easier to understand, clearer onsets.
  `--no-follow` = as before. Measured with NoteAPI's Likeness on 6 NCS songs: harmony/rhythm/dynamics 85.3 → 91.3 (rhythm 71.7 → 80.1,
  dynamics 90.4 → 99.9, timbre ≈ 70 → 74).
- `--protect` stays 0.33: 0.5 tested and rejected (rhythm 79.3 → 73.8, harmony slightly worse).

## 1.3.1 (2026-10-19)

- **AI villager singing sounds closer to the videos**: after the conversion `villager_sing.py` matches the long-term spectrum to the measured
  curve of the villager videos (gently, at most ±9 dB per band; `--no-colour` switches it off). Measured on "Thick of It" against the video
  of the same song: timbre distance 44.6 → 31.5, pitch still 0.10 semitones like the video.
- Pitch detection of the conversion now **FCPE** instead of RMVPE: just as accurate, about 25 % faster on the CPU.
- **Reproducible**: conversions run through `tools/rvc_infer.py` – fixed randomness, the model's noise is always made on the CPU. The same
  input gives the same result on every machine (two CPU runs checked: bit-identical), and a GPU would get the same random numbers.
- Training on the graphics card checked and rejected: Windows' Smart App Control blocks the AMD ROCm packages, DirectML cannot do the
  Fourier transform training needs. Training and conversion stay on the CPU.
- Icon for the Hostwerk plugin list (`hostwerk-icon.svg`, `hostwerk-icon:` in `plugin.yml`).

## 1.3.0 (2026-10-18)

- **Runs on every Bukkit server from Minecraft 1.17**, today and in the future: Spigot, Paper, Purpur, Folia – one jar. Java 17 as the
  target (before 25), compiled against the oldest Spigot API 1.17.1 (before Paper 26.3), `api-version: '1.17'`, `folia-supported: true`.
- Folia: own scheduler (`Scheduling`, via reflection, no Bukkit scheduler); sound category by name with fallbacks; old and new Gson
  (`Compat`); Java 21 methods replaced.
- Resource pack: `pack.mcmeta` with `pack_format`, `supported_formats` and `min_format`/`max_format` – valid for every format from 1.17,
  open at the top.
- **CI without maintenance**: weekly and on every push against the oldest and the newest Paper/Spigot API (`ci/latest-api.sh`) and on real
  servers (Paper 1.17.1, 1.20.4, newest, Folia newest, Spigot newest; `ci/smoke.py`). Checked locally: Paper 1.17.1, 1.20.4, 26.3 and
  Folia 26.3 load and enable VoiceAPI without errors.

## 1.2.0 (2026-10-18)

- **All 9 voices sing with their own hum** (trader, witch, allay, piglin, zombie, evoker, goat, cat instead of always the villager): stable
  spots per voice from `tools/find_hums.py` (`tools/hums.json`), pitch search per register, zombie take `say3` corrected by an octave;
  `VoicePack.HUM_SPEAKERS`.
- **Smoother singing** – `SingOptions` (`VoiceApi.sing(…, options)`, also as text `SingOptions.parse("legato=200; glide=2")`): volume,
  dynamics (volume variation compressed, default 0.35), tying small gaps (140 ms) with overlap (35 ms, legato hum without a new attack), a
  short passing note on jumps from 3 semitones, shortest note. `SingOptions.DETACHED` = the old, attacked sound.
- **No stutter on long notes**: extra-long hum variant (1.7 s), retrigger only after 1.45 s instead of 0.52 s.
- **Equally loud**: hum samples normalised by the loudness of the held part (all voices, takes, roots −20 dB ± 0.1) instead of the peak.
- **Villager singing from the AI voice model** (interface 3): `VoiceApi.songVoice / songVoices / convertSong / cancelSongConversion /
  deleteSongVoice / songVoiceMissing / songVoiceBusy / onSoundPackChanged`. Converted songs live in `plugins/VoiceAPI/songs/<id>/` as
  crossfaded 4 s sections (`voiceapi:song.<id>.<n>`, stream) and go into the sound pack; base pack now `pack/VoiceAPI-Sounds-base.zip`,
  `soundPack()` still points to `VoiceAPI-Sounds.zip`. Settings `rvc.home`, `rvc.model`; setup `tools/rvc_setup.py` (Windows/Linux, CPU only).
- **Villager voice model (AI, CPU only)**: `tools/villager_dataset.py` builds the training data from all villager sounds (10 pitches,
  3 tempi), `tools/villager_sing.py` converts the vocal track of a song with the trained RVC model (Applio) into the villager voice and mixes
  it with the original backing; training and conversion without a GPU.
- `tools/villager_voice.py`: classic vocoder (synthetic hum carrier, target curve from the reference videos) – rejected, sounds robotic.
- Song tests now measure video singing against the original song: the villager sings in the register of the original (not an octave higher).

## 1.1.0 (2026-10-18)

- **Sounds bundled**: the jar contains the finished sound pack (`VoiceAPI-Sounds.zip`, format 2 with the real villager hum) and places it in
  `plugins/VoiceAPI/` on start. NoteAPI mixes it into its resource pack by itself – no tool, no Python needed.
- Interface 2: `VoiceApi.soundPack()`, `soundPackFormat()`.
- Load order: VoiceAPI loads before NoteAPI (NoteAPI has `softdepend: [VoiceAPI]`), so NoteAPI includes the singing right at start.

## 1.0.0 (2026-10-17)

- **Speech from villager sounds**: text (German, English) → sounds, syllables, stress, intonation → sound commands for the game. Vowels
  from the held "Hmm" (started, triggered early, stopped; brightness via the pitch), consonants from block and animal noises.
- **9 voices** (villager, trader, witch, allay, piglin, zombie, evoker, goat, cat), **10 moods**, style (tempo, pitch, range, power),
  emphasis (`CAPS`, `*like this*`), numbers as words.
- **Singing**: syllables on notes (melismas, notes per word), one octave register for the whole melody, legato; it sings only "Hmm" like
  the well-known villager videos. Singing needs the resource pack (`sing.require-pack`, `VoiceApi.needsPack`): pack format 2 contains the
  real villager hum per root (`voiceapi:hum.villager.<root>`), cleanly cut, extended seamlessly, pulled onto the semitone.
- **Resource pack**: `tools/build_voice_pack.py` builds formant sounds (19 vowels × 14 roots, 7 voiced and 21 noise consonants) and mixes
  them into the NoteAPI pack; players with the pack hear real sounds (NoteAPI tells, or `packProbe`).
- **Speech clock** (5 ms), `Speech` with `onWord`/`onSegment`/`stop`, library `voices/*.voice`, tool `java -jar voiceapi.jar`.
- **Lab**: `voicelab.py` (load, measure, render vanilla sounds), `compare.py`, `sing_test.py` (real song vs. villager vs. pack).
- Interface 1: `VoiceApi.speak / sing / play / say / tell / cues / library / packAvailable / hearsPack / packProbe`.
