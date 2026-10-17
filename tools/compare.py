"""Vergleicht Renderings mit dem Original (z. B. echter Gesang vs. Dorfbewohner-Gesang vs. Gesang mit Paket-Lauten).

  python compare.py original.wav render1.wav [render2.wav …] [--labels "Villager,Paket"] [--out bericht.json] [--listen vergleich.wav]

Kennzahlen je Rendering (alle 0 … 1, hoeher = aehnlicher):
  melody     Tonhoehen-Verlauf (Korrelation der Kontur ohne Lage, Oktaven zaehlen gleich) und mittlerer Fehler in Halbtoenen (mod 12)
  rhythm     Korrelation der Lautstaerke-Verlaeufe (Onsets) und Ueberdeckung der Aktivitaet (wann wird gesungen)
  timbre     Aehnlichkeit der Klangfarbe (MFCC-Statistik, Kosinus) und Helligkeit (Spektral-Schwerpunkt)
  voice      wie sehr ein Klassifikator (PANNs, falls eingerichtet) Gesang/Sprache hoert
  score      gewichteter Gesamtwert
--listen schreibt Original und Renderings hintereinander in eine Datei zum Anhoeren.
"""
from __future__ import annotations

import argparse
import json
import warnings
from pathlib import Path

import numpy as np

SR = 22050
warnings.filterwarnings("ignore")


def load(path: Path, seconds: float | None = None) -> np.ndarray:
    import librosa
    y, _ = librosa.load(str(path), sr=SR, mono=True)
    if seconds:
        y = y[: int(seconds * SR)]
    return y.astype(np.float32)


