"""Baut die VoiceAPI-Laute fuer das NoteAPI-Ressourcenpaket: echte Vokale und Konsonanten statt Dorfbewohner-Laute.

Die Laute entstehen durch Formant-Synthese (ein Stimmton aus Glottis-Impulsen laeuft durch die Resonanzen des Mundraums – wie bei den klassischen
Sprachsynthesizern), die Konsonanten aus geformtem Rauschen und kurzen Verschluss-Bursts. Jeder Vokal wird fuer jeden Grundton (alle STEP
Halbtoene von ROOT_LOW bis ROOT_HIGH) eigens erzeugt, damit das Spiel ihn hoechstens zwei Halbtoene strecken muss und die Formanten (= der
Vokal) erhalten bleiben. Hoehere Grundtoene bekommen etwas hoehere Formanten (kleinerer Sprecher).

Namen wie de.passimoritz.voiceapi.pack.VoicePack:  voiceapi:v.<vokal>.<grundton>, voiceapi:c.<laut>.<grundton>, voiceapi:c.<laut>

Aufruf:  python build_voice_pack.py --out VoiceAPI-Sounds.zip            eigenstaendiges Paket (nur die Laute)
         python build_voice_pack.py --merge plugins/NoteAPI/pack/NoteAPI-Sounds.zip   die Laute in das NoteAPI-Paket mischen
         python build_voice_pack.py --preview ordner/                     die Laute als WAV zum Anhoeren
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
PACK_FORMAT = 97  # Minecraft 26.3
SOUND_FORMAT = 1  # muss zu VoicePack.FORMAT passen
STEP, ROOT_LOW, ROOT_HIGH = 4, 33, 85
VOWEL_MS, SONORANT_MS = 1500, 400
ROOTS = list(range(ROOT_LOW, ROOT_HIGH + 1, STEP))

# Vokal -> Formanten F1..F4 (Hz, maennliche Stimme); Bandbreiten unten
VOWELS = {
    "a": (730, 1090, 2440, 3400), "e": (530, 1840, 2480, 3300), "ee": (390, 2300, 2700, 3400), "i": (390, 1990, 2550, 3400),
    "ii": (270, 2290, 3010, 3600), "o": (570, 840, 2410, 3300), "oo": (380, 750, 2500, 3300), "u": (440, 1020, 2240, 3300),
    "uu": (300, 870, 2240, 3300), "oe": (500, 1500, 2300, 3300), "oee": (400, 1600, 2400, 3300), "ue": (400, 1700, 2300, 3300),
    "uee": (280, 1850, 2300, 3300), "ae": (660, 1720, 2410, 3400), "ah": (640, 1190, 2390, 3300), "aw": (570, 840, 2410, 3300),
    "er": (490, 1350, 1690, 3300), "schwa": (500, 1500, 2500, 3300), "r": (650, 1300, 2500, 3300),
}
BANDWIDTHS = (70, 100, 140, 200)
# klingende Konsonanten: Formanten, Bandbreiten, Pegel (dB zum Vokal), Gleiten zum Schwa nach dem Anfang
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
VOWEL_DB = -14.0  # Ziel-Pegel (RMS dBFS) der Vokale

CREDITS = """VoiceAPI-Laute
Formant-Synthese (tools/build_voice_pack.py), keine Aufnahmen. Frei verwendbar.
"""


def log(text: str) -> None:
    print(text, flush=True)


# ── Bausteine ───────────────────────────────────────────────

def midi_hz(midi: float) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def resonator(y: np.ndarray, freq: float, bw: float) -> np.ndarray:
    """Zweipoliger Resonator (Formant), Verstaerkung 1 in der Mitte."""
    from scipy.signal import lfilter
    r = np.exp(-np.pi * bw / SR)
    theta = 2 * np.pi * min(freq, SR * 0.45) / SR
    a = [1.0, -2 * r * np.cos(theta), r * r]
    b = [1 - 2 * r * np.cos(theta) + r * r]
    return lfilter(b, a, y)


def glottis(f0: np.ndarray, n: int, breath: float = 0.02, rng: np.random.Generator | None = None) -> np.ndarray:
    """Stimmton: Impulsfolge mit der Tonhoehe f0 (je Sample), -12 dB/Oktave gefaerbt, mit etwas Hauch."""
    from scipy.signal import lfilter
    rng = rng or np.random.default_rng(1)
    phase = np.cumsum(f0 / SR)
    pulses = np.zeros(n, dtype=np.float32)
    idx = np.where(np.diff(np.floor(phase)) > 0)[0] + 1
    pulses[idx[idx < n]] = 1.0
    # Glottis-Spektrum: zwei einpolige Tiefpaesse
    tilt = lfilter([1 - 0.97], [1, -0.97], pulses)
    tilt = lfilter([1 - 0.9], [1, -0.9], tilt)
    noise = rng.standard_normal(n).astype(np.float32) * breath * (0.5 + 0.5 * tilt / (np.abs(tilt).max() + 1e-9))
    return (tilt / (np.abs(tilt).max() + 1e-9) + noise).astype(np.float32)


def radiate(y: np.ndarray) -> np.ndarray:
    """Abstrahlung vom Mund: +6 dB/Oktave (erste Differenz)."""
    return np.concatenate([[0.0], np.diff(y)]).astype(np.float32)


def vowel_tract(source: np.ndarray, formants, bandwidths, scale: float = 1.0, gains=None) -> np.ndarray:
    y = source
    for k, (f, bw) in enumerate(zip(formants, bandwidths)):
        part = resonator(y, f * scale, bw)
        y = part if gains is None else part * gains[k]
    # feste hohe Formanten fuer Natuerlichkeit
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
    """Hoehere Stimmen haben kuerzere Vokaltrakte: Formanten etwas hoeher."""
    return float(np.clip(1.0 + 0.15 * (root - 50) / 12.0, 0.92, 1.35))


# ── Laute ───────────────────────────────────────────────────

def vowel(key: str, root: int, ms: int = VOWEL_MS) -> np.ndarray:
    rng = np.random.default_rng(root * 31 + hash(key) % 1000)
    n = int(ms / 1000 * SR)
    t = np.arange(n) / SR
    f0 = midi_hz(root)
    # Vibrato setzt nach 250 ms ein, dazu leichter Jitter
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
        # w/j: 70 ms der eigene Klang, dann oeffnet sich der Mund zum Schwa
        head = int(0.07 * SR)
        ramp = int(0.06 * SR)
        a = vowel_tract(src, formants, bandwidths, scale)
        b = vowel_tract(src, VOWELS["schwa"], BANDWIDTHS, scale)
        mix = np.concatenate([np.zeros(head), np.linspace(0, 1, ramp), np.ones(max(0, n - head - ramp))])[:n].astype(np.float32)
        y = a * (1 - mix) + b * mix
    else:
        y = vowel_tract(src, formants, bandwidths, scale)
        if key == "r":
            # Zaepfchen-r: Flattern bei 26 Hz mit etwas Reibung
            t = np.arange(n) / SR
            y = y * (0.55 + 0.45 * np.cos(2 * np.pi * 26 * t)) + band(noise(n, rng), 600, 2000) * 0.05
    y *= envelope(n, 10, 40)
    return normalize(y, VOWEL_DB + db)


def buzz(n: int, rng: np.random.Generator, f0: float = 115.0, formants=(300, 1100, 2300, 3300)) -> np.ndarray:
    """Stimmton fuer stimmhafte Konsonanten (feste Tonhoehe, kurz)."""
    src = glottis(np.full(n, f0, dtype=np.float32), n, breath=0.01, rng=rng)
    return vowel_tract(src, formants, (120, 200, 250, 300))


def aspiration(n: int, rng: np.random.Generator, formants=VOWELS["schwa"]) -> np.ndarray:
    """Hauch: Rauschen durch den Mundraum."""
    return vowel_tract(noise(n, rng) * 0.3, formants, (200, 250, 300, 350))


def burst(kind: str, rng: np.random.Generator) -> np.ndarray:
    """Der Verschluss-Burst eines Plosivs."""
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


# ── Paket ───────────────────────────────────────────────────

def ogg(y: np.ndarray, work: Path) -> bytes:
    """Kodiert als OGG Vorbis: mit soundfile, sonst mit ffmpeg."""
    try:
        import soundfile as sf
        buffer = io.BytesIO()
        sf.write(buffer, y, SR, format="OGG", subtype="VORBIS")
        return buffer.getvalue()
    except Exception:  # noqa: BLE001
        import soundfile as sf
        wav = work / "tmp.wav"
        out = work / "tmp.ogg"
        sf.write(str(wav), y, SR)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4", str(out)], check=True)
        return out.read_bytes()


def all_sounds():
    """Alle Laute: (Schluessel in sounds.json, Dateipfad ohne .ogg, Erzeuger)."""
    for key in VOWELS:
        for root in ROOTS:
            yield f"v.{key}.{root}", f"v/{key}/{root}", (lambda k=key, r=root: vowel(k, r))
    for key in SONORANTS:
        for root in ROOTS:
            yield f"c.{key}.{root}", f"c/{key}/{root}", (lambda k=key, r=root: sonorant(k, r))
    for key in NOISES:
        yield f"c.{key}", f"c/{key}", (lambda k=key: consonant(k))


def build_entries(progress) -> dict[str, bytes]:
    """Erzeugt alle Dateien des Pakets (Pfad im Zip -> Inhalt)."""
    entries: dict[str, bytes] = {}
    sounds: dict[str, dict] = {}
    items = list(all_sounds())
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        for i, (key, path, make) in enumerate(items):
            entries[f"assets/voiceapi/sounds/{path}.ogg"] = ogg(make(), work)
            sounds[key] = {"sounds": [{"name": f"voiceapi:{path}", "stream": False}]}
            if i % 40 == 0:
                progress(i, len(items))
    entries["assets/voiceapi/sounds.json"] = json.dumps(sounds, indent=1).encode("utf-8")
    entries["assets/voiceapi/voiceapi.json"] = json.dumps({"format": SOUND_FORMAT, "step": STEP, "roots": ROOTS, "vowels": list(VOWELS),
                                                            "sonorants": list(SONORANTS), "noises": NOISES}).encode("utf-8")
    entries["assets/voiceapi/CREDITS.txt"] = CREDITS.encode("utf-8")
    return entries


def write_standalone(target: Path, entries: dict[str, bytes]) -> None:
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": PACK_FORMAT, "min_format": PACK_FORMAT, "max_format": PACK_FORMAT,
                                                           "description": "VoiceAPI: Sprache mit echten Lauten"}}, indent=1))
        for name, data in entries.items():
            pack.writestr(name, data)


def merge(target: Path, entries: dict[str, bytes]) -> None:
    """Mischt die Laute in ein vorhandenes Paket (z. B. NoteAPI-Sounds.zip): alles Alte bleibt, voiceapi-Eintraege werden ersetzt."""
    tmp = target.with_suffix(".tmp.zip")
    with zipfile.ZipFile(target) as old, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as new:
        for info in old.infolist():
            if info.filename.startswith("assets/voiceapi/"):
                continue
            new.writestr(info, old.read(info.filename))
        for name, data in entries.items():
            new.writestr(name, data)
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
    # ein Satz aus Rohlauten: "ba na na" bei 45
    bits = [consonant("b"), vowel("a", 45, 180), sonorant("n", 45, 70), vowel("a", 45, 160), sonorant("n", 45, 70), vowel("a", 45, 300)]
    sf.write(str(folder / "banana.wav"), np.concatenate(bits), SR)
    log(f"Hoerproben in {folder}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", help="eigenstaendiges Paket schreiben")
    parser.add_argument("--merge", help="in dieses Paket mischen (NoteAPI-Sounds.zip)")
    parser.add_argument("--preview", help="Hoerproben als WAV in diesen Ordner")
    args = parser.parse_args()
    if args.preview:
        preview(Path(args.preview))
    if not args.out and not args.merge:
        if not args.preview:
            parser.error("--out, --merge oder --preview")
        return
    entries = build_entries(lambda i, n: log(f"  {i}/{n} Laute"))
    size = sum(len(v) for v in entries.values())
    if args.out:
        write_standalone(Path(args.out), entries)
        log(f"Paket: {args.out} ({len(entries)} Dateien, {size // 1024} KB)")
    if args.merge:
        target = Path(args.merge)
        if not target.exists():
            raise SystemExit(f"Paket fehlt: {target}")
        merge(target, entries)
        log(f"Laute in {target} gemischt ({len(entries)} Dateien, {size // 1024} KB) – NoteAPI: /music pack reload")


if __name__ == "__main__":
    main()
