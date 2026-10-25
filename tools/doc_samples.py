"""Renders the in-game speech samples of the documentation (docs/audio/*.mp3): real VoiceAPI output, played the way the Minecraft client plays it.

  python tools/doc_samples.py --jar target/voiceapi-<version>.jar --gson <gson.jar>

Needs: tools/voicelab.py setup (vanilla sounds in ~/.voiceapi/sounds), numpy + soundfile (the NoteAPI environment), ffmpeg on the PATH.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "audio"
PACK = ROOT / "src" / "main" / "resources" / "pack" / "VoiceAPI-Sounds.zip"

# name, command, text, language, speaker, style, with pack, title
SAMPLES = [
    ("speech-en-pack", "speak", "Welcome to the village, traveler! Would you like to trade some emeralds?", "en", "villager", "mood=happy", True,
     "Speech — English, villager, with the resource pack"),
    ("speech-de-pack", "speak", "Willkommen im Dorf, Reisender! Möchtest du ein paar Smaragde tauschen?", "de", "villager", "mood=happy", True,
     "Sprache — Deutsch, Villager, mit Ressourcenpaket"),
    ("speech-en-vanilla", "speak", "Welcome to the village, traveler! Would you like to trade some emeralds?", "en", "villager", "mood=happy",
     False, "Speech — English, villager, vanilla sounds only (no pack)"),
    ("speech-en-sad", "speak", "I lost all my emeralds... and the iron golem did nothing.", "en", "villager", "mood=sad", True,
     "Speech — English, villager, sad"),
    ("sing-en-pack", "sing", "Twinkle, twinkle, little star, how I wonder what you are", "en", "villager", "", True,
     "Singing — villager hum on the notes, with the resource pack"),
]
# Twinkle, twinkle (public domain): C C G G A A G – F F E E D D C
MELODY = [60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60]


def melody_file(folder: Path) -> Path:
    notes, t = [], 0
    for i, pitch in enumerate(MELODY):
        length = 800 if i in (6, 13) else 400
        notes.append({"start": t, "dur": length, "pitch": pitch, "volume": 0.85})
        t += length
    path = folder / "melody.json"
    path.write_text(json.dumps(notes), encoding="utf-8")
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", required=True)
    parser.add_argument("--gson", required=True, help="Gson jar (the server provides it; the tool alone needs it on the class path)")
    parser.add_argument("--python", default=sys.executable, help="Python with numpy + soundfile for voicelab.py")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="voiceapi-samples-"))
    classpath = os.pathsep.join([args.jar, args.gson])
    for name, command, text, lang, speaker, style, pack, title in SAMPLES:
        cues = work / f"{name}.json"
        cmd = ["java", "-cp", classpath, "de.passimoritz.voiceapi.tool.VoiceTool", command, text, "--lang", lang, "--speaker", speaker,
               "--cues", str(cues)]
        if style:
            cmd += ["--style", style]
        if command == "sing":
            cmd += ["--melody", str(melody_file(work))]
        if pack:
            cmd.append("--pack")
        subprocess.run(cmd, check=True)
        wav = work / f"{name}.wav"
        render = [args.python, str(ROOT / "tools" / "voicelab.py"), "render", str(cues), "--out", str(wav), "--seed", "1"]
        if pack:
            render += ["--pack", str(PACK)]
        subprocess.run(render, check=True)
        # 0.4 s of air around it, loudness evened out
        audio = "apad=pad_dur=0.4,adelay=400:all=1,loudnorm=I=-16:TP=-1.5"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-af", audio, "-ar", "44100", "-b:a", "128k",
                        str(OUT / f"{name}.mp3")], check=True)
        print(name, "->", OUT / f"{name}.mp3", flush=True)


if __name__ == "__main__":
    main()
