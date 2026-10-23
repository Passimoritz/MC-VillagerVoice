"""Draws the README pictures (English in docs/images/, German in docs/images/de/).

  python tools/readme_images.py --orig original.wav --video video.wav --ours villager.wav [--sections <song folder>]

- pipeline.png     how a song becomes villager singing
- progress.png     distance to the villager videos and note ends per version (measured, see CHANGELOG)
- spectrogram.png  the same 8 s: original singer, villager video, VoiceAPI villager
- cuts.png         where a song is cut into sections: fixed 4 s grid vs. quiet moments (needs --sections or uses --ours)
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(Path(__file__).resolve().parent))

TEXT = {
    "en": {
        "pipeline": ["Vocal track\n(from NoteAPI)", "Villager voice model\nRVC, CPU only", "Clean\nless hiss", "Colour\nlike the videos",
                     "Follow\ntiming + level", "Articulate\nsyllable by syllable", "Sections\ncut at quiet moments", "Sound pack\nNoteAPI plays it"],
        "pipeline_title": "From a song to villager singing",
        "progress_title": "Closer to the villager videos with every version",
        "distance": "distance to the videos (lower = closer)",
        "ends": "note ends: VoiceAPI vs. video (Thick of It: 39)",
        "spec_title": ["Original singer", "Villager video", "VoiceAPI villager"],
        "freq": "Hz", "time": "seconds",
        "cuts_title": "Section cuts: fixed 4 s grid (red) vs. quiet moments (green)",
        "level": "level (dB)",
    },
    "de": {
        "pipeline": ["Gesangsspur\n(von der NoteAPI)", "Villager-Stimmmodell\nRVC, nur CPU", "Reinigen\nweniger Rauschen",
                     "Klangfarbe\nwie die Videos", "Folgen\nTiming + Pegel", "Absetzen\nSilbe für Silbe", "Abschnitte\nan leisen Stellen geschnitten",
                     "Lautpaket\ndie NoteAPI spielt es"],
        "pipeline_title": "Vom Lied zum Villager-Gesang",
        "progress_title": "Mit jeder Version näher an den Villager-Videos",
        "distance": "Abstand zu den Videos (kleiner = näher)",
        "ends": "Tonenden: VoiceAPI vs. Video (Thick of It: 39)",
        "spec_title": ["Original-Sänger", "Villager-Video", "VoiceAPI-Villager"],
        "freq": "Hz", "time": "Sekunden",
        "cuts_title": "Abschnitt-Schnitte: festes 4-s-Raster (rot) vs. leise Stellen (grün)",
        "level": "Pegel (dB)",
    },
}
# measured with the test bench (villager videos "Thick of It" + "APT."), see CHANGELOG
VERSIONS = ["1.4", "1.5", "1.6", "1.7", "1.8"]
DISTANCE = [60.2, 51.4, 51.6, 48.1, 41.5]
ENDS = [None, None, None, 14, 40]

GREEN = "#3a7d44"
BROWN = "#8a5a3b"
SAND = "#e8dcc4"


def pipeline(lang: str, out: Path) -> None:
    import matplotlib.pyplot as plt
    from matplotlib.patches import FancyBboxPatch
    t = TEXT[lang]
    fig, ax = plt.subplots(figsize=(14, 2.6), dpi=130)
    ax.set_xlim(0, len(t["pipeline"]) * 1.75)
    ax.set_ylim(0, 1.3)
    ax.axis("off")
    for i, label in enumerate(t["pipeline"]):
        x = i * 1.75 + 0.1
        colour = BROWN if i in (0, 7) else GREEN if i == 1 else SAND
        ax.add_patch(FancyBboxPatch((x, 0.2), 1.45, 0.8, boxstyle="round,pad=0.03,rounding_size=0.08", fc=colour, ec="#5b4636", lw=1.2))
        ax.text(x + 0.725, 0.6, label, ha="center", va="center", fontsize=9, color="white" if i in (0, 1, 7) else "#3b2e22")
        if i < len(t["pipeline"]) - 1:
            ax.annotate("", xy=(x + 1.72, 0.6), xytext=(x + 1.48, 0.6), arrowprops=dict(arrowstyle="->", color="#5b4636", lw=1.5))
    ax.set_title(t["pipeline_title"], fontsize=13, color="#3b2e22")
    fig.tight_layout()
    fig.savefig(out, facecolor="white")
    plt.close(fig)


def progress(lang: str, out: Path) -> None:
    import matplotlib.pyplot as plt
    t = TEXT[lang]
    fig, (a, b) = plt.subplots(1, 2, figsize=(11, 3.8), dpi=130, gridspec_kw={"width_ratios": [3, 2]})
    bars = a.bar(VERSIONS, DISTANCE, color=[SAND] * (len(VERSIONS) - 1) + [GREEN], edgecolor="#5b4636")
    for bar, value in zip(bars, DISTANCE):
        a.text(bar.get_x() + bar.get_width() / 2, value + 0.8, f"{value:.1f}".replace(".", "," if lang == "de" else "."), ha="center", fontsize=9)
    a.set_ylim(0, max(DISTANCE) * 1.15)
    a.set_title(t["distance"], fontsize=10)
    a.spines[["top", "right"]].set_visible(False)
    labels = [v for v, e in zip(VERSIONS, ENDS) if e is not None]
    values = [e for e in ENDS if e is not None]
    b.bar(labels, values, color=[SAND, GREEN], edgecolor="#5b4636")
    b.axhline(39, color=BROWN, ls="--", lw=1.2)
    b.text(-0.4, 40.5, "video" if lang == "en" else "Video", color=BROWN, fontsize=9)
    b.set_ylim(0, 48)
    b.set_title(t["ends"], fontsize=10)
    b.spines[["top", "right"]].set_visible(False)
    fig.suptitle(t["progress_title"], fontsize=13, color="#3b2e22")
    fig.tight_layout()
    fig.savefig(out, facecolor="white")
    plt.close(fig)


def load(path: str, sr: int = 22050, seconds: float = 8.0) -> np.ndarray:
    import librosa
    y, _ = librosa.load(path, sr=sr, mono=True, duration=seconds)
    return y


def spectrogram(lang: str, out: Path, files: list[str]) -> None:
    import librosa
    import librosa.display
    import matplotlib.pyplot as plt
    t = TEXT[lang]
    fig, axes = plt.subplots(1, 3, figsize=(14, 3.6), dpi=130, sharey=True)
    for ax, file, title in zip(axes, files, t["spec_title"]):
        y = load(file)
        S = librosa.power_to_db(librosa.feature.melspectrogram(y=y, sr=22050, n_mels=96, fmax=8000), ref=np.max)
        librosa.display.specshow(S, sr=22050, x_axis="time", y_axis="mel", fmax=8000, ax=ax, cmap="magma", vmin=-70)
        ax.set_title(title, fontsize=11)
        ax.set_xlabel(t["time"])
    axes[0].set_ylabel(t["freq"])
    fig.tight_layout()
    fig.savefig(out, facecolor="white")
    plt.close(fig)


def cuts(lang: str, out: Path, file: str) -> None:
    import matplotlib.pyplot as plt
    import villager_sing as vs
    t = TEXT[lang]
    y = vs.load(Path(file))[: vs.SR * 20]
    cut = vs.boundaries(y)
    hop = int(0.01 * vs.SR)
    env = 20 * np.log10(np.sqrt(np.convolve(y ** 2, np.ones(4 * hop) / (4 * hop), mode="same")[::hop]) + 1e-5)
    time = np.arange(len(env)) * 0.01
    fig, ax = plt.subplots(figsize=(12, 3), dpi=130)
    ax.plot(time, env, color="#5b4636", lw=0.8)
    for c in cut[1:]:
        ax.axvline(c / vs.SR, color=GREEN, lw=2)
    for g in range(1, int(len(y) / vs.SR / 4) + 1):
        ax.axvline(g * 4, color="#c0392b", lw=1.2, ls="--")
    ax.set_ylim(max(-70, env.max() - 60), env.max() + 3)
    ax.set_xlabel(t["time"])
    ax.set_ylabel(t["level"])
    ax.set_title(t["cuts_title"], fontsize=11)
    ax.spines[["top", "right"]].set_visible(False)
    fig.tight_layout()
    fig.savefig(out, facecolor="white")
    plt.close(fig)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--orig", required=True)
    parser.add_argument("--video", required=True)
    parser.add_argument("--ours", required=True)
    parser.add_argument("--out", default=str(ROOT / "docs" / "images"))
    args = parser.parse_args()
    for lang in ("en", "de"):
        folder = Path(args.out) if lang == "en" else Path(args.out) / "de"
        folder.mkdir(parents=True, exist_ok=True)
        pipeline(lang, folder / "pipeline.png")
        progress(lang, folder / "progress.png")
        spectrogram(lang, folder / "spectrogram.png", [args.orig, args.video, args.ours])
        cuts(lang, folder / "cuts.png", args.ours)
        print("images:", folder)


if __name__ == "__main__":
    main()
