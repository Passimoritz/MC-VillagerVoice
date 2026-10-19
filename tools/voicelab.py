"""Voice lab: render utterances the way they sound in the game – without a server, as WAV to listen to and compare.

  python voicelab.py setup                                   downloads the vanilla sounds (Minecraft assets) to ~/.voiceapi/sounds
  python voicelab.py render cues.json --out piece.wav        plays the sound commands (java -jar voiceapi.jar speak … --cues cues.json)
                     [--pack NoteAPI-Sounds.zip] [--seed 1]  with the pack sounds instead of the vanilla sounds; seed for the random pick
  python voicelab.py atlas [--out atlas.json]                measures all vanilla sounds (onset, pitch, length, loudness)

Rendering imitates the Minecraft client: a sound event randomly picks one of its files (with weight, volume and pitch from sounds.json),
the speed (pitch) stretches or squeezes the file, a stop cuts it off immediately.
"""
from __future__ import annotations

import argparse
import json
import random
import sys
import urllib.request
import zipfile
from pathlib import Path

import numpy as np

HOME = Path.home() / ".voiceapi"
SOUNDS = HOME / "sounds"
EVENTS = HOME / "events.json"
SR = 44100
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES = "https://resources.download.minecraft.net/{h2}/{h}"


def log(text: str) -> None:
    print(text, flush=True)


# ── Load sounds ─────────────────────────────────────────────

def setup(version: str | None) -> None:
    """Downloads sounds.json and all sound files speech needs from the Mojang asset index."""
    manifest = json.load(urllib.request.urlopen(MANIFEST))
    wanted = version or manifest["latest"]["release"]
    entry = next((v for v in manifest["versions"] if v["id"] == wanted), None)
    if entry is None:
        raise SystemExit(f"Version {wanted} not in the manifest")
    meta = json.load(urllib.request.urlopen(entry["url"]))
    index = json.load(urllib.request.urlopen(meta["assetIndex"]["url"]))["objects"]
    log(f"Minecraft {wanted}: {len(index)} Assets")

    def fetch(key: str) -> bytes:
        h = index[key]["hash"]
        for attempt in range(5):
            try:
                return urllib.request.urlopen(RESOURCES.format(h2=h[:2], h=h), timeout=30).read()
            except Exception:  # noqa: BLE001
                if attempt == 4:
                    raise
    sounds = json.loads(fetch("minecraft/sounds.json"))
    HOME.mkdir(parents=True, exist_ok=True)
    EVENTS.write_text(json.dumps(sounds, indent=1), encoding="utf-8")
    needed = set()
    for event in sounds.values():
        for s in event["sounds"]:
            name = s if isinstance(s, str) else s.get("name")
            if isinstance(s, dict) and s.get("type") == "event":
                continue
            if name and (name.startswith("mob/") or name.startswith("entity/") or name.startswith("step/") or name.startswith("dig/")
                         or name.startswith("block/") or name.startswith("random/") or name.startswith("item/") or name.startswith("note/")):
                needed.add(name)
    done = 0
    for name in sorted(needed):
        dest = SOUNDS / f"{name}.ogg"
        if dest.exists():
            continue
        key = f"minecraft/sounds/{name}.ogg"
        if key not in index:
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(fetch(key))
        done += 1
        if done % 200 == 0:
            log(f"  {done} files …")
    log(f"done: {len(needed)} sounds in {SOUNDS}")


# ── Render ──────────────────────────────────────────────────

_cache: dict[str, np.ndarray] = {}


def read_audio(source) -> np.ndarray:
    """An OGG file (path or bytes) as mono float at SR."""
    import soundfile as sf
    import io
    import librosa
    data, rate = sf.read(io.BytesIO(source) if isinstance(source, (bytes, bytearray)) else str(source), always_2d=True)
    mono = data.mean(axis=1).astype(np.float32)
    if rate != SR:
        mono = librosa.resample(mono, orig_sr=rate, target_sr=SR)
    return mono


