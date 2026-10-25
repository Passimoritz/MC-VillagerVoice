"""A villager sings a song: vocal track → villager voice model (RVC, CPU only) → mixed with the original backing.

  python villager_sing.py --cache <NoteAPI cache folder> --out folder/ --title "Genre - Title" [--model villager] [--pitch auto|0|-12]
  python villager_sing.py --vocals vocals.wav --backing backing.wav --out folder/ --title "…"
  python villager_sing.py --vocals vocals.wav --chunks folder/ --id <piece-id>      for the plugin: villager singing as pack sections

--chunks (called by the plugin): converts only the vocals and stores them as short OGG sections with crossfades (<id>/<n>.ogg), cut at
quiet moments about every 4 s, plus voice.json (section starts, overlap, count, duration, model). The sections play one after the other and together make the singing
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
# cost of an octave change inside singing (_octaves): high enough that the comfort zone alone never moves a line in its middle
# (with 60: BTS 4, Döner 5 octave jumps between neighbouring notes; with 400: 0 and 1) – only the hard limits do
OCTAVE_SWITCH = 400.0


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


def convert(vocals: Path, out: Path, model: str, pitch: int, f0_method: str = "fcpe", index_rate: float = 1.0, protect: float = 0.5,
            noise: float = 0.0, shape: dict | None = None) -> None:
    pth, index = model_files(model)
    # through rvc_infer.py: fixed randomness, noise always from the CPU – the same result on every machine, CPU or GPU
    starter = Path(__file__).resolve().parent / "rvc_infer.py"
    cmd = [str(PYTHON), str(starter), str(APPLIO), "infer", "--input-path", str(vocals), "--output-path", str(out), "--pth-path", str(pth),
           "--pitch", str(pitch), "--f0-method", f0_method, "--index-rate", str(index_rate if index else 0), "--protect", str(protect),
           "--volume-envelope", "1.0", "--export-format", "WAV"]
    if index:
        cmd += ["--index-path", str(index)]
    log(f"  model {pth.name}" + (f" + {index.name}" if index else "") + f", pitch {pitch:+d}")
    # the model's latent noise (RVC samples it at 0.67): 0 = the clean, steady voice – with it the villager sounds breathy and tired
    env = dict(os.environ, VOICEAPI_PRIOR_NOISE=str(noise))
    # the pitch curve shaped inside the conversion (shape_f0: smoother, in the villager's register per phrase)
    env["VOICEAPI_F0"] = json.dumps(shape) if shape else ""
    result = subprocess.run(cmd, cwd=str(APPLIO), capture_output=True, text=True, encoding="utf-8", errors="replace", env=env)
    if result.returncode != 0 or not out.exists():
        raise SystemExit("Conversion failed:\n" + result.stdout[-1500:] + result.stderr[-1500:])


def prepare(y: np.ndarray, strength: float = 1.0, highpass_hz: float = 80.0, floor_over: float = 2.0, kick_hz: float = 1500.0,
            gate_db: float = -35.0, gate_floor_db: float = -40.0) -> np.ndarray:
    """Cleans the separated vocal track before the conversion. Separated vocals carry bleed of the backing (HTDemucs: kick, bass, a steady
    synth tone), and the voice model turns everything it is given into villager voice. Here
    1. a high-pass at `highpass_hz` (kick and bass below the voice),
    2. the steady floor per frequency (20th percentile over time × `floor_over`: drones, hiss) is subtracted,
    3. the percussive part below `kick_hz` (kick, plucked bass) is taken out – above it stay the consonants,
    4. frames without voice (harmonic energy in 150–4000 Hz after steps 1–3 more than `gate_db` below the loud parts) go down by
       `gate_floor_db`.
    The result is the input of the conversion and the reference of follow(). `strength` 0 switches it off, 1 = fully."""
    if strength <= 0:
        return y
    import librosa
    S = librosa.stft(y, n_fft=2048, hop_length=512)
    freqs = librosa.fft_frequencies(sr=SR, n_fft=2048)
    mag = np.abs(S)
    highpass = 1 / np.sqrt(1 + (highpass_hz / np.maximum(freqs, 1.0)) ** 4)
    floor = np.percentile(mag, 20, axis=1, keepdims=True) * floor_over
    keep = np.clip((mag - floor) / (mag + 1e-9), 0, 1)
    H, P = librosa.decompose.hpss(S, kernel_size=(17, 17), margin=1.0)
    percussive = np.abs(P) / (np.abs(H) + np.abs(P) + 1e-9)
    keep = keep * np.where(freqs[:, None] < kick_hz, 1 - percussive, 1.0) * highpass[:, None]
    band = (freqs >= 150) & (freqs < 4000)
    # measured after the floor is gone – otherwise a steady synth tone keeps the gate open
    level = 10 * np.log10((np.abs(H[band] * keep[band]) ** 2).sum(axis=0) + 1e-12)
    voiced = np.clip((level - (np.percentile(level, 95) + gate_db)) / 6.0, 0, 1)   # 6 dB soft knee
    voiced = np.convolve(voiced, np.ones(5) / 5, mode="same")
    gate = 10 ** (gate_floor_db * (1 - voiced) / 20)
    gain = 1 - strength * (1 - keep * gate[None, :])
    return librosa.istft(S * gain, hop_length=512, length=len(y)).astype(np.float32)


def _runs(mask: np.ndarray) -> list[tuple[int, int]]:
    """Start/end (exclusive) of every run of True."""
    d = np.diff(np.r_[0, mask.astype(np.int8), 0])
    return list(zip(np.flatnonzero(d == 1), np.flatnonzero(d == -1)))


def _octaves(lf: np.ndarray, lo: float, hi: float, pause: int, comfort: tuple[float, float] = (90.0, 150.0)) -> np.ndarray:
    """The octave shift per frame (whole octaves, log2) – chosen for the whole song at once (Viterbi): every semitone a note lies outside
    the `comfort` zone (Hz; the voice sounds best there: harmonic-to-noise +14 … +19 dB, at 60 Hz +11 – deeper it turns into a buzzing
    fry) costs a little, outside the hard limits `lo` … `hi` much; changing the octave costs much inside singing and almost nothing in a
    pause of at least `pause` frames. So a melody keeps its octave, the next singer gets his own in the pause before him, and a high
    voice (BTS ~330 Hz) is not pushed two octaves down into the fry: notes below 80 Hz 16 → 6 %. An octave change inside singing costs
    OCTAVE_SWITCH – the small comfort costs never add up to it, only notes beyond the hard limits do."""
    shifts = np.arange(-4, 5, dtype=np.float64)
    voiced = ~np.isnan(lf)
    v = np.where(voiced, lf, 0.0)[:, None] + shifts[None, :]
    c_lo, c_hi = np.log2(comfort[0]), np.log2(comfort[1])
    cost = 0.3 * 12 * (np.maximum(c_lo - v, 0.0) + np.maximum(v - c_hi, 0.0)) + np.where(v > hi, 10.0, 0.0) + np.where(v < lo, 10.0, 0.0)
    cost[~voiced] = 0.0
    # frames inside a pause of at least `pause` frames: changing the octave there is cheap
    in_pause = np.zeros(len(lf), dtype=bool)
    for a, b in _runs(~voiced):
        if b - a >= pause or a == 0 or b == len(lf):
            in_pause[a:b] = True
    change = np.where(in_pause, 0.5, OCTAVE_SWITCH)
    n, m = cost.shape
    total = cost[0].copy()
    back = np.zeros((n, m), dtype=np.int8)
    for t in range(1, n):
        best = int(np.argmin(total))
        switch = total[best] + change[t]
        stay = total
        back[t] = np.where(stay <= switch, np.arange(m), best)
        total = np.minimum(stay, switch) + cost[t]
    path = np.zeros(n, dtype=np.int64)
    path[-1] = int(np.argmin(total))
    for t in range(n - 1, 0, -1):
        path[t - 1] = back[t, path[t]]
    return shifts[path]


def shape_f0(f0: np.ndarray, frame_s: float = 0.01, low: float = 60.0, high: float = 178.0, fill_ms: float = 150.0,
             smooth_ms: float = 50.0, tune: float = 0.0, phrase_gap_ms: float = 300.0,
             comfort: tuple[float, float] = (90.0, 150.0)) -> np.ndarray:
    """Shapes the pitch curve the voice model sings (Hz per frame, 0 = no pitch) – called inside the conversion (rvc_infer.py):
    1. octave errors of the pitch tracker (runs up to 60 ms an octave off) are folded back – longer ones are real leaps and stay,
    2. gaps without pitch inside a phrase up to `fill_ms` are bridged – there the model would breathe noise (rap: half of the frames),
    3. jitter is removed by a median over `smooth_ms` (note steps stay steps – an average blurred them into slides: 5–7 % wrong notes)
       and the curve is pulled towards the nearest semitone by `tune` (0 … 1),
    4. the curve is moved by whole octaves into the range where the villager voice sounds good, `low` … `high` Hz – hard limits,
       measured with clean tones through the model: below 60 Hz it gets thin, quiet and noisy (harmonic-to-noise 60 Hz +11 dB, 55 Hz +7,
       45 Hz no voice at all), above 178 Hz it rattles (subharmonic 178 Hz −38 dB, 180 Hz −16 dB, 182 Hz −8 dB below the note). The
       octave is chosen for the whole song at once (_octaves(): as few octave changes as possible, and those in pauses ≥
       `phrase_gap_ms`, as much as possible in the `comfort` zone – the villager's own sounds lie at 95–117 Hz), so a deep rapper and a
       high singer both sing in the villager's voice, the notes stay right and one singer does not jump octaves. Whatever is still
       outside (a line wider than the range) is folded in by octaves.
    low ≤ 0 switches step 4 off."""
    f0 = np.asarray(f0, dtype=np.float64)
    voiced = f0 > 0
    if voiced.sum() < 3:
        return f0.copy()
    from scipy.ndimage import median_filter
    lf = np.where(voiced, np.log2(np.maximum(f0, 1e-3)), np.nan)
    short = max(1, int(round(0.06 / frame_s)))
    for a, b in _runs(voiced):
        if b - a >= 5:
            med = median_filter(lf[a:b], size=min(31, (b - a) | 1), mode="nearest")
            off = np.round(lf[a:b] - med)
            for c, e in _runs(off != 0):
                if e - c <= short:
                    lf[a + c:a + e] -= off[c:e]
    gap = max(1, int(round(phrase_gap_ms / 1000 / frame_s)))
    fill = int(round(fill_ms / 1000 / frame_s))
    for a, b in _runs(~voiced):
        if 0 < a and b < len(f0) and b - a <= fill:
            lf[a:b] = np.interp(np.arange(a, b), [a - 1, b], [lf[a - 1], lf[b]])
    sung = ~np.isnan(lf)
    k = int(round(smooth_ms / 1000 / frame_s))
    if k > 1:
        for a, b in _runs(sung):
            if b - a >= 3:
                lf[a:b] = median_filter(lf[a:b], size=min(k | 1, (b - a) | 1), mode="nearest")
    if tune > 0:
        midi = 69 + 12 * (lf - np.log2(440.0))
        lf = np.log2(440.0) + (midi + tune * (np.round(midi) - midi) - 69) / 12
    if low > 0:
        lo, hi = np.log2(low), np.log2(high)
        lf = lf + _octaves(lf, lo, hi, gap, tuple(comfort))
        # hard limits: whatever is still outside (a line wider than the range) is folded in by octaves
        lf = np.where(lf > hi, lf - np.ceil(lf - hi), lf)
        lf = np.where(lf < lo, lf + np.ceil(lo - lf), lf)
    return np.where(np.isnan(lf), 0.0, 2 ** np.nan_to_num(lf)).astype(np.float64)


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


def clean(y: np.ndarray, strength: float = 0.6) -> np.ndarray:
    """Takes the hiss out of the sung parts: the model's output is noticeably breathier than the singer and the villager videos (harmonic-
    to-noise about 5 dB lower), which sounds tired and robotic. Where a frame is mainly tonal (sung), the noisy part (HPSS percussive/
    residual) is lowered by `strength`; consonants and breaths (mainly noise) stay untouched. 0 switches it off."""
    if strength <= 0:
        return y
    import librosa
    S = librosa.stft(y, n_fft=2048, hop_length=512)
    H, P = librosa.decompose.hpss(S, kernel_size=(17, 17), margin=1.0)
    eh, ep = (np.abs(H) ** 2).sum(axis=0), (np.abs(P) ** 2).sum(axis=0)
    tonal = np.clip((10 * np.log10((eh + 1e-12) / (ep + 1e-12)) + 3) / 6, 0, 1)  # 0 below −3 dB, 1 above +3 dB
    tonal = np.convolve(tonal, np.ones(3) / 3, mode="same")
    keep = 1 - strength * tonal
    out = librosa.istft(H + P * keep[None, :], hop_length=512, length=len(y))
    return (out * (np.sqrt(np.mean(y ** 2)) / (np.sqrt(np.mean(out ** 2)) + 1e-9))).astype(np.float32)


def articulate(y: np.ndarray, strength: float = 2.0, fast_ms: float = 15.0, slow_ms: float = 180.0, floor: float = 0.25) -> np.ndarray:
    """Sings syllable by syllable like the villager videos: the dips between syllables are deepened (fast level / slow level, to the power
    of `strength`, never below `floor`). Measured: the videos set clearly more note ends than the original singer ("Thick of It": 39 vs.
    10), the plain conversion only 14 – it sounded bound together and tired. With strength 2 the note ends match the videos (40/39, 27/28)
    and the distance to the videos drops 46.6 → 41.5. 0 switches it off."""
    if strength <= 0:
        return y
    ef, es = _envelope(y, fast_ms), _envelope(y, slow_ms)
    gain = np.clip((ef / (es + 1e-9)) ** strength, floor, 1.0)
    k = np.hanning(2 * int(0.004 * SR) + 1)
    out = y * _smooth(gain, k)
    return (out * (np.sqrt(np.mean(y ** 2)) / (np.sqrt(np.mean(out ** 2)) + 1e-9))).astype(np.float32)


def follow_timbre(vocals: np.ndarray, y: np.ndarray, strength: float = 0.0, max_db: float = 6.0, window_ms: float = 400.0,
                  smooth_ms: float = 40.0) -> np.ndarray:
    """Optional (off by default): carries the singer's timbre movement over to the villager. The villager voice keeps a steadier timbre than
    a singer (its sounds move less: MFCC-delta 2.5 vs. 2.9–3.2 in the videos). Per frame, the singer's spectral shape (40 mel bands) minus
    its own average over `window_ms` – the vowel and brightness changes – is put onto the villager with `strength` (0 = off, 1 = fully),
    smoothed over `smooth_ms` and limited to ±`max_db` per band; only where the singer sings. More strength = livelier and easier to
    understand, but a little less "villager"."""
    if strength <= 0:
        return y
    import librosa
    from scipy.ndimage import uniform_filter1d
    n = min(len(vocals), len(y))
    hop, n_fft = 512, 2048
    So = np.abs(librosa.stft(vocals[:n], n_fft=n_fft, hop_length=hop)) ** 2
    Sy = librosa.stft(y[:n], n_fft=n_fft, hop_length=hop)
    mel = librosa.filters.mel(sr=SR, n_fft=n_fft, n_mels=40, fmin=80, fmax=10000)
    lo = 10 * np.log10(mel @ So + 1e-10)
    shape = lo - lo.mean(axis=0, keepdims=True)                      # spectral shape only, loudness stays with follow()
    frames = max(1, int(window_ms / 1000 * SR / hop))
    move = shape - uniform_filter1d(shape, size=frames, axis=1, mode="nearest")
    move = uniform_filter1d(move, size=max(1, int(smooth_ms / 1000 * SR / hop)), axis=1, mode="nearest")
    level = lo.max(axis=0)
    sung = (level > np.percentile(level, 95) - 30).astype(float)
    gain_db = np.clip(strength * move, -max_db, max_db) * sung[None, :]
    # mel bands back to FFT bins (each bin takes the weighted mean of the bands covering it)
    weights = mel / (mel.sum(axis=0, keepdims=True) + 1e-9)
    gain = 10 ** ((weights.T @ gain_db) / 20)
    out = librosa.istft(Sy * gain[:, : Sy.shape[1]], hop_length=hop, length=n)
    out = np.concatenate([out, y[n:]])
    return (out * (np.sqrt(np.mean(y ** 2)) / (np.sqrt(np.mean(out ** 2)) + 1e-9))).astype(np.float32)


def _smooth(x: np.ndarray, kernel: np.ndarray) -> np.ndarray:
    """x smoothed with the kernel (normalised to a sum of 1), same length – by FFT: a direct convolution of a song with a 0.1–0.4 s window
    takes minutes."""
    from scipy.signal import oaconvolve
    return oaconvolve(x, kernel / kernel.sum(), mode="same")


def _envelope(y: np.ndarray, win_ms: float) -> np.ndarray:
    n = max(1, int(SR * win_ms / 1000))
    k = np.hanning(2 * n + 1)
    return np.sqrt(np.maximum(_smooth(y.astype(np.float64) ** 2, k), 0) + 1e-12)


def follow(vocals: np.ndarray, y: np.ndarray, consonants: float = 0.5, win_ms: float = 25.0, clamp_db: float = 18.0,
           gate_db: float = -40.0, floor_db: float = -60.0) -> np.ndarray:
    """Puts the villager singing onto the performance of the original (`vocals`: best the cleaned track from prepare()):
    1. Offset: the model starts a few milliseconds early/late – shifted sample-exactly onto the onsets of the original (±50 ms).
    2. Level curve: volume every 25 ms like the original (at most ±18 dB, smoothed) – accents and syllable starts sit where the singer has
       them.
    3. Gate: where the original is more than `gate_db` below its loud parts, the villager goes down to `floor_db` (6 dB soft knee). The
       model sings at its own volume even on near silence (a rap intro at −96 dB came out at −19 dB as a droning buzz); the old limit of
       −18 dB left that audible as noise in every gap.
    4. Consonants: the sharp, voiceless parts of the original (percussive, above 2 kHz) mixed in quietly, only where the gate is open –
       the model slurs them; without them the text is harder to understand and the onsets blur. 0 switches it off.
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
    level = 20 * np.log10(eo / np.percentile(eo, 99))
    k = np.hanning(2 * int(SR * 0.02) + 1)
    opened = _smooth(np.clip((level - gate_db) / 6.0, 0, 1), k)
    gain *= 10 ** (floor_db * (1 - opened) / 20)
    k = np.hanning(2 * int(SR * 0.006) + 1)
    out = y * _smooth(gain, k)
    if consonants > 0:
        S = librosa.stft(vocals, n_fft=2048, hop_length=512)
        _, P = librosa.decompose.hpss(S, kernel_size=(17, 17), margin=2.0)
        P[librosa.fft_frequencies(sr=SR, n_fft=2048) < 2000] = 0
        out = out + consonants * librosa.istft(P, hop_length=512, length=n) * opened
    out *= float(np.sqrt(np.mean(vocals ** 2))) / (float(np.sqrt(np.mean(out ** 2))) + 1e-9)
    log(f"offset corrected: {lag / SR * 1000:+.1f} ms")
    return out.astype(np.float32)


