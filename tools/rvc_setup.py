"""Richtet das Villager-Stimmmodell ein – Windows und Linux, nur CPU (keine Grafikkarte noetig).

  python rvc_setup.py [--home ~/.voiceapi/rvc] [--model pfad/villager.pth] [--index pfad/villager.index] [--check]

Was passiert:
  1. eine eigene Python-Umgebung (<home>/venv, Python 3.12; mit uv, wenn vorhanden, sonst python -m venv)
  2. torch/torchaudio als CPU-Build und die Pakete von Applio (ohne NVIDIA-Pakete)
  3. Applio (RVC-Werkzeugkette) auf einem festen, getesteten Stand nach <home>/Applio
  4. die Basismodelle (ContentVec, RMVPE) – geladen mit weights_only=True, also ohne ausfuehrbaren Code
  5. das Villager-Modell nach <home>/models/villager.pth (+ .index): aus --model/--index, sonst aus plugins/VoiceAPI/models/
--check prueft nur, ob alles da ist (Ausgabe: OK oder was fehlt), und aendert nichts.
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

APPLIO_REPO = "https://github.com/IAHispano/Applio.git"
APPLIO_COMMIT = "5379cf261fa7d8085c3b077aa42dabca7c85310b"  # getestet am 18.10.2026
TORCH = ["torch==2.11.0", "torchaudio==2.11.0"]
TORCH_INDEX = "https://download.pytorch.org/whl/cpu"


def log(text: str) -> None:
    print(text, flush=True)


def venv_python(home: Path) -> Path:
    return home / "venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def run(cmd: list[str], cwd: Path | None = None) -> None:
    log("  $ " + " ".join(cmd))
    subprocess.run(cmd, cwd=str(cwd) if cwd else None, check=True)


def has_uv() -> bool:
    try:
        subprocess.run([sys.executable, "-m", "uv", "--version"], check=True, capture_output=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def pip_install(home: Path, args: list[str]) -> None:
    py = venv_python(home)
    if has_uv():
        run([sys.executable, "-m", "uv", "pip", "install", "--python", str(py), *args])
    else:
        run([str(py), "-m", "pip", "install", *args])


def check(home: Path) -> list[str]:
    missing = []
    if not venv_python(home).exists():
        missing.append("Python-Umgebung")
    applio = home / "Applio"
    if not (applio / "core.py").exists():
        missing.append("Applio")
    for rel in ("rvc/models/embedders/contentvec/pytorch_model.bin", "rvc/models/predictors/rmvpe.pt"):
        if not (applio / rel).exists():
            missing.append(rel)
    if not (home / "models" / "villager.pth").exists():
        missing.append("models/villager.pth")
    return missing


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--home", default=str(Path.home() / ".voiceapi" / "rvc"))
    parser.add_argument("--model")
    parser.add_argument("--index")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    home = Path(args.home).expanduser()
    if args.check:
        missing = check(home)
        log("OK" if not missing else "FEHLT: " + ", ".join(missing))
        sys.exit(0 if not missing else 1)
    home.mkdir(parents=True, exist_ok=True)

    log("1. Python-Umgebung")
    if not venv_python(home).exists():
        if has_uv():
            run([sys.executable, "-m", "uv", "venv", "--python", "3.12", str(home / "venv")])
        else:
            run([sys.executable, "-m", "venv", str(home / "venv")])

    log("2. Applio")
    applio = home / "Applio"
    if not (applio / ".git").exists():
        run(["git", "clone", "--filter=blob:none", APPLIO_REPO, str(applio)])
    run(["git", "fetch", "--quiet", "origin", APPLIO_COMMIT], cwd=applio)
    run(["git", "checkout", "--quiet", APPLIO_COMMIT], cwd=applio)

    log("3. Pakete (CPU)")
    pip_install(home, [*TORCH, "--index-url", TORCH_INDEX])
    req = home / "req-cpu.txt"
    lines = [l for l in (applio / "requirements-engine.txt").read_text(encoding="utf-8").splitlines()
             if l.strip() and not l.startswith(("nvidia-", "onnxruntime-gpu", "torch==", "torchaudio=="))]
    lines.append("onnxruntime>=1.17.0")
    req.write_text("\n".join(lines) + "\n", encoding="utf-8")
    pip_install(home, ["-r", str(req)])

    log("4. Basismodelle")
    run([str(venv_python(home)), "core.py", "prerequisites", "--no-pretraineds-hifigan", "--models", "--no-exe"], cwd=applio)

    log("5. Villager-Modell")
    models = home / "models"
    models.mkdir(exist_ok=True)
    source = Path(args.model) if args.model else None
    if source is None:
        for candidate in (Path("plugins/VoiceAPI/models/villager.pth"), Path(__file__).resolve().parent.parent / "models" / "villager.pth"):
            if candidate.exists():
                source = candidate
                break
    if source and source.exists():
        shutil.copy2(source, models / "villager.pth")
        index = Path(args.index) if args.index else source.with_suffix(".index")
        if index.exists():
            shutil.copy2(index, models / "villager.index")
        log(f"  Modell: {models / 'villager.pth'}")
    else:
        log("  (kein Modell angegeben – --model villager.pth, oder nach plugins/VoiceAPI/models/ legen)")
    missing = check(home)
    log("fertig" if not missing else "fertig, aber es fehlt noch: " + ", ".join(missing))


if __name__ == "__main__":
    main()
