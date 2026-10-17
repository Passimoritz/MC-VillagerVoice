package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Phoneme;

/**
 * Ein Laut zu einer Zeit: der kleinste Baustein eines Sprechstücks.
 *
 * @param startMs  Beginn in Millisekunden seit dem Anfang des Stücks
 * @param durMs    Dauer in Millisekunden
 * @param phoneme  der Laut
 * @param pitch    Tonhöhe am Anfang als MIDI-Nummer (das mittlere C ist 60; 43 ≈ 98 Hz, eine tiefe Männerstimme); bei Geräuschen die
 *                 Tonhöhe, in der das Geräusch gefärbt wird
 * @param pitchEnd Tonhöhe am Ende (für Gleiten innerhalb des Lauts; gleich {@code pitch} = gerade)
 * @param volume   Lautstärke 0 … 1
 * @param word     die Nummer des Wortes im Stück (0-basiert), −1 bei Pausen
 * @param stressed ob der Laut in einer betonten Silbe steht
 */
public record Segment(int startMs, int durMs, Phoneme phoneme, float pitch, float pitchEnd, float volume, int word, boolean stressed) {

    public Segment {
        if (phoneme == null) {
            throw new IllegalArgumentException("Ein Abschnitt braucht einen Laut");
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

    /** Ein gerader Abschnitt. */
    public Segment(int startMs, int durMs, Phoneme phoneme, float pitch, float volume, int word, boolean stressed) {
        this(startMs, durMs, phoneme, pitch, pitch, volume, word, stressed);
    }

    /** Eine Pause. */
    public static Segment pause(int startMs, int durMs) {
        return new Segment(startMs, durMs, Phoneme.SIL, 43f, 43f, 0f, -1, false);
    }

    public int endMs() {
        return startMs + durMs;
    }

    public boolean isPause() {
        return phoneme.isPause() || durMs == 0 || volume <= 0f;
    }

    /** Die Tonhöhe in der Mitte des Lauts. */
    public float midPitch() {
        return (pitch + pitchEnd) / 2f;
    }

    /** Derselbe Laut zu einer anderen Zeit. */
    public Segment at(int newStartMs) {
        return new Segment(newStartMs, durMs, phoneme, pitch, pitchEnd, volume, word, stressed);
    }

    /** Derselbe Laut mit anderer Dauer. */
    public Segment lasting(int newDurMs) {
        return new Segment(startMs, newDurMs, phoneme, pitch, pitchEnd, volume, word, stressed);
    }

    /** Derselbe Laut, um Halbtöne verschoben. */
    public Segment transposed(double semitones) {
        return new Segment(startMs, durMs, phoneme, (float) (pitch + semitones), (float) (pitchEnd + semitones), volume, word, stressed);
    }

    /** Derselbe Laut mit anderer Tonhöhe (gerade). */
    public Segment pitched(double newPitch) {
        return new Segment(startMs, durMs, phoneme, (float) newPitch, (float) newPitch, volume, word, stressed);
    }

    /** Derselbe Laut mit anderer Lautstärke (Faktor). */
    public Segment louder(double factor) {
        return new Segment(startMs, durMs, phoneme, pitch, pitchEnd, (float) (volume * factor), word, stressed);
    }
}
