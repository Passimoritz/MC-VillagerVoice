package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Phoneme;

/**
 * One sound at one time: the smallest building block of an utterance.
 *
 * @param startMs  start in milliseconds since the beginning of the utterance
 * @param durMs    duration in milliseconds
 * @param phoneme  the sound
 * @param pitch    pitch at the start as a MIDI number (middle C is 60; 43 ≈ 98 Hz, a low male voice); for noises the pitch the noise is
 *                 coloured with
 * @param pitchEnd pitch at the end (for gliding within the sound; equal to {@code pitch} = level)
 * @param volume   volume 0 … 1
 * @param word     the number of the word in the utterance (0-based), −1 for pauses
 * @param stressed whether the sound is in a stressed syllable
 */
public record Segment(int startMs, int durMs, Phoneme phoneme, float pitch, float pitchEnd, float volume, int word, boolean stressed) {

    public Segment {
        if (phoneme == null) {
            throw new IllegalArgumentException("A segment needs a phoneme");
        }
        startMs = Math.max(0, startMs);
        durMs = Math.max(0, durMs);
        pitch = clampPitch(pitch);
        pitchEnd = Float.isNaN(pitchEnd) ? pitch : clampPitch(pitchEnd);
        volume = Float.isNaN(volume) ? 0f : Math.max(0f, Math.min(1f, volume));
    }

    private static float clampPitch(float value) {
        return Float.isNaN(value) ? 43f : Math.max(12f, Math.min(108f, value));
    }

    /** A level segment. */
    public Segment(int startMs, int durMs, Phoneme phoneme, float pitch, float volume, int word, boolean stressed) {
        this(startMs, durMs, phoneme, pitch, pitch, volume, word, stressed);
    }

    /** A pause. */
    public static Segment pause(int startMs, int durMs) {
        return new Segment(startMs, durMs, Phoneme.SIL, 43f, 43f, 0f, -1, false);
    }

    public int endMs() {
        return startMs + durMs;
    }

    public boolean isPause() {
        return phoneme.isPause() || durMs == 0 || volume <= 0f;
    }

    /** The pitch in the middle of the sound. */
    public float midPitch() {
        return (pitch + pitchEnd) / 2f;
    }

    /** The same sound at another time. */
    public Segment at(int newStartMs) {
        return new Segment(newStartMs, durMs, phoneme, pitch, pitchEnd, volume, word, stressed);
    }

    /** The same sound with another duration. */
    public Segment lasting(int newDurMs) {
        return new Segment(startMs, newDurMs, phoneme, pitch, pitchEnd, volume, word, stressed);
    }

    /** The same sound, shifted by semitones. */
    public Segment transposed(double semitones) {
        return new Segment(startMs, durMs, phoneme, (float) (pitch + semitones), (float) (pitchEnd + semitones), volume, word, stressed);
    }

    /** The same sound with another (level) pitch. */
    public Segment pitched(double newPitch) {
        return new Segment(startMs, durMs, phoneme, (float) newPitch, (float) newPitch, volume, word, stressed);
    }

    /** The same sound with another volume (factor). */
    public Segment louder(double factor) {
        return new Segment(startMs, durMs, phoneme, pitch, pitchEnd, (float) (volume * factor), word, stressed);
    }
}
