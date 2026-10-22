"""A villager sings a song: vocal track → villager voice model (RVC, CPU only) → mixed with the original backing.

  python villager_sing.py --cache <NoteAPI cache folder> --out folder/ --title "Genre - Title" [--model villager] [--pitch auto|0|-12]
  python villager_sing.py --vocals vocals.wav --backing backing.wav --out folder/ --title "…"
  python villager_sing.py --vocals vocals.wav --chunks folder/ --id <piece-id>      for the plugin: villager singing as pack sections

--chunks (called by the plugin): converts only the vocals and stores them as short OGG sections with crossfades (<id>/<n>.ogg) plus
voice.json (section length, overlap, count, duration, model). The sections play one after the other and together make the singing
(the linear crossfades sum to 1); so the player can start anywhere and stays in time.

Output (descriptive names):
  <Title> - 1 Original - with backing.wav
  <Title> - 2 Villager - with backing.wav
  <Title> - 2 Villager - vocals only.wav
  <Title> - Comparison (original first, then villager).wav     12 s each, one after the other

The model lives in ~/.voiceapi/rvc/models/<model>.pth (+ .index); trained with villager_dataset.py and Applio (CPU). Everything runs without
a GPU so it also works on Linux servers without a graphics card.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

import numpy as np

HOME = Path(os.environ.get("VOICEAPI_RVC_HOME", str(Path.home() / ".voiceapi" / "rvc")))
APPLIO = HOME / "Applio"
PYTHON = HOME / "venv" / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
SR = 44100
CHUNK_MS = 4000      # length of one section (without overlap)
OVERLAP_MS = 60      # crossfade between two sections


def log(text: str) -> None:
    print(text, flush=True)


def load(path: Path, start: float = 0.0, end: float | None = None) -> np.ndarray:
    import librosa
    y, _ = librosa.load(str(path), sr=SR, mono=True, offset=start, duration=None if end is None else end - start)
    return y.astype(np.float32)


def model_files(name: str) -> tuple[Path, Path | None]:
    """The trained model: first ~/.voiceapi/rvc/models, otherwise the newest weights from the Applio training folder."""
    pth = HOME / "models" / f"{name}.pth"
    index = HOME / "models" / f"{name}.index"
    if pth.exists():
        return pth, index if index.exists() else None
    logs = APPLIO / "logs" / name
    weights = sorted((p for p in logs.glob(f"{name}*.pth") if not p.name.startswith(("G_", "D_"))), key=lambda p: p.stat().st_mtime)
    if not weights:
        raise SystemExit(f"No model {name}: neither {pth} nor weights in {logs}")
    indexes = sorted(logs.glob("*.index"), key=lambda p: p.stat().st_mtime)
    return weights[-1], indexes[-1] if indexes else None


def convert(vocals: Path, out: Path, model: str, pitch: int, f0_method: str = "fcpe", index_rate: float = 1.0, protect: float = 0.5) -> None:
    pth, index = model_files(model)
    # through rvc_infer.py: fixed randomness, noise always from the CPU – the same result on every machine, CPU or GPU
    starter = Path(__file__).resolve().parent / "rvc_infer.py"
    cmd = [str(PYTHON), str(starter), str(APPLIO), "infer", "--input-path", str(vocals), "--output-path", str(out), "--pth-path", str(pth),
           "--pitch", str(pitch), "--f0-method", f0_method, "--index-rate", str(index_rate if index else 0), "--protect", str(protect),
           "--volume-envelope", "1.0", "--export-format", "WAV"]
    if index:
        cmd += ["--index-path", str(index)]
    log(f"  model {pth.name}" + (f" + {index.name}" if index else "") + f", pitch {pitch:+d}")
    result = subprocess.run(cmd, cwd=str(APPLIO), capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode != 0 or not out.exists():
        raise SystemExit("Conversion failed:\n" + result.stdout[-1500:] + result.stderr[-1500:])


def auto_pitch(vocals: np.ndarray) -> int:
    """The videos sing in the register of the original; only very high voices (middle above G4) one octave lower.
    yin on 22 kHz instead of pyin: the same median in about a twentieth of the time (only the register matters here)."""
    import librosa
    y = librosa.resample(vocals[: SR * 60], orig_sr=SR, target_sr=22050)
    f0 = librosa.yin(y, fmin=65, fmax=1000, sr=22050, frame_length=2048, hop_length=512)
    rms = librosa.feature.rms(y=y, frame_length=2048, hop_length=512)[0][: len(f0)]
    vals = f0[: len(rms)][rms > rms.max() * 0.15]
    if len(vals) < 20:
        return 0
    median = 69 + 12 * np.log2(np.median(vals) / 440)
    return -12 if median > 67 else 0


# sound of the villager videos (long-term spectrum of their vocal track, dB per band relative to the loudest), measured on three reference videos
VIDEO_EDGES = [60, 120, 180, 250, 350, 500, 700, 1000, 1400, 2000, 2800, 4000, 5600, 8000, 11000]
VIDEO_CURVE = [-15, -13, -19, -15, -12, -2.5, 0, -6, -7, -14, -16, -31, -34, -38]


def colour(y: np.ndarray, strength: float = 0.7, limit_db: float = 9.0) -> np.ndarray:
    """Matches the long-term spectrum to the sound of the villager videos (gently: strength, at most limit_db per band) – closes the rest
    the voice model still carries over from the real singer (more bass, less nasal middle)."""
    import librosa
    S = librosa.stft(y, n_fft=2048, hop_length=512)
    power = np.abs(S) ** 2
    frame = power.sum(axis=0)
    loud = frame > frame.max() * 0.05
    if loud.sum() < 10:
        return y
    freqs = librosa.fft_frequencies(sr=SR, n_fft=2048)
    mean = power[:, loud].mean(axis=1)
    bands = np.array([10 * np.log10(mean[(freqs >= a) & (freqs < b)].mean() + 1e-12) for a, b in zip(VIDEO_EDGES[:-1], VIDEO_EDGES[1:])])
    bands -= bands.max()
    correction = np.clip((np.array(VIDEO_CURVE) - bands) * strength, -limit_db, limit_db)
    centers = np.sqrt(np.array(VIDEO_EDGES[:-1]) * np.array(VIDEO_EDGES[1:]))
    gain = 10 ** (np.interp(freqs, centers, correction) / 20)
    out = librosa.istft(S * gain[:, None], hop_length=512, length=len(y))
    return (out * (np.sqrt(np.mean(y ** 2)) / (np.sqrt(np.mean(out ** 2)) + 1e-9))).astype(np.float32)


def _envelope(y: np.ndarray, win_ms: float) -> np.ndarray:
    n = max(1, int(SR * win_ms / 1000))
    k = np.hanning(2 * n + 1)
    return np.sqrt(np.convolve(y.astype(np.float64) ** 2, k / k.sum(), mode="same") + 1e-12)


def follow(vocals: np.ndarray, y: np.ndarray, consonants: float = 0.5, win_ms: float = 25.0, clamp_db: float = 18.0,
           gate_db: float = -45.0) -> np.ndarray:
    """Puts the villager singing onto the performance of the original:
    1. Offset: the model starts a few milliseconds early/late – shifted sample-exactly onto the onsets of the original (±50 ms).
    2. Level curve: volume every 25 ms like the original (at most ±18 dB, smoothed) – accents and syllable starts sit where the singer has
       them; where the original is silent, it gets quiet (no breathing/noise from the model).
    3. Consonants: the sharp, voiceless parts of the original (percussive, above 2 kHz) mixed in quietly – the model slurs them; without
       them the text is harder to understand and the onsets blur. 0 switches it off.
    Measured with NoteAPI's Likeness on 6 NCS songs: harmony/rhythm/dynamics 85.3 → 91.3."""
    import librosa
    n = min(len(vocals), len(y))
    vocals, y = vocals[:n], y[:n].astype(np.float32)
    hop = 64
    a = librosa.onset.onset_strength(y=vocals, sr=SR, hop_length=hop)
    b = librosa.onset.onset_strength(y=y, sr=SR, hop_length=hop)
    m = min(len(a), len(b))
    a, b = a[:m] - a[:m].mean(), b[:m] - b[:m].mean()
    reach = int(0.05 * SR / hop)
    scores = [float(np.dot(a[max(0, -l):m - max(0, l)], b[max(0, l):m - max(0, -l)])) for l in range(-reach, reach + 1)]
    lag = (int(np.argmax(scores)) - reach) * hop
    if lag > 0:
        y = np.concatenate([y[lag:], np.zeros(lag, np.float32)])
    elif lag < 0:
        y = np.concatenate([np.zeros(-lag, np.float32), y[:lag]])
    eo, ec = _envelope(vocals, win_ms), _envelope(y, win_ms)
    lo, hi = 10 ** (-clamp_db / 20), 10 ** (clamp_db / 20)
    gain = np.clip(eo / ec, lo, hi)
    quiet = eo < np.percentile(eo, 99) * 10 ** (gate_db / 20)
    gain[quiet] = np.minimum(gain[quiet], lo)
    k = np.hanning(2 * int(SR * 0.006) + 1)
    out = y * np.convolve(gain, k / k.sum(), mode="same")
    if consonants > 0:
        S = librosa.stft(vocals, n_fft=2048, hop_length=512)
        _, P = librosa.decompose.hpss(S, kernel_size=(17, 17), margin=2.0)
        P[librosa.fft_frequencies(sr=SR, n_fft=2048) < 2000] = 0
        out = out + consonants * librosa.istft(P, hop_length=512, length=n)
    out *= float(np.sqrt(np.mean(vocals ** 2))) / (float(np.sqrt(np.mean(out ** 2))) + 1e-9)
    log(f"offset corrected: {lag / SR * 1000:+.1f} ms")
    return out.astype(np.float32)


