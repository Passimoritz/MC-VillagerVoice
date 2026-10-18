# Changelog

## 1.2.0 (18.10.2026)

- **Alle 9 Stimmen singen mit eigenem Hmm** (Händler, Hexe, Allay, Piglin, Zombie, Magier, Ziege, Katze statt immer Dorfbewohner): stabile Stellen je
  Stimme aus `tools/find_hums.py` (`tools/hums.json`), Tonhöhensuche je Lage, Zombie-Aufnahme `say3` eine Oktave korrigiert; `VoicePack.HUM_SPEAKERS`.
- **Smoother Gesang** – `SingOptions` (`VoiceApi.sing(…, options)`, auch als Text `SingOptions.parse("legato=200; glide=2")`): Lautstärke, Dynamik
  (Lautstärke-Schwankung gestaucht, Standard 0,35), Binden kleiner Lücken (140 ms) mit Überlappung (35 ms, Legato-Hmm ohne Neuanschlag), kurzer
  Zwischenton bei Sprüngen ab 3 Halbtönen, kürzeste Note. `SingOptions.DETACHED` = der alte, angeschlagene Klang.
- **Kein Hakeln bei langen Tönen**: extra-lange Hmm-Variante (1,7 s), Neuanschlag erst nach 1,45 s statt nach 0,52 s.
- **Gleich laut**: Hmm-Samples nach der Lautheit des gehaltenen Teils normiert (alle Stimmen, Aufnahmen, Grundtöne −20 dB ± 0,1) statt nach der Spitze.
- **Villager-Gesang aus dem KI-Stimmmodell** (Schnittstelle 3): `VoiceApi.songVoice / songVoices / convertSong / cancelSongConversion /
  deleteSongVoice / songVoiceMissing / songVoiceBusy / onSoundPackChanged`. Umgewandelte Gesänge liegen in `plugins/VoiceAPI/songs/<id>/` als
  überblendete 4-s-Abschnitte (`voiceapi:song.<id>.<n>`, stream) und kommen ins Lautpaket; Grundpaket jetzt `pack/VoiceAPI-Sounds-base.zip`,
  `soundPack()` zeigt weiter auf `VoiceAPI-Sounds.zip`. Einstellungen `rvc.home`, `rvc.model`; Einrichtung `tools/rvc_setup.py` (Windows/Linux, nur CPU).
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
