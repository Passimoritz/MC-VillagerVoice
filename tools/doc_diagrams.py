"""Draws the documentation diagrams as SVG (English in docs/images/, German in docs/images/de/), light and dark (GitHub follows the
reader's colour scheme). Plain Python, no packages needed.

  python tools/doc_diagrams.py

The speech and singing diagrams use real output of the plugin (VoiceTool speak/sing … --cues, see docs/en/tools.md), copied in below.
The measured pictures (spectrogram, progress, cuts) come from tools/readme_images.py.
"""
from __future__ import annotations

import math
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent.parent

STYLE = """
<style>
  .bg{fill:#fbf8f1}.panel{fill:#ffffff;stroke:#e2d6bf}.ink{fill:#33281e}.muted{fill:#7c6b58}.faint{fill:#b9a88f}
  .t{font-family:-apple-system,'Segoe UI',Helvetica,Arial,sans-serif}.mono{font-family:ui-monospace,'Cascadia Code',Consolas,monospace}
  .green{fill:#3a7d44}.green-l{fill:#dcefd9}.brown{fill:#8a5a3b}.brown-l{fill:#f1e2d3}.sky{fill:#3f7cc0}.sky-l{fill:#dde9f7}
  .gold{fill:#c98b1b}.gold-l{fill:#f8ecd2}.red{fill:#b8473b}.red-l{fill:#f6dcd8}.violet{fill:#7653b0}.violet-l{fill:#e8e0f5}
  .on-dark{fill:#ffffff}.line{stroke:#6b5848;fill:none}.grid{stroke:#ece3d2}.dash{stroke-dasharray:5 5}
  .s-green{stroke:#3a7d44}.s-brown{stroke:#8a5a3b}.s-sky{stroke:#3f7cc0}.s-gold{stroke:#c98b1b}.s-red{stroke:#b8473b}.s-violet{stroke:#7653b0}
  .arrow{fill:#6b5848}
  @media (prefers-color-scheme: dark){
    .bg{fill:#0d1117}.panel{fill:#161b22;stroke:#30363d}.ink{fill:#e6edf3}.muted{fill:#9ba6b2}.faint{fill:#5a6470}
    .green-l{fill:#17301c}.brown-l{fill:#33251a}.sky-l{fill:#152a44}.gold-l{fill:#3a2c10}.red-l{fill:#3d1d19}.violet-l{fill:#2a2140}
    .green{fill:#4c9a57}.brown{fill:#a8724e}.sky{fill:#5592d6}.gold{fill:#d9a03a}.red{fill:#d0614f}.violet{fill:#9373cc}
    .line{stroke:#9ba6b2}.grid{stroke:#21262d}.arrow{fill:#9ba6b2}
    .s-green{stroke:#4c9a57}.s-brown{stroke:#a8724e}.s-sky{stroke:#5592d6}.s-gold{stroke:#d9a03a}.s-red{stroke:#d0614f}.s-violet{stroke:#9373cc}
  }
</style>
<defs>
  <marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
    <path d="M0,0 L10,5 L0,10 z" class="arrow"/></marker>
</defs>
"""


class Svg:
    def __init__(self, w: int, h: int, title: str):
        self.w, self.h, self.title, self.parts = w, h, title, []

    def add(self, s: str) -> None:
        self.parts.append(s)

    def text(self, x, y, s, size=14, cls="ink", anchor="start", weight=400, mono=False, italic=False):
        family = "mono" if mono else "t"
        style = ' font-style="italic"' if italic else ""
        for i, line in enumerate(str(s).split("\n")):
            self.add(f'<text x="{x:.1f}" y="{y + i * size * 1.25:.1f}" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}"'
                     f' class="{family} {cls}"{style}>{escape(line)}</text>')

    def rect(self, x, y, w, h, cls="panel", r=10, extra=""):
        self.add(f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{r}" class="{cls}" {extra}/>')

    def box(self, x, y, w, h, title, sub="", fill="panel", title_cls="ink", sub_cls="muted", size=15, r=12):
        self.rect(x, y, w, h, fill, r, 'stroke-width="1.2"' if fill == "panel" else "")
        lines = sub.split("\n") if sub else []
        block = size + len(lines) * 15.6
        top = y + (h - block) / 2 + size * 0.8
        self.text(x + w / 2, top, title, size, title_cls, "middle", 650)
        for i, line in enumerate(lines):
            self.text(x + w / 2, top + 19 + i * 15.6, line, 12.5, sub_cls, "middle")

    def arrow(self, x1, y1, x2, y2, cls="line", width=1.8, dash=False, curve=None):
        d = f"M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f}" if curve is None else f"M{x1:.1f},{y1:.1f} Q{curve[0]:.1f},{curve[1]:.1f} {x2:.1f},{y2:.1f}"
        self.add(f'<path d="{d}" class="{cls}{" dash" if dash else ""}" stroke-width="{width}" marker-end="url(#arr)"/>')

    def line(self, x1, y1, x2, y2, cls="line", width=1.2, dash=False):
        self.add(f'<path d="M{x1:.1f},{y1:.1f} L{x2:.1f},{y2:.1f}" class="{cls}{" dash" if dash else ""}" stroke-width="{width}"/>')

    def pill(self, x, y, s, fill="green", cls="on-dark", size=12):
        w = len(s) * size * 0.6 + 20
        self.rect(x, y, w, size + 12, fill, (size + 12) / 2)
        self.text(x + w / 2, y + size + 3, s, size, cls, "middle", 600)
        return w

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        body = "\n".join(self.parts)
        path.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}"'
                        f' role="img" aria-label="{escape(self.title)}">\n<title>{escape(self.title)}</title>{STYLE}'
                        f'<rect width="{self.w}" height="{self.h}" rx="16" class="bg"/>\n{body}\n</svg>\n', encoding="utf-8")


