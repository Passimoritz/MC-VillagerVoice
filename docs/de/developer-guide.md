# Entwickler-Handbuch

*[English](../en/developer-guide.md) · Deutsch* · [← Übersicht](../../README.de.md)

Alles läuft über eine Klasse: **`de.passimoritz.voiceapi.VoiceApi`** (Schnittstellenversion `VoiceApi.API_VERSION = 3`).

## Dein Plugin vorbereiten

`plugin.yml`:

```yaml
softdepend: [VoiceAPI]     # oder depend: [VoiceAPI], wenn dein Plugin ohne nicht funktioniert
```

Maven — VoiceAPI liegt in keinem öffentlichen Maven-Repository; einmal bauen (`mvn install`) und als `provided` einbinden:

```xml
<dependency>
  <groupId>de.passimoritz</groupId>
  <artifactId>voiceapi</artifactId>
  <version>1.9.0</version>
  <scope>provided</scope>
</dependency>
```

Ohne Compile-Abhängigkeit geht es auch über Reflection (VoiceAPI selbst spricht so mit der NoteAPI).

## Sprechen

```java
if (VoiceApi.isAvailable()) {
    Utterance gruss = VoiceApi.speak("Willkommen im Dorf, Reisender!", Language.GERMAN, Speaker.VILLAGER,
            Style.of(Style.Mood.HAPPY));
    Speech speech = VoiceApi.play(gruss, () -> villager.getLocation(), null, 1.0, () -> getLogger().info("fertig"));
    speech.onWord((index, wort) -> zeigeUntertitel(wort));   // Untertitel
    // speech.stop();                                         // abbrechen
}
```

Kurzformen:

```java
VoiceApi.say("Hallo!", Language.GERMAN, Speaker.VILLAGER, Style.DEFAULT, () -> npc.getLocation());   // alle in der Nähe hören es
VoiceApi.tell(spieler, "Nur du hörst das.", Language.GERMAN, Speaker.WITCH, Style.parse("mood=whisper"));
```

`speak` braucht keinen Server — es funktioniert in Tests und im [Kommandozeilen-Werkzeug](tools.md). Nur `play`, `say` und `tell`
brauchen einen laufenden Server.

![Vom Text zum Klang](../images/de/speech-pipeline.svg)

## Singen

```java
List<MelodyNote> melodie = List.of(
        new MelodyNote(0, 400, 60),      // Start ms, Länge ms, MIDI-Tonhöhe (60 = C4)
        new MelodyNote(400, 400, 60),
        new MelodyNote(800, 400, 67),
        new MelodyNote(1200, 400, 67),
        new MelodyNote(1600, 400, 69),
        new MelodyNote(2000, 400, 69),
        new MelodyNote(2400, 800, 67));
Utterance lied = VoiceApi.sing("Twinkle, twinkle, little star", Language.ENGLISH, Speaker.VILLAGER, melodie, Style.DEFAULT);
if (VoiceApi.needsPack(lied)) {
    // Gesang hören nur Spieler mit Ressourcenpaket (sing.require-pack) – sorge dafür, dass sie es haben
}
VoiceApi.play(lied, () -> buehne, null, 1.0, null);
```

- Die Silben werden auf die Noten verteilt (mehrere Noten je Silbe = Melisma). `new MelodyNote(...).forWord(i)` bindet eine Note an Wort *i*.
- Die ganze Melodie wird oktavweise in die Lage der Stimme geschoben (der Villager singt C4 als C3).
- Feinschliff mit `SingOptions`:

```java
SingOptions weich = SingOptions.DEFAULT.withLegato(200, 40).withGlide(2, 50).withDynamics(0.2).withVolume(0.7);
SingOptions gleich = SingOptions.parse("legato=200; overlap=40; glide=2; glideMs=50; dynamics=0.2; volume=0.7; minNote=70");
VoiceApi.sing(text, Language.ENGLISH, Speaker.VILLAGER, melodie, Style.DEFAULT, weich);
```

