"""Listening test with real songs: real singing vs. villager singing (vanilla) vs. villager singing with the pack sounds.

  python sing_test.py --cache <NoteAPI cache folder> --out folder/ [--from 0 --to 30] [--lang en] [--pack zip] [--whisper small | --lyrics "Text"]

Takes the tracks separated by the NoteAPI converter (stems/vocals.wav, bass, drums, other) and the detected vocal notes
(transcription-v2.json, part "vocals"). Whisper gets the text from the vocal track – word by word with times (--whisper, default), or you
give it (--lyrics). Every word gets the notes that sound during its time (if some are missing, the pitch of the vocal track there),
VoiceAPI sings the text on them (java … sing), voicelab.py renders, compare.py measures. Output:
  <Title> - 1 Original - vocals only / with backing.wav
  <Title> - 2 Villager Vanilla - vocals only / with backing.wav     villager (vanilla sounds)
  <Title> - 3 Villager Pack - vocals only / with backing.wav        the same with the pack sounds (with --pack)
  lyrics.json, melody.json, cues-*.json, report.json, <Title> - Comparison one after the other (…).wav
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
    """Whisper: words with times (relative to the excerpt)."""
    from faster_whisper import WhisperModel
    import librosa
    y, sr = librosa.load(str(vocals), sr=16000, mono=True, offset=start, duration=end - start)
    whisper = WhisperModel(model, device="cpu", compute_type="int8")
    # pass the audio directly (no file access through PyAV)
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
    """Median pitch of the vocal track in a window (MIDI), if something is sung there."""
    import librosa
    seg = vocals[int(start * SR): int(end * SR)]
    if len(seg) < 2048:
        return None
    f0, voiced, _ = librosa.pyin(seg, fmin=70, fmax=900, sr=SR, frame_length=2048)
    vals = f0[~np.isnan(f0)]
    if len(vals) < 3:
        return None
    return float(np.round(69 + 12 * np.log2(np.median(vals) / 440.0)))


def syllables_of(word: str, language: str) -> int:
    """Rough syllable count of a word (vowel groups)."""
    vowels = "aeiouyäöü"
    w = word.lower()
    count = 0
    prev = False
    for c in w:
        is_v = c in vowels
        if is_v and not prev:
            count += 1
        prev = is_v
    if language == "en" and w.endswith("e") and count > 1 and not w.endswith(("le", "ee", "ye")):
        count -= 1
    return max(1, count)


def melody_from_pitch(vocals: np.ndarray, words: list[dict] | None) -> list[dict]:
    """The melody straight from the vocal track: the pitch curve is split into notes (new note when the pitch stays away by more than
    0.8 semitones for longer than 60 ms, at a voice pause or at a clear rise in energy = new syllable at the same pitch). Every note:
    median pitch rounded to the semitone, volume from the track. So no sung note is missing – whatever Whisper makes of the text.
    Words (if present) only assign the word index to the notes (for subtitles)."""
    import librosa
    hop = 256
    f0, voiced, prob = librosa.pyin(vocals, fmin=65, fmax=900, sr=SR, frame_length=2048, hop_length=hop)
    midi = 69 + 12 * np.log2(np.maximum(np.nan_to_num(f0, nan=1.0), 1.0) / 440.0)
    rms = librosa.feature.rms(y=vocals, frame_length=2048, hop_length=hop)[0]
    # voiced = pyin considers it voiced (loosely: for rough, low voices the confidence is low) and something sounds there
    n0 = min(len(voiced), len(rms))
    ok = (np.nan_to_num(voiced[:n0]) > 0.35) & (rms[:n0] > rms.max() * 0.08)
    ok = np.pad(ok, (0, max(0, len(voiced) - n0)))
    onset = librosa.onset.onset_strength(y=vocals, sr=SR, hop_length=hop)
    n = min(len(midi), len(ok), len(rms), len(onset))
    midi, ok, rms, onset = midi[:n], ok[:n], rms[:n], onset[:n]
    # smooth octave errors: if the curve jumps by ~12, pull it back
    for k in range(1, n):
        if ok[k] and ok[k - 1]:
            while midi[k] - midi[k - 1] > 7:
                midi[k] -= 12
            while midi[k - 1] - midi[k] > 7:
                midi[k] += 12
    hop_s = hop / SR
    min_frames = int(0.06 / hop_s)
    peaks = set(librosa.util.peak_pick(onset, pre_max=3, post_max=3, pre_avg=5, post_avg=5, delta=0.35 * (onset.max() + 1e-9), wait=int(0.09 / hop_s)).tolist())
    notes = []
    k = 0
    while k < n:
        if not ok[k]:
            k += 1
            continue
        start = k
        ref = [midi[k]]
        k += 1
        while k < n and ok[k]:
            center = float(np.median(ref[-8:]))
            if abs(midi[k] - center) > 0.8:
                # does the deviation persist? (60 ms) -> new note
                ahead = midi[k:k + min_frames]
                if len(ahead) >= min_frames and np.all(np.abs(ahead - center) > 0.6) and k - start >= min_frames:
                    break
            if k in peaks and k - start >= min_frames and rms[k] > rms[max(0, k - min_frames)] * 1.25:
                break  # new syllable at (almost) the same pitch
            ref.append(midi[k])
            k += 1
        a, b = start * hop_s, k * hop_s
        if b - a >= 0.055:
            notes.append({"start": int(a * 1000), "dur": int((b - a) * 1000), "pitch": float(np.round(np.median(ref))), "loud": float(np.sqrt(np.mean(rms[start:k] ** 2)))})
    # close tiny gaps (legato), volume 0.5 … 1
    for i in range(len(notes) - 1):
        gap = notes[i + 1]["start"] - (notes[i]["start"] + notes[i]["dur"])
        if 0 < gap <= 40:
            notes[i]["dur"] += gap
    peak = max((m["loud"] for m in notes), default=1.0) or 1.0
    for m in notes:
        m["volume"] = round(0.5 + 0.5 * min(1.0, m.pop("loud") / peak) ** 0.5, 2)
    if words:
        for m in notes:
            t = m["start"] / 1000
            idx = [i for i, w in enumerate(words) if w["start"] - 0.05 <= t]
            m["word"] = idx[-1] if idx else 0
    return notes


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
    parser.add_argument("--fresh", action="store_true", help="recognise the text again instead of reusing lyrics.json")
    parser.add_argument("--pack-only", action="store_true", help="only render the pack variant (vanilla singing is switched off)")
    parser.add_argument("--title", help="name for the files (default: folder name)")
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

    # 1. text and melody
    if args.lyrics:
        text = args.lyrics
        melody = melody_from_pitch(vocals, None)
        log(f"melody: {len(melody)} notes, text given")
    else:
        lyrics_file = out / "lyrics.json"
        if lyrics_file.exists() and not args.fresh:
            words = json.loads(lyrics_file.read_text(encoding="utf-8"))
        else:
            words = transcribe(cache / "stems" / "vocals.wav", args.start, end, args.lang, args.whisper)
            lyrics_file.write_text(json.dumps(words, indent=0, ensure_ascii=False), encoding="utf-8")
        text = " ".join(w["text"] for w in words)
        melody = melody_from_pitch(vocals, words)
        log(f"Whisper: {len(words)} words: {text[:160]}")
        log(f"melody: {len(melody)} notes from {len(notes)} detected vocal notes ({args.start:.1f}-{end:.1f} s)")
    if not text.strip() or not melody:
        raise SystemExit("no singing found")
    (out / "melody.json").write_text(json.dumps(melody, indent=0), encoding="utf-8")

    # 2. let it sing
    for label, pack in (("villager", False), ("pack", True)):
        if pack and not args.pack:
            continue
        if not pack and args.pack_only:
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

    # 3. mix
    backing = sum(load(cache / "stems" / f"{stem}.wav", args.start, end) for stem in ("bass", "drums", "other") if (cache / "stems" / f"{stem}.wav").exists())
    n = len(vocals)
    title = args.title or out.name
    names = {"original": "Original", "villager": "Villager Vanilla", "pack": "Villager Pack"}
    sf.write(str(out / f"{title} - 1 {names['original']} - vocals only.wav"), vocals, SR)
    sf.write(str(out / f"{title} - 1 {names['original']} - with backing.wav"), np.clip(vocals + backing, -1, 1), SR)
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
        sf.write(str(out / f"{title} - {number} {names[label]} - vocals only.wav"), np.clip(y, -1, 1), SR)
        sf.write(str(out / f"{title} - {number} {names[label]} - with backing.wav"), np.clip(y + backing, -1, 1), SR)
        number += 1

    # 4. measure
    renders, labels = [], []
    for label, number_name in (("villager", "2 Villager Vanilla"), ("pack", f"{2 if args.pack_only else 3} Villager Pack")):
        path = out / f"{title} - {number_name} - vocals only.wav"
        if path.exists():
            renders.append(str(path))
            labels.append(label)
    order = "Original, " + ", ".join("Vanilla" if l == "villager" else "Pack" for l in labels)
    subprocess.run([python, "-I", "-X", "utf8", str(HERE / "compare.py"), str(out / f"{title} - 1 Original - vocals only.wav"), *renders, "--labels", ",".join(labels),
                    "--out", str(out / "report.json"), "--listen", str(out / f"{title} - Comparison one after the other ({order}).wav")], check=True)
    log(f"done: {out}")


if __name__ == "__main__":
    main()