# ── texts ──

T = {
    "en": {
        "lang": "en",
        "banner_tag": "Speech and singing from villager sounds",
        "banner_sub": "Spigot · Paper · Purpur · Folia — Minecraft 1.17 to today — one 0.2 MB jar",
        "banner_say": "Welcome to the village!",
        "arch_title": "How VoiceAPI fits into a server",
        "arch_plugins": ("Your plugin", "NoteAPI, games, NPCs\nsoftdepend: VoiceAPI"),
        "arch_api": ("VoiceApi", "speak · sing · play · cues\nlibrary · songs · pack"),
        "arch_stages": [("Read", "letters → sounds,\nsyllables, stress"), ("Prosody", "length, melody,\nloudness, mood"),
                        ("Render", "sound commands,\nvanilla or pack"), ("Speech clock", "5 ms steps,\nstart + stop")],
        "arch_players": ("Players", "per player"),
        "arch_pack": "with resource pack: real vowels",
        "arch_vanilla": "without: villager + block sounds",
        "arch_files": "plugins/VoiceAPI/ — config.yml · voices/*.voice · songs/ · pack/ · tools/",
        "sp_title": "From text to sound — real output for “Willkommen im Dorf!” (German)",
        "sp_1": "1  Read", "sp_2": "2  Prosody — pitch (MIDI) and length of every sound", "sp_3": "3  Render — sound commands",
        "sp_stress": "stressed vowel", "sp_vowel": "vowel", "sp_cons": "consonant", "sp_ms": "ms",
        "sp_vanilla": "vanilla", "sp_pack": "with pack",
        "sp_note": "An exclamation: the sentence melody falls at the end. Stressed syllables are longer, louder and carry an accent peak.",
        "pv_title": "What players hear",
        "pv_pack": "With the resource pack", "pv_vanilla": "Without (vanilla sounds)",
        "pv_rows": [("Vowels", "formant samples per root", "voiceapi:v.o.45 …", "the villager's “Hmm”, started at the\nsyllable's pitch, stopped in time"),
                    ("Consonants", "recorded consonant samples", "voiceapi:c.k, c.f …", "sand, gravel, calcite, a button,\na lava pop, horse breath …"),
                    ("Singing", "the real hum of each voice,", "exactly on the semitone", "off by default — the vanilla hum\ncannot hold a note")],
        "pv_hint": "VoiceAPI decides per player — NoteAPI tells it who loaded the pack (or your own packProbe).",
        "dl_title": "Where the sounds come from (since 1.9.0)",
        "dl_jar": ("voiceapi.jar", "0.2 MB — code only"), "dl_start": ("Server start", "never waits"),
        "dl_have": "file there,\nSHA-256 right?", "dl_yes": "yes", "dl_no": "no",
        "dl_get": ("Download in the background", "GitHub release “voice-pack”\n.part → check SHA-256 → move"),
        "dl_base": ("VoiceAPI-Sounds-base.zip", "plugins/VoiceAPI/pack/ — 23 MB"),
        "dl_songs": ("+ villager songs", "songs/<id>/*.ogg"),
        "dl_pack": ("VoiceAPI-Sounds.zip", "the finished sound pack"),
        "dl_event": ("onSoundPackChanged", "NoteAPI mixes it into\nits resource pack"),
        "dl_offline": ("Offline / download off", "runs without resource pack;\nplace the file by hand"),
        "size_title": "Jar size", "size_before": "1.8.0 — sounds inside", "size_after": "1.9.0 — sounds downloaded",
        "size_sounds": "sound pack — 98 %", "size_note": "2,405 OGG Vorbis files: 9 hum voices × 14 roots × 4 lengths × 2 takes, plus speech sounds",
        "clock_title": "Why a speech clock of its own",
        "clock_tick": "server tick — every 50 ms", "clock_voice": "speech clock — every 5 ms",
        "clock_want": "wanted start of a sound", "clock_late": "up to 49 ms late", "clock_ok": "at most 4 ms late",
        "clock_note": "A syllable lasts 60–150 ms: on the tick grid speech would stumble. VoiceAPI starts and stops sounds on its own thread.",
        "sing_title": "Singing — real output for “Twinkle, twinkle, little star”",
        "sing_melody": "melody as written", "sing_sung": "sung by the villager — one octave lower, in its own register",
        "sing_cues": "hum sounds", "sing_legato": "legato: the next syllable starts 35 ms early",
        "sing_note": "Every syllable is a held hum of the villager (voiceapi:hum.villager.<root>.<length>), pulled onto the semitone.",
        "compat_title": "One jar for every server",
        "compat_mc": "Minecraft", "compat_servers": "Spigot · Paper · Purpur · Folia · forks", "compat_java": "Java 17+",
        "compat_ci": "Checked every Monday by CI on real servers — if a new version breaks something, GitHub sends an email.",
        "compat_future": "future",
        "conv_title": "Villager singing for a song — convertSong()",
        "conv_steps": [("Vocal track", "WAV from NoteAPI"), ("Job file", "songs/<id>.job\nsurvives restarts"),
                       ("Voice model", "RVC on the CPU\nvillager_sing.py"), ("Sections", "≈ 4 s, cut at\nquiet moments"),
                       ("Swap in", "<id>.part → <id>"), ("Sound pack", "rewritten +\nonSoundPackChanged")],
        "conv_resume": "server stopped mid-way? the next start continues (rvc.resume)",
        "conv_play": "playback: SongVoice.cues() on NoteAPI's music clock",
    },
    "de": {
        "lang": "de",
        "banner_tag": "Sprache und Gesang aus Villager-Lauten",
        "banner_sub": "Spigot · Paper · Purpur · Folia — Minecraft 1.17 bis heute — ein Jar mit 0,2 MB",
        "banner_say": "Willkommen im Dorf!",
        "arch_title": "Wie sich VoiceAPI in einen Server einfügt",
        "arch_plugins": ("Dein Plugin", "NoteAPI, Spiele, NPCs\nsoftdepend: VoiceAPI"),
        "arch_api": ("VoiceApi", "speak · sing · play · cues\nlibrary · songs · pack"),
        "arch_stages": [("Lesen", "Buchstaben → Laute,\nSilben, Betonung"), ("Prosodie", "Länge, Melodie,\nLautstärke, Stimmung"),
                        ("Rendern", "Klangbefehle,\nVanilla oder Paket"), ("Sprechuhr", "5-ms-Schritte,\nStart + Stopp")],
        "arch_players": ("Spieler", "je Spieler"),
        "arch_pack": "mit Ressourcenpaket: echte Vokale",
        "arch_vanilla": "ohne: Villager- + Blockgeräusche",
        "arch_files": "plugins/VoiceAPI/ — config.yml · voices/*.voice · songs/ · pack/ · tools/",
        "sp_title": "Vom Text zum Klang — echte Ausgabe für „Willkommen im Dorf!“",
        "sp_1": "1  Lesen", "sp_2": "2  Prosodie — Tonhöhe (MIDI) und Länge jedes Lauts", "sp_3": "3  Rendern — Klangbefehle",
        "sp_stress": "betonter Vokal", "sp_vowel": "Vokal", "sp_cons": "Konsonant", "sp_ms": "ms",
        "sp_vanilla": "Vanilla", "sp_pack": "mit Paket",
        "sp_note": "Ein Ausruf: Die Satzmelodie fällt am Ende. Betonte Silben sind länger, lauter und tragen einen Akzentgipfel.",
        "pv_title": "Was Spieler hören",
        "pv_pack": "Mit Ressourcenpaket", "pv_vanilla": "Ohne (Vanilla-Laute)",
        "pv_rows": [("Vokale", "Formant-Samples je Grundton", "voiceapi:v.o.45 …", "das „Hmm“ des Villagers, auf der Tonhöhe\nder Silbe gestartet, rechtzeitig gestoppt"),
                    ("Konsonanten", "aufgenommene Konsonanten", "voiceapi:c.k, c.f …", "Sand, Kies, Calcit, ein Knopf,\nein Lava-Plopp, Pferdeatem …"),
                    ("Gesang", "das echte Summen jeder Stimme,", "genau auf dem Halbton", "standardmäßig aus — das Vanilla-\nSummen kann keinen Ton halten")],
        "pv_hint": "VoiceAPI entscheidet je Spieler — die NoteAPI sagt, wer das Paket geladen hat (oder dein eigener packProbe).",
        "dl_title": "Woher die Laute kommen (seit 1.9.0)",
        "dl_jar": ("voiceapi.jar", "0,2 MB — nur Code"), "dl_start": ("Serverstart", "wartet nie"),
        "dl_have": "Datei da,\nSHA-256 richtig?", "dl_yes": "ja", "dl_no": "nein",
        "dl_get": ("Download im Hintergrund", "GitHub-Release „voice-pack“\n.part → SHA-256 prüfen → verschieben"),
        "dl_base": ("VoiceAPI-Sounds-base.zip", "plugins/VoiceAPI/pack/ — 23 MB"),
        "dl_songs": ("+ Villager-Lieder", "songs/<id>/*.ogg"),
        "dl_pack": ("VoiceAPI-Sounds.zip", "das fertige Lautpaket"),
        "dl_event": ("onSoundPackChanged", "die NoteAPI mischt es in\nihr Ressourcenpaket"),
        "dl_offline": ("Offline / Download aus", "läuft ohne Ressourcenpaket;\nDatei von Hand ablegen"),
        "size_title": "Größe des Jars", "size_before": "1.8.0 — Laute im Jar", "size_after": "1.9.0 — Laute per Download",
        "size_sounds": "Lautpaket — 98 %", "size_note": "2.405 OGG-Vorbis-Dateien: 9 Summ-Stimmen × 14 Grundtöne × 4 Längen × 2 Takes, dazu Sprachlaute",
        "clock_title": "Warum eine eigene Sprechuhr",
        "clock_tick": "Server-Tick — alle 50 ms", "clock_voice": "Sprechuhr — alle 5 ms",
        "clock_want": "gewünschter Start eines Lauts", "clock_late": "bis zu 49 ms zu spät", "clock_ok": "höchstens 4 ms zu spät",
        "clock_note": "Eine Silbe dauert 60–150 ms: im Tick-Raster würde die Sprache stolpern. VoiceAPI startet und stoppt Laute auf eigenem Thread.",
        "sing_title": "Singen — echte Ausgabe für „Twinkle, twinkle, little star“",
        "sing_melody": "Melodie wie notiert", "sing_sung": "vom Villager gesungen — eine Oktave tiefer, in seiner eigenen Lage",
        "sing_cues": "Summ-Laute", "sing_legato": "Legato: die nächste Silbe beginnt 35 ms früher",
        "sing_note": "Jede Silbe ist ein gehaltenes Summen des Villagers (voiceapi:hum.villager.<Grundton>.<Länge>), auf den Halbton gezogen.",
        "compat_title": "Ein Jar für jeden Server",
        "compat_mc": "Minecraft", "compat_servers": "Spigot · Paper · Purpur · Folia · Forks", "compat_java": "Java 17+",
        "compat_ci": "Jeden Montag von der CI auf echten Servern geprüft — bricht eine neue Version etwas, schickt GitHub eine Mail.",
        "compat_future": "künftig",
        "conv_title": "Villager-Gesang für ein Lied — convertSong()",
        "conv_steps": [("Gesangsspur", "WAV von der NoteAPI"), ("Auftragsdatei", "songs/<id>.job\nübersteht Neustarts"),
                       ("Stimmmodell", "RVC auf der CPU\nvillager_sing.py"), ("Abschnitte", "≈ 4 s, an leisen\nStellen geschnitten"),
                       ("Einsetzen", "<id>.part → <id>"), ("Lautpaket", "neu geschrieben +\nonSoundPackChanged")],
        "conv_resume": "Server mittendrin gestoppt? Der nächste Start macht weiter (rvc.resume)",
        "conv_play": "Wiedergabe: SongVoice.cues() auf der Musikuhr der NoteAPI",
    },
}

