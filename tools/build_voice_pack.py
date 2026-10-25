"""Builds the VoiceAPI sounds for the NoteAPI resource pack: real vowels and consonants instead of villager sounds, plus the sung hum of
the villager (the other voices were removed in 1.9.0: they sounded too far from the villager videos and made the pack four times as big).

The vowels come from formant synthesis (a voice tone of glottal pulses runs through the resonances of the mouth – like the classic speech
synthesizers), the consonants from shaped noise and short plosive bursts. Every vowel is generated for every root (every STEP semitones
from ROOT_LOW to ROOT_HIGH) so the game has to stretch it by at most two semitones and the formants (= the vowel) stay intact. Higher roots
get slightly higher formants (a smaller speaker). The hum is the real vanilla recording of the villager, pulled onto the semitone. The pack is reproducible (sorted
entries, fixed time, OGG stored) and encoded at PACK_RATE (22.05 kHz: about a quarter smaller) – the same sounds give the same SHA-1, so
players keep it in their cache.

Names like de.passimoritz.voiceapi.pack.VoicePack:  voiceapi:v.<vowel>.<root>, voiceapi:c.<sound>.<root>, voiceapi:c.<sound>,
voiceapi:hum.<voice>.<root>.<length>[g]

Usage:   python build_voice_pack.py --out VoiceAPI-Sounds.zip            standalone pack (only the sounds)
         python build_voice_pack.py --merge plugins/NoteAPI/pack/NoteAPI-Sounds.zip   mix the sounds into the NoteAPI pack
         python build_voice_pack.py --preview folder/                     the sounds as WAV to listen to
         python build_voice_pack.py --repack old.zip --out new.zip       rewrite an existing pack: only the villager, PACK_RATE,
                                                                        reproducible (no synthesis, no vanilla sounds needed)
"""
from __future__ import annotations

import argparse
import io
import json
import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

import numpy as np

SR = 44100
PACK_FORMAT = 97  # Minecraft 26.3 (for information only; the pack is valid for all versions, see pack_meta)
PACK_FORMAT_OLDEST = 7  # Minecraft 1.17
PACK_FORMAT_OPEN = 9999  # open upper end: future versions accept the pack without a warning
SOUND_FORMAT = 3  # muss zu VoicePack.FORMAT passen (3: only the villager, 22.05 kHz)
PACK_RATE = 22050  # sample rate of the OGG files in the pack (the synthesis runs at SR)
VORBIS_QUALITY = "4"
ZIP_TIME = (1980, 1, 1, 0, 0, 0)  # fixed time of every entry: same sounds -> same zip -> same SHA-1
STEP, ROOT_LOW, ROOT_HIGH = 4, 33, 85
VOWEL_MS, SONORANT_MS = 1500, 400
ROOTS = list(range(ROOT_LOW, ROOT_HIGH + 1, STEP))

