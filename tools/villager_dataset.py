"""Training data for the villager voice model (RVC): all villager sounds from the Minecraft assets, prepared so the model learns the voice
across the whole singing range.

  python villager_dataset.py --out folder/ [--speaker villager] [--rich]

- Source: ~/.voiceapi/sounds/mob/villager/*.ogg (voicelab.py setup downloads them).
- Every take is stored at several pitches (-7 … +12 semitones, the way the game plays them via speed) – so the model knows the villager in
  high and low registers it never sings naturally.
- Slight variants (tempo ±8 %, level) against memorising; silence at the edges cut, levels matched.
- --rich: Minecraft only has 17 villager takes, each a single, steady sound – a model trained on them alone keeps its timbre too static
  (sounds stiff when singing). --rich adds
    * phrases: 2–5 different takes (hmm, yes, no, haggle …) at changing pitches, crossfaded into each other – the voice learns to move
      between its sounds like a singer moves between vowels;
    * movement: vibrato (4.5–6.5 Hz, ±0.2–0.7 semitones, starting after the attack) and glides (±3 semitones) inside the takes;
  and keeps only the original tempo of the plain pitch variants (the phrases bring enough tempo variety).
"""
from __future__ import annotations

import argparse
import random
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


def moving(y: np.ndarray, base: float, rng: random.Random) -> np.ndarray:
    """Plays a take with a moving pitch – a base shift plus a glide and a vibrato that sets in after the attack – the way the game would if
    it could change the speed while playing (the formants move along, like in every pitched villager sound)."""
    n = len(y)
    t = np.arange(n) / SR
    glide = rng.uniform(-3.0, 3.0) * np.clip(t / max(t[-1], 1e-3), 0, 1) if rng.random() < 0.6 else 0.0
    depth = rng.uniform(0.2, 0.7) if rng.random() < 0.7 else 0.0
    rate = rng.uniform(4.5, 6.5)
    onset = rng.uniform(0.08, 0.2)
    vibrato = depth * np.sin(2 * np.pi * rate * t) * np.clip((t - onset) / 0.15, 0, 1)
    speed = 2 ** ((base + glide + vibrato) / 12)
    pos = np.cumsum(speed)
    pos = pos[pos < n - 1]
    z = np.interp(pos, np.arange(n), y).astype(np.float32)
    return z / (np.abs(z).max() + 1e-9) * 0.8


def phrase(takes: list[np.ndarray], rng: random.Random) -> np.ndarray:
    """2–5 different takes at changing pitches, crossfaded (30–70 ms) or with short gaps – a sung line made of villager sounds."""
    count = rng.randint(2, 5)
    base = rng.choice(SHIFTS)
    out = np.zeros(0, dtype=np.float32)
    for k in range(count):
        step = rng.choice([-5, -3, -2, 0, 2, 3, 4, 5, 7])
        shift = float(np.clip(base + (step if k else 0), -7, 12))
        z = moving(rng.choice(takes), shift, rng) * rng.uniform(0.75, 1.0)
        if len(out) and rng.random() < 0.7:
            fade = min(int(rng.uniform(0.03, 0.07) * SR), len(z) // 3, len(out) // 3)
            mix = out[-fade:] * np.linspace(1, 0, fade) + z[:fade] * np.linspace(0, 1, fade)
            out = np.concatenate([out[:-fade], mix, z[fade:]])
        else:
            gap = np.zeros(int(rng.uniform(0.02, 0.12) * SR), dtype=np.float32) if len(out) else np.zeros(0, dtype=np.float32)
            out = np.concatenate([out, gap, z])
        base = shift
    return out / (np.abs(out).max() + 1e-9) * 0.8


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True)
    parser.add_argument("--speaker", default="villager")
    parser.add_argument("--rich", action="store_true", help="add phrases and pitch movement (livelier timbre)")
    parser.add_argument("--phrases", type=int, default=260, help="number of phrases with --rich")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    import soundfile as sf
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    total = 0.0
    count = 0
    pad = np.zeros(int(0.05 * SR), dtype=np.float32)  # a little silence in between helps the slicing during preprocessing
    takes = [load(name) for name in SOURCES[args.speaker]]
    for name, y in zip(SOURCES[args.speaker], takes):
        for s in SHIFTS:
            for t in ([1.0] if args.rich else TEMPI):
                z = np.concatenate([pad, variant(y, s, t), pad])
                sf.write(str(out / f"{Path(name).name}_{s:+d}_{int(t * 100)}.wav"), z, SR)
                total += len(z) / SR
                count += 1
    if args.rich:
        rng = random.Random(args.seed)
        for i in range(args.phrases):
            z = np.concatenate([pad, phrase(takes, rng), pad])
            sf.write(str(out / f"phrase_{i:03d}.wav"), z, SR)
            total += len(z) / SR
            count += 1
    print(f"{count} files, {total / 60:.1f} min in {out}")


if __name__ == "__main__":
    main()
