# Developer guide

*English · [Deutsch](../de/developer-guide.md)* · [← Overview](../../README.md)

Everything goes through one class: **`de.passimoritz.voiceapi.VoiceApi`** (interface version `VoiceApi.API_VERSION = 4`).

## Set up your plugin

`plugin.yml`:

```yaml
softdepend: [VoiceAPI]     # or depend: [VoiceAPI] if your plugin cannot work without it
```

Maven — VoiceAPI is not on a public Maven repository; build it once (`mvn install`) and depend on it as `provided`:

```xml
<dependency>
  <groupId>de.passimoritz</groupId>
  <artifactId>voiceapi</artifactId>
  <version>1.9.0</version>
  <scope>provided</scope>
</dependency>
```

Without a compile dependency you can also call it through reflection (VoiceAPI itself talks to NoteAPI that way).

## Speak

```java
if (VoiceApi.isAvailable()) {
    Utterance greeting = VoiceApi.speak("Welcome to the village, traveler!", Language.ENGLISH, Speaker.VILLAGER,
            Style.of(Style.Mood.HAPPY));
    Speech speech = VoiceApi.play(greeting, () -> villager.getLocation(), null, 1.0, () -> getLogger().info("done"));
    speech.onWord((index, word) -> showSubtitle(word));   // subtitles
    // speech.stop();                                      // cancel
}
```

Short forms:

```java
VoiceApi.say("Hello!", Language.ENGLISH, Speaker.VILLAGER, Style.DEFAULT, () -> npc.getLocation());   // everyone near the place hears it
VoiceApi.tell(player, "Only you hear this.", Language.ENGLISH, Speaker.VILLAGER, Style.parse("mood=whisper"));
```

`speak` needs no server — it works in tests and in the [command-line tool](tools.md). Only `play`, `say` and `tell` need a running server.

![From text to sound](../images/speech-pipeline.svg)

## Sing

```java
List<MelodyNote> melody = List.of(
        new MelodyNote(0, 400, 60),      // start ms, length ms, MIDI pitch (60 = C4)
        new MelodyNote(400, 400, 60),
        new MelodyNote(800, 400, 67),
        new MelodyNote(1200, 400, 67),
        new MelodyNote(1600, 400, 69),
        new MelodyNote(2000, 400, 69),
        new MelodyNote(2400, 800, 67));
Utterance song = VoiceApi.sing("Twinkle, twinkle, little star", Language.ENGLISH, Speaker.VILLAGER, melody, Style.DEFAULT);
if (VoiceApi.needsPack(song)) {
    // singing is only played to players with the resource pack (sing.require-pack) – make sure they have it
}
VoiceApi.play(song, () -> stage, null, 1.0, null);
```

- Syllables are spread over the notes (several notes per syllable = melisma). `new MelodyNote(...).forWord(i)` pins a note to word *i*.
- The whole melody is moved by octaves into the voice's register (the villager sings C4 as C3).
- Fine-tune with `SingOptions`:

```java
SingOptions smooth = SingOptions.DEFAULT.withLegato(200, 40).withGlide(2, 50).withDynamics(0.2).withVolume(0.7);
SingOptions same   = SingOptions.parse("legato=200; overlap=40; glide=2; glideMs=50; dynamics=0.2; volume=0.7; minNote=70");
VoiceApi.sing(text, Language.ENGLISH, Speaker.VILLAGER, melody, Style.DEFAULT, smooth);
```

| Option | Meaning |
|---|---|
| `volume` | overall volume (0–1) |
| `dynamics` | how much louder accented notes are |
| `legato`, `overlap` | tie notes (ms) and let them overlap (ms) — smooth instead of choppy |
| `glide`, `glideMs` | glide on jumps of at least *n* semitones, over *ms* |
| `minNote` | shortest note in ms |
| `SingOptions.DETACHED` | every note attacked separately (the old sound) |

![Singing on notes](../images/singing.svg)

## Voices, moods and style

**The voice** is `Speaker.VILLAGER` — the voice of the well-known villager videos, at its natural pitch (≈ 100 Hz) with a range of ±12
semitones (the game plays sounds at half to double speed). Since 1.9.0 it is the only one: the other eight creatures (trader, witch, allay,
piglin, zombie villager, evoker, goat, cat) sounded too far from the videos and made the resource pack four times as big. Their ids and
names (`witch`, `hexe` …) still work and become the villager, so saved `.voice` files and older plugins keep working.

| `Style.Mood` | Effect | | `Style.Mood` | Effect |
|---|---|---|---|---|
| `NEUTRAL` | matter-of-fact | | `CALM` | even, slow, soft |
| `HAPPY` | higher, livelier, faster | | `EXCITED` | fast, wide range, loud |
| `SAD` | lower, flatter, slower, longer pauses | | `WHISPER` | quiet, almost monotone |
| `ANGRY` | louder, hard, falling accents | | `ROBOT` | monotone, equal syllables |
| `SCARED` | higher, shaky, fast | | `TIRED` | slow, low, fading |

```java
Style a = Style.of(Style.Mood.SAD).withRate(0.8).withPitch(-3);
Style b = Style.parse("rate=1.2; pitch=-3; range=1.4; energy=1.1; mood=happy");
```

`rate` = tempo, `pitch` = semitones up/down, `range` = how far the melody moves, `energy` = loudness and emphasis.

**In the text:** `?` rises, `.` falls, `,` stays open, `CAPS` and `*stars*` emphasise, `…` makes a long pause, numbers are spoken as words.

## Play: places, listeners, observers

