"""Villager-Stimme: wandelt eine Gesangs- oder Sprachaufnahme in die Stimme des Dorfbewohners – Text, Tonhoehe, Tempo und Ausdruck bleiben.

  python villager_voice.py gesang.wav --out villager.wav [--shift auto|-12|0] [--character 0.6] [--hum villager]

So geht's (Vocoder/Talkbox): Das Dorfbewohner-„Hmm“ (saubere Schleife aus den Original-Aufnahmen) ist der Traeger. Er folgt der Tonhoehen-
kurve der Aufnahme stufenlos (keine Stufen, kein Abhacken) – im Zweifel oktavversetzt in die Lage des Dorfbewohners (--shift auto). Dann wird
dem Traeger das Spektrum der Aufnahme aufgepraegt (Formanten = Vokale, Konsonanten als Rauschen), so bleibt der Text verstaendlich, waehrend
der Klang (Obertonstruktur, Naeseln) vom Dorfbewohner kommt. --character regelt, wie viel Dorfbewohner (1) oder Original (0) im Klang steckt.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

SR = 44100
HOP = 256
N_FFT = 2048


def log(text: str) -> None:
    print(text, flush=True)


def load(path: Path) -> np.ndarray:
    import librosa
    y, _ = librosa.load(str(path), sr=SR, mono=True)
    return y.astype(np.float32)


def hum_loop(speaker: str = "villager", seconds: float = 4.0) -> tuple[np.ndarray, float]:
    """Eine lange, saubere Hmm-Schleife bei der natuerlichen Tonhoehe (Hz) der Stimme."""
    import build_voice_pack as b
    import soundfile as sf
    import librosa
    file, start, end, mid_a, mid_b, midi = b.HUMS[speaker][0]
    data, rate = sf.read(str(b.SOUNDS / f"{file}.ogg"), always_2d=True)
    y = data.mean(axis=1).astype(np.float32)
    if rate != SR:
        y = librosa.resample(y, orig_sr=rate, target_sr=SR)
    whole = b.flatten_pitch(y[int(start * SR): int(end * SR)], midi)
    loop = whole[int((mid_a - start) * SR): int((mid_b - start) * SR)]
    fade = int(0.05 * SR)
    # eine Periode der Schleife, deren Ende auf ihren Anfang ueberblendet ist: beliebig oft hintereinander nahtlos
    period = loop[: len(loop) - fade].copy()
    period[-fade:] = loop[len(loop) - 2 * fade: len(loop) - fade] * np.linspace(1, 0, fade) + loop[:fade] * np.linspace(0, 1, fade)
    out = np.tile(period, int(np.ceil(seconds * SR / len(period))))
    return out / (np.abs(out).max() + 1e-9), b.midi_hz(midi)


def pitch_contour(y: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Tonhoehe (Hz) je Sample und Stimmhaftigkeit je Frame – robust: unsichere Frames halten den letzten Ton, Oktav-/Harmoniefehler werden an
    der laufenden Lage der Phrase gemessen und zurueckgezogen, kurze Zacken per Median entfernt; Luecken halten, nicht gleiten."""
    import librosa
    from scipy.signal import medfilt
    f0, voiced, prob = librosa.pyin(y, fmin=65, fmax=1000, sr=SR, frame_length=N_FFT, hop_length=HOP, resolution=0.05)
    rms = librosa.feature.rms(y=y, frame_length=N_FFT, hop_length=HOP)[0]
    n = min(len(f0), len(rms))
    f0, voiced, prob, rms = f0[:n], np.nan_to_num(voiced[:n]) > 0.5, np.nan_to_num(prob[:n]), rms[:n]
    sure = voiced & (prob > 0.5) & (rms > rms.max() * 0.05) & ~np.isnan(f0)
    ok = voiced & (rms > rms.max() * 0.05) & ~np.isnan(f0)
    midi = 69 + 12 * np.log2(np.where(ok, np.nan_to_num(f0, nan=100.0), 100.0) / 440.0)
    if not ok.any():
        return np.full(n * HOP, 100.0, dtype=np.float32), ok
    # (keine Oktav-Korrektur an einer laufenden Lage: bei grossen Spruengen der Melodie faltet sie richtige Toene falsch)
    # unsichere Frames: den letzten sicheren Ton halten
    last = None
    for k in range(n):
        if ok[k] and sure[k]:
            last = midi[k]
        elif ok[k] and last is not None:
            midi[k] = last
    # Luecken: halten, in der Mitte springen
    idx = np.where(ok)[0]
    filled = midi.copy()
    prev = idx[0]
    filled[:prev] = midi[prev]
    for nxt in idx[1:]:
        if nxt - prev > 1:
            mid = (prev + nxt) // 2
            filled[prev + 1: mid + 1] = midi[prev]
            filled[mid + 1: nxt] = midi[nxt]
        prev = nxt
    filled[prev:] = midi[prev]
    # Zacken weg (5 Frames = 29 ms), Vibrato (5-7 Hz) bleibt
    filled = medfilt(filled, 5)
    per_sample = np.repeat(filled, HOP)[: n * HOP]
    return (440.0 * 2 ** ((per_sample - 69) / 12)).astype(np.float32), ok


