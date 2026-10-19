"""Villager singt einen Song: Gesangsspur → Villager-Stimmmodell (RVC, nur CPU) → mit der Original-Begleitung gemischt.

  python villager_sing.py --cache <NoteAPI-Cache-Ordner> --out ordner/ --title "Genre - Titel" [--model villager] [--pitch auto|0|-12]
  python villager_sing.py --vocals gesang.wav --backing begleitung.wav --out ordner/ --title "…"
  python villager_sing.py --vocals gesang.wav --chunks ordner/ --id <stueck-id>      fuer das Plugin: Villager-Gesang als Paket-Abschnitte

--chunks (vom Plugin aufgerufen): wandelt nur den Gesang und legt ihn als kurze OGG-Abschnitte mit Ueberblendung ab (<id>/<n>.ogg) und dazu
voice.json (Abschnitt-Laenge, Ueberlappung, Anzahl, Dauer, Modell). Die Abschnitte spielen nacheinander und ergeben zusammen den Gesang
(lineare Ueberblendung summiert sich zu 1); so kann der Abspieler an jeder Stelle einsteigen und bleibt im Takt.

Ergebnis (sprechende Namen):
  <Titel> - 1 Original - mit Begleitung.wav
  <Titel> - 2 Villager - mit Begleitung.wav
  <Titel> - 2 Villager - nur Gesang.wav
  <Titel> - Vergleich (erst Original, dann Villager).wav     je 12 s hintereinander

Das Modell liegt in ~/.voiceapi/rvc/models/<model>.pth (+ .index); trainiert mit villager_dataset.py und Applio (CPU). Alles laeuft ohne GPU,
damit es auch auf Linux-Servern ohne Grafikkarte geht.
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
CHUNK_MS = 4000      # so lang ist ein Abschnitt (ohne Ueberlappung)
OVERLAP_MS = 60      # Ueberblendung zwischen zwei Abschnitten


def log(text: str) -> None:
    print(text, flush=True)


def load(path: Path, start: float = 0.0, end: float | None = None) -> np.ndarray:
    import librosa
    y, _ = librosa.load(str(path), sr=SR, mono=True, offset=start, duration=None if end is None else end - start)
    return y.astype(np.float32)


def model_files(name: str) -> tuple[Path, Path | None]:
    """Das trainierte Modell: zuerst ~/.voiceapi/rvc/models, sonst das neueste Gewicht aus dem Applio-Trainingsordner."""
    pth = HOME / "models" / f"{name}.pth"
    index = HOME / "models" / f"{name}.index"
    if pth.exists():
        return pth, index if index.exists() else None
    logs = APPLIO / "logs" / name
    weights = sorted((p for p in logs.glob(f"{name}*.pth") if not p.name.startswith(("G_", "D_"))), key=lambda p: p.stat().st_mtime)
    if not weights:
        raise SystemExit(f"Kein Modell {name}: weder {pth} noch Gewichte in {logs}")
    indexes = sorted(logs.glob("*.index"), key=lambda p: p.stat().st_mtime)
    return weights[-1], indexes[-1] if indexes else None


def convert(vocals: Path, out: Path, model: str, pitch: int, f0_method: str = "fcpe", index_rate: float = 0.75, protect: float = 0.33) -> None:
    pth, index = model_files(model)
    # ueber rvc_infer.py: fester Zufall, Rauschen immer von der CPU – gleiches Ergebnis auf jedem Rechner, CPU wie GPU
    starter = Path(__file__).resolve().parent / "rvc_infer.py"
    cmd = [str(PYTHON), str(starter), str(APPLIO), "infer", "--input-path", str(vocals), "--output-path", str(out), "--pth-path", str(pth),
           "--pitch", str(pitch), "--f0-method", f0_method, "--index-rate", str(index_rate if index else 0), "--protect", str(protect),
           "--volume-envelope", "1.0", "--export-format", "WAV"]
    if index:
        cmd += ["--index-path", str(index)]
    log(f"  Modell {pth.name}" + (f" + {index.name}" if index else "") + f", Tonhoehe {pitch:+d}")
    result = subprocess.run(cmd, cwd=str(APPLIO), capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode != 0 or not out.exists():
        raise SystemExit("Umwandlung fehlgeschlagen:\n" + result.stdout[-1500:] + result.stderr[-1500:])


def auto_pitch(vocals: np.ndarray) -> int:
    """Die Videos singen in der Lage des Originals; nur sehr hohe Stimmen (Mitte ueber G4) eine Oktave tiefer."""
    import librosa
    f0, v, _ = librosa.pyin(vocals[: SR * 60], fmin=65, fmax=1000, sr=SR, frame_length=2048, hop_length=512)
    vals = f0[np.nan_to_num(v) > 0.5]
    if len(vals) == 0:
        return 0
    median = 69 + 12 * np.log2(np.median(vals) / 440)
    return -12 if median > 67 else 0


# Klang der Villager-Videos (Langzeitspektrum ihrer Gesangsspur, dB je Band relativ zum lautesten), gemessen an drei Referenzvideos
VIDEO_EDGES = [60, 120, 180, 250, 350, 500, 700, 1000, 1400, 2000, 2800, 4000, 5600, 8000, 11000]
VIDEO_CURVE = [-15, -13, -19, -15, -12, -2.5, 0, -6, -7, -14, -16, -31, -34, -38]


def colour(y: np.ndarray, strength: float = 0.7, limit_db: float = 9.0) -> np.ndarray:
    """Gleicht das Langzeitspektrum an den Klang der Villager-Videos an (sanft: strength, hoechstens limit_db je Band) – schliesst den Rest,
    den das Stimmmodell noch vom echten Saenger mitbringt (mehr Bass, weniger nasale Mitte)."""
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


def write_chunks(y: np.ndarray, folder: Path, ident: str, model: str) -> dict:
    """Schneidet den Gesang in ueberblendete Abschnitte und schreibt sie als OGG (Vorbis) – Abschnitt n beginnt bei n*CHUNK_MS - OVERLAP_MS."""
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
        # lineare Ueberblendung ueber 2*over um jede Grenze herum: Einblenden am Anfang (ausser beim ersten), Ausblenden am Ende (ausser beim letzten)
        if n > 0:
            k = min(2 * over, len(part))
            part[:k] *= np.linspace(0, 1, k)
        if n < count - 1:
            k = min(2 * over, len(part))
            part[-k:] *= np.linspace(1, 0, k)
        if float(np.abs(part).max()) < 1e-3:
            silent.append(n)  # stiller Abschnitt: nicht ablegen, der Abspieler ueberspringt ihn
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
    parser.add_argument("--chunks", help="Ordner fuer die Paket-Abschnitte (Plugin)")
    parser.add_argument("--id", help="Kennung des Stuecks (mit --chunks)")
    parser.add_argument("--model", default="villager")
    parser.add_argument("--pitch", default="auto")
    parser.add_argument("--f0-method", default="fcpe", help="fcpe (Standard, schneller, gleich genau) oder rmvpe")
    parser.add_argument("--no-colour", action="store_true", help="den Klang nicht an die Villager-Videos angleichen")
    parser.add_argument("--index-rate", type=float, default=0.75)
    parser.add_argument("--protect", type=float, default=0.33)
    parser.add_argument("--label", default="Villager")
    args = parser.parse_args()
    import soundfile as sf
    if args.chunks:
        if not args.vocals or not args.id:
            raise SystemExit("--chunks braucht --vocals und --id")
        folder = Path(args.chunks).resolve()
        folder.mkdir(parents=True, exist_ok=True)
        vocals = load(Path(args.vocals), args.start, args.end)
        src = folder / "_gesang.wav"
        sf.write(str(src), vocals, SR)
        pitch = auto_pitch(vocals) if args.pitch == "auto" else int(args.pitch)
        raw = folder / "_villager.wav"
        log("@@step Villager singt")
        convert(src, raw, args.model, pitch, args.f0_method, args.index_rate, args.protect)
        y = load(raw)
        if not args.no_colour:
            y = colour(y)
        y = np.pad(y, (0, max(0, len(vocals) - len(y))))[: len(vocals)]
        y *= float(np.sqrt(np.mean(vocals ** 2))) / (float(np.sqrt(np.mean(y ** 2))) + 1e-9)
        y = np.clip(y, -0.98, 0.98)
        log("@@step Abschnitte")
        meta = write_chunks(y, folder, args.id, args.model)
        src.unlink(missing_ok=True)
        raw.unlink(missing_ok=True)
        log(f"@@done {args.id} {meta['count']} Abschnitte, {meta['lengthMs'] / 1000:.1f} s")
        return
    if not args.out or not args.title:
        raise SystemExit("--out und --title (oder --chunks und --id)")
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
    src = out / f"{title} - 1 Original - nur Gesang.wav"
    sf.write(str(src), vocals, SR)
    sf.write(str(out / f"{title} - 1 Original - mit Begleitung.wav"), np.clip(vocals + backing, -1, 1), SR)
    pitch = auto_pitch(vocals) if args.pitch == "auto" else int(args.pitch)
    raw = out / f"_{args.label}-roh.wav"
    convert(src, raw, args.model, pitch, args.f0_method, args.index_rate, args.protect)
    y = load(raw)
    if not args.no_colour:
        y = colour(y)
    y = np.pad(y, (0, max(0, n - len(y))))[:n]
    # Pegel wie der Original-Gesang
    y *= float(np.sqrt(np.mean(vocals ** 2))) / (float(np.sqrt(np.mean(y ** 2))) + 1e-9)
    sf.write(str(out / f"{title} - 2 {args.label} - nur Gesang.wav"), np.clip(y, -1, 1), SR)
    sf.write(str(out / f"{title} - 2 {args.label} - mit Begleitung.wav"), np.clip(y + backing, -1, 1), SR)
    # Vergleich: lauteste 12 s des Gesangs, erst Original, dann Villager (mit Begleitung)
    win = 12 * SR
    if n > win:
        energy = np.convolve(vocals ** 2, np.ones(SR) / SR, mode="valid")[::SR // 4]
        best = int(np.argmax([energy[i: i + 48].sum() for i in range(max(1, len(energy) - 48))])) * (SR // 4)
    else:
        best = 0
    a = np.clip(vocals + backing, -1, 1)[best: best + win]
    b = np.clip(y + backing, -1, 1)[best: best + win]
    sf.write(str(out / f"{title} - Vergleich (erst Original, dann {args.label}).wav"), np.concatenate([a, np.zeros(SR // 2, dtype=np.float32), b]), SR)
    raw.unlink(missing_ok=True)
    log(f"fertig: {out}")


if __name__ == "__main__":
    main()
