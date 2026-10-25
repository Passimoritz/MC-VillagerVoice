# Tools

*English · [Deutsch](../de/tools.md)* · [← Overview](../../README.md)

## Command-line tool (Java, no server)

The jar contains a tool that speaks text without a server — into a `.voice` file or a list of sound commands. It needs Gson on the class
path (on a server Gson comes with Bukkit):

```bash
java -cp "voiceapi-1.9.0.jar:gson-2.10.1.jar" de.passimoritz.voiceapi.tool.VoiceTool read "Willkommen im Dorf!" --lang de
```

On Windows use `;` instead of `:` between the two jars. Real output:

```text
Willkommen [vɪl.ˈkɔ.mən]
im [ˈɪm]
Dorf [ˈdɔrf] EXCLAMATION
```

| Command | What it does |
|---|---|
| `speak "Text" [--lang de\|en] [--speaker villager] [--style "rate=1.1; mood=happy"] [--out x.voice] [--cues x.json] [--pack]` | speak a text |
| `sing "Text" --melody melody.json [--lang …] [--speaker …] [--out …] [--cues …] [--pack]` | sing on a melody |
| `render piece.voice --cues piece.json [--pack]` | sound commands of a saved file |
| `read "Text" [--lang …]` | how the text is read (syllables, stress) |
| `sounds` | all vanilla sounds the renderer needs |

Melody file: `[{"start": 0, "dur": 400, "pitch": 60, "volume": 0.8, "word": 0}, …]` (`word` optional; NoteAPI's form with `"tick"` = 50 ms
also works). Cue file: `{"speaker": "villager", "lengthMs": 1189, "cues": [{"at": 0, "sound": "…", "pitch": 1.0, "volume": 0.5, "stop": 120}, …]}`.

## Python tools

They are packed into the jar and placed in `plugins/VoiceAPI/tools/` on every start. Most need numpy, scipy, librosa and soundfile — the
NoteAPI environment (`~/.noteapi/venv`) has them.

| Tool | Purpose |
|---|---|
| `voicelab.py` | `setup` downloads the vanilla sounds; `render cues.json --out x.wav [--pack zip]` plays sound commands the way the client does; `atlas` measures all vanilla sounds |
| `build_voice_pack.py` | builds the pack sounds (`--out`) or mixes them into NoteAPI's pack (`--merge`) |
| `villager_sing.py` | AI villager singing for a vocal track ([Villager singing](villager-singing.md)) |
| `villager_speak.py` | the villager speaks a spoken recording: noise detected and filtered, speech settings, pauses closed (`VoiceApi.convertSpeech`) |
| `rvc_setup.py` | sets up the voice-model environment (CPU only), `--check` |
| `rvc_infer.py` | reproducible conversion (fixed randomness) — called by `villager_sing.py` |
| `villager_dataset.py` | training data from the villager sounds (`--rich` for a livelier timbre) |
| `villager_voice.py` | an older vocoder approach (kept for comparison) |
| `sing_test.py`, `compare.py`, `find_hums.py` | song tests and metrics against the villager videos |
| `readme_images.py` | the measured pictures (spectrogram, progress, cuts) |
| `doc_diagrams.py` | the SVG diagrams of this documentation (no packages needed) |
| `doc_samples.py`, `doc_speeches.py` | the audio samples of this documentation |