# vowel -> formants F1..F4 (Hz, male voice); bandwidths below
VOWELS = {
    "a": (730, 1090, 2440, 3400), "e": (530, 1840, 2480, 3300), "ee": (390, 2300, 2700, 3400), "i": (390, 1990, 2550, 3400),
    "ii": (270, 2290, 3010, 3600), "o": (570, 840, 2410, 3300), "oo": (380, 750, 2500, 3300), "u": (440, 1020, 2240, 3300),
    "uu": (300, 870, 2240, 3300), "oe": (500, 1500, 2300, 3300), "oee": (400, 1600, 2400, 3300), "ue": (400, 1700, 2300, 3300),
    "uee": (280, 1850, 2300, 3300), "ae": (660, 1720, 2410, 3400), "ah": (640, 1190, 2390, 3300), "aw": (570, 840, 2410, 3300),
    "er": (490, 1350, 1690, 3300), "schwa": (500, 1500, 2500, 3300), "r": (650, 1300, 2500, 3300),
}
BANDWIDTHS = (70, 100, 140, 200)
# voiced consonants: formants, bandwidths, level (dB relative to the vowel), glide to schwa after the start
SONORANTS = {
    "m": ((250, 1000, 2200, 3300), (120, 300, 300, 300), -7, False),
    "n": ((250, 1700, 2600, 3300), (120, 300, 300, 300), -7, False),
    "ng": ((250, 2100, 2800, 3300), (120, 300, 300, 300), -8, False),
    "l": ((360, 1400, 2600, 3300), (90, 180, 220, 300), -4, False),
    "r": ((550, 1300, 2300, 3300), (120, 160, 200, 300), -5, False),
    "w": ((300, 650, 2200, 3300), (80, 120, 200, 300), -5, True),
    "j": ((270, 2200, 3000, 3600), (70, 120, 200, 300), -5, True),
}
NOISES = ["p", "b", "t", "d", "k", "g", "f", "v", "s", "z", "sh", "zh", "th", "dh", "h", "x", "xi", "ts", "pf", "ch", "dj"]
# singing: the real hum of a voice (vanilla sound from ~/.voiceapi/sounds, from voicelab.py setup)
# several takes per voice: file, start (shortly before the onset, with the natural rise), end, stable middle (loop), pitch (MIDI)
# (level and pitch curves of the takes measured: loop only where level and pitch are stable – before the natural decay)
HUMS = {"villager": [("mob/villager/idle2", 0.32, 0.72, 0.40, 0.70, 42.6), ("mob/villager/idle1", 0.18, 0.48, 0.28, 0.46, 45.0),
                     ("mob/villager/no1", 0.10, 0.42, 0.16, 0.40, 43.2)]}
# only the villager since 1.9.0 (tools/hums.json still lists the stable spots of the former voices, for find_hums.py)
# lengths (ms in total, ms held): short for short notes (air in between like in the videos), long for held ones
HUM_RMS_DB = -20.0  # loudness of the held hum (dBFS)
HUM_LENGTHS = {"s": (350, 200), "m": (520, 340), "l": (750, 480), "x": (1700, 1450)}  # x: held notes without a new attack
SOUNDS = Path.home() / ".voiceapi" / "sounds"
VOWEL_DB = -14.0  # target level (RMS dBFS) of the vowels

CREDITS = """VoiceAPI sounds
Vowels and consonants: formant synthesis (tools/build_voice_pack.py), no recordings. Hums: Minecraft's own villager sounds.
"""


def log(text: str) -> None:
    print(text, flush=True)


# ── Building blocks ─────────────────────────────────────────

def midi_hz(midi: float) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def resonator(y: np.ndarray, freq: float, bw: float) -> np.ndarray:
    """Two-pole resonator (formant), gain 1 at the centre."""
    from scipy.signal import lfilter
    r = np.exp(-np.pi * bw / SR)
    theta = 2 * np.pi * min(freq, SR * 0.45) / SR
    a = [1.0, -2 * r * np.cos(theta), r * r]
    b = [1 - 2 * r * np.cos(theta) + r * r]
    return lfilter(b, a, y)


def glottis(f0: np.ndarray, n: int, breath: float = 0.02, rng: np.random.Generator | None = None) -> np.ndarray:
    """Voice tone: pulse train at pitch f0 (per sample), coloured -12 dB/octave, with a little breath."""
    from scipy.signal import lfilter
    rng = rng or np.random.default_rng(1)
    phase = np.cumsum(f0 / SR)
    pulses = np.zeros(n, dtype=np.float32)
    idx = np.where(np.diff(np.floor(phase)) > 0)[0] + 1
    pulses[idx[idx < n]] = 1.0
    # glottal spectrum: two one-pole low-passes
    tilt = lfilter([1 - 0.97], [1, -0.97], pulses)
    tilt = lfilter([1 - 0.9], [1, -0.9], tilt)
    noise = rng.standard_normal(n).astype(np.float32) * breath * (0.5 + 0.5 * tilt / (np.abs(tilt).max() + 1e-9))
    return (tilt / (np.abs(tilt).max() + 1e-9) + noise).astype(np.float32)


def radiate(y: np.ndarray) -> np.ndarray:
    """Radiation from the mouth: +6 dB/octave (first difference)."""
    return np.concatenate([[0.0], np.diff(y)]).astype(np.float32)