# real output: VoiceTool speak "Willkommen im Dorf!" --lang de  (start ms, sound, length ms, pitch from, pitch to, stressed)
SPEECH = [(0, "v", 65, 43.7, 44.5, 0), (65, "ɪ", 61, 44.5, 44.5, 0), (126, "l", 60, 44.5, 44.0, 0), (186, "k", 85, 46.3, 47.1, 1),
          (271, "ɔ", 119, 47.1, 45.6, 1), (390, "m", 69, 41.0, 41.8, 0), (459, "ə", 41, 41.8, 41.8, 0), (500, "n", 65, 41.8, 41.3, 0),
          (595, "ɪ", 64, 40.9, 40.9, 0), (659, "m", 71, 40.9, 40.4, 0), (760, "d", 71, 43.9, 44.7, 1), (831, "ɔ", 157, 44.7, 39.7, 1),
          (988, "r", 78, 39.7, 39.2, 1), (1066, "f", 123, 39.7, 39.2, 1)]
WORDS = [("Willkommen", "vɪl.ˈkɔ.mən", 0, 595), ("im", "ˈɪm", 595, 760), ("Dorf!", "ˈdɔrf", 760, 1189)]
VANILLA = [(0, "villager.no"), (58, "horse.breathe"), (78, "villager.no"), (129, "villager.no"), (244, "calcite.place"),
           (313, "villager.death"), (380, "villager.no"), (457, "villager.no"), (498, "villager.no"), (597, "villager.no"),
           (646, "villager.no"), (760, "villager.no"), (818, "small_fall"), (870, "villager.death"), (967, "villager.no"),
           (1124, "horse.breathe")]
