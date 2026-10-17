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


def syllables_of(word: str, language: str) -> int:
    """Grobe Silbenzahl eines Wortes (Vokalgruppen)."""
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


def melody_for_words(words: list[dict], notes: list[list], vocals: np.ndarray, language: str = "en") -> list[dict]:
    """Eine saubere, singbare Melodie: je Silbe ein Ton. Die Zeit kommt von Whisper (Wort, gleichmaessig auf seine Silben verteilt), die
    Tonhoehe aus der Gesangsspur selbst (Median der Tonhoehe im Silbenfenster, auf den Halbton gerundet); die erkannten Noten (basic-pitch)
    dienen nur als Stuetze, wenn die Tonhoehe nicht messbar ist. Keine Streuer, keine Oktavspruenge durch Obertoene."""
    import librosa
    f0, voiced, _ = librosa.pyin(vocals, fmin=70, fmax=900, sr=SR, frame_length=2048, hop_length=512)
    midi = 69 + 12 * np.log2(np.maximum(np.nan_to_num(f0, nan=1.0), 1.0) / 440.0)
    ok = np.nan_to_num(voiced) > 0.5
    hop_s = 512 / SR

    def pitch_between(a: float, b: float) -> float | None:
        i, j = int(a / hop_s), max(int(a / hop_s) + 1, int(b / hop_s))
        vals = midi[i:j][ok[i:j]]
        if len(vals) < 2:
            return None
        return float(np.round(np.median(vals)))

    # Energie-Verlauf fuer Silben-Einsaetze und Wortenden
    rms = librosa.feature.rms(y=vocals, frame_length=1024, hop_length=512)[0]
    onset_env = librosa.onset.onset_strength(y=vocals, sr=SR, hop_length=512)
    # Whisper legt bei schnellen Passagen mehrere Woerter auf dieselbe Zeit: Zeiten der Reihe nach erzwingen, zu kurze Woerter bleiben stumm
    cursor = 0.0
    for w in words:
        if w["start"] < cursor:
            w["start"] = cursor
        if w["end"] < w["start"] + 0.06:
            w["end"] = w["start"] + 0.06
        cursor = w["start"] + 0.04
    melody = []
    last = 60.0
    for i, w in enumerate(words):
        start = w["start"]
        if i + 1 < len(words) and words[i + 1]["start"] - start < 0.07:
            continue  # kein Platz fuer einen eigenen Ton
        # Wortende: spaetestens der Beginn des naechsten Wortes, hoechstens 1,2 s, und nicht laenger als der Gesang dort klingt
        end = min(w["end"], start + 1.2, words[i + 1]["start"] if i + 1 < len(words) else w["end"] + 0.3)
        f_a, f_b = int(start / hop_s), max(int(start / hop_s) + 2, int(end / hop_s))
        seg_rms = rms[f_a:f_b]
        if len(seg_rms) > 3:
            loud_frames = np.where(seg_rms > seg_rms.max() * 0.15)[0]
            end = min(end, start + (loud_frames[-1] + 2) * hop_s) if len(loud_frames) else end
        if end - start < 0.08:
            end = start + 0.08
        count = syllables_of(w["text"], language)
        # Silben-Einsaetze aus dem Energie-Anstieg im Wort (mindestens 90 ms Abstand), so viele wie Silben
        cuts = [start]
        if count > 1:
            env = onset_env[f_a:f_b]
            if len(env) > 3:
                peaks = librosa.util.peak_pick(env, pre_max=2, post_max=2, pre_avg=3, post_avg=3, delta=0.3 * (env.max() + 1e-9), wait=3)
                cand = sorted([(env[k], start + k * hop_s) for k in peaks if start + k * hop_s > start + 0.09 and start + k * hop_s < end - 0.07], reverse=True)
                cuts += [t for _, t in cand[: count - 1]]
                cuts = sorted(cuts)
            while len(cuts) < count:
                # fehlende Einsaetze: Rest gleichmaessig teilen
                gaps = [(cuts[k + 1] if k + 1 < len(cuts) else end) - cuts[k] for k in range(len(cuts))]
                k = int(np.argmax(gaps))
                nxt = cuts[k + 1] if k + 1 < len(cuts) else end
                if nxt - cuts[k] < 0.18:
                    break
                cuts.insert(k + 1, (cuts[k] + nxt) / 2)
        cuts = sorted(set(cuts))
        for k, a in enumerate(cuts):
            b = cuts[k + 1] if k + 1 < len(cuts) else end
            if b - a < 0.06:
                continue
            pitch = pitch_between(a + 0.02, b - 0.02) or pitch_between(a, b)
            if pitch is None:
                near = [n for n in notes if n[0] < b and n[1] > a]
                pitch = float(max(near, key=lambda n: n[3])[2]) if near else last
            while pitch - last > 9:
                pitch -= 12
            while last - pitch > 9:
                pitch += 12
            last = pitch
            seg = vocals[int(a * SR): int(b * SR)]
            loud = float(np.sqrt(np.mean(seg ** 2))) if len(seg) else 0.0
            melody.append({"start": int(a * 1000), "dur": max(80, int((b - a) * 1000) - 15), "pitch": pitch, "loud": loud, "word": i})
    peak = max((m["loud"] for m in melody), default=1.0) or 1.0
    for m in melody:
        m["volume"] = round(0.5 + 0.5 * min(1.0, m.pop("loud") / peak) ** 0.5, 2)
    # gleiche Toene direkt hintereinander im selben Wort verbinden (ein gehaltener Ton statt Stottern)
    merged: list[dict] = []
    for m in melody:
        if merged and merged[-1]["word"] == m["word"] and merged[-1]["pitch"] == m["pitch"] and merged[-1]["start"] + merged[-1]["dur"] >= m["start"] - 20:
            merged[-1]["dur"] = m["start"] + m["dur"] - merged[-1]["start"]
        else:
            merged.append(m)
    return merged


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
        melody = melody_for_words(words, notes, vocals, args.lang)
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
    title = out.name
    names = {"original": "Original", "villager": "Villager Vanilla", "pack": "Villager Paket"}
    sf.write(str(out / f"{title} - 1 {names['original']} - nur Gesang.wav"), vocals, SR)
    sf.write(str(out / f"{title} - 1 {names['original']} - mit Begleitung.wav"), np.clip(vocals + backing, -1, 1), SR)
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
        sf.write(str(out / f"{title} - {number} {names[label]} - nur Gesang.wav"), np.clip(y, -1, 1), SR)
        sf.write(str(out / f"{title} - {number} {names[label]} - mit Begleitung.wav"), np.clip(y + backing, -1, 1), SR)
        number += 1

    # 4. messen
    renders = [str(out / f"{title} - 2 Villager Vanilla - nur Gesang.wav")]
    labels = "villager"
    if (out / f"{title} - 3 Villager Paket - nur Gesang.wav").exists():
        renders.append(str(out / f"{title} - 3 Villager Paket - nur Gesang.wav"))
        labels += ",pack"
    subprocess.run([python, "-I", "-X", "utf8", str(HERE / "compare.py"), str(out / f"{title} - 1 Original - nur Gesang.wav"), *renders, "--labels", labels,
                    "--out", str(out / "bericht.json"), "--listen", str(out / f"{title} - Vergleich nacheinander (Original, Vanilla, Paket).wav")], check=True)
    log(f"fertig: {out}")


if __name__ == "__main__":
    main()