def vowel_tract(source: np.ndarray, formants, bandwidths, scale: float = 1.0, gains=None) -> np.ndarray:
    y = source
    for k, (f, bw) in enumerate(zip(formants, bandwidths)):
        part = resonator(y, f * scale, bw)
        y = part if gains is None else part * gains[k]
    # fixed high formant for naturalness
    y = resonator(y, 4500 * scale, 300)
    return radiate(y)


def envelope(n: int, attack_ms: float, release_ms: float) -> np.ndarray:
    env = np.ones(n, dtype=np.float32)
    a = int(attack_ms / 1000 * SR)
    r = int(release_ms / 1000 * SR)
    if a > 0:
        env[:a] = np.linspace(0, 1, a) ** 1.5
    if r > 0:
        env[-r:] *= np.linspace(1, 0, r)
    return env


def normalize(y: np.ndarray, db: float) -> np.ndarray:
    rms = float(np.sqrt(np.mean(y ** 2))) + 1e-9
    y = y * (10 ** (db / 20) / rms)
    return soft_limit(y)


def soft_limit(y: np.ndarray, knee: float = 0.7, ceiling: float = 0.95) -> np.ndarray:
    out = y.copy()
    hot = np.abs(out) > knee
    out[hot] = np.sign(out[hot]) * (knee + (ceiling - knee) * np.tanh((np.abs(out[hot]) - knee) / (ceiling - knee)))
    return out.astype(np.float32)


def noise(n: int, rng: np.random.Generator) -> np.ndarray:
    return rng.standard_normal(n).astype(np.float32)


def band(y: np.ndarray, low: float | None, high: float | None, order: int = 4) -> np.ndarray:
    from scipy.signal import butter, sosfilt
    nyq = SR / 2
    if low and high:
        sos = butter(order, [low / nyq, min(high, nyq * 0.98) / nyq], btype="band", output="sos")
    elif low:
        sos = butter(order, low / nyq, btype="high", output="sos")
    else:
        sos = butter(order, min(high, nyq * 0.98) / nyq, btype="low", output="sos")
    return sosfilt(sos, y).astype(np.float32)


def size_scale(root: int) -> float:
    """Higher voices have shorter vocal tracts: formants a little higher."""
    return float(np.clip(1.0 + 0.15 * (root - 50) / 12.0, 0.92, 1.35))


# ── Sounds ──────────────────────────────────────────────────

def vowel(key: str, root: int, ms: int = VOWEL_MS) -> np.ndarray:
    rng = np.random.default_rng(root * 31 + hash(key) % 1000)
    n = int(ms / 1000 * SR)
    t = np.arange(n) / SR
    f0 = midi_hz(root)
    # vibrato starts after 250 ms, plus slight jitter
    vib = np.where(t > 0.25, np.sin(2 * np.pi * 5.3 * t) * 0.22 * np.clip((t - 0.25) / 0.3, 0, 1), 0.0)
    jitter = np.cumsum(rng.standard_normal(n) * 0.0004)
    jitter -= np.linspace(jitter[0], jitter[-1], n)
    track = f0 * 2 ** ((vib + jitter * 6) / 12)
    src = glottis(track, n, breath=0.03, rng=rng)
    y = vowel_tract(src, VOWELS[key], BANDWIDTHS, size_scale(root))
    y *= envelope(n, 12, 60)
    return normalize(y, VOWEL_DB)