PACK = [(0, "c.v"), (65, "v.i.45"), (126, "c.l.45"), (186, "c.k"), (271, "v.o.49"), (318, "v.o.45"), (390, "c.m.41"), (459, "v.schwa.41"),
        (500, "c.n.41"), (595, "v.i.41"), (659, "c.m.41"), (760, "c.d"), (831, "v.o.45"), (893, "v.o.41"), (988, "c.r.41"), (1066, "c.f")]
# real output: VoiceTool sing "Twinkle, twinkle, little star" --melody (C C G G A A G, 400 ms each, the last 800)
MELODY = [(0, 400, 60), (400, 400, 60), (800, 400, 67), (1200, 400, 67), (1600, 400, 69), (2000, 400, 69), (2400, 800, 67)]
SUNG = [(0, 400, 48, "Twin"), (365, 435, 48, "kle,"), (810, 390, 55, "twin"), (1165, 435, 55, "kle,"), (1565, 435, 57, "lit"),
        (1965, 435, 57, "tle"), (2365, 835, 55, "star")]
HUMS = ["49.s", "49.sg", "57.sg", "57.sg", "57.sg", "57.sg", "57.lg"]
NOTE_NAMES = {48: "C3", 55: "G3", 57: "A3", 60: "C4", 67: "G4", 69: "A4"}