def limit(y: np.ndarray, ceiling: float = 0.97) -> np.ndarray:
    """Peak limiter instead of hard clipping: a smooth gain (5 ms attack, 120 ms release) keeps the mix under the ceiling without
    the crackle of clipped peaks."""
    peak = np.abs(y)
    if peak.max() <= ceiling:
        return y.astype(np.float32)
    need = np.minimum(1.0, ceiling / np.maximum(peak, 1e-9))
    hold = int(0.005 * SR)
    from scipy.ndimage import minimum_filter1d
    # the minimum around every sample: the gain is already down when a peak arrives (look-ahead) and stays down briefly after it
    gain = minimum_filter1d(need, size=2 * hold + 1)
    release = int(0.12 * SR)
    k = np.hanning(2 * release + 1)
    smooth = np.convolve(gain, k / k.sum(), mode="same")
    gain = np.minimum(gain, smooth)
    return np.clip(y * gain, -ceiling, ceiling).astype(np.float32)


def write_chunks(y: np.ndarray, folder: Path, ident: str, model: str) -> dict:
    """Cuts the singing into crossfaded sections and writes them as OGG (Vorbis) – section n starts at n*CHUNK_MS - OVERLAP_MS."""
    import soundfile as sf
    folder.mkdir(parents=True, exist_ok=True)
    for old in folder.glob("*.ogg"):
        old.unlink()
    chunk = int(CHUNK_MS / 1000 * SR)
    over = int(OVERLAP_MS / 1000 * SR)
    count = int(np.ceil(len(y) / chunk))
    silent = []
    for n in range(count):
        a = max(0, n * chunk - over)
        b = min(len(y), (n + 1) * chunk + over)
        part = y[a:b].copy()
        # linear crossfade over 2*over around every boundary: fade in at the start (except the first), fade out at the end (except the last)
        if n > 0:
            k = min(2 * over, len(part))
            part[:k] *= np.linspace(0, 1, k)
        if n < count - 1:
            k = min(2 * over, len(part))
            part[-k:] *= np.linspace(1, 0, k)
        if float(np.abs(part).max()) < 1e-3:
            silent.append(n)  # silent section: not stored, the player skips it
            continue
        sf.write(str(folder / f"{n}.ogg"), part.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    meta = {"id": ident, "chunkMs": CHUNK_MS, "overlapMs": OVERLAP_MS, "count": count, "silent": silent,
            "lengthMs": int(len(y) / SR * 1000), "model": model, "sampleRate": SR}
    (folder / "voice.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")
    return meta


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cache")
    parser.add_argument("--vocals")
    parser.add_argument("--backing")
    parser.add_argument("--from", dest="start", type=float, default=0.0)
    parser.add_argument("--to", dest="end", type=float)
    parser.add_argument("--out")
    parser.add_argument("--title")
    parser.add_argument("--chunks", help="folder for the pack sections (plugin)")
    parser.add_argument("--id", help="id of the piece (with --chunks)")
    parser.add_argument("--model", default="villager")
    parser.add_argument("--pitch", default="auto")
    parser.add_argument("--f0-method", default="fcpe", help="fcpe (default, faster, just as accurate) or rmvpe")
    parser.add_argument("--no-colour", action="store_true", help="do not match the sound to the villager videos")
    parser.add_argument("--index-rate", type=float, default=1.0, help="how much of the villager's own timbre (index) to use, 0 … 1")
    parser.add_argument("--protect", type=float, default=0.5, help="how much consonants/breaths stay untouched, 0 … 0.5")
    parser.add_argument("--consonants", type=float, default=0.5, help="mix in the consonants of the original (0 = off)")
    parser.add_argument("--no-follow", action="store_true", help="do not align offset, level curve and consonants to the original")
    parser.add_argument("--vocal-gain", type=float, default=0.0, help="villager level in the mix relative to the original vocals (dB)")
    parser.add_argument("--label", default="Villager")
    args = parser.parse_args()
    import soundfile as sf
    if args.chunks:
        if not args.vocals or not args.id:
            raise SystemExit("--chunks needs --vocals and --id")
        folder = Path(args.chunks).resolve()
        folder.mkdir(parents=True, exist_ok=True)
        vocals = load(Path(args.vocals), args.start, args.end)
        src = folder / "_vocals.wav"
        sf.write(str(src), vocals, SR)
        pitch = auto_pitch(vocals) if args.pitch == "auto" else int(args.pitch)
        raw = folder / "_villager.wav"
        log("@@step Villager is singing")
        convert(src, raw, args.model, pitch, args.f0_method, args.index_rate, args.protect)
        y = load(raw)
        if not args.no_colour:
            y = colour(y)
        y = np.pad(y, (0, max(0, len(vocals) - len(y))))[: len(vocals)]
        y *= float(np.sqrt(np.mean(vocals ** 2))) / (float(np.sqrt(np.mean(y ** 2))) + 1e-9)
        if not args.no_follow:
            y = follow(vocals, y, args.consonants)
        y = np.clip(y, -0.98, 0.98)
        log("@@step Writing sections")
        meta = write_chunks(y, folder, args.id, args.model)
        src.unlink(missing_ok=True)
        raw.unlink(missing_ok=True)
        log(f"@@done {args.id} {meta['count']} sections, {meta['lengthMs'] / 1000:.1f} s")
        return
    if not args.out or not args.title:
        raise SystemExit("--out and --title (or --chunks and --id)")
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    if args.cache:
        stems = Path(args.cache) / "stems"
        vocals = load(stems / "vocals.wav", args.start, args.end)
        backing = sum(load(stems / f"{s}.wav", args.start, args.end) for s in ("bass", "drums", "other") if (stems / f"{s}.wav").exists())
    else:
        vocals = load(Path(args.vocals), args.start, args.end)
        backing = load(Path(args.backing), args.start, args.end) if args.backing else np.zeros_like(vocals)
    n = min(len(vocals), len(backing)) if np.ndim(backing) else len(vocals)
    vocals, backing = vocals[:n], (backing[:n] if np.ndim(backing) else np.zeros(n, dtype=np.float32))
    title = args.title
    src = out / f"{title} - 1 Original - vocals only.wav"
    sf.write(str(src), vocals, SR)
    sf.write(str(out / f"{title} - 1 Original - with backing.wav"), limit(vocals + backing), SR)
    pitch = auto_pitch(vocals) if args.pitch == "auto" else int(args.pitch)
    raw = out / f"_{args.label}-raw.wav"
    convert(src, raw, args.model, pitch, args.f0_method, args.index_rate, args.protect)
    y = load(raw)
    if not args.no_colour:
        y = colour(y)
    y = np.pad(y, (0, max(0, n - len(y))))[:n]
    # level like the original vocals
    y *= float(np.sqrt(np.mean(vocals ** 2))) / (float(np.sqrt(np.mean(y ** 2))) + 1e-9)
    if not args.no_follow:
        y = follow(vocals, y, args.consonants)
    sf.write(str(out / f"{title} - 2 {args.label} - vocals only.wav"), limit(y), SR)
    # the videos keep the original instrumental untouched and the villager about as loud as the original singer (measured: −0.6 dB)
    sung = y * 10 ** (args.vocal_gain / 20) + backing
    sf.write(str(out / f"{title} - 2 {args.label} - with backing.wav"), limit(sung), SR)
    # comparison: the loudest 12 s of the vocals, original first, then villager (with backing)
    win = 12 * SR
    if n > win:
        energy = np.convolve(vocals ** 2, np.ones(SR) / SR, mode="valid")[::SR // 4]
        best = int(np.argmax([energy[i: i + 48].sum() for i in range(max(1, len(energy) - 48))])) * (SR // 4)
    else:
        best = 0
    a = limit(vocals + backing)[best: best + win]
    b = limit(sung)[best: best + win]
    sf.write(str(out / f"{title} - Comparison (original first, then {args.label}).wav"), np.concatenate([a, np.zeros(SR // 2, dtype=np.float32), b]), SR)
    raw.unlink(missing_ok=True)
    log(f"done: {out}")


if __name__ == "__main__":
    main()
