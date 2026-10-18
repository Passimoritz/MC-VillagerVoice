"""Villager singt einen Song: Gesangsspur → Villager-Stimmmodell (RVC, nur CPU) → mit der Original-Begleitung gemischt.

  python villager_sing.py --cache <NoteAPI-Cache-Ordner> --out ordner/ --title "Genre - Titel" [--model villager] [--pitch auto|0|-12]
  python villager_sing.py --vocals gesang.wav --backing begleitung.wav --out ordner/ --title "…"

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
import subprocess
import sys
from pathlib import Path

import numpy as np

HOME = Path.home() / ".voiceapi" / "rvc"
APPLIO = HOME / "Applio"
PYTHON = HOME / "venv" / ("Scripts/python.exe" if sys.platform == "win32" else "bin/python")
SR = 44100


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


def convert(vocals: Path, out: Path, model: str, pitch: int, f0_method: str = "rmvpe", index_rate: float = 0.75, protect: float = 0.33) -> None:
    pth, index = model_files(model)
    cmd = [str(PYTHON), "core.py", "infer", "--input-path", str(vocals), "--output-path", str(out), "--pth-path", str(pth),
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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cache")
    parser.add_argument("--vocals")
    parser.add_argument("--backing")
    parser.add_argument("--from", dest="start", type=float, default=0.0)
    parser.add_argument("--to", dest="end", type=float)
    parser.add_argument("--out", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--model", default="villager")
    parser.add_argument("--pitch", default="auto")
    parser.add_argument("--f0-method", default="rmvpe")
    parser.add_argument("--index-rate", type=float, default=0.75)
    parser.add_argument("--protect", type=float, default=0.33)
    parser.add_argument("--label", default="Villager")
    args = parser.parse_args()
    import soundfile as sf
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
