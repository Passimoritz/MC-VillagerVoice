"""End-to-end check of the villager voice model on a machine without a GPU: synthesises a short sung melody, converts it with
tools/villager_sing.py --chunks (exactly what the plugin runs) and checks the result.

  <rvc venv python> ci/voice_model_test.py

Fails if CUDA/ROCm is visible to torch (the test must prove CPU-only operation), if the conversion fails, or if the sections are silent.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
SR = 44100


def melody() -> np.ndarray:
    """A sung-like test signal: harmonic voice tone with vibrato and a vowel-ish spectral tilt, five notes with short gaps."""
    notes = [57, 60, 64, 62, 60]
    parts = []
    rng = np.random.default_rng(1)
    for midi in notes:
        n = int(0.9 * SR)
        t = np.arange(n) / SR
        f0 = 440 * 2 ** ((midi - 69) / 12) * 2 ** (0.25 * np.sin(2 * np.pi * 5.5 * t) / 12)
        phase = 2 * np.pi * np.cumsum(f0) / SR
        tone = sum((0.8 ** h) * np.sin(h * phase) for h in range(1, 18))
        env = np.minimum(1, t / 0.04) * np.minimum(1, (t[-1] - t) / 0.08)
        parts += [tone * env * 0.15 + rng.standard_normal(n) * 0.002, np.zeros(int(0.15 * SR))]
    return np.concatenate(parts).astype(np.float32)


def main() -> None:
    os.environ.setdefault("CUDA_VISIBLE_DEVICES", "")
    import torch
    print(f"torch {torch.__version__}, CUDA available: {torch.cuda.is_available()}, threads: {torch.get_num_threads()}", flush=True)
    if torch.cuda.is_available():
        raise SystemExit("a GPU is visible – this test must run CPU-only")
    work = Path(tempfile.mkdtemp(prefix="voiceapi-model-"))
    src = work / "melody.wav"
    sf.write(str(src), melody(), SR)
    started = time.time()
    result = subprocess.run([sys.executable, "-X", "utf8", str(ROOT / "tools" / "villager_sing.py"), "--vocals", str(src), "--chunks",
                             str(work / "song"), "--id", "ci-test"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    took = time.time() - started
    print(result.stdout[-3000:], result.stderr[-3000:], sep="\n", flush=True)
    meta_file = work / "song" / "voice.json"
    if result.returncode != 0 or not meta_file.exists():
        raise SystemExit("conversion failed")
    meta = json.loads(meta_file.read_text(encoding="utf-8"))
    chunks = sorted((work / "song").glob("*.ogg"))
    if not chunks:
        raise SystemExit("no sections written")
    level = max(float(np.sqrt(np.mean(sf.read(str(c))[0] ** 2))) for c in chunks)
    print(f"OK: {meta['count']} sections, {meta['lengthMs'] / 1000:.1f} s of singing in {took:.0f} s on the CPU, level {20 * np.log10(level + 1e-9):.1f} dBFS")
    if level < 1e-3:
        raise SystemExit("the converted singing is silent")


if __name__ == "__main__":
    main()
