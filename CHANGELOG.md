# Changelog

## 1.3.2 (19.10.2026)

- **Villager-Gesang folgt dem Vortrag des Originals** (`villager_sing.py`, `follow`): Versatz des Modells (3–10 ms) samplegenau ausgeglichen,
  Pegelverlauf alle 25 ms wie das Original (±18 dB, geglättet; wo das Original schweigt, ist auch der Villager still – kein Atmen/Rauschen),
  scharfe Konsonanten des Originals leise dazu (`--consonants`, Standard 0,5) – Text verständlicher, Einsätze klarer. `--no-follow` = wie bisher.
  Gemessen mit der NoteAPI-Likeness an 6 NCS-Liedern: Harmonie/Rhythmus/Dynamik 85,3 → 91,3 (Rhythmus 71,7 → 80,1, Dynamik 90,4 → 99,9,
  Klangfarbe ≈ 70 → 74).

## 1.3.1 (19.10.2026)

- **KI-Villager-Gesang klingt näher an den Videos**: `villager_sing.py` gleicht das Langzeitspektrum nach der Umwandlung an die gemessene Kurve der
  Villager-Videos an (sanft, höchstens ±9 dB je Band; `--no-colour` schaltet es ab). Gemessen an „Thick of It“ gegen das Video desselben Songs:
  Klang-Abstand 44,6 → 31,5, Tonhöhe weiter 0,10 Halbtöne wie das Video.
- Tonhöhenerkennung der Umwandlung jetzt **FCPE** statt RMVPE: gleich genau, etwa 25 % schneller auf der CPU.
- **Reproduzierbar**: Umwandlungen laufen über `tools/rvc_infer.py` – fester Zufall, das Rauschen des Modells entsteht immer auf der CPU. Gleiche
  Eingabe ergibt auf jedem Rechner dasselbe Ergebnis (zwei CPU-Läufe geprüft: bitgenau gleich), und eine GPU bekäme dieselben Zufallszahlen.
- Training auf der Grafikkarte geprüft und verworfen: Die AMD-ROCm-Pakete blockiert Windows' Smart App Control, DirectML kann die für das
  Training nötige Fourier-Transformation nicht. Training und Umwandlung bleiben CPU.
- Symbol für die Hostwerk-Plugin-Liste (`hostwerk-icon.svg`, `hostwerk-icon:` in der `plugin.yml`).

## 1.3.0 (18.10.2026)

- **Läuft auf jedem Bukkit-Server ab Minecraft 1.17**, heute und künftig: Spigot, Paper, Purpur, Folia – ein Jar. Java 17 als Ziel (vorher 25),
  übersetzt gegen die älteste Spigot-API 1.17.1 (vorher Paper 26.3), `api-version: '1.17'`, `folia-supported: true`.
- Folia: eigener Scheduler (`Scheduling`, per Reflexion, kein Bukkit-Scheduler); Klang-Kategorie nach Namen mit Ausweichen; Gson alt und neu
  (`Compat`); Java-21-Methoden ersetzt.
- Ressourcenpaket: `pack.mcmeta` mit `pack_format`, `supported_formats` und `min_format`/`max_format` – gültig für jedes Format ab 1.17, oben offen.
- **CI ohne Pflege**: wöchentlich und bei jedem Push gegen die älteste und die neueste Paper-/Spigot-API (`ci/latest-api.sh`) und auf echten Servern
  (Paper 1.17.1, 1.20.4, neueste, Folia neueste, Spigot neueste; `ci/smoke.py`). Lokal geprüft: Paper 1.17.1, 1.20.4, 26.3 und Folia 26.3 laden
  und schalten die VoiceAPI fehlerfrei ein.

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
