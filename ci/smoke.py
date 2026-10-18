"""Rauchtest auf einem echten Server: laedt ihn herunter, startet ihn mit der VoiceAPI und prueft, dass sie fehlerfrei einschaltet.

  python ci/smoke.py --project paper --version latest --jar target/voiceapi-x.jar      Server starten und pruefen
  python ci/smoke.py --project paper --version latest --resolve                         nur ausgeben: "<version> <java>" (fuer die CI)
  python ci/smoke.py --server pfad/spigot.jar --jar …                                   einen vorhandenen Server-Jar nehmen (Spigot aus BuildTools)

--version: eine Minecraft-Version (1.20.4), "latest" (neueste stabile) oder "oldest" (aelteste, die das Plugin unterstuetzt: 1.17.1).
Versionen und noetiges Java kommen aus der PaperMC-API (fill.papermc.io) – keine festen Nummern, damit der Test ohne Pflege gueltig bleibt.
Nur Python-Standardbibliothek.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from pathlib import Path

API = "https://fill.papermc.io/v3/projects/{project}"
OLDEST = "1.17.1"
STABLE = re.compile(r"^\d+(\.\d+)+$")


def get(url: str) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": "VoiceAPI-CI (github.com/Passimoritz/MC-VoiceAPI)"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def resolve(project: str, version: str) -> tuple[str, int]:
    """Version und Mindest-Java."""
    info = get(API.format(project=project))
    families = list(info["versions"].values())
    if version == "latest":
        version = next(v for fam in families for v in fam if STABLE.match(v))
    elif version == "oldest":
        version = OLDEST
    meta = get(API.format(project=project) + f"/versions/{version}")
    java = int(meta["version"]["java"]["version"]["minimum"])
    return version, java


def download(project: str, version: str, target: Path) -> None:
    build = get(API.format(project=project) + f"/versions/{version}/builds/latest")
    url = build["downloads"]["server:default"]["url"]
    req = urllib.request.Request(url, headers={"User-Agent": "VoiceAPI-CI"})
    with urllib.request.urlopen(req, timeout=300) as resp, open(target, "wb") as out:
        shutil.copyfileobj(resp, out)


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def run(server: Path, jar: Path, timeout: int, java: str) -> int:
    work = Path(tempfile.mkdtemp(prefix="voiceapi-smoke-"))
    shutil.copy2(server, work / "server.jar")
    (work / "plugins").mkdir()
    shutil.copy2(jar, work / "plugins" / jar.name)
    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        f"server-port={free_port()}\nonline-mode=false\nmotd=voiceapi-smoke\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\n",
        encoding="utf-8")
    cmd = [java, "-Xmx1536M", "-Dcom.mojang.eula.agree=true", "-DPaper.IgnoreJavaVersion=true", "-jar", "server.jar", "--nogui"]
    print("$", " ".join(cmd), "in", work, flush=True)
    proc = subprocess.Popen(cmd, cwd=work, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                            encoding="utf-8", errors="replace")
    lines: list[str] = []
    done = threading.Event()

    def reader():
        for line in proc.stdout:
            lines.append(line.rstrip())
            try:
                print(line, end="", flush=True)
            except (UnicodeError, OSError):
                pass  # Konsole kann ein Zeichen nicht darstellen: der Test geht weiter
            if re.search(r"Done \(\d", line):
                done.set()
    threading.Thread(target=reader, daemon=True).start()
    started = done.wait(timeout)
    if started:
        time.sleep(5)  # Plugins melden sich nach "Done" noch
        try:
            proc.stdin.write("stop\n")
            proc.stdin.flush()
        except OSError:
            pass
    try:
        proc.wait(120)
    except subprocess.TimeoutExpired:
        proc.kill()
    log = "\n".join(lines)
    problems = []
    if not started:
        problems.append("Server ist nicht hochgefahren")
    if not re.search(r"Enabling VoiceAPI", log):
        problems.append("VoiceAPI wurde nicht eingeschaltet")
    if not re.search(r"VoiceAPI \S+ bereit", log):
        problems.append("VoiceAPI hat ihre Bereit-Meldung nicht ausgegeben")
    for bad in (r"Error occurred while enabling VoiceAPI", r"Could not load '.*voiceapi", r"UnsupportedClassVersionError",
                r"NoSuchMethodError", r"NoClassDefFoundError", r"\[VoiceAPI\].*Exception", r"Unsupported API version"):
        hit = re.search(bad, log)
        if hit:
            problems.append("im Log: " + hit.group(0))
    shutil.rmtree(work, ignore_errors=True)
    if problems:
        print("\nFEHLGESCHLAGEN: " + "; ".join(problems), flush=True)
        return 1
    print("\nOK: VoiceAPI laeuft", flush=True)
    return 0


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--project", default="paper")
    parser.add_argument("--version", default="latest")
    parser.add_argument("--jar")
    parser.add_argument("--server")
    parser.add_argument("--resolve", action="store_true")
    parser.add_argument("--timeout", type=int, default=420)
    parser.add_argument("--java", default=os.environ.get("JAVA", "java"))
    args = parser.parse_args()
    if args.resolve:
        version, java = resolve(args.project, args.version)
        print(version, java)
        return
    if not args.jar:
        parser.error("--jar fehlt")
    jar = Path(sorted(Path().glob(args.jar))[-1]) if any(c in args.jar for c in "*?") else Path(args.jar)
    if args.server:
        server = Path(args.server)
    else:
        version, java = resolve(args.project, args.version)
        print(f"{args.project} {version} (Java {java}+)", flush=True)
        server = Path(tempfile.mkdtemp(prefix="voiceapi-server-")) / "server.jar"
        download(args.project, version, server)
    sys.exit(run(server, jar, args.timeout, args.java))


if __name__ == "__main__":
    main()