| Option | Bedeutung |
|---|---|
| `volume` | Gesamtlautstärke (0–1) |
| `dynamics` | wie viel lauter betonte Noten sind |
| `legato`, `overlap` | Noten binden (ms) und überlappen lassen (ms) — weich statt abgehackt |
| `glide`, `glideMs` | bei Sprüngen ab *n* Halbtönen gleiten, über *ms* |
| `minNote` | kürzeste Note in ms |
| `SingOptions.DETACHED` | jede Note einzeln angesetzt (der alte Klang) |

![Singen auf Noten](../images/de/singing.svg)

## Stimmen, Stimmungen und Stil

| `Speaker` | Stimme | | `Style.Mood` | Wirkung |
|---|---|---|---|---|
| `VILLAGER` | Dorfbewohner | | `NEUTRAL` | sachlich |
| `TRADER` | fahrender Händler | | `HAPPY` | höher, lebhafter, schneller |
| `WITCH` | Hexe | | `SAD` | tiefer, flacher, langsamer, längere Pausen |
| `ALLAY` | Hilfsgeist | | `ANGRY` | lauter, hart, fallende Akzente |
| `PIGLIN` | Piglin | | `SCARED` | höher, zittrig, schnell |
| `ZOMBIE` | Zombie-Dorfbewohner | | `CALM` | gleichmäßig, langsam, weich |
| `EVOKER` | Magier („wololo“) | | `EXCITED` | schnell, großer Umfang, laut |
| `GOAT` | schreiende Ziege | | `WHISPER` | leise, fast monoton |
| `CAT` | Katze | | `ROBOT` · `TIRED` | monoton, gleich lange Silben · langsam, tief, verklingend |

Jede Stimme hat eine natürliche Tonhöhe und einen Umfang von ±12 Halbtönen (das Spiel spielt Laute mit halber bis doppelter Geschwindigkeit).

```java
Style a = Style.of(Style.Mood.SAD).withRate(0.8).withPitch(-3);
Style b = Style.parse("rate=1.2; pitch=-3; range=1.4; energy=1.1; mood=happy");
```

`rate` = Tempo, `pitch` = Halbtöne hoch/runter, `range` = wie weit sich die Melodie bewegt, `energy` = Lautstärke und Nachdruck.

**Im Text:** `?` steigt, `.` fällt, `,` bleibt offen, `GROSSBUCHSTABEN` und `*Sternchen*` betonen, `…` macht eine lange Pause, Zahlen werden
als Wörter gesprochen.

## Abspielen: Orte, Zuhörer, Beobachter

```java
Speech s = VoiceApi.play(utterance,
        () -> entity.getLocation(),            // wo – jeden Tick gefragt, die Stimme folgt also einem laufenden Wesen
        p -> p.getWorld().equals(welt),        // wer es hört (null = alle in der Nähe)
        1.0,                                   // Lautstärke
        () -> weiter());                       // wenn fertig (Hauptthread)
s.onWord((i, wort) -> ...);                    // jedes Wort
s.onSegment(segment -> ...);                   // jeder Laut
s.elapsedMs(); s.lengthMs(); s.isPlaying(); s.stop();
VoiceApi.stopAll();
```

**Threads:** VoiceAPI vom Hauptthread aufrufen (Folia: globale Region). Orte und Zuhörer werden jeden Tick auf dem Hauptthread gefragt;
Beobachter und `whenDone` laufen auf dem Hauptthread. Die Laute selbst taktet die eigene 5-ms-Uhr von VoiceAPI:

![Sprechuhr](../images/de/speech-clock.svg)

## Ressourcenpaket je Spieler

