# Changelog

## 1.9.0 (2026-10-25)

Reported on a rap song ("Döner von Ibo"): "hardly audible, lots of noise", then "parts can't be heard or understood, sing smoother, stay in
the villager's register" and "several singers in one song must each hit their register and notes". Measured on four songs (60 s "Döner
von Ibo", 45 s each of "Never Gonna Give You Up", BTS "Dynamite", "Despacito" with singer + rapper) against 1.9.0's first state / 1.8.0.

- **The villager sings in its own register – for every singer, with the right notes, within hard limits** (`--register`, default
  `60,178` Hz): the pitch curve is shaped inside the conversion (`shape_f0()`, hooked into Applio by `rvc_infer.py` via `VOICEAPI_F0`).
  - Hard limits measured with clean tones through the model: below 60 Hz the voice gets thin, quiet and noisy (harmonic-to-noise 60 Hz
    +11 dB, 55 Hz +7, 45 Hz no voice at all), above 178 Hz it rattles (subharmonic 178 Hz −38 dB, 180 Hz −16, 182 Hz −8 below the note) –
    that is where 1.8.0 sang most songs (Rick Astley 276 Hz, BTS 330 Hz). Nothing is sung outside; best between 90 and 150 Hz.
  - The octave is chosen for the whole song at once (Viterbi): as few octave changes as possible and those in pauses, notes preferably in
    90–150 Hz – a deep rapper and a high singer both sing in the villager's voice, one singer keeps his octave within a line. Only notes of a
    line wider than the range are folded in by an octave.
  - Pitch tracker `rmvpe` instead of `fcpe` (`--f0-method`): on separated vocals with bleed fcpe disagreed with rmvpe in 10–13 % of the
    frames; jitter is removed by a median (note steps stay steps – the former average blurred them into slides).
  - An octave change inside singing costs `OCTAVE_SWITCH` (400): the small comfort costs never add up to it, so a drifting line is not
    moved in its middle – only notes beyond the hard limits are.
  - Measured per sung note as a listener hears it (rmvpe on singer and villager, stable notes ≥ 80 ms without their first and last 30 ms;
    wrong = more than half a semitone off, octave ignored; octave jump = neighbouring notes in different octaves against the melody; first
    round of 1.9 → now): BTS 120 notes, wrong 9 → **0**, octave jumps 19 → **0**; Rick 191 notes, 8 → **2**, 7 → 9 (his high notes
    358–419 Hz would lie above 178 Hz – folded by the hard limit); Döner 130 notes, 6 → **0**, 5 → **1**; Despacito 125 notes, 6 → **1**,
    0 → 0. Measured per 10 ms frame the share off by more than half a semitone fell 15–21 % → 6–12 %; what remains there are the ~30 ms
    slides into each new note, which a listener does not hear as a wrong note.
- **Smoother**: gaps without pitch inside a phrase up to 150 ms are bridged (`--pitch-fill-ms`) – the model breathed noise there – and
  jitter is removed (`--pitch-smooth-ms 50`, median); optional pull to the semitone (`--tune`). Syllable chopping is off by default
  (`--articulation 0`, was 2: it caused the most drop-outs). Voice breaks per second Döner 2.7 → **1.8**, Rick 2.8 → **2.1**; noise bursts
  Döner 11.7 → **7.2 %**, BTS 6.7 → 5.8 % (Rick 3.1 → 2.8, Despacito 6.5 → 7.5).
- **No more lost passages, easier to follow**: the model gets the vocal track as it is; a cleaned copy (`prepare()`: high-pass, steady
  floor, kick, voice gate) only steers level, gate and consonants in `follow()`, which now closes a real gate (−60 dB) in pauses – the model
  sings at its own volume on near silence (an intro at −96 dB came out at −19 dB). Index rate 0.5 (was 1.0). Sung passages where the
  villager is 15 dB quieter than the singer: Döner 3.4 → **0.2 %**, Rick 1.8 → **0.2 %**, BTS 5.1 → **0.2 %**, Despacito 4.6 → **0 %**.
  How closely the sound changes follow the singer (MFCC deltas – what makes words recognisable): Döner 0.24 → **0.37**, Rick 0.17 → **0.25**,
  BTS 0.20 → **0.31**, Despacito 0.25 → **0.34**. Whisper recognises Despacito 0 → 23–34 % of the words; German rap stays unintelligible
  for it (0–2 %) – the villager voice has its limits there.
- **Louder where the stem is quiet** (`--min-level`, default −18 dBFS for the sung parts, never lowered, then the peak limiter instead of
  hard clipping): "Döner von Ibo" −18.2 → −14.2 LUFS. Optional `--even` (0 … 1) pulls quiet lines towards the loud ones (no measurable gain
  on the four songs, so off by default).
- **Conversion much faster**: the long smoothing windows ran as direct convolutions (the syllable step alone took 308 s for 60 s of
  singing); now by FFT. A 45–60 s song converts in 35–45 s on the CPU.
