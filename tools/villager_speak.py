"""The villager speaks: a spoken recording (speech, a voice line, an old radio recording) → villager voice model (RVC, CPU only).

  python villager_speak.py --input speech.wav --out villager.wav
  python villager_speak.py --input speech.wav --chunks folder/ --id <id>       for the plugin (VoiceApi.convertSpeech): sections like songs

The singing mode (villager_sing.py) is made for separated vocal tracks of songs; spoken recordings need other things:
1. Noise is detected and filtered first: the quietest frames give the noise profile (hiss, hum, room, crowd), the signal-to-noise
   distance says how much to take out (a clean recording, e.g. speech synthesis, is left alone), then spectral gating and the speech band
   (90–7000 Hz). If Demucs is installed (the NoteAPI environment), the voice is separated from the background before that (--separate).
2. Conversion with speech settings: pitch detection rmvpe, the speaker's melody kept (no singing register, no syllable dips), the
   villager timbre from the index, hiss in the voiced parts lowered.
3. The pauses are closed: what the model still made of noise more than --gate dB under the speech goes down by 30 dB.
Uses the conversion of villager_sing.py (same model, same fixed randomness).
"""
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import villager_sing as vs  # noqa: E402  (next to this file)

SR = vs.SR
CLEAN_SNR_DB = 45.0   # at least this far between speech and noise: a clean recording, no noise filter


def log(text: str) -> None:
    print(text, flush=True)


def noise_profile(y: np.ndarray, n_fft: int = 2048, hop: int = 512) -> tuple[np.ndarray, np.ndarray, float, float]:
    """STFT, the noise per frequency (median of the quietest 15 % of frames) and the noise floor and speech level in dB."""
    import librosa
    spec = librosa.stft(y, n_fft=n_fft, hop_length=hop)
    mag = np.abs(spec)
    frame_db = 20 * np.log10(np.sqrt(np.mean(mag ** 2, axis=0)) + 1e-9)
    quiet = frame_db <= np.percentile(frame_db, 15)
    if quiet.sum() < 4:
        quiet = np.argsort(frame_db)[:4]
    return spec, np.median(mag[:, quiet], axis=1), float(np.percentile(frame_db, 15)), float(np.percentile(frame_db, 90))


def denoise(y: np.ndarray, strength: float = 1.0) -> tuple[np.ndarray, str]:
    """Detects the noise and filters it out (spectral gating against the measured profile). @return filtered signal, what was found"""
    import librosa
    from scipy.ndimage import uniform_filter
    spec, noise, floor_db, speech_db = noise_profile(y)
    snr = speech_db - floor_db
    found = f"speech {snr:.0f} dB above the noise"
    if strength <= 0 or snr >= CLEAN_SNR_DB:
        return y, found + " – clean, not filtered"
    mag = np.abs(spec)
    threshold = noise[:, None] * (1.5 + 2.5 * strength)
    mask = np.clip((mag - threshold) / (threshold + 1e-9), 0.0, 1.0)
    mask = uniform_filter(mask, size=(3, 5))                       # smooth over frequency and time: no "musical noise"
    floor = 10 ** (-(10 + 14 * strength) / 20)                     # how far noise-only bins go down (10–24 dB)
    mask = floor + (1 - floor) * mask
    out = librosa.istft(spec * mask, hop_length=512, length=len(y))
    return out.astype(np.float32), found + f" – filtered (strength {strength:.1f})"


def speech_band(y: np.ndarray, low: float = 90.0, high: float = 7000.0) -> np.ndarray:
    from scipy.signal import butter, sosfiltfilt
    sos = butter(4, [low, high], btype="bandpass", fs=SR, output="sos")
    return sosfiltfilt(sos, y).astype(np.float32)


def has_demucs() -> bool:
    import importlib.util
    return importlib.util.find_spec("demucs") is not None


def separate(y: np.ndarray, work: Path) -> np.ndarray | None:
    """The voice without background via Demucs (if installed); None if not available."""
    import soundfile as sf
    src = work / "_input.wav"
    sf.write(str(src), y, SR)
    result = subprocess.run([sys.executable, "-m", "demucs", "--two-stems", "vocals", "-n", "htdemucs", "-d", "cpu", "-o",
                             str(work / "_sep"), str(src)], capture_output=True, text=True)
    vocals = work / "_sep" / "htdemucs" / "_input" / "vocals.wav"
    if result.returncode != 0 or not vocals.exists():
        log("Demucs failed – continuing without separation")
        return None
    return vs.load(vocals)[: len(y)]