def even(y: np.ndarray, strength: float = 0.5, window_ms: float = 300.0, max_boost_db: float = 12.0, range_db: float = 35.0) -> np.ndarray:
    """Evens out the level of the singing: every passage is pulled towards the level of the loud parts by `strength` (0 = off, 1 = all equally
    loud), slowly (`window_ms`) so syllables keep their accents, at most `max_boost_db` up. follow() copies the singer's dynamics, and
    quiet lines (ad-libs, a softer verse) then vanished behind the note blocks. Pauses (more than `range_db` below the loud parts) stay
    silent – nothing is raised there."""
    if strength <= 0:
        return y
    env = 20 * np.log10(_envelope(y, window_ms) + 1e-9)
    loud = np.percentile(env, 95)
    weight = np.clip((env - (loud - range_db)) / 10.0, 0, 1)
    gain_db = np.clip((loud - 3.0 - env) * strength, 0.0, max_boost_db) * weight
    k = np.hanning(2 * int(0.05 * SR) + 1)
    return (y * 10 ** (_smooth(gain_db, k) / 20)).astype(np.float32)


def sung_level(y: np.ndarray) -> float:
    """Level of the sung parts in dBFS: mean power of the 50 ms windows within 20 dB of the loud parts (95th percentile) – pauses do not
    count, so a song with many gaps is not judged quieter than it sounds."""
    win = int(0.05 * SR)
    frames = y[: len(y) // win * win].astype(np.float64).reshape(-1, win)
    power = (frames ** 2).mean(axis=1) + 1e-12
    db = 10 * np.log10(power)
    sung = db > np.percentile(db, 95) - 20
    return float(10 * np.log10(power[sung].mean())) if sung.any() else -120.0


def level_up(y: np.ndarray, min_db: float = -18.0) -> np.ndarray:
    """Raises quiet singing to at least `min_db` (sung_level), never lowers it. The villager follows the level of the separated vocals,
    and those are quiet in many mixes (rap: −23 dB) – in Minecraft a sound cannot play louder than its file (volume ≤ 1), so next to the
    note blocks such a villager was hardly audible. Peaks are caught by limit() afterwards. 0 or above switches it off."""
    if min_db >= 0:
        return y
    lift = min_db - sung_level(y)
    return (y * 10 ** (max(0.0, lift) / 20)).astype(np.float32)


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
    smooth = _smooth(gain, k)
    gain = np.minimum(gain, smooth)
    return np.clip(y * gain, -ceiling, ceiling).astype(np.float32)


def boundaries(y: np.ndarray, reach_ms: int = 1000) -> list[int]:
    """Where to cut the singing into sections (sample positions, first 0): about every CHUNK_MS, but at the quietest moment within
    ±reach_ms – between syllables or in a breath. Minecraft starts streamed sounds with a slightly varying delay; at a quiet cut such a
    delay cannot be heard, inside a sung note it would double the note for a moment."""
    chunk = int(CHUNK_MS / 1000 * SR)
    reach = int(reach_ms / 1000 * SR)
    hop = int(0.01 * SR)
    win = int(0.05 * SR)
    energy = _smooth(y.astype(np.float64) ** 2, np.ones(win))[::hop]
    cuts = [0]
    while cuts[-1] + chunk + reach < len(y):
        nominal = cuts[-1] + chunk
        a, b = (nominal - reach) // hop, (nominal + reach) // hop
        best = a + int(np.argmin(energy[a:b]))
        cuts.append(max(cuts[-1] + chunk // 2, best * hop))
    return cuts


def write_chunks(y: np.ndarray, folder: Path, ident: str, model: str) -> dict:
    """Cuts the singing into crossfaded sections at quiet moments and writes them as OGG (Vorbis): section n covers starts[n] − OVERLAP_MS
    … starts[n+1] + OVERLAP_MS. Everything is written into <folder>.part first and swapped in at the end – an interrupted conversion never
    leaves a half-written song behind."""
    import shutil
    import soundfile as sf
    part_dir = folder.with_name(folder.name + ".part")
    part_dir.mkdir(parents=True, exist_ok=True)
    for old in part_dir.glob("*.ogg"):
        old.unlink()
    over = int(OVERLAP_MS / 1000 * SR)
    cuts = boundaries(y)
    count = len(cuts)
    silent = []
    for n in range(count):
        a = max(0, cuts[n] - over)
        b = min(len(y), (cuts[n + 1] if n + 1 < count else len(y)) + over)
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
        sf.write(str(part_dir / f"{n}.ogg"), part.astype(np.float32), SR, format="OGG", subtype="VORBIS")
    meta = {"id": ident, "chunkMs": CHUNK_MS, "overlapMs": OVERLAP_MS, "count": count, "silent": silent,
            "starts": [int(round(c / SR * 1000)) for c in cuts], "lengthMs": int(len(y) / SR * 1000), "model": model, "sampleRate": SR}
    (part_dir / "voice.json").write_text(json.dumps(meta, indent=1), encoding="utf-8")
    for temp in part_dir.glob("_*.wav"):
        temp.unlink()
    # swap in: the old song (if any) only disappears once the new one is complete
    old_dir = folder.with_name(folder.name + ".old")
    if old_dir.exists():
        shutil.rmtree(old_dir)
    if folder.exists():
        folder.rename(old_dir)
    part_dir.rename(folder)
    if old_dir.exists():
        shutil.rmtree(old_dir, ignore_errors=True)
    return meta


def sing(vocals: np.ndarray, work: Path, args: argparse.Namespace) -> np.ndarray:
    """The villager sings the vocal track (both for the plugin and for listening samples), before the final limiter:
    - the model gets the vocal track as it is – cleaned (prepare()) it lost syllables (2–3 % of the sung passages went silent); the
      cleaned track only steers level, gate and consonants (follow()), so the bleed stays out of the pauses,
    - the pitch curve is shaped inside the conversion (shape_f0(): bridged, smoothed, every phrase in the villager's register),
    - then hiss down, villager sound, level curve, even level, syllables, minimum level."""
    import soundfile as sf
    # only 12 dB off where prepare() hears no voice: quiet lines and ad-libs (14 dB below the singing in "Döner von Ibo") stay audible;
    # what is really silent (an intro with bleed only) the gate in follow() closes anyway
    cleaned = prepare(vocals, args.prepare, gate_floor_db=-12.0)
    src, raw = work / "_vocals.wav", work / "_villager.wav"
    sf.write(str(src), vocals, SR)
    shape = None
    if args.register != "off":
        low, high = (float(v) for v in args.register.split(","))
        shape = {"low": low, "high": high, "fill_ms": args.pitch_fill_ms, "smooth_ms": args.pitch_smooth_ms, "tune": args.tune}
        pitch = 0 if args.pitch == "auto" else int(args.pitch)
    else:
        pitch = auto_pitch(cleaned) if args.pitch == "auto" else int(args.pitch)
    convert(src, raw, args.model, pitch, args.f0_method, args.index_rate, args.protect, args.noise, shape)
    y = clean(load(raw), args.clean)
    if not args.no_colour:
        y = colour(y)
    n = len(vocals)
    y = np.pad(y, (0, max(0, n - len(y))))[:n]
    y *= float(np.sqrt(np.mean(cleaned ** 2))) / (float(np.sqrt(np.mean(y ** 2))) + 1e-9)
    if not args.no_follow:
        y = follow(cleaned, y, args.consonants)
    y = follow_timbre(cleaned, y, args.timbre_follow, args.timbre_max_db, args.timbre_window_ms, args.timbre_smooth_ms)
    y = level_up(articulate(even(y, args.even), args.articulation), args.min_level)
    src.unlink(missing_ok=True)
    raw.unlink(missing_ok=True)
    return y


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
    parser.add_argument("--f0-method", default="rmvpe", help="rmvpe (default: robust on separated vocals with bleed) or fcpe (faster)")
    parser.add_argument("--no-colour", action="store_true", help="do not match the sound to the villager videos")
    parser.add_argument("--prepare", type=float, default=1.0, help="how strongly the reference for level and gate is cleaned of bleed (0 … 1)")
    parser.add_argument("--register", default="60,178", help="lowest,highest pitch in Hz the villager sings (hard limits, moved by octaves; 'off' = --pitch)")
    parser.add_argument("--pitch-fill-ms", type=float, default=150.0, help="bridge gaps without pitch inside a phrase up to this length")
    parser.add_argument("--pitch-smooth-ms", type=float, default=50.0, help="remove pitch jitter (median over this time, ms)")
    parser.add_argument("--tune", type=float, default=0.0, help="pull the pitch towards the nearest semitone (0 … 1)")
    parser.add_argument("--even", type=float, default=0.0, help="even out the level of quiet passages (0 = off … 1)")
    parser.add_argument("--min-level", type=float, default=-18.0, help="raise quiet singing to at least this level (dBFS, 0 = off)")
    parser.add_argument("--index-rate", type=float, default=0.5, help="how much of the villager's own timbre (index) to use, 0 … 1")
    parser.add_argument("--protect", type=float, default=0.5, help="how much consonants/breaths stay untouched, 0 … 0.5")
    parser.add_argument("--noise", type=float, default=0.0, help="the model's latent noise (1 = RVC's own; 0 = clean, steady voice)")
    parser.add_argument("--clean", type=float, default=0.6, help="lower the hiss in sung parts (0 = off … 1)")
    parser.add_argument("--consonants", type=float, default=0.5, help="mix in the consonants of the original (0 = off)")
    parser.add_argument("--no-follow", action="store_true", help="do not align offset, level curve and consonants to the original")
    parser.add_argument("--vocal-gain", type=float, default=0.0, help="villager level in the mix relative to the original vocals (dB)")
    parser.add_argument("--articulation", type=float, default=0.0, help="sing syllable by syllable like the videos (0 = bound together)")
    parser.add_argument("--timbre-follow", type=float, default=0.0, help="carry the singer's timbre movement over (0 = off … 1 = fully)")
    parser.add_argument("--timbre-max-db", type=float, default=6.0, help="limit of the timbre movement per band (dB)")
    parser.add_argument("--timbre-window-ms", type=float, default=400.0, help="what counts as movement: changes against this average (ms)")
    parser.add_argument("--timbre-smooth-ms", type=float, default=40.0, help="smoothing of the timbre movement (ms)")
    parser.add_argument("--label", default="Villager")
    args = parser.parse_args()
    import soundfile as sf
    if args.chunks:
        if not args.vocals or not args.id:
            raise SystemExit("--chunks needs --vocals and --id")
        folder = Path(args.chunks).resolve()
        work = folder.with_name(folder.name + ".part")
        work.mkdir(parents=True, exist_ok=True)
        vocals = load(Path(args.vocals), args.start, args.end)
        log("@@step Villager is singing")
        y = limit(sing(vocals, work, args))
        log("@@step Writing sections")
        meta = write_chunks(y, folder, args.id, args.model)
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
    sf.write(str(out / f"{title} - 1 Original - vocals only.wav"), vocals, SR)
    sf.write(str(out / f"{title} - 1 Original - with backing.wav"), limit(vocals + backing), SR)
    y = sing(vocals, out, args)
    sf.write(str(out / f"{title} - 2 {args.label} - vocals only.wav"), limit(y), SR)
    # the videos keep the original instrumental untouched and the villager about as loud as the original singer (measured: −0.6 dB)
    sung = y * 10 ** (args.vocal_gain / 20) + backing
    sf.write(str(out / f"{title} - 2 {args.label} - with backing.wav"), limit(sung), SR)
    # comparison: the loudest 12 s of the vocals, original first, then villager (with backing)
    win = 12 * SR
    if n > win:
        # 1 s moving sum via cumulative sum (a direct convolution with a 1 s window took minutes for a 60 s song)
        c = np.concatenate([[0.0], np.cumsum(vocals.astype(np.float64) ** 2)])
        energy = ((c[SR:] - c[:-SR]) / SR)[::SR // 4]
        best = int(np.argmax([energy[i: i + 48].sum() for i in range(max(1, len(energy) - 48))])) * (SR // 4)
    else:
        best = 0
    a = limit(vocals + backing)[best: best + win]
    b = limit(sung)[best: best + win]
    sf.write(str(out / f"{title} - Comparison (original first, then {args.label}).wav"), np.concatenate([a, np.zeros(SR // 2, dtype=np.float32), b]), SR)
    log(f"done: {out}")


if __name__ == "__main__":
    main()
