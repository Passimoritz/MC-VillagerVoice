"""Finds the spots in each voice's vanilla sounds where it holds a note – the basis for its sung "Hmm" in the pack (HUMS in
build_voice_pack.py). Per take: the longest voiced stretch with a steady pitch (at most ±0.6 semitones), a little run-up before it (the
natural onset), the decay after it, the stable middle as a loop and the pitch (MIDI). The best three per voice.

  python tools/find_hums.py [--write]       (--write: the result as hums.json next to the script, build_voice_pack.py reads it)

Needs numpy, librosa, soundfile (the NoteAPI environment ~/.noteapi/venv) and the vanilla sounds in ~/.voiceapi/sounds (voicelab.py setup).
"""
import argparse
import json
from pathlib import Path

import numpy as np

SOUNDS = Path.home() / ".voiceapi" / "sounds"
SR = 22050
# per voice: folder and file name prefixes that sound like a voice (no steps, hits, deaths …)
SPEAKERS = {
    "trader": ("mob/wandering_trader", ["idle", "no", "yes", "haggle"]),
    "witch": ("entity/witch", ["ambient", "celebrate"]),
    "allay": ("mob/allay", ["idle_with_item", "idle_without_item", "item_given", "item_taken"]),
    "piglin": ("mob/piglin", ["idle", "admire", "celebrate", "jealous"]),
    "zombie": ("mob/zombie_villager", ["say"]),
    "evoker": ("mob/evocation_illager", ["idle", "celebrate", "prepare_wololo", "cast"]),
    "goat": ("mob/goat", ["idle", "scream"]),
    "cat": ("mob/cat", ["meow", "beg", "purreow"]),
}


def best_run(path: Path):
    import librosa
    y, _ = librosa.load(str(path), sr=SR, mono=True)
    if len(y) < SR * 0.15:
        return None
    hop = 256
    f0, voiced, prob = librosa.pyin(y, fmin=70, fmax=1400, sr=SR, frame_length=2048, hop_length=hop)
    rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=hop)[0]
    n = min(len(f0), len(rms))
    midi = np.where(voiced[:n], 69 + 12 * np.log2(np.nan_to_num(f0[:n], nan=440.0) / 440.0), np.nan)
    loud = rms[:n] > 0.12 * rms[:n].max()
    best = None
    start = 0
    while start < n:
        if np.isnan(midi[start]) or not loud[start]:
            start += 1
            continue
        end = start
        while end + 1 < n and not np.isnan(midi[end + 1]) and loud[end + 1] and abs(midi[end + 1] - np.nanmedian(midi[start:end + 1])) <= 0.6:
            end += 1
        length = (end - start + 1) * hop / SR
        if length >= 0.1:
            score = length * float(np.nanmean(prob[start:end + 1])) * float(rms[start:end + 1].mean())
            if best is None or score > best[0]:
                best = (score, start, end)
        start = end + 1
    if best is None:
        return None
    _, a, b = best
    t0, t1 = a * hop / SR, (b + 1) * hop / SR
    duration = len(y) / SR
    stable = t1 - t0
    pitch = float(np.nanmedian(midi[a:b + 1]))
    return {
        "score": best[0],
        "start": round(max(0.0, t0 - 0.06), 3),
        "end": round(min(duration, t1 + 0.12), 3),
        "mid_a": round(t0 + 0.15 * stable, 3),
        "mid_b": round(t1 - 0.12 * stable, 3),
        "midi": round(pitch, 1),
        "stable": round(stable, 3),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    found = {}
    for speaker, (folder, prefixes) in SPEAKERS.items():
        candidates = []
        for file in sorted((SOUNDS / folder).glob("*.ogg")):
            if not any(file.stem.startswith(p) for p in prefixes):
                continue
            run = best_run(file)
            if run:
                run["file"] = f"{folder}/{file.stem}"
                candidates.append(run)
        candidates.sort(key=lambda r: -r["score"])
        # the best three whose pitches are close together (otherwise the game jumps between registers)
        chosen = []
        for c in candidates:
            if not chosen or abs(c["midi"] - chosen[0]["midi"]) <= 4:
                chosen.append(c)
            if len(chosen) == 3:
                break
        found[speaker] = chosen
        print(f"{speaker:8s} " + " | ".join(f"{c['file'].split('/')[-1]} {c['stable']*1000:.0f} ms @ {c['midi']}" for c in chosen) if chosen else f"{speaker}: nothing")
    if args.write:
        out = {sp: [[c["file"], c["start"], c["end"], c["mid_a"], c["mid_b"], c["midi"]] for c in runs] for sp, runs in found.items() if runs}
        (Path(__file__).parent / "hums.json").write_text(json.dumps(out, indent=1), encoding="utf-8")
        print("hums.json written")


if __name__ == "__main__":
    main()