def sonorant(key: str, root: int, ms: int = SONORANT_MS) -> np.ndarray:
    rng = np.random.default_rng(root * 17 + len(key))
    formants, bandwidths, db, glide = SONORANTS[key]
    n = int(ms / 1000 * SR)
    f0 = midi_hz(root)
    src = glottis(np.full(n, f0, dtype=np.float32), n, breath=0.02, rng=rng)
    scale = size_scale(root)
    if glide:
        # w/j: 70 ms of its own sound, then the mouth opens to schwa
        head = int(0.07 * SR)
        ramp = int(0.06 * SR)
        a = vowel_tract(src, formants, bandwidths, scale)
        b = vowel_tract(src, VOWELS["schwa"], BANDWIDTHS, scale)
        mix = np.concatenate([np.zeros(head), np.linspace(0, 1, ramp), np.ones(max(0, n - head - ramp))])[:n].astype(np.float32)
        y = a * (1 - mix) + b * mix
    else:
        y = vowel_tract(src, formants, bandwidths, scale)
        if key == "r":
            # uvular r: flutter at 26 Hz with a little friction
            t = np.arange(n) / SR
            y = y * (0.55 + 0.45 * np.cos(2 * np.pi * 26 * t)) + band(noise(n, rng), 600, 2000) * 0.05
    y *= envelope(n, 10, 40)
    return normalize(y, VOWEL_DB + db)


def buzz(n: int, rng: np.random.Generator, f0: float = 115.0, formants=(300, 1100, 2300, 3300)) -> np.ndarray:
    """Voicing for voiced consonants (fixed pitch, short)."""
    src = glottis(np.full(n, f0, dtype=np.float32), n, breath=0.01, rng=rng)
    return vowel_tract(src, formants, (120, 200, 250, 300))


def aspiration(n: int, rng: np.random.Generator, formants=VOWELS["schwa"]) -> np.ndarray:
    """Breath: noise through the mouth."""
    return vowel_tract(noise(n, rng) * 0.3, formants, (200, 250, 300, 350))


def burst(kind: str, rng: np.random.Generator) -> np.ndarray:
    """The release burst of a plosive."""
    if kind == "p":
        y = band(noise(int(0.014 * SR), rng), None, 1200)
    elif kind == "t":
        y = band(noise(int(0.012 * SR), rng), 3200, None)
    else:
        y = band(noise(int(0.022 * SR), rng), 1200, 2800)
    return (y * envelope(len(y), 0.5, 8)).astype(np.float32)


def consonant(key: str) -> np.ndarray:
    rng = np.random.default_rng(sum(map(ord, key)))
    parts: list[np.ndarray] = []

    def shaped(y: np.ndarray, attack: float, release: float, db: float) -> np.ndarray:
        return normalize(y * envelope(len(y), attack, release), db)

    if key in ("p", "t", "k", "b", "d", "g"):
        voiced = key in ("b", "d", "g")
        kind = {"p": "p", "b": "p", "t": "t", "d": "t", "k": "k", "g": "k"}[key]
        if voiced:
            parts.append(normalize(buzz(int(0.035 * SR), rng) * envelope(int(0.035 * SR), 3, 10), -22))
        parts.append(normalize(burst(kind, rng), -10 if not voiced else -14))
        asp_ms = {"p": 45, "t": 55, "k": 60}[kind] if not voiced else 15
        asp = aspiration(int(asp_ms / 1000 * SR), rng, VOWELS["a"] if kind == "p" else VOWELS["schwa"])
        parts.append(shaped(asp, 2, asp_ms * 0.6, -26 if not voiced else -32))
    elif key in ("f", "v"):
        n = int(0.13 * SR)
        y = band(noise(n, rng), 1200, 9000) * 0.6 + band(noise(n, rng), 300, 1500) * 0.2
        y = shaped(y, 15, 30, -24)
        if key == "v":
            y = y * 0.8 + normalize(buzz(n, rng, formants=(250, 1100, 2300, 3300)) * envelope(n, 10, 30), -24)
        parts.append(y)
    elif key in ("s", "z"):
        n = int(0.15 * SR)
        y = band(noise(n, rng), 4800, None, 6)
        y = y + band(noise(n, rng), 5500, 9000, 4) * 0.8
        y = shaped(y, 18, 35, -19)
        if key == "z":
            y = y * 0.8 + normalize(buzz(n, rng, formants=(250, 1400, 2500, 3300)) * envelope(n, 10, 30), -23)
        parts.append(y)
    elif key in ("sh", "zh"):
        n = int(0.16 * SR)
        y = band(noise(n, rng), 1800, 5200, 4) + band(noise(n, rng), 2400, 3600, 2) * 0.6
        y = shaped(y, 18, 35, -19)
        if key == "zh":
            y = y * 0.8 + normalize(buzz(n, rng, formants=(250, 1600, 2400, 3300)) * envelope(n, 10, 30), -23)
        parts.append(y)
    elif key in ("th", "dh"):
        n = int(0.12 * SR)
        y = band(noise(n, rng), 1500, 8000, 2)
        y = shaped(y, 15, 30, -27)
        if key == "dh":
            y = y * 0.8 + normalize(buzz(n, rng) * envelope(n, 10, 30), -24)
        parts.append(y)
    elif key == "h":
        n = int(0.11 * SR)
        parts.append(shaped(aspiration(n, rng, VOWELS["a"]), 15, 30, -26))
    elif key == "x":
        n = int(0.14 * SR)
        t = np.arange(n) / SR
        y = band(noise(n, rng), 700, 2200, 4) * (0.6 + 0.4 * np.cos(2 * np.pi * 34 * t))
        parts.append(shaped(y, 15, 30, -22))
    elif key == "xi":
        n = int(0.13 * SR)
        y = band(noise(n, rng), 2800, 6500, 4)
        parts.append(shaped(y, 15, 30, -22))
    elif key in ("ts", "pf", "ch", "dj"):
        first, second = {"ts": ("t", "s"), "pf": ("p", "f"), "ch": ("t", "sh"), "dj": ("d", "zh")}[key]
        a = consonant(first)
        b = consonant(second)
        return np.concatenate([a[: int(0.03 * SR)], b])
    y = np.concatenate(parts).astype(np.float32)
    return soft_limit(y)