- Checked and not adopted: less excitation noise, lower `--protect` (worse), index rate 0.25, stronger `--clean`.
- `ci/villager_sing_test.py`: fast checks without the voice model (bleed removal, gate, minimum level, even level, pitch shaping with two
  singers, handover without pause, no note in the rattle range), run by the voice-model workflow. Songs converted before keep their old
  singing; convert them again.

**Sound pack: four times smaller, reproducible, a pack of its own** (concept: `docs/konzept-soundpack-laden.md`):

- **Only the villager voice.** The other eight voices (trader, witch, allay, piglin, zombie villager, evoker, goat, cat) are gone: their
  hums were 80 % of the pack. Their ids and names still work and become the villager (`Speaker.find`), so saved pieces keep playing.
- **Base pack 23.7 MB → 5.9 MB** (2,405 → 725 files): only the villager's hums, every OGG at 22.05 kHz (Vorbis q4, a quarter smaller;
  measured on 61 files −27 %). Not in the jar any more – it is downloaded on the first start (below); with the sounds inside (`-P bundled-pack`) the jar is 3.7 MB. `VoicePack.FORMAT` 3 (NoteAPI rebuilds its packs once).
  `tools/build_voice_pack.py --repack old.zip --out new.zip` rewrites an existing pack like this without synthesis.
- **Reproducible packs**: `VoiceAPI-Sounds.zip` was rewritten with new timestamps on every start, so packs built from it got a new SHA-1
  and every player downloaded again. Now (`PackZip`, also in `build_voice_pack.py`): sorted entries, fixed time, OGG stored – the same
  sounds give the same bytes; the file is only rewritten when its content changes.
- `PackBridge` finds the sounds in NoteAPI's own voice pack (`plugins/NoteAPI/pack/VoiceAPI-Base.zip`) and asks NoteAPI
  `hasVoiceSound(uuid)` (older NoteAPI: `hasRealSound`).

**Small jar, speech mode, documentation:**

- **Jar 23.7 MB → 0.2 MB**: the sound pack (98 % of the jar) is no longer bundled. On the first start VoiceAPI downloads it once in
  the background from the release `voice-pack` (`VoiceAPI-Sounds-81d420b13543.zip`, 5.9 MB), checks the SHA-256 built into the plugin
  (`SoundDownload.SHA256`), writes into `.part` and only then moves the file to `plugins/VoiceAPI/pack/VoiceAPI-Sounds-base.zip`. The
  server start never waits; until the file is there VoiceAPI runs "without resource pack" as before, and when it arrives
  `onSoundPackChanged` tells NoteAPI to mix the sounds in.
- Offline servers: place the file by hand (any VoiceAPI pack of the current format is used, even with another checksum), `pack.download:
  false` never downloads, `pack.download-url` points to a mirror (`{sha}`, `{sha256}`). `mvn package -P bundled-pack` builds the
  jar with the sounds inside (3.7 MB, then nothing is downloaded).
- The sounds stay in the repository (`src/main/resources/pack/VoiceAPI-Sounds.zip`); a test fails if they change without updating the
  checksum.
