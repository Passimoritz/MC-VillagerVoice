# Changelog

## Unveröffentlicht

- **Villager-Stimmmodell (KI, nur CPU)**: `tools/villager_dataset.py` baut die Trainingsdaten aus allen Dorfbewohner-Lauten (10 Tonlagen, 3 Tempi),
  `tools/villager_sing.py` wandelt die Gesangsspur eines Liedes mit dem trainierten RVC-Modell (Applio) in die Villager-Stimme und mischt sie mit
  der Original-Begleitung; Training und Umwandlung ohne GPU.
- `tools/villager_voice.py`: klassischer Vocoder (synthetischer Hmm-Träger, Zielkurve aus den Referenzvideos) – verworfen, klingt roboterhaft.
- Liedtests vermessen jetzt Videogesang gegen den Originalsong: der Villager singt in der Lage des Originals (nicht eine Oktave höher).

## 1.1.0 (18.10.2026)

- **Laute beigelegt**: Das Jar enthält das fertige Lautpaket (`VoiceAPI-Sounds.zip`, Format 2 mit echtem Dorfbewohner-Hmm) und legt es beim Start
  nach `plugins/VoiceAPI/`. Die NoteAPI mischt es von selbst in ihr Ressourcenpaket – kein Werkzeug, kein Python nötig.
- Schnittstelle 2: `VoiceApi.soundPack()`, `soundPackFormat()`.
- Ladereihenfolge: VoiceAPI lädt vor der NoteAPI (die NoteAPI hat `softdepend: [VoiceAPI]`), damit sie den Gesang gleich beim Start einbindet.

## 1.0.0 (17.10.2026)

- **Sprache aus Dorfbewohner-Lauten**: Text (Deutsch, Englisch) → Laute, Silben, Betonung, Satzmelodie → Klang-Befehle fürs Spiel. Vokale
  aus dem gehaltenen „Hmm“ (gestartet, vorgezogen, gestoppt; Helligkeit über die Tonhöhe), Konsonanten aus Block- und Tiergeräuschen.
- **9 Stimmen** (Dorfbewohner, Händler, Hexe, Allay, Piglin, Zombie, Magier, Ziege, Katze), **10 Stimmungen**, Stil (Tempo, Tonlage, Umfang,
  Kraft), Hervorhebung (`GROSS`, `*so*`), Zahlen als Wörter.
- **Singen**: Silben auf Töne (Melismen, Töne je Wort), eine Oktavlage für die ganze Melodie, legato; gesungen wird nur „Hmm“ wie in den
  bekannten Dorfbewohner-Videos. Gesang braucht das Ressourcenpaket (`sing.require-pack`, `VoiceApi.needsPack`): Paket-Format 2 enthält das echte
  Dorfbewohner-Hmm je Grundton (`voiceapi:hum.villager.<grundton>`), sauber geschnitten, nahtlos verlängert, auf den Halbton gezogen.
- **Ressourcenpaket**: `tools/build_voice_pack.py` baut Formant-Laute (19 Vokale × 14 Grundtöne, 7 klingende und 21 Geräusch-Konsonanten) und
  mischt sie in das NoteAPI-Paket; Spieler mit Paket hören echte Laute (Auskunft über die NoteAPI oder `packProbe`).
- **Sprach-Uhr** (5 ms), `Speech` mit `onWord`/`onSegment`/`stop`, Bibliothek `voices/*.voice`, Werkzeug `java -jar voiceapi.jar`.
- **Labor**: `voicelab.py` (Vanilla-Klänge laden, vermessen, rendern), `compare.py`, `sing_test.py` (echter Song vs. Dorfbewohner vs. Paket).
- Schnittstelle 1: `VoiceApi.speak / sing / play / say / tell / cues / library / packAvailable / hearsPack / packProbe`.