def synth_carrier(f0: np.ndarray, harmonics_db: np.ndarray) -> np.ndarray:
    """Ein sauberer Traeger ohne Schleifen-Flattern: Summe von Obertoenen mit der gemessenen Pegelverteilung des Hmm, Phase stetig."""
    phase = 2 * np.pi * np.cumsum(f0) / SR
    out = np.zeros_like(f0, dtype=np.float32)
    for h, db in enumerate(harmonics_db, start=1):
        amp = 10 ** (db / 20)
        if amp < 1e-3:
            continue
        # Obertoene oberhalb 12 kHz weglassen (Aliasing)
        mask = (f0 * h) < 12000
        out += (amp * np.sin(h * phase) * mask).astype(np.float32)
    return out / (np.abs(out).max() + 1e-9)


def follow(loop: np.ndarray, loop_hz: float, f0: np.ndarray) -> np.ndarray:
    """Liest die Schleife mit veraenderlicher Geschwindigkeit, so dass ihre Tonhoehe f0 (je Sample) folgt (Schleife lang genug, kein Rücksprung)."""
    speed = f0 / loop_hz
    read = np.cumsum(speed)
    need = int(read[-1]) + 2
    while len(loop) < need:
        loop = np.concatenate([loop, loop])  # die Schleife ist an ihrem Ende bereits auf ihren Anfang ueberblendet
    return np.interp(read, np.arange(len(loop)), loop).astype(np.float32)


def envelope_spectrum(mag: np.ndarray, bands: int = 40) -> np.ndarray:
    """Die spektrale Huellkurve je Frame: Energie in Mel-Baendern, zurueck auf die Frequenzachse interpoliert (glatt, ohne Obertonkamm)."""
    import librosa
    fb = librosa.filters.mel(sr=SR, n_fft=N_FFT, n_mels=bands, fmin=50, fmax=12000)
    band_energy = fb @ (mag ** 2)
    norm = fb.sum(axis=1, keepdims=True) + 1e-9
    band_level = np.sqrt(band_energy / norm)
    # zurueck auf Bins: gewichtete Summe der Bandpegel
    weights = fb / (fb.sum(axis=0, keepdims=True) + 1e-9)
    return (weights.T @ band_level).astype(np.float32)


# Pegel der Obertoene 1..24 des Dorfbewohner-Hmm (dB, gemessen an der Schleife; ersetzt durch Messung in hum_harmonics(), wenn verfuegbar)
HUM_HARMONICS = None


def hum_harmonics(speaker: str = "villager") -> np.ndarray:
    global HUM_HARMONICS
    if HUM_HARMONICS is None:
        loop, hz = hum_loop(speaker)
        y = loop[: int(2 * SR)]
        spec = np.abs(np.fft.rfft(y * np.hanning(len(y))))
        f = np.fft.rfftfreq(len(y), 1 / SR)
        levels = []
        for h in range(1, 25):
            band = (f > hz * h * 0.94) & (f < hz * h * 1.06)
            levels.append(20 * np.log10(spec[band].max() + 1e-9))
        HUM_HARMONICS = np.array(levels) - max(levels)
    return HUM_HARMONICS


