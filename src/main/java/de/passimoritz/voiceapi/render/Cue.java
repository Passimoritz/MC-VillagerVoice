package de.passimoritz.voiceapi.render;

/**
 * Ein Klang, den der Abspieler zu einer Zeit startet – und, wenn {@link #stopMs()} gesetzt ist, wieder stoppt. Das ist der fertige, vom Spiel
 * verstandene Befehl: Klangname, Geschwindigkeit (Tonhöhe), Lautstärke.
 *
 * @param atMs    Beginn in Millisekunden seit dem Anfang des Stücks (kann durch Vor-Trigger auch vor dem ersten Laut liegen)
 * @param sound   der Name des Klang-Ereignisses ({@code entity.villager.no}, {@code voiceapi:v.a.43})
 * @param pitch   Geschwindigkeit 0,5 … 2,0 (eine Oktave nach unten bzw. oben)
 * @param volume  Lautstärke 0 … 1
 * @param stopMs  wann der Klang gestoppt wird (ms), oder −1 (ausklingen lassen)
 * @param segment die Nummer des Lauts im Stück, aus dem der Klang stammt (für Anzeigen und Tests), −1 ohne
 */
public record Cue(int atMs, String sound, float pitch, float volume, int stopMs, int segment) {

    public Cue {
        if (sound == null || sound.isBlank()) {
            throw new IllegalArgumentException("Ein Klang braucht einen Namen");
        }
        pitch = Float.isNaN(pitch) ? 1f : Math.max(0.5f, Math.min(2.0f, pitch));
        volume = Float.isNaN(volume) ? 0f : Math.max(0f, Math.min(1f, volume));
        if (stopMs >= 0 && stopMs < atMs) {
            stopMs = atMs;
        }
    }

    /** Ob der Klang gestoppt wird. */
    public boolean stops() {
        return stopMs >= 0;
    }

    /** Derselbe Klang zu einer anderen Zeit (Stopp verschiebt sich mit). */
    public Cue shifted(int offsetMs) {
        return new Cue(atMs + offsetMs, sound, pitch, volume, stopMs < 0 ? -1 : stopMs + offsetMs, segment);
    }

    /** Derselbe Klang mit früherem Stopp. */
    public Cue stoppedAt(int newStopMs) {
        return new Cue(atMs, sound, pitch, volume, newStopMs, segment);
    }

    /** Derselbe Klang mit anderer Lautstärke (Faktor). */
    public Cue scaled(double factor) {
        return new Cue(atMs, sound, pitch, (float) (volume * factor), stopMs, segment);
    }
}
