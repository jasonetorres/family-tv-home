#!/usr/bin/env python3
"""Original synthesized UI sounds. No samples. CC0, written for this project.

Writes:
  notes/app/src/main/res/raw/note_pop.wav
  notes/app/src/main/res/raw/sparkle.wav
  notes/app/src/main/res/raw/whoosh.wav
  ticker/app/src/main/res/raw/td_cheer.wav
"""
import math
import random
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RATE = 44100


def write(path: Path, samples):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wav:
        wav.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        frames = b"".join(struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples)
        wav.writeframes(frames)
    print(path.relative_to(ROOT), len(samples) / RATE)


def env(t, attack, release, dur):
    if t < attack:
        return t / attack
    if t > dur - release:
        return max(0.0, (dur - t) / release)
    return 1.0


def tone(freq, dur, volume=0.4, attack=0.01, release=0.08):
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        out.append(volume * env(t, attack, release, dur) * math.sin(2 * math.pi * freq * t))
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for track in tracks:
        for i, s in enumerate(track):
            out[i] += s
    peak = max(1e-6, max(abs(s) for s in out))
    return [s / peak * 0.7 for s in out]


def note_pop():
    return mix(tone(523.25, 0.18, 0.35), tone(784.0, 0.22, 0.22, attack=0.02))


def sparkle():
    return mix(
        tone(1046.5, 0.16, 0.25),
        tone(1318.5, 0.20, 0.22, attack=0.03),
        tone(1568.0, 0.28, 0.18, attack=0.06),
    )


def whoosh():
    rng = random.Random(7)
    dur = 0.45
    n = int(RATE * dur)
    out = []
    prev = 0.0
    for i in range(n):
        t = i / RATE
        noise = rng.uniform(-1, 1)
        prev = prev * 0.85 + noise * 0.15
        sweep = 180 + 900 * (t / dur)
        out.append(0.55 * env(t, 0.04, 0.12, dur) * prev * (0.6 + 0.4 * math.sin(2 * math.pi * sweep * t)))
    return out


def cheer():
    # A short original major chord plus a soft noise burst. Not a stadium recording.
    rng = random.Random(11)
    chord = mix(tone(523.25, 0.7, 0.3), tone(659.25, 0.7, 0.25), tone(783.99, 0.75, 0.28), tone(1046.5, 0.55, 0.16, attack=0.04))
    n = len(chord)
    for i in range(n):
        t = i / RATE
        chord[i] += 0.08 * env(t, 0.02, 0.2, n / RATE) * rng.uniform(-1, 1)
    peak = max(abs(s) for s in chord)
    return [s / peak * 0.7 for s in chord]


def main():
    notes = ROOT / "notes/app/src/main/res/raw"
    ticker = ROOT / "ticker/app/src/main/res/raw"
    write(notes / "note_pop.wav", note_pop())
    write(notes / "sparkle.wav", sparkle())
    write(notes / "whoosh.wav", whoosh())
    write(ticker / "td_cheer.wav", cheer())


if __name__ == "__main__":
    main()
