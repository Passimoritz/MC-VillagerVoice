package de.passimoritz.voiceapi.sing;

import de.passimoritz.voiceapi.Compat;

/**
 * A note of a melody that is sung to.
 *
 * @param startMs start in milliseconds
 * @param durMs   duration in milliseconds
 * @param pitch   pitch as a MIDI number (middle C is 60); the voice moves it by whole octaves into its range
 * @param volume  volume 0 … 1
 * @param word    the word of the text this note belongs to (0-based), −1 = syllables are distributed in order
 */
public record MelodyNote(int startMs, int durMs, double pitch, float volume, int word) {

    public MelodyNote {
        startMs = Math.max(0, startMs);
        durMs = Math.max(1, durMs);
        pitch = Double.isFinite(pitch) ? Compat.clamp(pitch, 12.0, 108.0) : 60.0;
        volume = Float.isNaN(volume) ? 0.8f : Math.max(0.01f, Math.min(1f, volume));
        word = Math.max(-1, word);
    }

    /** A note without a word (the syllables are distributed in order). */
    public MelodyNote(int startMs, int durMs, double pitch, float volume) {
        this(startMs, durMs, pitch, volume, -1);
    }

    /** A note at the default volume. */
    public MelodyNote(int startMs, int durMs, double pitch) {
        this(startMs, durMs, pitch, 0.85f, -1);
    }

    /** The same note, assigned to a word of the text (0-based): its syllables get exactly these notes. */
    public MelodyNote forWord(int index) {
        return new MelodyNote(startMs, durMs, pitch, volume, index);
    }

    public int endMs() {
        return startMs + durMs;
    }
}