def banner(t: dict, out: Path) -> None:
    s = Svg(1200, 300, "VoiceAPI — " + t["banner_tag"])
    for i in range(60):
        x = 660 + i * 8.6
        h = 16 + 78 * abs(math.sin(i * 0.37) * math.sin(i * 0.11 + 0.6)) + (20 if i % 9 in (2, 3) else 0)
        s.rect(x, 168 - h / 2, 5.4, h, "green" if (i // 7) % 2 == 0 else "brown", 2.7, 'opacity="0.88"')
    s.text(70, 128, "VoiceAPI", 66, "ink", weight=800)
    s.text(72, 170, t["banner_tag"], 24, "green", weight=600)
    s.text(72, 206, t["banner_sub"], 15, "muted")
    s.rect(820, 30, 300, 46, "panel", 23, 'stroke-width="1.2"')
    s.text(970, 59, "“" + t["banner_say"] + "”", 17, "ink", "middle", 600, italic=True)
    s.add('<path d="M884,75.5 L874,96 L904,75.5" class="panel" stroke-width="1.2"/>')
    x = 72
    for label, fill in (("Java 17+", "green"), ("API 3", "brown"), ("MC 1.17 → 26.x", "sky"), ("Folia ✓", "violet")):
        x += s.pill(x, 232, label, fill) + 10
    s.save(out)


def architecture(t: dict, out: Path) -> None:
    s = Svg(1200, 470, t["arch_title"])
    s.text(40, 48, t["arch_title"], 22, "ink", weight=700)
    s.box(40, 84, 220, 110, *t["arch_plugins"], fill="brown-l")
    s.box(330, 84, 240, 110, *t["arch_api"], fill="green", title_cls="on-dark", sub_cls="on-dark", size=18)
    s.arrow(260, 139, 326, 139, width=2.2)
    x = 40
    for i, (title, sub) in enumerate(t["arch_stages"]):
        s.box(x, 262, 220, 100, title, sub)
        s.text(x + 16, 284, str(i + 1), 13, "faint", weight=700)
        if i < 3:
            s.arrow(x + 220, 312, x + 254, 312)
        x += 256
    s.arrow(450, 194, 150, 258, curve=(330, 250))
    s.box(940, 84, 220, 64, *t["arch_players"], fill="sky", title_cls="on-dark", sub_cls="on-dark", size=17)
    s.arrow(850, 262, 1000, 152, curve=(990, 240), width=2.2)
    s.rect(940, 160, 220, 34, "green-l", 8)
    s.text(1050, 182, t["arch_pack"], 12, "ink", "middle")
    s.rect(940, 200, 220, 34, "brown-l", 8)
    s.text(1050, 222, t["arch_vanilla"], 12, "ink", "middle")
    s.rect(40, 396, 1120, 44, "panel", 10, 'stroke-width="1.2"')
    s.text(600, 424, t["arch_files"], 14, "muted", "middle", mono=True)
    s.save(out)


def speech(t: dict, out: Path) -> None:
    s = Svg(1200, 645, t["sp_title"])
    s.text(40, 46, t["sp_title"], 21, "ink", weight=700)
    left, right, total = 150, 1160, 1189
    px = lambda ms: left + (right - left) * ms / total
    s.text(40, 101, t["sp_1"], 14, "green", weight=700)
    for word, ipa, a, b in WORDS:
        s.rect(px(a) + 2, 72, px(b) - px(a) - 4, 50, "green-l", 8)
        s.text((px(a) + px(b)) / 2, 93, word, 15, "ink", "middle", 650)
        s.text((px(a) + px(b)) / 2, 112, "[" + ipa + "]", 13, "muted", "middle", mono=True)
    s.text(40, 162, t["sp_2"], 14, "green", weight=700)
    top, bottom, lo, hi = 190, 390, 38.0, 49.0
    py = lambda p: bottom - (bottom - top) * (p - lo) / (hi - lo)
    for p in range(38, 50, 2):
        s.line(left, py(p), right, py(p), "grid", 1)
        s.text(left - 12, py(p) + 4, str(p), 11, "faint", "end")
    for ms in range(0, 1201, 200):
        x = px(min(ms, total))
        s.line(x, top, x, bottom, "grid", 1)
        s.text(x, bottom + 18, f"{ms} {t['sp_ms']}" if ms == 1200 else str(ms), 11, "faint", "middle")
    for start, ph, dur, p0, p1, stress in SPEECH:
        x0, x1 = px(start), px(start + dur)
        vowel = ph in "ɪɔəa"
        cls = ("s-green" if stress else "s-sky") if vowel else "s-brown"
        s.add(f'<path d="M{x0 + 4:.1f},{py(p0):.1f} L{x1 - 4:.1f},{py(p1):.1f}" class="{cls}" stroke-width="{10 if vowel else 5}"'
              f' stroke-linecap="round" fill="none" opacity="{1 if vowel else 0.6}"/>')
        s.text((x0 + x1) / 2, py(max(p0, p1)) - 13, ph, 13, "ink", "middle", 600, mono=True)
    lx = right - 420
    for i, (cls, width, label) in enumerate((("s-green", 10, t["sp_stress"]), ("s-sky", 10, t["sp_vowel"]), ("s-brown", 5, t["sp_cons"]))):
        x = lx + i * 140
        s.add(f'<path d="M{x},{top - 14} l24,0" class="{cls}" stroke-width="{width}" stroke-linecap="round"/>')
        s.text(x + 34, top - 10, label, 12, "muted")
    s.text(40, 442, t["sp_3"], 14, "green", weight=700)
    for row, (label, cues, cls) in enumerate(((t["sp_pack"], PACK, "green"), (t["sp_vanilla"], VANILLA, "brown"))):
        y = 456 + row * 70
        s.text(left - 12, y + 36, label, 12, "muted", "end", 600)
        s.rect(left, y, right - left, 62, f"{cls}-l", 8)
        for i, (at, name) in enumerate(cues):
            x = px(at)
            s.line(x, y + 5, x, y + 57, f"s-{cls}", 2)
            last = x > right - 90
            s.text(x - 4 if last else x + 4, y + 17 + (i % 3) * 18, name, 10.5, "ink", "end" if last else "start", mono=True)
    s.text(40, 618, t["sp_note"], 13, "muted")
    s.save(out)


def pack_vs_vanilla(t: dict, out: Path) -> None:
    s = Svg(1200, 420, t["pv_title"])
    s.text(40, 48, t["pv_title"], 22, "ink", weight=700)
    s.rect(220, 70, 450, 46, "green", 10)
    s.text(445, 99, t["pv_pack"], 16, "on-dark", "middle", 700)
    s.rect(700, 70, 460, 46, "brown", 10)
    s.text(930, 99, t["pv_vanilla"], 16, "on-dark", "middle", 700)
    for i, (what, pack, code, van) in enumerate(t["pv_rows"]):
        y = 128 + i * 82
        s.text(40, y + 42, what, 17, "ink", weight=700)
        s.rect(220, y, 450, 70, "green-l", 10)
        s.rect(700, y, 460, 70, "brown-l", 10)
        s.text(240, y + 30, pack, 14, "ink")
        s.text(240, y + 52, code, 12.5, "muted", mono=code.startswith("voiceapi"))
        s.text(720, y + 30, van, 14, "ink")
    s.text(40, 390, t["pv_hint"], 13.5, "muted")
    s.save(out)


def download(t: dict, out: Path) -> None:
    s = Svg(1200, 520, t["dl_title"])
    s.text(40, 48, t["dl_title"], 22, "ink", weight=700)
    s.box(40, 80, 200, 80, *t["dl_jar"], fill="brown", title_cls="on-dark", sub_cls="on-dark")
    s.arrow(240, 120, 286, 120)
    s.box(290, 80, 170, 80, *t["dl_start"])
    s.arrow(460, 120, 518, 120)
    cx, cy = 614, 120
    s.add(f'<path d="M{cx},{cy - 60} L{cx + 94},{cy} L{cx},{cy + 60} L{cx - 94},{cy} z" class="gold-l"/>')
    s.text(cx, cy - 4, t["dl_have"], 13, "ink", "middle", 600)
    s.box(790, 70, 370, 100, *t["dl_get"], fill="sky-l")
    s.arrow(708, 120, 786, 120)
    s.text(746, 110, t["dl_no"], 12, "muted", "middle", 600)
    s.box(434, 250, 360, 80, *t["dl_base"], fill="green", title_cls="on-dark", sub_cls="on-dark", size=16)
    s.arrow(cx, cy + 60, cx, 246)
    s.text(cx + 12, 212, t["dl_yes"], 12, "muted", weight=600)
    s.arrow(930, 170, 798, 272, curve=(920, 262))
    s.text(812, 200, "SHA-256 ✓", 12, "green", weight=700)
    s.box(940, 250, 220, 80, *t["dl_offline"], fill="red-l")
    s.arrow(1080, 170, 1060, 246, dash=True)
    s.box(80, 400, 230, 80, *t["dl_songs"])
    s.box(434, 400, 360, 80, *t["dl_pack"], fill="green-l", size=16)
    s.arrow(310, 440, 430, 440)
    s.arrow(614, 330, 614, 396)
    s.box(880, 400, 280, 80, *t["dl_event"], fill="violet-l")
    s.arrow(794, 440, 876, 440)
    s.save(out)


def jar_size(t: dict, out: Path) -> None:
    s = Svg(1200, 250, t["size_title"])
    s.text(40, 46, t["size_title"], 22, "ink", weight=700)
    full, x0 = 820, 270
    for i, (label, sounds, code, total) in enumerate(((t["size_before"], 23.25, 0.45, "23.7 MB"), (t["size_after"], 0.0, 0.23, "0.23 MB"))):
        y = 76 + i * 70
        s.text(40, y + 30, label, 14.5, "ink", weight=600)
        w_sounds, w_code = full * sounds / 23.7, max(5, full * code / 23.7)
        if w_sounds:
            s.rect(x0, y + 8, w_sounds, 36, "brown", 6)
            s.text(x0 + w_sounds / 2, y + 31, t["size_sounds"], 13, "on-dark", "middle", 600)
        s.rect(x0 + w_sounds, y + 8, w_code, 36, "green", 3)
        s.text(x0 + w_sounds + w_code + 12, y + 32, total.replace(".", ",") if t["lang"] == "de" else total, 16, "ink", weight=700)
    s.text(40, 226, t["size_note"], 12.5, "muted")
    s.save(out)


def clock(t: dict, out: Path) -> None:
    s = Svg(1200, 320, t["clock_title"])
    s.text(40, 46, t["clock_title"], 22, "ink", weight=700)
    left, right, span = 60, 1140, 300
    px = lambda ms: left + (right - left) * ms / span
    wanted = [37, 112, 171, 236]
    for row, (label, step, cls) in enumerate(((t["clock_tick"], 50, "brown"), (t["clock_voice"], 5, "green"))):
        y = 96 + row * 100
        s.text(left, y - 12, label, 14, "ink", weight=600)
        s.line(left, y + 20, right, y + 20, "line", 1.2)
        for ms in range(0, span + 1, step):
            h = 18 if ms % 50 == 0 else 8
            s.line(px(ms), y + 20 - h / 2, px(ms), y + 20 + h / 2, f"s-{cls}", 2 if ms % 50 == 0 else 1)
        for w in wanted:
            actual = -(-w // step) * step
            s.add(f'<circle cx="{px(w):.1f}" cy="{y + 20}" r="5.5" class="gold"/>')
            s.rect(px(w), y + 34, max(3, px(actual) - px(w)), 8, cls, 3)
        s.text(right, y + 60, t["clock_late" if step == 50 else "clock_ok"], 12.5, cls, "end", 700)
    s.add(f'<circle cx="{left + 6}" cy="292" r="5.5" class="gold"/>')
    s.text(left + 18, 296, t["clock_want"], 12.5, "muted")
    s.text(left + 250, 296, t["clock_note"], 12.5, "muted")
    s.save(out)


def singing(t: dict, out: Path) -> None:
    s = Svg(1200, 520, t["sing_title"])
    s.text(40, 46, t["sing_title"], 21, "ink", weight=700)
    left, right, total = 150, 1160, 3200
    px = lambda ms: left + (right - left) * ms / total
    for ms in range(0, 3201, 400):
        s.line(px(ms), 74, px(ms), 420, "grid", 1)
        s.text(px(ms), 438, f"{ms} ms" if ms == 3200 else str(ms), 11, "faint", "middle")
    s.text(40, 84, t["sing_melody"], 13, "muted", weight=600)
    py1 = lambda p: 188 - (p - 60) * 9
    for a, d, p in MELODY:
        s.rect(px(a) + 2, py1(p) - 10, px(a + d) - px(a) - 4, 20, "sky", 5)
        s.text(px(a) + 8, py1(p) + 4, NOTE_NAMES[p], 11, "on-dark", weight=600)
    s.text(40, 228, t["sing_sung"], 13, "muted", weight=600)
    py2 = lambda p: 346 - (p - 48) * 9
    for i, (a, d, p, syl) in enumerate(SUNG):
        s.rect(px(a) + 1, py2(p) - 15, px(a + d) - px(a) - 2, 30, "green" if i % 2 == 0 else "brown", 6, 'opacity="0.92"')
        s.text(px(a) + 9, py2(p) + 5, syl, 13, "on-dark", weight=700)
        s.text(px(a) + 4, py2(p) - 22, NOTE_NAMES[p], 10.5, "faint")
    s.arrow(px(560), 398, px(380), 358, curve=(px(470), 396))
    s.text(px(580), 402, t["sing_legato"], 12, "muted")
    s.text(40, 470, t["sing_cues"], 13, "muted", weight=600)
    for (a, _, _, _), hum in zip(SUNG, HUMS):
        s.text(px(a) + 4, 470, hum, 11, "ink", mono=True)
    s.text(40, 502, t["sing_note"], 12.5, "muted")
    s.save(out)


def compat(t: dict, out: Path) -> None:
    s = Svg(1200, 300, t["compat_title"])
    s.text(40, 46, t["compat_title"], 22, "ink", weight=700)
    versions = ["1.17", "1.18", "1.19", "1.20", "1.21", "26.1", "26.2", "26.3"]
    left, right = 150, 1000
    step = (right - left) / (len(versions) - 1)
    s.text(40, 112, t["compat_mc"], 13, "muted", weight=600)
    s.rect(left - 16, 96, right - left + 32, 22, "green", 11)
    s.rect(right + 24, 96, 136, 22, "green-l", 11)
    s.text(right + 92, 112, t["compat_future"] + " →", 12, "green", "middle", 700)
    for i, v in enumerate(versions):
        x = left + i * step
        s.add(f'<circle cx="{x:.1f}" cy="107" r="4.5" class="on-dark"/>')
        s.text(x, 142, v, 13, "ink", "middle", 600)
    s.rect(left - 16, 166, 1026, 40, "sky-l", 10)
    s.text(left - 16 + 513, 192, t["compat_servers"] + "   ·   " + t["compat_java"], 14, "ink", "middle", 600)
    for v, x in (("Paper 1.17.1", left), ("Paper 1.20.4", left + 3 * step), ("Paper latest", right - 60), ("Folia latest", right + 50),
                 ("Spigot latest", right + 150)):
        s.line(x, 216, x, 228, "line", 1.5)
        s.text(x, 248, "✓ " + v, 11.5, "green", "middle", 600)
    s.text(40, 282, t["compat_ci"], 12.5, "muted")
    s.save(out)


def conversion(t: dict, out: Path) -> None:
    s = Svg(1200, 300, t["conv_title"])
    s.text(40, 46, t["conv_title"], 22, "ink", weight=700)
    fills = ["brown-l", "gold-l", "green", "panel", "panel", "violet-l"]
    x = 40
    for i, ((title, sub), fill) in enumerate(zip(t["conv_steps"], fills)):
        dark = fill == "green"
        s.box(x, 76, 170, 100, title, sub, fill=fill, title_cls="on-dark" if dark else "ink", sub_cls="on-dark" if dark else "muted")
        if i < 5:
            s.arrow(x + 170, 126, x + 192, 126)
        x += 194
    s.arrow(500, 180, 160, 180, dash=True, curve=(330, 228))
    s.text(40, 254, "↻  " + t["conv_resume"], 13, "gold", weight=600)
    s.text(40, 280, "▶  " + t["conv_play"], 13, "muted")
    s.save(out)


DRAW = {"banner": banner, "architecture": architecture, "speech-pipeline": speech, "pack-vs-vanilla": pack_vs_vanilla,
        "sound-download": download, "jar-size": jar_size, "speech-clock": clock, "singing": singing, "compatibility": compat,
        "song-conversion": conversion}


def main() -> None:
    for lang, folder in (("en", ROOT / "docs" / "images"), ("de", ROOT / "docs" / "images" / "de")):
        for name, draw in DRAW.items():
            draw(T[lang], folder / f"{name}.svg")
    print("drew", len(DRAW) * 2, "diagrams")


if __name__ == "__main__":
    main()
