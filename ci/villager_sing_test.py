"""Fast checks of villager_sing.py's processing around the voice model (no model needed, synthetic signals, a few seconds):

  <rvc venv python> ci/villager_sing_test.py

- prepare() takes the bleed of a separated vocal track out (kick below the voice, a steady synth tone, noise in the gaps) and keeps the voice,
- follow() makes the villager silent where the singer is silent (the model sings on near silence at its own volume),
- level_up() raises quiet singing to the minimum level and never lowers loud singing, limit() keeps the peaks under the ceiling.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))
import villager_sing as vs  # noqa: E402

SR = vs.SR
RNG = np.random.default_rng(7)


def db(x: np.ndarray) -> float:
    return float(10 * np.log10(np.mean(np.asarray(x, np.float64) ** 2) + 1e-20))


def band_db(y: np.ndarray, lo: float, hi: float) -> float:
    spec = np.abs(np.fft.rfft(y)) ** 2
    f = np.fft.rfftfreq(len(y), 1 / SR)
    return float(10 * np.log10(spec[(f >= lo) & (f < hi)].sum() + 1e-20))


def voice(seconds: float, midi: int = 57) -> np.ndarray:
    """A sung-like tone: harmonics with vibrato and short syllables (on 0.35 s, off 0.15 s)."""
    t = np.arange(int(seconds * SR)) / SR
    f0 = 440 * 2 ** ((midi - 69) / 12) * 2 ** (0.3 * np.sin(2 * np.pi * 5.5 * t) / 12)
    phase = 2 * np.pi * np.cumsum(f0) / SR
    tone = sum((0.75 ** h) * np.sin(h * phase) for h in range(1, 16))
    syllables = (np.mod(t, 0.5) < 0.35).astype(float)
    k = np.hanning(int(0.02 * SR))
    return (0.1 * tone * np.convolve(syllables, k / k.sum(), mode="same")).astype(np.float32)


def kick(seconds: float) -> np.ndarray:
    """Kick bleed: a decaying 55 Hz thump every 0.5 s, shifted against the syllables."""
    n = int(seconds * SR)
    out = np.zeros(n)
    t = np.arange(int(0.25 * SR)) / SR
    hit = np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.06)
    for start in range(int(0.4 * SR), n - len(t), int(0.5 * SR)):
        out[start:start + len(t)] += hit
    return (0.08 * out).astype(np.float32)


def test_prepare() -> None:
    seconds = 8.0
    v = voice(seconds)
    v[: int(2 * SR)] = 0                          # intro without singing
    drone = 0.01 * np.sin(2 * np.pi * 1100 * np.arange(len(v)) / SR)
    mix = (v + kick(seconds) + drone + 0.002 * RNG.standard_normal(len(v))).astype(np.float32)
    out = vs.prepare(mix)
    intro, sung = slice(0, int(1.8 * SR)), slice(int(2.5 * SR), None)
    assert db(out[intro]) < db(mix[intro]) - 25, f"intro bleed not removed: {db(mix[intro]):.1f} → {db(out[intro]):.1f} dB"
    assert band_db(out[sung], 30, 90) < band_db(mix[sung], 30, 90) - 12, "kick below the voice not removed"
    kept = band_db(out[sung], 200, 3000) - band_db(v[sung], 200, 3000)
    assert kept > -3, f"voice lost {kept:.1f} dB"
    assert np.array_equal(vs.prepare(mix, 0.0), mix), "strength 0 must leave the track unchanged"


def test_follow_gate() -> None:
    seconds = 6.0
    ref = voice(seconds)
    ref[: int(2 * SR)] *= 1e-4                    # the singer is (nearly) silent for 2 s
    villager = (voice(seconds, 60) + 0.05 * RNG.standard_normal(len(ref))).astype(np.float32)  # the model sings and hisses everywhere
    out = vs.follow(ref, villager, consonants=0.0)
    gap, sung = out[int(0.2 * SR): int(1.7 * SR)], out[int(2.5 * SR):]
    assert db(gap) < db(sung) - 50, f"gap only {db(sung) - db(gap):.1f} dB below the singing"


def test_level() -> None:
    quiet = voice(4.0) * 0.1                       # about −30 dB sung
    up = vs.level_up(quiet, -18.0)
    assert abs(vs.sung_level(up) + 18.0) < 0.5, f"raised to {vs.sung_level(up):.1f} dB"
    loud = voice(4.0) * 2.0
    assert np.array_equal(vs.level_up(loud, -18.0), loud.astype(np.float32)), "loud singing must stay"
    assert np.array_equal(vs.level_up(quiet, 0.0), quiet), "0 switches it off"
    assert np.abs(vs.limit(up * 4)).max() <= 0.97 + 1e-6


def test_even() -> None:
    loud, quiet = voice(2.0), voice(2.0) * 10 ** (-14 / 20)          # a line 14 dB quieter
    y = np.concatenate([loud, np.zeros(SR), quiet]).astype(np.float32)
    out = vs.even(y, 0.5)
    before = db(y[-int(1.5 * SR):]) - db(y[: int(1.5 * SR)])
    after = db(out[-int(1.5 * SR):]) - db(out[: int(1.5 * SR)])
    assert after > before + 4, f"quiet line not raised: {before:.1f} → {after:.1f} dB"
    assert np.abs(out[int(2.3 * SR): int(2.7 * SR)]).max() < 1e-6, "the pause must stay silent"
    assert np.array_equal(vs.even(y, 0.0), y), "0 switches it off"


def test_shape_f0() -> None:
    """Two singers (a deep rapper around 110 Hz, a high singer around 330 Hz), each a phrase of notes, with gaps and an octave error."""
    frame = 0.01
    deep = np.repeat([110.0, 123.47, 98.0], 40)                       # A2 B2 G2
    high = np.repeat([329.63, 392.0, 349.23], 40)                      # E4 G4 F4
    f0 = np.concatenate([deep, np.zeros(60), high]).astype(np.float64)  # 0.6 s pause between the singers
    f0[20:28] = 0                                                       # 80 ms without pitch inside the rapper's phrase
    f0[60] *= 2                                                         # a single octave error of the tracker
    out = vs.shape_f0(f0, frame, fill_ms=150, smooth_ms=0)
    assert np.all(out[20:28] > 0), "short gap inside a phrase not bridged"
    assert np.all(out[120:180] == 0), "the pause between the singers must stay silent"
    assert abs(np.log2(out[60] / out[59])) < 0.1, "octave error not folded back"
    for part, notes in ((out[:120], deep), (out[180:], high)):
        med = np.median(part[part > 0])
        assert 60 <= part[part > 0].min() and part.max() <= 178, f"outside the limits 60–178 Hz: {part[part > 0].min():.0f}–{part.max():.0f} Hz"
        steps = np.round(12 * np.log2(part[[30, 70, 110]] / part[[30, 70, 110]][0]))
        assert np.array_equal(steps, np.round(12 * np.log2(notes[[30, 70, 110]] / notes[30]))), "intervals (the melody) changed"
        octaves = np.log2(part[[30, 70, 110]] / notes[[30, 70, 110]])
        assert np.allclose(octaves, np.round(octaves), atol=0.01), "moved by something other than whole octaves"
    # the next singer takes over without a pause: still each in the register, and never in the range where the model rattles (> 180 Hz)
    handover = vs.shape_f0(np.concatenate([deep, high]), frame, fill_ms=150, smooth_ms=0)
    for part in (handover[:120], handover[120:]):
        assert 60 <= part.min() and part.max() <= 178, f"handover without pause: {part.min():.0f}–{part.max():.0f} Hz"
    wide = vs.shape_f0(np.repeat([55.0, 440.0, 55.0], 40), frame, fill_ms=0, smooth_ms=0)
    assert wide.max() <= 178 + 1e-6 and wide.min() >= 60 - 1e-6, f"{wide.min():.0f}–{wide.max():.0f} Hz – outside the hard limits"
    # a melody line that fits keeps one octave: no octave jump inside (that sounds like a wrong note)
    line = np.repeat([220.0, 247.0, 277.0, 330.0, 294.0, 247.0], 25)
    moved = np.round(np.log2(vs.shape_f0(line, frame, fill_ms=0, smooth_ms=0) / line), 3)
    assert len(set(moved)) == 1, f"octave jumps inside a line that fits: {sorted(set(moved))}"
    # a long line that drifts upwards (2 s around 260 Hz, then 2 s around 340 Hz): one octave down its end lies above the comfort zone,
    # two down below it – uncomfortable either way, but it must not change octave in its middle (that sounds like a wrong note)
    long = np.concatenate([np.tile(np.repeat([250.0, 270.0, 260.0, 280.0], 25), 8), np.tile(np.repeat([330.0, 350.0, 340.0, 345.0], 25), 8)])
    moved = np.round(np.log2(vs.shape_f0(long, frame, fill_ms=0, smooth_ms=0) / long), 3)
    assert len(set(moved)) == 1, f"octave change inside a long line: {sorted(set(moved))}"
    tuned = vs.shape_f0(np.full(50, 113.0), frame, low=0, fill_ms=0, smooth_ms=0, tune=1.0)
    assert np.allclose(tuned, 110.0, rtol=1e-3), "tune 1 must snap to the nearest semitone"


def main() -> None:
    for test in (test_prepare, test_follow_gate, test_level, test_even, test_shape_f0):
        test()
        print(f"ok  {test.__name__}")


if __name__ == "__main__":
    main()