def gate(y: np.ndarray, below_db: float = 24.0, depth_db: float = 30.0) -> np.ndarray:
    """10 ms frames more than `below_db` under the speech (90th percentile) go down by `depth_db`; opens fast, closes over ~150 ms."""
    if below_db <= 0:
        return y
    hop = SR // 100
    frames = len(y) // hop
    if frames < 2:
        return y
    db = 20 * np.log10(np.sqrt(np.mean(y[: frames * hop].reshape(frames, hop) ** 2, axis=1)) + 1e-9)
    target = np.where(db > np.percentile(db, 90) - below_db, 1.0, 10 ** (-depth_db / 20))
    gain, g = np.empty(frames), float(target[0])
    for i, t in enumerate(target):
        g += (t - g) * (0.6 if t > g else 0.065)
        gain[i] = g
    curve = np.interp(np.arange(len(y)), np.arange(frames) * hop + hop / 2, gain)
    return (y * curve).astype(np.float32)


def speak(x: np.ndarray, work: Path, args: argparse.Namespace) -> np.ndarray:
    import soundfile as sf
    if args.separate != "off" and has_demucs():
        log("@@step Separating the voice")
        voice = separate(x, work)
        if voice is not None:
            x = voice
    elif args.separate == "on":
        raise SystemExit("--separate on needs Demucs (pip install demucs)")
    log("@@step Filtering noise")
    x, found = denoise(x, args.denoise)
    log("Noise: " + found)
    x = speech_band(x)
    src = work / "_speech.wav"
    sf.write(str(src), x, SR)
    pitch = vs.auto_pitch(x) if args.pitch == "auto" else int(args.pitch)
    raw = work / "_villager.wav"
    log("@@step Villager is speaking")
    vs.convert(src, raw, args.model, pitch, args.f0_method, args.index_rate, args.protect, 0.0)
    y = vs.clean(vs.load(raw), args.clean)
    if not args.no_colour:
        y = vs.colour(y)
    y = np.pad(y, (0, max(0, len(x) - len(y))))[: len(x)]
    y *= float(np.sqrt(np.mean(x ** 2))) / (float(np.sqrt(np.mean(y ** 2))) + 1e-9)
    y = vs.follow(x, y, args.consonants)
    y = gate(y, args.gate)
    return vs.limit(y)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", "--vocals", dest="input", required=True, help="the spoken recording (any format ffmpeg/librosa reads)")
    parser.add_argument("--out", help="WAV to write")
    parser.add_argument("--chunks", help="folder for the pack sections (plugin)")
    parser.add_argument("--id", help="id (with --chunks)")
    parser.add_argument("--model", default="villager")
    parser.add_argument("--pitch", default="auto")
    parser.add_argument("--f0-method", default="rmvpe")
    parser.add_argument("--index-rate", type=float, default=1.0)
    parser.add_argument("--protect", type=float, default=0.5)
    parser.add_argument("--clean", type=float, default=0.6, help="lower the hiss in voiced parts (0 = off … 1)")
    parser.add_argument("--consonants", type=float, default=0.3, help="mix in the consonants of the original (0 = off)")
    parser.add_argument("--no-colour", action="store_true", help="do not match the sound to the villager videos")
    parser.add_argument("--denoise", type=float, default=1.0, help="noise filter strength (0 = off … 1); clean recordings are skipped")
    parser.add_argument("--gate", type=float, default=24.0, help="close pauses this many dB under the speech (0 = off)")
    parser.add_argument("--separate", choices=("auto", "on", "off"), default="auto", help="separate the voice with Demucs if installed")
    args = parser.parse_args()
    if not args.out and not (args.chunks and args.id):
        raise SystemExit("--out, or --chunks and --id")
    x = vs.load(Path(args.input))
    if args.chunks:
        folder = Path(args.chunks).resolve()
        work = folder.with_name(folder.name + ".part")
        work.mkdir(parents=True, exist_ok=True)
        y = speak(x, work, args)
        log("@@step Writing sections")
        meta = vs.write_chunks(y, folder, args.id, args.model)
        log(f"@@done {args.id} {meta['count']} sections, {meta['lengthMs'] / 1000:.1f} s")
        return
    import soundfile as sf
    with tempfile.TemporaryDirectory(prefix="villager-speak-") as tmp:
        y = speak(x, Path(tmp), args)
    out = Path(args.out).resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(out), y, SR)
    log(f"done: {out} ({len(y) / SR:.1f} s)")


if __name__ == "__main__":
    main()
