"""Famous short speeches spoken by the villager voice model (docs/audio/villager-*.mp3), next to the original for comparison.

  python tools/doc_speeches.py

Every recording is public domain (US government works on Wikimedia Commons) and cut to 10–20 s. There is no free recording of a famous
German speech (German broadcast rights), so the German sample is Ernst Reuter's 1948 Berlin speech (text public domain since 2024) read
by the Windows speech synthesis (voice Hedda) and then converted – the villager part is the same voice model as for songs.
Needs: the voice model set up (tools/rvc_setup.py), numpy, scipy, librosa, soundfile, ffmpeg, curl; the German sample needs Windows.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "audio"
AGENT = "VoiceAPI-docs/1.0 (https://github.com/Passimoritz/MC-VillagerVoice)"
COMMONS = "https://upload.wikimedia.org/wikipedia/commons/"

# name, source file on Wikimedia Commons, parts (from s, to s) – the applause in the Moon speech is cut out
SPEECHES = [
    ("moon", "5/50/Jfk_rice_university_we_choose_to_go_to_the_moon.ogg", [(517.5, 522.3), (527.9, 536.3)]),   # J. F. Kennedy, Rice 1962
    ("small-step", "d/dd/Armstrong_Small_Step.ogg", [(13.6, 23.6)]),                                          # Neil Armstrong, Apollo 11
    ("berliner", "b/b1/JFK_Ich_bin_ein_Berliner_2nd_time.ogg", [(0.0, 18.9)]),                               # J. F. Kennedy, Berlin 1963
]
# the speech mode of the plugin (tools/villager_speak.py, VoiceApi.convertSpeech): noise detected and filtered, speech settings,
# pauses closed – without Demucs, exactly as a server converts it
REUTER = ("Ihr Völker der Welt, ihr Völker in Amerika, in England, in Frankreich, in Italien! Schaut auf diese Stadt und erkennt, "
          "dass ihr diese Stadt und dieses Volk nicht preisgeben dürft, nicht preisgeben könnt!")


def run(*cmd: str) -> None:
    subprocess.run(list(cmd), check=True)


def mp3(wav: Path, out: Path, extra: str = "") -> None:
    run("ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-af", (extra + "," if extra else "") + "afade=t=in:d=0.05,areverse,"
        "afade=t=in:d=0.3,areverse,loudnorm=I=-16:TP=-1.5", "-ar", "44100", "-ac", "1", "-b:a", "96k", str(out))


def villager(wav: Path, work: Path, name: str, script: Path) -> Path:
    out = work / f"{name}-villager.wav"
    run(sys.executable, str(script), "--input", str(wav), "--out", str(out), "--separate", "off")
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--villager-speak", default=str(ROOT / "tools" / "villager_speak.py"))
    args = parser.parse_args()
    script = Path(args.villager_speak)
    OUT.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="voiceapi-speeches-"))
    for name, source, parts in SPEECHES:
        full = work / Path(source).name
        run("curl", "-sSfL", "-A", AGENT, "-o", str(full), COMMONS + source)
        trims = "".join(f"[0:a]atrim={a}:{b},asetpts=PTS-STARTPTS[p{i}];" for i, (a, b) in enumerate(parts))
        joined = "".join(f"[p{i}]" for i in range(len(parts))) + f"concat=n={len(parts)}:v=0:a=1[cut]"
        raw = work / f"{name}-raw.wav"
        run("ffmpeg", "-y", "-loglevel", "error", "-i", str(full), "-filter_complex", trims + joined, "-map", "[cut]", "-ac", "1",
            "-ar", "44100", str(raw))
        mp3(raw, OUT / f"original-{name}.mp3")
        mp3(villager(raw, work, name, script), OUT / f"villager-{name}.mp3")
        print(name, "done", flush=True)
    if sys.platform == "win32":
        tts = work / "reuter.wav"
        ps = ("Add-Type -AssemblyName System.Speech; $syn = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
              "$syn.SelectVoice('Microsoft Hedda Desktop'); $syn.Rate = -1; "
              "$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(44100, 16, 1); "
              f"$syn.SetOutputToWaveFile('{tts}', $fmt); $syn.Speak($env:REUTER_TEXT); $syn.Dispose()")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], check=True, env={**__import__("os").environ, "REUTER_TEXT": REUTER})
        mp3(villager(tts, work, "reuter", script), OUT / "villager-reuter.mp3")
        print("reuter done", flush=True)


if __name__ == "__main__":
    main()