def flatten_pitch(piece: np.ndarray, midi: float) -> np.ndarray:
    """Pulls the pitch of the excerpt onto a fixed note: every 10 ms the measured pitch is brought to the target by resampling (the original
    hums slightly off – sung, it has to sit on the semitone like in the well-known videos)."""
    import librosa
    hop = int(0.01 * SR)
    target = midi_hz(midi)
    # search range around the register of the voice (zombie ~75 Hz … cat/allay ~900 Hz)
    f0 = librosa.yin(piece, fmin=max(40.0, target / 1.6), fmax=min(2000.0, target * 1.6), sr=SR, frame_length=2048, hop_length=hop)
    # do not follow measurement outliers (octave errors, noise): only values near the target count, then smooth
    f0 = np.where((f0 > target * 0.62) & (f0 < target * 1.5), f0, target)
    from scipy.signal import medfilt
    f0 = medfilt(f0, 7)
    kernel = np.ones(5) / 5
    f0 = np.convolve(np.pad(f0, 2, mode="edge"), kernel, mode="valid")
    # continuous read speed (interpolated per sample): target / measured – no jump at frame boundaries, so no clicks
    frame_pos = np.arange(len(f0)) * hop + hop / 2
    speed_per_sample = np.interp(np.arange(len(piece)), frame_pos, target / f0)
    # read position in the original: integral of the speed over the output samples
    read = np.cumsum(speed_per_sample)
    read = read[read < len(piece) - 1]
    return np.interp(read, np.arange(len(piece)), piece).astype(np.float32)