class Bank:
    """The sounds: vanilla from ~/.voiceapi/sounds, pack sounds from a resource pack (zip)."""

    def __init__(self, pack: Path | None, seed: int):
        self.random = random.Random(seed)
        self.events = json.loads(EVENTS.read_text(encoding="utf-8")) if EVENTS.exists() else {}
        self.pack = zipfile.ZipFile(pack) if pack else None
        self.pack_events: dict[str, dict] = {}
        if self.pack:
            for name in self.pack.namelist():
                if name.startswith("assets/") and name.endswith("/sounds.json"):
                    namespace = name.split("/")[1]
                    for key, value in json.loads(self.pack.read(name)).items():
                        self.pack_events[f"{namespace}:{key}"] = value

    def pick(self, event: str) -> tuple[np.ndarray, float, float] | None:
        """A random file of the event: audio, volume and pitch factor from sounds.json."""
        if ":" in event and not event.startswith("minecraft:"):
            spec = self.pack_events.get(event)
            namespace = event.split(":")[0]
            base = f"assets/{namespace}/sounds/"
        else:
            spec = self.events.get(event.replace("minecraft:", ""))
            namespace = "minecraft"
            base = None
        if not spec:
            return None
        entries = [s if isinstance(s, dict) else {"name": s} for s in spec["sounds"]]
        entries = [e for e in entries if e.get("type", "sound") == "sound"]
        if not entries:
            return None
        weights = [e.get("weight", 1) for e in entries]
        chosen = self.random.choices(entries, weights=weights)[0]
        name = chosen["name"]
        if ":" in name:
            namespace, name = name.split(":", 1)
            base = f"assets/{namespace}/sounds/"
        key = (base or "") + name
        if key not in _cache:
            try:
                if base and self.pack:
                    _cache[key] = read_audio(self.pack.read(f"{base}{name}.ogg"))
                else:
                    _cache[key] = read_audio(SOUNDS / f"{name}.ogg")
            except Exception as error:  # noqa: BLE001
                log(f"  (sound missing: {event} → {name}: {error})")
                return None
        return _cache[key], float(chosen.get("volume", 1.0)), float(chosen.get("pitch", 1.0))


def render(cues: dict, bank: Bank) -> np.ndarray:
    """Mixes the sound commands: start at at (ms), speed pitch, volume volume, stop at stop (ms, -1 = let decay)."""
    items = cues["cues"]
    end_ms = max([c["at"] + 3000 for c in items] + [cues.get("lengthMs", 0) + 500])
    out = np.zeros(int(end_ms / 1000 * SR) + SR, dtype=np.float32)
    missing = set()
    for cue in items:
        picked = bank.pick(cue["sound"])
        if picked is None:
            missing.add(cue["sound"])
            continue
        audio, vol, base_pitch = picked
        speed = cue["pitch"] * base_pitch
        # speed: resample the file (faster = shorter and higher)
        n = int(len(audio) / speed)
        idx = np.linspace(0, len(audio) - 1, max(2, n))
        y = np.interp(idx, np.arange(len(audio)), audio) * cue["volume"] * vol
        if cue.get("stop", -1) >= 0:
            keep = int((cue["stop"] - cue["at"]) / 1000 * SR)
            if keep < len(y):
                y = y[:max(0, keep)]
                fade = min(len(y), int(0.004 * SR))
                if fade > 0:
                    y[-fade:] *= np.linspace(1, 0, fade)
        start = int(cue["at"] / 1000 * SR)
        stop = min(len(out), start + len(y))
        if stop > start:
            out[start:stop] += y[: stop - start]
    if missing:
        log("  missing sounds: " + ", ".join(sorted(missing)))
    peak = float(np.abs(out).max()) + 1e-9
    if peak > 0.95:
        out *= 0.95 / peak
    last = np.where(np.abs(out) > 1e-4)[0]
    return out[: (last[-1] + int(0.2 * SR)) if len(last) else SR]


