# VoiceAPI

Sprache und Gesang aus Dorfbewohner-Lauten für **Paper 26.3** (Java 25) – als eigenes Plugin, das andere Plugins (z. B. [NoteAPI](https://github.com/Passimoritz/MC-MusicAPI))
nutzen. Ein Text (Deutsch oder Englisch) wird zu Lauten, Silben, Betonung und Satzmelodie und dann mit den Klängen gesprochen, die jeder Spieler
schon hat: Das „Hmm“ des Dorfbewohners trägt die Vokale, Sand, Kies, ein Knopfklick und ein Lavablubb sind die Konsonanten. Wer das
NoteAPI-Ressourcenpaket mit den VoiceAPI-Lauten geladen hat, hört stattdessen echte Vokale und Konsonanten (Formant-Synthese).

**Keine Befehle.** Die NoteAPI (oder jedes andere Plugin) bindet die VoiceAPI ein und bietet sie an.

- **Sprechen** – `VoiceApi.speak("Willkommen im Dorf!", Language.GERMAN, Speaker.VILLAGER, Style.of(Mood.HAPPY))`: Aussprache nach den Regeln
  der Rechtschreibung (Deutsch) bzw. Wörterbuch + Regeln (Englisch), Zahlen als Wörter, Betonung, Fragen steigen, Punkte fallen, Kommas lassen
  offen, `GROSS` und `*so*` heben hervor, `…` macht eine lange Pause
- **Singen** – `VoiceApi.sing(text, lang, speaker, melodie, style)`: Silben auf Töne (Melismen, Töne je Wort), eine Oktavlage für die ganze
  Melodie, legato. Gesungen wird wie in den bekannten Dorfbewohner-Videos: **nur „Hmm“**, exakt auf den Tönen – dafür braucht es das
  Ressourcenpaket (echtes, sauber geschnittenes und auf den Halbton gezogenes Dorfbewohner-Hmm je Grundton). Mit Vanilla-Klängen trifft der
  Dorfbewohner die Töne nicht (sein Brummen steht nur ~180 ms stabil, das Spiel wählt zufällig eine von drei Aufnahmen), darum gilt
  `sing.require-pack: true`: Spieler ohne Paket hören keinen Gesang, und `VoiceApi.needsPack(utterance)` sagt dem einbindenden Plugin, dass es
  das Paket erzwingen soll
- **9 Stimmen** – Dorfbewohner, Fahrender Händler, Hexe, Allay, Piglin, Zombie-Dorfbewohner, Magier (Wololo), Schreiende Ziege, Katze
- **10 Stimmungen** – neutral, fröhlich, traurig, wütend, ängstlich, ruhig, aufgeregt, flüstern, Roboter, müde; dazu Tempo, Tonlage, Umfang, Kraft
- **Sprach-Uhr** – eigener Thread mit 5-ms-Schritten: Klänge starten und stoppen auf die Millisekunde (der Server-Tick wäre zu grob)
- **`.voice`-Dateien** – lesbar, von Hand änderbar, Bibliothek in `plugins/VoiceAPI/voices/`
- **Werkzeuge** – `java -jar voiceapi.jar speak …` (ohne Server), `tools/voicelab.py` (so rendern, wie es im Spiel klingt), `tools/build_voice_pack.py`
  (Paket-Laute bauen und in das NoteAPI-Paket mischen), `tools/compare.py` und `tools/sing_test.py` (echter Gesang vs. Dorfbewohner)
- **Villager-Stimmmodell (KI, in Arbeit)** – `tools/villager_dataset.py` + `tools/villager_sing.py`: ein eigenes RVC-Stimmmodell, trainiert nur auf
  den Dorfbewohner-Lauten des Spiels, wandelt echten Gesang in die Villager-Stimme – Text, Melodie und Tempo bleiben. Läuft **ohne GPU** (siehe unten)

## Installation

`voiceapi-<Version>.jar` in den `plugins`-Ordner legen und den Server neu starten. Das Plugin, das sie nutzt, trägt `VoiceAPI` unter `softdepend`
(oder `depend`) ein. Die echten Laute liegen dem Jar bei (`plugins/VoiceAPI/VoiceAPI-Sounds.zip`); die **NoteAPI mischt sie von selbst** in ihr
Ressourcenpaket. Mit der NoteAPI singt ein Dorfbewohner die Gesangsmelodie umgewandelter Lieder mit (Frage beim Umwandeln, Mischpult → 🎤 Stimme).
Selbst bauen geht weiter mit `python plugins/VoiceAPI/tools/build_voice_pack.py --merge plugins/NoteAPI/pack/NoteAPI-Sounds.zip`.

## Für Entwickler (Schnittstelle 2)

```java
if (VoiceApi.isAvailable()) {
    Utterance greeting = VoiceApi.speak("Willkommen im Dorf, Reisender!", Language.GERMAN, Speaker.VILLAGER, Style.of(Style.Mood.HAPPY));
    Speech speech = VoiceApi.play(greeting, () -> villager.getLocation(), null, 1.0, () -> log("fertig"));
    speech.onWord((index, word) -> showSubtitle(word));   // für Untertitel
    speech.stop();                                         // abbrechen
}
```

| Was | Wie |
|---|---|
| Text lesen | `VoiceApi.read(text, lang)` → Wörter mit Silben, Betonung, Satzgrenzen |
| sprechen | `VoiceApi.speak(text, lang, speaker, style)` → `Utterance` (ohne Server) |
| singen | `VoiceApi.sing(text, lang, speaker, List<MelodyNote>, style)` |
| abspielen | `VoiceApi.play(utterance, place, listeners, volume, whenDone)` → `Speech`; `say(…)` an einem Ort, `tell(player, …)` nur für einen |
| Klang-Befehle | `VoiceApi.cues(utterance, withPack)` → `Cue` (Klang, Geschwindigkeit, Lautstärke, Start, Stopp) |
| Bibliothek | `VoiceApi.library().save(utterance)` / `get(id)` / `all()` – Dateien `voices/<id>.voice` |
| Paket | `VoiceApi.packAvailable()`, `hearsPack(player)`, `packProbe(predicate)` (statt der NoteAPI-Auskunft), `needsPack(utterance)` (Gesang), `soundPack()` (die beigelegten Laute) |

- **Sprache**: `Language.GERMAN`, `Language.ENGLISH`, `Language.guess(text)`.
- **Stil**: `Style.parse("rate=1.2; pitch=-3; range=1.4; energy=1.1; mood=happy")` oder `Style.of(Mood.SAD).withRate(0.8)`.
- **Stimme**: `Speaker.VILLAGER` … jede hat eine natürliche Tonlage und einen Umfang von ±12 Halbtönen (halbe bis doppelte Geschwindigkeit).
- **Threads**: Aufrufe im Hauptthread. Orte und Zuhörer werden je Server-Tick im Hauptthread abgefragt; Beobachter (`onWord`, `onSegment`) und
  `whenDone` laufen im Hauptthread.
- **Einstellungen** (`config.yml`): `speech.rate`, `speech.volume`, `speech.category` (Standard: Stimme/Sprache), `sing.require-pack`, `pack.use`,
  `pack.file`.

## Wie die Sprache entsteht

1. **Laute** ([`lang`](src/main/java/de/passimoritz/voiceapi/lang)): Deutsch nach den Regeln der Rechtschreibung (Vokallänge aus Dehnungs-h,
   Doppelvokal und Folgekonsonanten; sch, ch als ach-/ich-Laut, st/sp am Anfang, Auslautverhärtung, Murmel-e, vokalisches r, Vorsilben,
   Endungen), Englisch mit einem Wörterbuch der häufigen Wörter und Regeln (stummes e, Digraphe, -tion, -ed, -s …). Silben nach dem
   Prinzip des maximalen Anlauts, Betonung nach Vorsilben und Endungen.
2. **Prosodie** ([`prosody`](src/main/java/de/passimoritz/voiceapi/prosody)): Dauer je Laut (betont länger, unbetont kürzer, Phrasenende
   gedehnt), Melodie (Deklination, Akzent-Gipfel, Satzende fällt/steigt/bleibt offen), Lautstärke, Stimmung; kleine, aus dem Text
   abgeleitete Schwankungen.
3. **Rendern** ([`render`](src/main/java/de/passimoritz/voiceapi/render)): **Vanilla** – das gehaltene „Hmm“ der Stimme wird mit der Tonhöhe
   der Silbe gestartet (um seinen Einsatz vorgezogen) und am Ende des Vokals gestoppt; helle Vokale etwas höher, dunkle tiefer (die Formanten
   wandern mit); offene betonte Vokale beginnen mit dem „Uh!“, Ausrufe mit dem Schrei; Konsonanten aus Block- und Tiergeräuschen, stimmhafte mit
   leisem Stimmton. **Paket** – Sprache: Vokale als Formant-Samples je Grundton (alle 4 Halbtöne, höchstens 2 gestreckt), Konsonanten als
   Samples; Gesang: das echte Dorfbewohner-Hmm (`voiceapi:hum.villager.<grundton>`), nahtlos verlängert und auf den Halbton gezogen.
4. **Abspielen** ([`play`](src/main/java/de/passimoritz/voiceapi/play)): Sprach-Uhr, Stopps, Zuhörer, Paket je Spieler.

Die Zahlen der Stimmen (Einsatz, Länge, Tonlage, Lautheit jedes Klangs) stammen aus einer Vermessung aller Klänge von Minecraft 26.3
(`tools/voicelab.py atlas`).

## Liedtests

`tools/sing_test.py` nimmt ein vom NoteAPI-Umwandler zerlegtes Lied (NCS-Titel), holt den Text mit Whisper Wort für Wort aus der Gesangsspur,
die Melodie aus den erkannten Gesangsnoten, lässt den Dorfbewohner denselben Text auf dieselbe Melodie singen und legt ab: Original,
Dorfbewohner (Vanilla) und Dorfbewohner mit Paket, jeweils allein und mit der Begleitung, plus `vergleich.wav` zum Hintereinanderhören und
`bericht.json` mit Kennzahlen (`tools/compare.py`: Melodie-Kontur und Halbton-Fehler, Rhythmus, Klangfarbe, wie sehr ein Klassifikator Gesang hört).
Maßstab sind drei bekannte „Villager singt“-Videos. Gemessen (Videogesang gegen den Gesang des Originalsongs, beide mit demucs getrennt): Der
Villager singt **in der Lage des Originals** (Mitte 54,0 → 54,1 bzw. 62,6 → 58,7), 0,1–0,2 Halbtöne neben dem Raster, zu 98 % tonal, mit
120–500 Hz um 8–23 dB abgesenkt, Mitten um 700–1400 Hz vorn und über 4 kHz um 9–12 dB gedämpft. Das schaffen weder Hmm-Noten noch ein
klassischer Vocoder (`tools/villager_voice.py`, klingt roboterhaft) – die Videos nutzen ein KI-Stimmmodell, darum der Weg unten.

## Villager-Stimmmodell (KI, nur CPU)

Wie die Videos: ein **RVC-Modell** (Retrieval-based Voice Conversion, Werkzeugkette [Applio](https://github.com/IAHispano/Applio)), das die
Villager-Stimme gelernt hat und jeden Gesang in sie umwandelt. Alles läuft auf der **CPU** – auch auf Linux-Servern ohne Grafikkarte.

1. **Einrichten** (einmal): Python 3.12-Umgebung in `~/.voiceapi/rvc/venv` mit `torch` (CPU-Build) und den Paketen aus Applios
   `requirements-engine.txt` ohne die NVIDIA-Pakete; Applio nach `~/.voiceapi/rvc/Applio`, dann `python core.py prerequisites --no-exe`
   (Basismodelle HiFi-GAN 40k, RMVPE, ContentVec; geladen mit `weights_only=True`).
2. **Daten**: `python tools/villager_dataset.py --out ~/.voiceapi/rvc/dataset/villager` – alle 17 Dorfbewohner-Aufnahmen in 10 Tonlagen
   (−7 … +12 Halbtöne, wie das Spiel sie per Geschwindigkeit spielt) und 3 Tempi: 510 Dateien, 4,2 min.
3. **Trainieren** (in `Applio/`): `core.py preprocess --model-name villager --sample-rate 40000 …`, `core.py extract … --f0-method rmvpe --gpu -`,
   `core.py train --model-name villager --sample-rate 40000 --batch-size 8 --gpu - --total-epoch 200 --save-every-epoch 5`, `core.py index …`.
   Auf einem Ryzen 7 7800X3D dauert eine Runde etwa 4 Minuten (200 Runden ≈ 13 Stunden, einmalig).
4. **Singen lassen**: `python tools/villager_sing.py --cache <NoteAPI-Cache-Ordner> --out <Ordner> --title "Genre - Titel"` – nimmt die
   getrennte Gesangsspur, wandelt sie mit dem Modell (`~/.voiceapi/rvc/models/villager.pth` oder das neueste Trainingsgewicht) und mischt mit
   der Original-Begleitung. Dateien: `<Titel> - 1 Original - mit Begleitung.wav`, `<Titel> - 2 Villager - mit Begleitung.wav`,
   `<Titel> - 2 Villager - nur Gesang.wav`, `<Titel> - Vergleich (erst Original, dann Villager).wav`.

Die Anbindung an Plugin und Ressourcenpaket (fertige Villager-Gesangsspur je Lied als Klang im Paket) folgt, sobald das Modell fertig trainiert ist.

## Bauen

```
mvn verify
```

Die Python-Werkzeuge in `tools/` werden ins Jar gepackt und beim Start nach `plugins/VoiceAPI/tools/` gelegt; sie brauchen die
NoteAPI-Umgebung (`~/.noteapi/venv`: numpy, scipy, librosa, soundfile) oder ein eigenes Python mit diesen Paketen.