def same_length(a: np.ndarray, b: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    n = max(len(a), len(b))
    return np.pad(a, (0, n - len(a))), np.pad(b, (0, n - len(b)))


def pitch_track(y: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Tonhoehe je Frame (MIDI) und ob dort ein Ton klingt. yin mit kurzen Fenstern (auch kurze Silben zaehlen), Stimmhaftigkeit aus
    Flachheit des Spektrums und Lautstaerke – pyin haelt die kurzen, gestoppten Dorfbewohner-Laute sonst fuer unstimmhaft."""
    import librosa
    hop = 256
    f0 = librosa.yin(y, fmin=50, fmax=1100, sr=SR, frame_length=1024, hop_length=hop)
    flat = librosa.feature.spectral_flatness(y=y, n_fft=1024, hop_length=hop)[0]
    rms = librosa.feature.rms(y=y, frame_length=1024, hop_length=hop)[0]
    n = min(len(f0), len(flat), len(rms))
    voiced = (flat[:n] < 0.05) & (rms[:n] > rms.max() * 0.08)
    midi = 69 + 12 * np.log2(np.maximum(f0[:n], 1.0) / 440.0)
    return midi, voiced


def melody(a: np.ndarray, b: np.ndarray) -> dict:
    ma, va = pitch_track(a)
    mb, vb = pitch_track(b)
    n = min(len(ma), len(mb))
    both = va[:n] & vb[:n]
    if both.sum() < 10:
        return {"contour": 0.0, "error_semitones": 12.0, "frames": int(both.sum())}
    x = ma[:n][both]
    z = mb[:n][both]
    # Kontur ohne Lage: Oktaven sind egal (die Stimme faltet in ihren Umfang)
    diff = (z - x + 6) % 12 - 6
    offset = np.median(diff)
    error = np.abs(((diff - offset) + 6) % 12 - 6)
    corr = float(np.corrcoef(x - np.median(x), z - np.median(z))[0, 1]) if x.std() > 0.1 and z.std() > 0.1 else 0.0
    return {"contour": round(max(0.0, corr), 3), "error_semitones": round(float(error.mean()), 2), "frames": int(both.sum()),
            "score": round(max(0.0, corr) * 0.6 + max(0.0, 1 - error.mean() / 4) * 0.4, 3)}


def rhythm(a: np.ndarray, b: np.ndarray) -> dict:
    import librosa
    ea = librosa.onset.onset_strength(y=a, sr=SR, hop_length=256)
    eb = librosa.onset.onset_strength(y=b, sr=SR, hop_length=256)
    n = min(len(ea), len(eb))
    corr = float(np.corrcoef(ea[:n], eb[:n])[0, 1]) if ea[:n].std() > 0 and eb[:n].std() > 0 else 0.0
    ra = librosa.feature.rms(y=a, hop_length=256)[0]
    rb = librosa.feature.rms(y=b, hop_length=256)[0]
    n = min(len(ra), len(rb))
    on_a = ra[:n] > ra.max() * 0.08
    on_b = rb[:n] > rb.max() * 0.08
    union = (on_a | on_b).sum()
    overlap = float((on_a & on_b).sum() / union) if union else 0.0
    return {"onsets": round(max(0.0, corr), 3), "activity": round(overlap, 3), "score": round(max(0.0, corr) * 0.5 + overlap * 0.5, 3)}


def timbre(a: np.ndarray, b: np.ndarray) -> dict:
    import librosa
    def stats(y):
        m = librosa.feature.mfcc(y=y, sr=SR, n_mfcc=13)[1:]
        return np.concatenate([m.mean(axis=1), m.std(axis=1)])
    sa, sb = stats(a), stats(b)
    cos = float(np.dot(sa, sb) / (np.linalg.norm(sa) * np.linalg.norm(sb) + 1e-9))
    ca = float(np.median(librosa.feature.spectral_centroid(y=a, sr=SR)))
    cb = float(np.median(librosa.feature.spectral_centroid(y=b, sr=SR)))
    bright = float(min(ca, cb) / max(ca, cb)) if max(ca, cb) > 0 else 0.0
    return {"mfcc": round(max(0.0, cos), 3), "centroid_hz": [int(ca), int(cb)], "brightness": round(bright, 3), "score": round(max(0.0, cos) * 0.7 + bright * 0.3, 3)}


_tagger = None


def voice(y: np.ndarray) -> dict:
    """Was ein auf AudioSet trainiertes Netz hoert (PANNs); leer, wenn nicht eingerichtet."""
    global _tagger
    try:
        import librosa
        from panns_inference import AudioTagging, labels
        if _tagger is None:
            _tagger = AudioTagging(checkpoint_path=None, device="cpu")
        audio = librosa.resample(y, orig_sr=SR, target_sr=32000)
        clip, _ = _tagger.inference(audio[None, :])
        probs = {labels[j]: float(clip[0][j]) for j in range(len(labels))}
        keys = ["Singing", "Speech", "Music", "Animal", "Male singing", "Female singing", "Synthetic singing", "Humming", "Chant", "A capella"]
        top = sorted(probs.items(), key=lambda kv: -kv[1])[:5]
        return {k: round(probs.get(k, 0.0), 3) for k in keys} | {"top": [[k, round(v, 3)] for k, v in top]}
    except Exception as error:  # noqa: BLE001
        return {"error": str(error)[:80]}


def compare(original: np.ndarray, render: np.ndarray) -> dict:
    a, b = same_length(original, render)
    m = melody(a, b)
    r = rhythm(a, b)
    t = timbre(a, b)
    v = voice(b)
    sung = max(v.get("Singing", 0.0), v.get("Speech", 0.0), v.get("Humming", 0.0)) if "error" not in v else None
    score = m.get("score", 0) * 0.4 + r["score"] * 0.3 + t["score"] * 0.3
    return {"melody": m, "rhythm": r, "timbre": t, "voice": v, "voice_like": sung, "score": round(score, 3)}


def listen(original: np.ndarray, renders: list[np.ndarray], target: Path, seconds: float = 12.0) -> None:
    import soundfile as sf
    gap = np.zeros(int(0.6 * SR), dtype=np.float32)
    pieces = [original[: int(seconds * SR)]]
    for r in renders:
        pieces += [gap, r[: int(seconds * SR)]]
    sf.write(str(target), np.concatenate(pieces), SR)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("original")
    parser.add_argument("renders", nargs="+")
    parser.add_argument("--labels")
    parser.add_argument("--out")
    parser.add_argument("--listen")
    parser.add_argument("--seconds", type=float)
    args = parser.parse_args()
    labels = args.labels.split(",") if args.labels else [Path(p).stem for p in args.renders]
    original = load(Path(args.original), args.seconds)
    renders = [load(Path(p), args.seconds) for p in args.renders]
    report = {"original": {"voice": voice(original)}}
    for label, render in zip(labels, renders):
        report[label] = compare(original, render)
        c = report[label]
        print(f"{label:16s} score {c['score']:.3f} | Melodie {c['melody'].get('score', 0):.3f} (Kontur {c['melody'].get('contour', 0):.2f}, "
              f"Fehler {c['melody'].get('error_semitones', 0):.2f} HT) | Rhythmus {c['rhythm']['score']:.3f} | Klangfarbe {c['timbre']['score']:.3f}"
              + (f" | Stimme {c['voice_like']:.2f}" if c['voice_like'] is not None else ""))
    if args.out:
        Path(args.out).write_text(json.dumps(report, indent=1), encoding="utf-8")
    if args.listen:
        listen(original, renders, Path(args.listen))
        print(f"zum Anhoeren: {args.listen}")


if __name__ == "__main__":
    main()