def write_wav(path: Path, audio: np.ndarray) -> None:
    import soundfile as sf
    sf.write(str(path), audio, SR)


# ── Atlas ───────────────────────────────────────────────────

def atlas(out: Path | None) -> None:
    """Measures all files in ~/.voiceapi/sounds: duration, onset, stable pitch, brightness, noisiness, loudness."""
    import librosa
    import warnings
    warnings.filterwarnings("ignore")
    hop = 110
    rows = {}
    files = sorted(SOUNDS.rglob("*.ogg"))
    for i, path in enumerate(files):
        name = path.relative_to(SOUNDS).with_suffix("").as_posix()
        try:
            y = librosa.resample(read_audio(path), orig_sr=SR, target_sr=22050)
        except Exception:  # noqa: BLE001
            continue
        if len(y) < 1200:
            continue
        f0 = librosa.yin(y, fmin=50, fmax=1200, sr=22050, frame_length=1024, hop_length=hop)
        rms = librosa.feature.rms(y=y, frame_length=1024, hop_length=hop)[0]
        flat = librosa.feature.spectral_flatness(y=y, n_fft=1024, hop_length=hop)[0]
        cent = librosa.feature.spectral_centroid(y=y, sr=22050, n_fft=1024, hop_length=hop)[0]
        n = min(len(f0), len(rms), len(flat), len(cent))
        f0, rms, flat, cent = f0[:n], rms[:n], flat[:n], cent[:n]
        loud = rms > rms.max() * 0.1
        tonal = (flat < 0.03) & loud
        midi = 69 + 12 * np.log2(np.maximum(f0, 1) / 440.0)
        t = np.arange(n) * hop / 22050
        best = (0, None)
        k = 0
        while k < n:
            if not tonal[k]:
                k += 1
                continue
            j = k
            ref = midi[k]
            while j < n and tonal[j] and abs(midi[j] - ref) < 1.5:
                j += 1
            if j - k > best[0]:
                best = (j - k, float(np.median(midi[k:j])))
            k = max(j, k + 1)
        rows[name] = {
            "dur_ms": int(n * hop / 22050 * 1000),
            "onset_ms": int(t[loud][0] * 1000) if loud.any() else None,
            "tonal_onset_ms": int(t[tonal][0] * 1000) if tonal.any() else None,
            "loud_ms": int(loud.sum() * hop / 22050 * 1000),
            "stable_ms": int(best[0] * hop / 22050 * 1000),
            "stable_midi": round(best[1], 1) if best[1] is not None else None,
            "centroid": int(np.median(cent[loud])) if loud.any() else 0,
            "flatness": round(float(np.median(flat[loud])), 3) if loud.any() else 0,
            "tonal": round(float(tonal.mean()), 2),
        }
        if i % 300 == 0:
            log(f"  {i}/{len(files)}")
    target = out or (HOME / "atlas.json")
    target.write_text(json.dumps(rows, indent=0), encoding="utf-8")
    log(f"{len(rows)} sounds measured → {target}")


# ── Command line ────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    s = sub.add_parser("setup")
    s.add_argument("--version")
    r = sub.add_parser("render")
    r.add_argument("cues")
    r.add_argument("--out", required=True)
    r.add_argument("--pack")
    r.add_argument("--seed", type=int, default=1)
    a = sub.add_parser("atlas")
    a.add_argument("--out")
    args = parser.parse_args()
    if args.command == "setup":
        setup(args.version)
    elif args.command == "render":
        cues = json.loads(Path(args.cues).read_text(encoding="utf-8"))
        bank = Bank(Path(args.pack) if args.pack else None, args.seed)
        audio = render(cues, bank)
        write_wav(Path(args.out), audio)
        log(f"{args.out}: {len(audio) / SR:.2f} s, {len(cues['cues'])} sounds ({cues.get('renderer', 'vanilla')})")
    elif args.command == "atlas":
        atlas(Path(args.out) if args.out else None)


if __name__ == "__main__":
    main()
