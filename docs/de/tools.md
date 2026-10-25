# Werkzeuge

*[English](../en/tools.md) · Deutsch* · [← Übersicht](../../README.de.md)

## Kommandozeilen-Werkzeug (Java, ohne Server)

Das Jar enthält ein Werkzeug, das Text ohne Server spricht — in eine `.voice`-Datei oder eine Liste von Klangbefehlen. Es braucht Gson im
Klassenpfad (auf einem Server bringt Bukkit Gson mit):

```bash
java -cp "voiceapi-1.9.0.jar:gson-2.10.1.jar" de.passimoritz.voiceapi.tool.VoiceTool read "Willkommen im Dorf!" --lang de
```

Unter Windows `;` statt `:` zwischen den beiden Jars. Echte Ausgabe:

```text
Willkommen [vɪl.ˈkɔ.mən]
im [ˈɪm]
Dorf [ˈdɔrf] EXCLAMATION
```

| Befehl | Was er tut |
|---|---|
| `speak "Text" [--lang de\|en] [--speaker villager] [--style "rate=1.1; mood=happy"] [--out x.voice] [--cues x.json] [--pack]` | Text sprechen |
| `sing "Text" --melody melodie.json [--lang …] [--speaker …] [--out …] [--cues …] [--pack]` | auf eine Melodie singen |
| `render stueck.voice --cues stueck.json [--pack]` | Klangbefehle einer gespeicherten Datei |
| `read "Text" [--lang …]` | wie der Text gelesen wird (Silben, Betonung) |
| `sounds` | alle Vanilla-Laute, die der Renderer braucht |

Melodiedatei: `[{"start": 0, "dur": 400, "pitch": 60, "volume": 0.8, "word": 0}, …]` (`word` optional; die NoteAPI-Form mit `"tick"` =
50 ms geht auch). Cue-Datei: `{"speaker": "villager", "lengthMs": 1189, "cues": [{"at": 0, "sound": "…", "pitch": 1.0, "volume": 0.5, "stop": 120}, …]}`.

## Python-Werkzeuge

Sie stecken im Jar und landen bei jedem Start in `plugins/VoiceAPI/tools/`. Die meisten brauchen numpy, scipy, librosa und soundfile —
die NoteAPI-Umgebung (`~/.noteapi/venv`) hat sie.

| Werkzeug | Zweck |
|---|---|
| `voicelab.py` | `setup` lädt die Vanilla-Laute; `render cues.json --out x.wav [--pack zip]` spielt Klangbefehle wie der Client; `atlas` vermisst alle Vanilla-Laute |
| `build_voice_pack.py` | baut die Paket-Laute (`--out`) oder mischt sie ins NoteAPI-Paket (`--merge`) |
| `villager_sing.py` | KI-Villager-Gesang für eine Gesangsspur ([Villager-Gesang](villager-singing.md)) |
| `rvc_setup.py` | richtet die Stimmmodell-Umgebung ein (nur CPU), `--check` |
| `rvc_infer.py` | reproduzierbare Umwandlung (fester Zufall) — von `villager_sing.py` aufgerufen |
| `villager_dataset.py` | Trainingsdaten aus den Villager-Lauten (`--rich` für lebendigere Klangfarbe) |
| `villager_voice.py` | ein älterer Vocoder-Ansatz (zum Vergleich behalten) |
| `sing_test.py`, `compare.py`, `find_hums.py` | Liedtests und Messwerte gegen die Villager-Videos |
| `readme_images.py` | die gemessenen Bilder (Spektrogramm, Fortschritt, Schnitte) |
| `doc_diagrams.py` | die SVG-Grafiken dieser Doku (keine Pakete nötig) |
| `doc_samples.py`, `doc_speeches.py` | die Hörbeispiele dieser Doku |