```java
Speech s = VoiceApi.play(utterance,
        () -> entity.getLocation(),            // where – asked every tick, so the voice follows a moving entity
        p -> p.getWorld().equals(world),       // who hears it (null = everyone near the place)
        1.0,                                   // volume
        () -> next());                         // when done (main thread)
s.onWord((i, word) -> ...);                    // every word
s.onSegment(segment -> ...);                   // every sound
s.elapsedMs(); s.lengthMs(); s.isPlaying(); s.stop();
VoiceApi.stopAll();
```

**Threads:** call VoiceAPI from the main thread (Folia: the global region). Places and listeners are asked on the main thread each tick;
observers and `whenDone` run on the main thread. The sounds themselves are timed by VoiceAPI's own 5 ms clock:

![Speech clock](../images/speech-clock.svg)

## Resource pack per player

```java
VoiceApi.packAvailable();                 // does the pack (e.g. NoteAPI's) contain the VoiceAPI sounds?
VoiceApi.hearsPack(player);               // does this player hear them?
VoiceApi.packProbe(p -> myPackPlugin.hasPack(p));   // decide yourself instead of asking NoteAPI
VoiceApi.soundPack();                     // Optional<Path>: the finished sound pack to merge into your own resource pack
VoiceApi.onSoundPackChanged(() -> rebuildMyPack());   // it changed (sounds downloaded, song converted) – main thread
```

If your plugin builds its own resource pack: merge `VoiceApi.soundPack()` (namespace `assets/voiceapi/…`) into it and rebuild on
`onSoundPackChanged`. See [Sound pack](sound-pack.md).

![What players hear](../images/pack-vs-vanilla.svg)

## Sound commands without playing

```java
List<Cue> cues = VoiceApi.cues(utterance, true);   // with pack sounds; false = vanilla
// Cue(atMs, sound, pitch, volume, stopMs, segment)
```

Useful to play the voice on your own clock (NoteAPI does that for songs) or to export it.

## Library and `.voice` files

```java
VoiceApi.library().save(utterance);   // plugins/VoiceAPI/voices/<id>.voice
VoiceApi.library().get("welcome");
VoiceApi.library().all();
```

<a id="voice-files"></a>A `.voice` file is plain text — edit it by hand to fine-tune timing and melody (real output):

```text
# VoiceAPI utterance
title: Willkommen im Dorf!
lang: de
speaker: villager
style: rate=1.00; pitch=0.0; range=1.00; energy=1.00; mood=neutral
text: Willkommen im Dorf!
---
> Willkommen
0 v 65 43.7>44.5 0.52
65 ɪ 61 44.5 0.74
126 l 60 44.5>44.0 0.59
186 k 85 46.3>47.1 0.74 *
271 ɔ 119 47.1>45.6 0.99 *
…
```

Every sound line: start (ms), sound (IPA), length (ms), pitch (MIDI; `a>b` glides), volume, `*` = stressed. `> word` starts a word, `_` is
a pause. Sung files have `kind: sung`.

## Villager singing for songs (AI)

```java
VoiceApi.songVoiceMissing();   // what is missing to convert (empty = voice model ready)
VoiceApi.convertSong("my-song", vocalsWav, text -> bossBar.setTitle(text))
        .thenAccept(voice -> getLogger().info("villager sings " + voice.id()));
VoiceApi.songVoice("my-song").ifPresent(v -> playOnMyClock(v.cues()));   // v.cuesFrom(ms) after a jump
VoiceApi.songVoiceBusy(); VoiceApi.cancelSongConversion(); VoiceApi.deleteSongVoice("my-song");
```

Details: [Villager singing](villager-singing.md).

## The villager speaks recordings (AI, interface 4)

For spoken recordings — voice lines for an NPC, a speech, an old radio recording — use the speech mode instead of `convertSong`:

```java
VoiceApi.convertSpeech("blacksmith-greeting", Path.of("plugins/MyPlugin/lines/greeting.wav"), text -> getLogger().info(text))
        .thenAccept(voice -> VoiceApi.songVoice(voice.id()).ifPresent(v -> playOnMyClock(v.cues())));
```

Noise is **detected and filtered** first (the quietest moments give the noise profile; clean recordings are left alone), the model converts
with speech settings (the speaker's melody stays, no singing aids) and the pauses are closed. The result is stored and played exactly like a
converted song. Settings: `rvc.speech.*` ([Configuration](configuration.md#rvc--ai-villager-singing-for-songs)).

## Reading text

```java
List<Word> words = VoiceApi.read("Willkommen im Dorf!", Language.GERMAN);   // syllables, stress, sentence ends
Language.guess("Hello there");                                                // ENGLISH
```

## API reference (interface 4)

| Area | Methods |
|---|---|
| status | `isAvailable()`, `API_VERSION` |
| text → speech | `read`, `speak`, `sing` (with/without `SingOptions`), `cues` |
| playing | `play`, `say`, `tell`, `stopAll`, `needsPack`, `player()` |
| library | `library()` → `save`, `get`, `all` |
| resource pack | `packAvailable`, `hearsPack`, `packProbe`, `soundPack`, `soundPackFormat`, `pack()`, `onSoundPackChanged` |
| villager songs and speech | `songVoice`, `songVoices`, `songVoiceMissing`, `songVoiceBusy`, `convertSong`, `convertSpeech` (4), `cancelSongConversion`, `deleteSongVoice` |

Older input keeps working: the old voice names (`witch`, `dorfbewohner`, `hexe` … → villager), German moods (`traurig`) and keys (`stimmung=`, `binden=`).
