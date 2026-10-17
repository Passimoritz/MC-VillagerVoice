"""Hoertest mit echten Liedern: echter Gesang vs. Dorfbewohner-Gesang (Vanilla) vs. Dorfbewohner-Gesang mit Paket-Lauten.

  python sing_test.py --cache <NoteAPI-Cache-Ordner> --out ordner/ [--from 0 --to 30] [--lang en] [--pack zip] [--whisper small | --lyrics "Text"]

Nimmt die vom NoteAPI-Umwandler getrennten Spuren (stems/vocals.wav, bass, drums, other) und die erkannten Gesangsnoten
(transcription-v2.json, Spur "vocals"). Den Text holt Whisper aus der Gesangsspur – Wort fuer Wort mit Zeiten (--whisper, Standard), oder du
gibst ihn an (--lyrics). Jedes Wort bekommt die Toene, die in seiner Zeit klingen (fehlen welche, die Tonhoehe der Gesangsspur dort), die
VoiceAPI singt den Text darauf (java … sing), voicelab.py rendert, compare.py misst. Ergebnis:
  1-original.wav / 1-original-vocals.wav     Original mit Begleitung / nur Gesang
  2-villager.wav / 2-villager-vocals.wav     Dorfbewohner (Vanilla-Klaenge) mit Begleitung / allein
  3-pack.wav / 3-pack-vocals.wav             dasselbe mit den Paket-Lauten (wenn --pack)
  lyrics.json, melody.json, cues-*.json, bericht.json, vergleich.wav (Gesang hintereinander)
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
SR = 44100


def log(text: str) -> None:
    print(text, flush=True)


def load(path: Path, start: float, end: float) -> np.ndarray:
    import librosa
    y, _ = librosa.load(str(path), sr=SR, mono=True, offset=start, duration=end - start)
    return y.astype(np.float32)


def transcribe(vocals: Path, start: float, end: float, language: str, model: str) -> list[dict]:
    """Whisper: Woerter mit Zeiten (relativ zum Ausschnitt)."""
    from faster_whisper import WhisperModel
    import librosa
    y, sr = librosa.load(str(vocals), sr=16000, mono=True, offset=start, duration=end - start)
    whisper = WhisperModel(model, device="cpu", compute_type="int8")
    # Audio direkt uebergeben (kein Dateizugriff ueber PyAV)
    segments, _ = whisper.transcribe(y.astype(np.float32), language=language, word_timestamps=True, beam_size=5, vad_filter=False)
    words = []
    for segment in segments:
        for w in segment.words or []:
            text = re.sub(r"[^\w'’\-]", "", w.word.strip(), flags=re.UNICODE)
            if text:
                words.append({"text": text, "start": round(w.start, 3), "end": round(w.end, 3), "p": round(w.probability, 2)})
    return words


def notes_in(notes: list, start: float, end: float, min_ms: int = 80) -> list[list]:
    picked = []
    for n in notes:
        s, e, midi, conf = n[0], n[1], int(round(n[2])), n[3]
        if e <= start or s >= end or (e - s) * 1000 < min_ms or conf < 0.3:
            continue
        picked.append([max(s, start) - start, min(e, end) - start, midi, conf])
    picked.sort()
    mono: list[list] = []
    for note in picked:
        if mono and note[0] < mono[-1][1] - 0.02:
            if note[3] > mono[-1][3] and note[0] - mono[-1][0] < 0.08:
                mono[-1] = note
            elif note[3] > mono[-1][3]:
                mono[-1][1] = note[0]
                mono.append(note)
        else:
            mono.append(note)
    return mono


def pitch_of(vocals: np.ndarray, start: float, end: float) -> float | None:
    """Mittlere Tonhoehe der Gesangsspur in einem Fenster (MIDI), wenn dort gesungen wird."""
    import librosa
    seg = vocals[int(start * SR): int(end * SR)]
    if len(seg) < 2048:
        return None
    f0, voiced, _ = librosa.pyin(seg, fmin=70, fmax=900, sr=SR, frame_length=2048)
    vals = f0[~np.isnan(f0)]
    if len(vals) < 3:
        return None
    return float(np.round(69 + 12 * np.log2(np.median(vals) / 440.0)))


def melody_for_words(words: list[dict], notes: list[list], vocals: np.ndarray) -> list[dict]:
    """Jedes Wort bekommt die Toene seiner Zeit; ohne Ton: ein Ton in der Tonhoehe der Gesangsspur."""
    melody = []
    used = set()
    last_pitch = 60.0
    for i, w in enumerate(words):
        a, b = w["start"] - 0.06, w["end"] + 0.04
        own = [(k, n) for k, n in enumerate(notes) if k not in used and n[0] < b and n[1] > a]
        if not own:
            pitch = pitch_of(vocals, w["start"], w["end"]) or last_pitch
            melody.append({"start": int(w["start"] * 1000), "dur": max(80, int((w["end"] - w["start"]) * 1000)), "pitch": pitch, "volume": 0.8, "word": i})
            last_pitch = pitch
            continue
        for k, n in own:
            used.add(k)
            s = max(n[0], a)
            e = min(n[1], b + 0.15)
            melody.append({"start": int(s * 1000), "dur": max(60, int((e - s) * 1000)), "pitch": n[2], "volume": round(0.45 + 0.55 * min(1.0, n[3]), 2), "word": i})
            last_pitch = n[2]
    melody.sort(key=lambda m: m["start"])
    return melody


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cache", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--from", dest="start", type=float, default=0.0)
    parser.add_argument("--to", dest="end", type=float)
    parser.add_argument("--lang", default="en")
    parser.add_argument("--speaker", default="villager")
    parser.add_argument("--pack")
    parser.add_argument("--jar")
    parser.add_argument("--classes")
    parser.add_argument("--style", default="")
    parser.add_argument("--lyrics")
    parser.add_argument("--whisper", default="small")
    parser.add_argument("--fresh", action="store_true", help="Text neu erkennen statt lyrics.json wiederzuverwenden")
    args = parser.parse_args()
    cache = Path(args.cache)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    python = sys.executable
    java = ["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8"] + (["-jar", args.jar] if args.jar else ["-cp", args.classes or "target/classes", "de.passimoritz.voiceapi.tool.VoiceTool"])
    import soundfile as sf
    import librosa
    duration = sf.info(str(cache / "stems" / "vocals.wav")).duration
    end = min(args.end or duration, duration)
    vocals = load(cache / "stems" / "vocals.wav", args.start, end)
    transcription = json.loads((cache / "transcription-v2.json").read_text(encoding="utf-8"))
    notes = notes_in(transcription.get("parts", {}).get("vocals", []), args.start, end)

    # 1. Text und Melodie
    if args.lyrics:
        text = args.lyrics
        melody = [{"start": int(n[0] * 1000), "dur": int((n[1] - n[0]) * 1000), "pitch": n[2], "volume": round(0.6 + 0.4 * min(1.0, n[3]), 2)} for n in notes]
        log(f"Melodie: {len(melody)} Toene, Text vorgegeben")
    else:
        lyrics_file = out / "lyrics.json"
        if lyrics_file.exists() and not args.fresh:
            words = json.loads(lyrics_file.read_text(encoding="utf-8"))
        else:
            words = transcribe(cache / "stems" / "vocals.wav", args.start, end, args.lang, args.whisper)
            lyrics_file.write_text(json.dumps(words, indent=0, ensure_ascii=False), encoding="utf-8")
        text = " ".join(w["text"] for w in words)
        melody = melody_for_words(words, notes, vocals)
        log(f"Whisper: {len(words)} Woerter: {text[:160]}")
        log(f"Melodie: {len(melody)} Toene aus {len(notes)} erkannten Gesangsnoten ({args.start:.1f}-{end:.1f} s)")
    if not text.strip() or not melody:
        raise SystemExit("kein Gesang gefunden")
    (out / "melody.json").write_text(json.dumps(melody, indent=0), encoding="utf-8")

    # 2. singen lassen
    for label, pack in (("villager", False), ("pack", True)):
        if pack and not args.pack:
            continue
        cues = out / f"cues-{label}.json"
        cmd = java + ["sing", text, "--melody", str(out / "melody.json"), "--lang", args.lang, "--speaker", args.speaker, "--cues", str(cues)]
        if args.style:
            cmd += ["--style", args.style]
        if pack:
            cmd.append("--pack")
        subprocess.run(cmd, check=True)
        render = [python, "-I", "-X", "utf8", str(HERE / "voicelab.py"), "render", str(cues), "--out", str(out / f"{label}-raw.wav")]
        if pack:
            render += ["--pack", args.pack]
        subprocess.run(render, check=True)

    # 3. mischen
    backing = sum(load(cache / "stems" / f"{stem}.wav", args.start, end) for stem in ("bass", "drums", "other") if (cache / "stems" / f"{stem}.wav").exists())
    n = len(vocals)
    sf.write(str(out / "1-original-vocals.wav"), vocals, SR)
    sf.write(str(out / "1-original.wav"), np.clip(vocals + backing, -1, 1), SR)
    target_rms = float(np.sqrt(np.mean(vocals ** 2))) + 1e-9
    number = 2
    for label in ("villager", "pack"):
        raw = out / f"{label}-raw.wav"
        if not raw.exists():
            continue
        y, rate = sf.read(str(raw), always_2d=True)
        y = y.mean(axis=1).astype(np.float32)
        if rate != SR:
            y = librosa.resample(y, orig_sr=rate, target_sr=SR)
        y = np.pad(y, (0, max(0, n - len(y))))[:n]
        y *= target_rms / (float(np.sqrt(np.mean(y ** 2))) + 1e-9)
        sf.write(str(out / f"{number}-{label}-vocals.wav"), np.clip(y, -1, 1), SR)
        sf.write(str(out / f"{number}-{label}.wav"), np.clip(y + backing, -1, 1), SR)
        number += 1

    # 4. messen
    renders = [str(out / "2-villager-vocals.wav")] + ([str(out / "3-pack-vocals.wav")] if (out / "3-pack-vocals.wav").exists() else [])
    labels = "villager" + (",pack" if len(renders) > 1 else "")
    subprocess.run([python, "-I", "-X", "utf8", str(HERE / "compare.py"), str(out / "1-original-vocals.wav"), *renders, "--labels", labels,
                    "--out", str(out / "bericht.json"), "--listen", str(out / "vergleich.wav")], check=True)
    log(f"fertig: {out}")


if __name__ == "__main__":
    main()
