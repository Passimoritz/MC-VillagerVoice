# Developer guide

*English · [Deutsch](../de/developer-guide.md)* · [← Overview](../../README.md)

Everything goes through one class: **`de.passimoritz.voiceapi.VoiceApi`** (interface version `VoiceApi.API_VERSION = 3`).

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
VoiceApi.tell(player, "Only you hear this.", Language.ENGLISH, Speaker.WITCH, Style.parse("mood=whisper"));
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

| `Speaker` | Voice | | `Style.Mood` | Effect |
|---|---|---|---|---|
| `VILLAGER` | villager | | `NEUTRAL` | matter-of-fact |
| `TRADER` | wandering trader | | `HAPPY` | higher, livelier, faster |
| `WITCH` | witch | | `SAD` | lower, flatter, slower, longer pauses |
| `ALLAY` | allay | | `ANGRY` | louder, hard, falling accents |
| `PIGLIN` | piglin | | `SCARED` | higher, shaky, fast |
| `ZOMBIE` | zombie villager | | `CALM` | even, slow, soft |
| `EVOKER` | evoker ("wololo") | | `EXCITED` | fast, wide range, loud |
| `GOAT` | screaming goat | | `WHISPER` | quiet, almost monotone |
| `CAT` | cat | | `ROBOT` · `TIRED` | monotone, equal syllables · slow, low, fading |

Each voice has a natural pitch and a range of ±12 semitones (the game plays sounds at half to double speed).

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

## Reading text

```java
List<Word> words = VoiceApi.read("Willkommen im Dorf!", Language.GERMAN);   // syllables, stress, sentence ends
Language.guess("Hello there");                                                // ENGLISH
```

## API reference (interface 3)

| Area | Methods |
|---|---|
| status | `isAvailable()`, `API_VERSION` |
| text → speech | `read`, `speak`, `sing` (with/without `SingOptions`), `cues` |
| playing | `play`, `say`, `tell`, `stopAll`, `needsPack`, `player()` |
| library | `library()` → `save`, `get`, `all` |
| resource pack | `packAvailable`, `hearsPack`, `packProbe`, `soundPack`, `soundPackFormat`, `pack()`, `onSoundPackChanged` |
| villager songs | `songVoice`, `songVoices`, `songVoiceMissing`, `songVoiceBusy`, `convertSong`, `cancelSongConversion`, `deleteSongVoice` |

Older German input keeps working: German voice names (`dorfbewohner`, `hexe` …), moods (`traurig`) and keys (`stimmung=`, `binden=`).