def convert(vocals: np.ndarray, speaker: str = "villager", shift: str | float = "auto", character: float = 0.6, log=log, carrier_kind: str = "synth") -> np.ndarray:
    import librosa
    loop, loop_hz = hum_loop(speaker)
    f0, ok = pitch_contour(vocals)
    n_frames = len(ok)
    voiced_midi = 69 + 12 * np.log2(f0[np.repeat(ok, HOP)[: len(f0)]] / 440.0) if ok.any() else np.array([48.0])
    median = float(np.median(voiced_midi))
    target = 69 + 12 * np.log2(loop_hz / 440.0) + 5.0  # Gesangslage der Videos: ~5 Halbtoene ueber dem Brummen
    if shift == "auto":
        # wie die Videos: der Dorfbewohner singt in der Lage des Originals (gemessen an zwei Referenzen: 54,0 → 54,1, 62,6 → 58,7)
        semitones = 0.0 if median < 66 else -12.0
    else:
        semitones = float(shift)
    log(f"  Tonhoehe: Original-Mitte {median:.1f} (MIDI), Traeger {semitones:+.0f} Halbtoene → {median + semitones:.1f}")
    f0_shifted = f0 * 2 ** (semitones / 12)
    carrier = synth_carrier(f0_shifted, hum_harmonics(speaker)) if carrier_kind == "synth" else follow(loop, loop_hz, f0_shifted)
    carrier = carrier[: len(vocals)]
    carrier = np.pad(carrier, (0, max(0, len(vocals) - len(carrier))))
    # Rauschen fuer Konsonanten (stimmlose Anteile)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(len(vocals)).astype(np.float32) * 0.3

    S_mod = librosa.stft(vocals, n_fft=N_FFT, hop_length=HOP)
    S_car = librosa.stft(carrier, n_fft=N_FFT, hop_length=HOP)
    S_noi = librosa.stft(noise, n_fft=N_FFT, hop_length=HOP)
    frames = min(S_mod.shape[1], S_car.shape[1], S_noi.shape[1])
    S_mod, S_car, S_noi = S_mod[:, :frames], S_car[:, :frames], S_noi[:, :frames]
    mag_mod = np.abs(S_mod)
    env_mod = envelope_spectrum(mag_mod)
    # zeitlich leicht glaetten (kein Flattern), und normieren: nur die Form zaehlt, der Pegel kommt unten je Frame
    env_mod = np.apply_along_axis(lambda r: np.convolve(np.pad(r, 1, mode="edge"), np.ones(3) / 3, mode="valid"), 1, env_mod)
    env_mod = env_mod / (env_mod.mean(axis=0, keepdims=True) + 1e-9) + 1e-6
    env_car = envelope_spectrum(np.abs(S_car))
    env_car = env_car / (env_car.mean(axis=0, keepdims=True) + 1e-9) + 1e-6
    env_noi = envelope_spectrum(np.abs(S_noi)) + 1e-6
    freqs = librosa.fft_frequencies(sr=SR, n_fft=N_FFT)[:, None]
    voiced = np.convolve(np.pad(ok[:frames].astype(float), 2, mode="edge"), np.ones(5) / 5, mode="valid")[None, :]
    level_mod = np.sqrt((mag_mod ** 2).sum(axis=0)) + 1e-9
    gate = (level_mod > level_mod.max() * 10 ** (-45 / 20)).astype(float)[None, :]
    # Wie stark die Original-Huellkurve den Traeger formt: character 1 = Villager-Klang bleibt (Text leidet), 0 = Original-Huellkurve ganz.
    alpha = 1.0 - 0.5 * character          # 0.5 … 1.0
    keep = character                        # wie viel vom eigenen Villager-Spektrum bleibt
    car_shaped = S_car / env_car ** (1 - keep) * env_mod ** alpha
    # Rauschen nur fuer die stimmlosen Anteile (Zischlaute): oberhalb 4 kHz immer etwas, sonst nur in unstimmhaften Frames
    high = np.clip((freqs - 4000) / 3000, 0, 1)
    noi_shaped = S_noi / env_noi * env_mod ** 1.0
    noise_gain = high * 0.25 + (1 - high) * (1 - voiced) * 0.5
    mix = car_shaped * (1 - 0.6 * high) * voiced + noi_shaped * noise_gain
    # Villager-Faerbung, gemessen an den Videos (Villager-Gesang minus Original-Gesang, dB je Band): Grundton-Bereich weg, Mitten vor, oben zu
    eq_hz = np.array([60, 120, 180, 250, 350, 500, 700, 1000, 1400, 2000, 2800, 4000, 5600, 8000, 12000])
    eq_db = np.array([-6, -8, -16, -14, -12, -2, 2, 1, 5, 1, 2, -10, -10, -12, -14])
    colour = 10 ** (np.interp(freqs[:, 0], eq_hz, eq_db) / 20)[:, None]
    mix *= colour
    # Langzeitspektrum an die Videos angleichen (Zielkurve = Mittel der Referenz-Gesangsspuren, dB je Band relativ zum lautesten Band)
    edges = np.array([60, 120, 180, 250, 350, 500, 700, 1000, 1400, 2000, 2800, 4000, 5600, 8000, 11000])
    target = np.array([-15, -13, -19, -15, -12, -2.5, 0, -6, -7, -14, -16, -31, -34, -38])
    loud_frames = level_mod > level_mod.max() * 0.1
    for _ in range(3):
        power = (np.abs(mix[:, loud_frames]) ** 2).mean(axis=1)
        bands = np.array([10 * np.log10(power[(freqs[:, 0] >= a) & (freqs[:, 0] < b)].mean() + 1e-12) for a, b in zip(edges[:-1], edges[1:])])
        bands -= bands.max()
        correction = np.clip(target - bands, -12, 12) * 0.8
        centers = np.sqrt(edges[:-1] * edges[1:])
        mix *= 10 ** (np.interp(freqs[:, 0], centers, correction) / 20)[:, None]
    level_mix = np.sqrt((np.abs(mix) ** 2).sum(axis=0)) + 1e-9
    mix *= (level_mod / level_mix)[None, :] * gate
    out = librosa.istft(mix, hop_length=HOP, length=len(vocals))
    peak = float(np.abs(out).max()) + 1e-9
    if peak > 0.95:
        out *= 0.95 / peak
    return out.astype(np.float32)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("--out", required=True)
    parser.add_argument("--shift", default="auto")
    parser.add_argument("--character", type=float, default=0.6)
    parser.add_argument("--hum", default="villager")
    parser.add_argument("--carrier", default="synth", choices=["synth", "loop"], help="Traeger: synthetisch (sauber) oder die Hmm-Schleife")
    args = parser.parse_args()
    import soundfile as sf
    y = load(Path(args.source))
    out = convert(y, args.hum, args.shift, args.character, carrier_kind=args.carrier)
    sf.write(args.out, out, SR)
    log(f"{args.out}: {len(out) / SR:.1f} s")


if __name__ == "__main__":
    main()
