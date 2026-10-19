"""Training data for the villager voice model (RVC): all villager sounds from the Minecraft assets, prepared so the model learns the voice
across the whole singing range.

  python villager_dataset.py --out folder/ [--speaker villager]

- Source: ~/.voiceapi/sounds/mob/villager/*.ogg (voicelab.py setup downloads them).
- Every take is stored at several pitches (-7 … +12 semitones, the way the game plays them via speed) – so the model knows the villager in
  high and low registers it never sings naturally.
- Slight variants (tempo ±8 %, level) against memorising; silence at the edges cut, levels matched.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

SR = 44100
SOUNDS = Path.home() / ".voiceapi" / "sounds"
SOURCES = {
    "villager": ["mob/villager/idle1", "mob/villager/idle2", "mob/villager/idle3", "mob/villager/yes1", "mob/villager/yes2", "mob/villager/yes3",
                 "mob/villager/no1", "mob/villager/no2", "mob/villager/no3", "mob/villager/haggle1", "mob/villager/haggle2", "mob/villager/haggle3",
                 "mob/villager/hit1", "mob/villager/hit2", "mob/villager/hit3", "mob/villager/hit4", "mob/villager/death"],
}
SHIFTS = [-7, -5, -3, 0, 2, 4, 5, 7, 9, 12]
TEMPI = [0.92, 1.0, 1.08]


def load(name: str) -> np.ndarray:
    import soundfile as sf
    import librosa
    data, rate = sf.read(str(SOUNDS / f"{name}.ogg"), always_2d=True)
    y = data.mean(axis=1).astype(np.float32)
    if rate != SR:
        y = librosa.resample(y, orig_sr=rate, target_sr=SR)
    # remove silence at the edges
    env = np.abs(y)
    thr = env.max() * 0.02
    idx = np.where(env > thr)[0]
    if len(idx):
        y = y[max(0, idx[0] - 441): idx[-1] + 2205]
    return y / (np.abs(y).max() + 1e-9) * 0.8


def variant(y: np.ndarray, semitones: float, tempo: float) -> np.ndarray:
    """Pitch like in the game (speed, the formants move along) and tempo separately."""
    import librosa
    speed = 2 ** (semitones / 12)
    idx = np.arange(0, len(y) - 1, speed)
    z = np.interp(idx, np.arange(len(y)), y).astype(np.float32)
    if abs(tempo - 1.0) > 1e-3:
        z = librosa.effects.time_stretch(z, rate=tempo)
    return z / (np.abs(z).max() + 1e-9) * 0.8


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True)
    parser.add_argument("--speaker", default="villager")
    args = parser.parse_args()
    import soundfile as sf
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    total = 0.0
    count = 0
    for name in SOURCES[args.speaker]:
        y = load(name)
        for s in SHIFTS:
            for t in TEMPI:
                z = variant(y, s, t)
                # a little silence in between helps the slicing during preprocessing
                z = np.concatenate([np.zeros(int(0.05 * SR), dtype=np.float32), z, np.zeros(int(0.05 * SR), dtype=np.float32)])
                sf.write(str(out / f"{Path(name).name}_{s:+d}_{int(t * 100)}.wav"), z, SR)
                total += len(z) / SR
                count += 1
    print(f"{count} files, {total / 60:.1f} min in {out}")


if __name__ == "__main__":
    main()