```java
VoiceApi.packAvailable();                 // enthält das Paket (z. B. das der NoteAPI) die VoiceAPI-Laute?
VoiceApi.hearsPack(spieler);              // hört dieser Spieler sie?
VoiceApi.packProbe(p -> meinPaketPlugin.hatPaket(p));   // selbst entscheiden statt die NoteAPI zu fragen
VoiceApi.soundPack();                     // Optional<Path>: das fertige Lautpaket zum Einmischen in dein Ressourcenpaket
VoiceApi.onSoundPackChanged(() -> paketNeuBauen());   // es hat sich geändert (Laute geladen, Lied umgewandelt) – Hauptthread
```

Baut dein Plugin ein eigenes Ressourcenpaket: `VoiceApi.soundPack()` (Namensraum `assets/voiceapi/…`) einmischen und bei
`onSoundPackChanged` neu bauen. Siehe [Lautpaket](sound-pack.md).

![Was Spieler hören](../images/de/pack-vs-vanilla.svg)

## Klangbefehle ohne Abspielen

```java
List<Cue> cues = VoiceApi.cues(utterance, true);   // mit Paket-Lauten; false = Vanilla
// Cue(atMs, sound, pitch, volume, stopMs, segment)
```

Praktisch, um die Stimme auf einer eigenen Uhr zu spielen (die NoteAPI macht das bei Liedern) oder sie zu exportieren.

## Bibliothek und `.voice`-Dateien

```java
VoiceApi.library().save(utterance);   // plugins/VoiceAPI/voices/<id>.voice
VoiceApi.library().get("willkommen");
VoiceApi.library().all();
```

<a id="voice-dateien"></a>Eine `.voice`-Datei ist reiner Text — von Hand bearbeitbar, um Timing und Melodie fein einzustellen (echte Ausgabe):

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

Jede Lautzeile: Start (ms), Laut (IPA), Länge (ms), Tonhöhe (MIDI; `a>b` gleitet), Lautstärke, `*` = betont. `> Wort` beginnt ein Wort,
`_` ist eine Pause. Gesungene Dateien haben `kind: sung`.

## Villager-Gesang für Lieder (KI)

```java
VoiceApi.songVoiceMissing();   // was zum Umwandeln fehlt (leer = Stimmmodell bereit)
VoiceApi.convertSong("mein-lied", vocalsWav, text -> bossBar.setTitle(text))
        .thenAccept(stimme -> getLogger().info("Villager singt " + stimme.id()));
VoiceApi.songVoice("mein-lied").ifPresent(v -> aufMeinerUhrSpielen(v.cues()));   // v.cuesFrom(ms) nach einem Sprung
VoiceApi.songVoiceBusy(); VoiceApi.cancelSongConversion(); VoiceApi.deleteSongVoice("mein-lied");
```

Details: [Villager-Gesang](villager-singing.md).

## Text lesen

```java
List<Word> woerter = VoiceApi.read("Willkommen im Dorf!", Language.GERMAN);   // Silben, Betonung, Satzenden
Language.guess("Hallo zusammen");                                              // GERMAN
```

## API-Übersicht (Schnittstelle 3)

| Bereich | Methoden |
|---|---|
| Status | `isAvailable()`, `API_VERSION` |
| Text → Sprache | `read`, `speak`, `sing` (mit/ohne `SingOptions`), `cues` |
| Abspielen | `play`, `say`, `tell`, `stopAll`, `needsPack`, `player()` |
| Bibliothek | `library()` → `save`, `get`, `all` |
| Ressourcenpaket | `packAvailable`, `hearsPack`, `packProbe`, `soundPack`, `soundPackFormat`, `pack()`, `onSoundPackChanged` |
| Villager-Lieder | `songVoice`, `songVoices`, `songVoiceMissing`, `songVoiceBusy`, `convertSong`, `cancelSongConversion`, `deleteSongVoice` |

Ältere deutsche Eingaben funktionieren weiter: deutsche Stimmennamen (`dorfbewohner`, `hexe` …), Stimmungen (`traurig`) und Schlüssel
(`stimmung=`, `binden=`).