def hum(speaker: str, root: int, length: str = "l", take: int = 0, legato: bool = False) -> np.ndarray:
    """A sung hum like in the well-known videos: the original recording with its natural onset, pulled onto the semitone, the stable middle
    extended seamlessly, then slowly fading; brought to the root by playing the recording faster (so the villager gets higher and brighter
    – exactly the sound of the videos)."""
    import soundfile as sf
    import librosa
    file, start, end, mid_a, mid_b, midi = HUMS[speaker][take % len(HUMS[speaker])]
    data, rate = sf.read(str(SOUNDS / f"{file}.ogg"), always_2d=True)
    y = data.mean(axis=1).astype(np.float32)
    if rate != SR:
        y = librosa.resample(y, orig_sr=rate, target_sr=SR)
    whole = flatten_pitch(y[int(start * SR): int(end * SR)], midi)
    # to the root: speed (convert first, then assemble in target time – so the note keeps its length)
    speed = 2 ** ((root - midi) / 12)

    def faster(part):
        idx = np.arange(0, len(part) - 1, speed)
        return np.interp(idx, np.arange(len(part)), part).astype(np.float32)
    # legato: without the natural onset – straight from the held part, with a short fade-in (no new attack, the phrase is tied)
    head = faster(whole[int((mid_a - start) * SR): int((mid_b - start) * SR)] if legato else whole[: int((mid_b - start) * SR)])
    loop = faster(whole[int((mid_a - start) * SR): int((mid_b - start) * SR)])
    tail = faster(whole[int((mid_b - start) * SR):])
    ms, sustain_ms = HUM_LENGTHS[length]
    total = int(ms / 1000 * SR)
    # at high roots the recording runs very fast and the loop gets short: crossfade correspondingly shorter
    fade = max(64, min(int(0.05 * SR), len(loop) // 3))
    out = head.copy()
    while len(out) < total + fade and len(loop) > 2 * fade:
        a = out[-fade:] * np.linspace(1, 0, fade)
        b = loop[:fade] * np.linspace(0, 1, fade)
        out = np.concatenate([out[:-fade], a + b, loop[fade:]])
    out = np.pad(out, (0, max(0, total - len(out))))[:total]
    # natural decay after the held part (the voice lets go)
    sustain = int(sustain_ms / 1000 * SR)
    release = np.ones(total, dtype=np.float32)
    release[sustain:] = np.linspace(1, 0, total - sustain) ** 1.6
    out *= release
    # slightly fading like a voice holding the note
    t = np.arange(len(out)) / SR
    out *= np.where(t < 0.12, 1.0, 10 ** (-4 / 20 * np.clip((t - 0.12) / 0.4, 0, 1)))
    out *= envelope(len(out), 28 if legato else 3, 20)
    # sound like in the videos: about 20 dB thinner below 300 Hz, the formant around 900 Hz emphasised, softer at the top (the faster
    # running recording otherwise gets too sharp)
    out = band(out, 150, None, 2)
    out = out + 0.3 * band(out, 600, 1300, 2)
    soft = band(out, None, 2600, 2)
    out = soft + 0.4 * (out - soft)
    # level by the loudness of the held part (50–300 ms), not by the peak: so all voices, takes and roots are equally loud
    body = out[int(0.05 * SR): int(0.3 * SR)]
    out = out * (10 ** (HUM_RMS_DB / 20) / (float(np.sqrt(np.mean(body ** 2))) + 1e-9))
    return soft_limit(out, knee=0.75, ceiling=0.97)


# ── Pack ────────────────────────────────────────────────────

def ogg(y: np.ndarray, work: Path) -> bytes:
    """Encodes as OGG Vorbis at PACK_RATE (ffmpeg; without ffmpeg with soundfile at SR)."""
    import soundfile as sf
    if shutil.which("ffmpeg") is None:
        buffer = io.BytesIO()
        sf.write(buffer, y, SR, format="OGG", subtype="VORBIS")
        return buffer.getvalue()
    wav = work / "tmp.wav"
    sf.write(str(wav), y, SR)
    return encode(wav, work)


def encode(source: Path, work: Path) -> bytes:
    """A sound file as OGG Vorbis at PACK_RATE (mono, VORBIS_QUALITY, without metadata)."""
    out = work / "tmp-out.ogg"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(source), "-ac", "1", "-ar", str(PACK_RATE), "-c:a", "libvorbis",
                    "-q:a", VORBIS_QUALITY, "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(out)], check=True)
    return out.read_bytes()


def all_sounds():
    """All sounds: (key in sounds.json, file path without .ogg, generator)."""
    for key in VOWELS:
        for root in ROOTS:
            yield f"v.{key}.{root}", f"v/{key}/{root}", (lambda k=key, r=root: vowel(k, r))
    for key in SONORANTS:
        for root in ROOTS:
            yield f"c.{key}.{root}", f"c/{key}/{root}", (lambda k=key, r=root: sonorant(k, r))
    for key in NOISES:
        yield f"c.{key}", f"c/{key}", (lambda k=key: consonant(k))
    for speaker in HUMS:
        for root in ROOTS:
            for length in HUM_LENGTHS:
                for take in range(len(HUMS[speaker])):
                    yield f"hum.{speaker}.{root}.{length}", f"hum/{speaker}/{root}_{length}{take}", (lambda sp=speaker, r=root, ln=length, t=take: hum(sp, r, ln, t))
                    yield f"hum.{speaker}.{root}.{length}g", f"hum/{speaker}/{root}_{length}g{take}", (lambda sp=speaker, r=root, ln=length, t=take: hum(sp, r, ln, t, True))


def build_entries(progress) -> dict[str, bytes]:
    """Generates all files of the pack (path in the zip -> content)."""
    entries: dict[str, bytes] = {}
    sounds: dict[str, dict] = {}
    items = list(all_sounds())
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        for i, (key, path, make) in enumerate(items):
            entries[f"assets/voiceapi/sounds/{path}.ogg"] = ogg(make(), work)
            # several takes of the same sound (hum): the game picks one at random – all are pulled onto the same note
            sounds.setdefault(key, {"sounds": []})["sounds"].append({"name": f"voiceapi:{path}", "stream": False})
            if i % 40 == 0:
                progress(i, len(items))
    entries["assets/voiceapi/sounds.json"] = json.dumps(sounds, indent=1).encode("utf-8")
    entries["assets/voiceapi/voiceapi.json"] = json.dumps({"format": SOUND_FORMAT, "step": STEP, "roots": ROOTS, "vowels": list(VOWELS),
                                                            "sonorants": list(SONORANTS), "noises": NOISES, "hums": list(HUMS),
                                                            "rate": PACK_RATE}).encode("utf-8")
    entries["assets/voiceapi/CREDITS.txt"] = CREDITS.encode("utf-8")
    return entries


def pack_meta(description: str) -> dict:
    """pack.mcmeta that every Minecraft version from 1.17 accepts – future ones too, without maintenance: the old field pack_format (up to
    1.20.1), supported_formats (1.20.2 – 1.21.8) and min_format/max_format (from 1.21.9) with an open upper end. The sounds (sounds.json,
    OGG) have had the same format since 1.9."""
    return {"pack": {"pack_format": PACK_FORMAT_OLDEST, "supported_formats": {"min_inclusive": PACK_FORMAT_OLDEST, "max_inclusive": PACK_FORMAT_OPEN},
                     "min_format": PACK_FORMAT_OLDEST, "max_format": PACK_FORMAT_OPEN, "description": description}}


def write_zip(target: Path, entries: dict[str, bytes]) -> None:
    """Writes a reproducible zip: sorted, fixed time, OGG/PNG stored (like PackZip in the plugin)."""
    with zipfile.ZipFile(target, "w") as pack:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, date_time=ZIP_TIME)
            info.compress_type = zipfile.ZIP_STORED if name.endswith((".ogg", ".png")) else zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            pack.writestr(info, entries[name], compresslevel=9)


def write_standalone(target: Path, entries: dict[str, bytes]) -> None:
    write_zip(target, {"pack.mcmeta": json.dumps(pack_meta("VoiceAPI: speech and singing with real sounds"), indent=1).encode("utf-8"), **entries})


def repack(source: Path, target: Path) -> None:
    """Rewrites an existing pack without synthesis: only the voices in HUMS, every OGG re-encoded at PACK_RATE, sounds.json and
    voiceapi.json without the removed voices, reproducible zip."""
    keep = tuple(f"assets/voiceapi/sounds/hum/{speaker}/" for speaker in HUMS)
    entries: dict[str, bytes] = {}
    with zipfile.ZipFile(source) as old, tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        names = [n for n in old.namelist() if not n.endswith("/")]
        for i, name in enumerate(names):
            if name.startswith("assets/voiceapi/sounds/hum/") and not name.startswith(keep):
                continue
            if name.startswith("assets/voiceapi/sounds/song/"):
                continue  # the villager singings of songs come from the plugin (songs/), not from the base pack
            data = old.read(name)
            if name.endswith(".ogg"):
                original = work / "in.ogg"
                original.write_bytes(data)
                data = encode(original, work)
            entries[name] = data
            if i % 200 == 0:
                log(f"  {i}/{len(names)}")
    sounds = json.loads(entries["assets/voiceapi/sounds.json"])
    sounds = {key: value for key, value in sounds.items() if not key.startswith("hum.") or key.split(".")[1] in HUMS}
    entries["assets/voiceapi/sounds.json"] = json.dumps(sounds, indent=1).encode("utf-8")
    marker = json.loads(entries["assets/voiceapi/voiceapi.json"])
    marker.update({"format": SOUND_FORMAT, "hums": list(HUMS), "rate": PACK_RATE})
    marker.pop("songs", None)
    entries["assets/voiceapi/voiceapi.json"] = json.dumps(marker).encode("utf-8")
    write_zip(target, entries)


def merge(target: Path, entries: dict[str, bytes]) -> None:
    """Mixes the sounds into an existing pack (e.g. NoteAPI-Sounds.zip): everything old stays, voiceapi entries are replaced."""
    tmp = target.with_suffix(".tmp.zip")
    with zipfile.ZipFile(target) as old:
        merged = {n: old.read(n) for n in old.namelist() if not n.endswith("/") and not n.startswith("assets/voiceapi/")}
    merged.update(entries)
    write_zip(tmp, merged)
    shutil.move(str(tmp), str(target))


def preview(folder: Path) -> None:
    import soundfile as sf
    folder.mkdir(parents=True, exist_ok=True)
    for key in VOWELS:
        sf.write(str(folder / f"v-{key}-45.wav"), vowel(key, 45, 800), SR)
        sf.write(str(folder / f"v-{key}-65.wav"), vowel(key, 65, 800), SR)
    for key in SONORANTS:
        sf.write(str(folder / f"c-{key}-45.wav"), sonorant(key, 45), SR)
    for key in NOISES:
        sf.write(str(folder / f"c-{key}.wav"), consonant(key), SR)
    for root in (45, 57):
        for take in range(len(HUMS["villager"])):
            for length in HUM_LENGTHS:
                sf.write(str(folder / f"hum-villager-{root}-{length}{take}.wav"), hum("villager", root, length, take), SR)
    # a word from raw sounds: "ba na na" at 45
    bits = [consonant("b"), vowel("a", 45, 180), sonorant("n", 45, 70), vowel("a", 45, 160), sonorant("n", 45, 70), vowel("a", 45, 300)]
    sf.write(str(folder / "banana.wav"), np.concatenate(bits), SR)
    log(f"samples in {folder}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", help="write a standalone pack")
    parser.add_argument("--merge", help="mix into this pack (NoteAPI-Sounds.zip)")
    parser.add_argument("--preview", help="samples as WAV into this folder")
    parser.add_argument("--repack", help="rewrite this existing pack (with --out): only the villager, PACK_RATE, reproducible")
    args = parser.parse_args()
    if args.repack:
        if not args.out:
            parser.error("--repack needs --out")
        repack(Path(args.repack), Path(args.out))
        log(f"pack: {args.out} ({Path(args.out).stat().st_size // 1024} KB)")
        return
    if args.preview:
        preview(Path(args.preview))
    if not args.out and not args.merge:
        if not args.preview:
            parser.error("--out, --merge or --preview")
        return
    entries = build_entries(lambda i, n: log(f"  {i}/{n} sounds"))
    size = sum(len(v) for v in entries.values())
    if args.out:
        write_standalone(Path(args.out), entries)
        log(f"pack: {args.out} ({len(entries)} files, {size // 1024} KB)")
    if args.merge:
        target = Path(args.merge)
        if not target.exists():
            raise SystemExit(f"pack missing: {target}")
        merge(target, entries)
        log(f"sounds mixed into {target} ({len(entries)} files, {size // 1024} KB) – NoteAPI: /music pack reload")


if __name__ == "__main__":
    main()