- **The villager speaks recordings — speech mode with noise detection** (interface 4): `VoiceApi.convertSpeech(id, recording, progress)`
  and `tools/villager_speak.py` convert spoken recordings (voice lines, speeches, old radio recordings) instead of songs. The noise is
  detected first (the quietest 15 % give a noise profile; clean recordings are left alone) and filtered by spectral gating, the voice model
  converts with speech settings (rmvpe, the speaker's melody kept) and the pauses are closed. Stored and played like converted songs;
  interrupted conversions resume in the right mode (job file line 2 `speech`). Settings `rvc.speech.denoise`, `gate`, `separate` (Demucs
  if installed). Measured pauses vs. speech: −24 … −29 dB of villager murmur before → −46 … −74 dB.
- **The repository is public and now called `MC-VillagerVoice`** (old links redirect). The plugin keeps its name VoiceAPI, so
  integrations (NoteAPI) keep working.
- **New documentation** in English (`docs/en/`) and German (`docs/de/`): getting started, configuration, developer guide, how it works,
  sound pack, villager singing, tools, development, troubleshooting. Diagrams as SVG in light and dark (`tools/doc_diagrams.py`, the speech
  and singing diagrams show real plugin output), audio samples in `docs/audio/`: famous public-domain speeches spoken by the villager
  voice model (`tools/doc_speeches.py`: voice separated from the background first, speech settings, gate in the pauses) and live in-game
  speech (`tools/doc_samples.py`).

## 1.8.0 (2026-10-23)

- **Syllable by syllable like the videos** (`--articulation`, default 2): the villager videos set clearly more note ends than the original
  singer ("Thick of It": video 39, original 10, VoiceAPI so far 14) – bound together, the villager sounded tired. The dips between syllables
  are now deepened: note ends 40/39 and 27/28 like the videos, distance to the videos 48.1 → **41.5**, NoteAPI Likeness 87.0 → **88.2**
  (rhythm 67.7 → 71.5).
- **Optional timbre transfer** (off by default; `rvc.timbre-follow.strength`, `max-db`, `window-ms`, `smooth-ms` in `config.yml`, CLI
  `--timbre-follow` …): carries the singer's vowel and brightness changes over to the villager. At 0.3 the timbre moves like in the videos
  (2.80/3.14 vs. video 2.90/3.21; off: 2.48/2.63) – livelier and easier to understand, a little less "villager".
- Checked and not adopted (no measurable gain): finer vocal separation (`htdemucs_ft`: distance unchanged at 4× the time), added room/reverb
  (the videos are not wetter than VoiceAPI), blending the old and new model (48.3–49.5 vs. 48.1), more fine-tuning (epoch 60: 48.5, 80: 50.1).
- README (English + German) with pictures (`docs/images/`, `docs/images/de/`, drawn by `tools/readme_images.py`); NoteAPI's vocals switch
  (Original / Villager / Notes / Off) and mixer volume described.

## 1.7.0 (2026-10-23)

- **Livelier timbre – model "villager2"**: Minecraft only has 17 villager takes, each a single steady sound; a model trained on them keeps its
  timbre too static. `tools/villager_dataset.py --rich` builds phrases from 2–5 different takes at changing pitches (crossfaded) plus vibrato
  and glides; the model was fine-tuned on that (CPU only). Epoch 40 against the villager videos: distance 51.6 → 48.1, timbre movement 2.36/2.52 → 2.50/2.67 (videos
  2.90/3.21), NoteAPI Likeness 87.0 (target ≥ 87). Release `voice-model` carries the new `villager.pth` + `.index`.
- **No more doubled notes at section boundaries** (found by NoteAPI: +6.8 dB for 60 ms at 4 of 55 boundaries): Minecraft starts streamed
  sounds with a slightly varying delay, and a cut inside a sung note then doubles it. The sections are now cut at the quietest moment within
  ±1 s of every 4 s (`voice.json` → `starts`; level at the cuts 8–16 dB below the singing instead of right inside it). Songs converted
  before keep working on the fixed grid; convert them again for the quiet cuts.
- **Interrupted conversions are no longer lost**: a conversion writes into `songs/<id>.part` and swaps it in only when complete (an old
  version stays until then); a job file `songs/<id>.job` remembers the vocal track, and on the next start VoiceAPI continues interrupted
  conversions by itself (`rvc.resume`, default on). Leftovers of older versions (`_vocals.wav`, `_villager.wav`, `.part`/`.old` folders) are
  removed on start.

## 1.6.0 (2026-10-22)

- **Livelier, less robotic villager**: measured on German songs (Schlager: Helene Fischer, Andrea Berg; rap: TJ_beastboy, Apache 207)
  and the two villager videos. The pitch movements (vibrato, slides, bends) were already carried over like in the videos – the tired,
  robotic sound came from **hiss**: the model's output was about 5 dB breathier than the singer and the videos, and up to a third of the sung
  parts fell apart into noise.
  - The model's latent noise is switched off (`rvc_infer.py`: `VOICEAPI_PRIOR_NOISE`, `villager_sing.py --noise`, default 0; RVC samples it
    at 0.67 – that only adds breath).
  - New `clean` step (`--clean`, default 0.6): in sung parts the noisy part of the villager voice is lowered, consonants and breaths stay.
  - Result: sung parts that stay voiced 78 % → 92 % of the original, harmonic-to-noise at the level of the videos ("Thick of It" +3.5 dB vs.
    video +3.0 dB, before −0.3), distance to the videos unchanged (51.0–51.6), NoteAPI Likeness 88.9.
- Mixer volume: NoteAPI controls the villager's level itself (mixer → vocals → source "Villager", its volume slider 0 … 3×).

## 1.5.0 (2026-10-22)

- **Closer to the villager videos**: training finished (200 epochs, CPU only); every 10th–20th epoch measured against the villager videos
  ("Thick of It", "APT.": timbre, long-term spectrum, voicing, pitch) – **epoch 140** is the closest and is the new model (release
  `voice-model`). Conversion defaults `--index-rate 1.0` (more of the villager's own timbre) and `--protect 0.5`. Distance to the videos
  60.2 → 51.4, pitch like the video (0.10 semitones); NoteAPI Likeness on 8 NCS songs 88.7 (target ≥ 87; before 91.3 – the closer to the
  original performance, the less like the video).
- **Mix like the videos**: they keep the original instrumental untouched and the villager about as loud as the original singer (measured);
  `villager_sing.py` now limits peaks smoothly instead of clipping them, `--vocal-gain` adjusts the villager's level.
- **Faster**: register detection with yin (0.1 s instead of 12.5 s per song), consonant separation with smaller kernels.
- **Proven without a GPU on Linux and Windows Server**: new workflow *Voice model (CPU, Linux + Windows Server)* sets the voice model up
  from scratch on GitHub's runners every week and converts a melody like the plugin (`ci/voice_model_test.py`, fails if torch sees a GPU).

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
