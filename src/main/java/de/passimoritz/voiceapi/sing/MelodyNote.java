package de.passimoritz.voiceapi.sing;

/**
 * Ein Ton einer Melodie, auf den gesungen wird.
 *
 * @param startMs Beginn in Millisekunden
 * @param durMs   Dauer in Millisekunden
 * @param pitch   Tonhöhe als MIDI-Nummer (das mittlere C ist 60); die Stimme rückt ihn um ganze Oktaven in ihren Umfang
 * @param volume  Lautstärke 0 … 1
 */
public record MelodyNote(int startMs, int durMs, double pitch, float volume, int word) {

    public MelodyNote {
        startMs = Math.max(0, startMs);
        durMs = Math.max(1, durMs);
        pitch = Double.isFinite(pitch) ? Math.clamp(pitch, 12.0, 108.0) : 60.0;
        volume = Float.isNaN(volume) ? 0.8f : Math.max(0.01f, Math.min(1f, volume));
        word = Math.max(-1, word);
    }

    /** Ein Ton ohne Zuordnung zu einem Wort (die Silben werden der Reihe nach verteilt). */
    public MelodyNote(int startMs, int durMs, double pitch, float volume) {
        this(startMs, durMs, pitch, volume, -1);
    }

    /** Ein Ton mit voller Lautstärke. */
    public MelodyNote(int startMs, int durMs, double pitch) {
        this(startMs, durMs, pitch, 0.85f, -1);
    }

    /** Derselbe Ton, einem Wort des Textes zugeordnet (0-basiert): seine Silben bekommen genau diese Töne. */
    public MelodyNote forWord(int index) {
        return new MelodyNote(startMs, durMs, pitch, volume, index);
    }

    public int endMs() {
        return startMs + durMs;
    }
}
