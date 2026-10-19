package de.passimoritz.voiceapi.render;

/**
 * A sound the player starts at a time – and, if {@link #stopMs()} is set, stops again. This is the finished command the game understands:
 * sound name, speed (pitch), volume.
 *
 * @param atMs    start in milliseconds since the beginning of the utterance (can lie before the first sound because of early triggers)
 * @param sound   the name of the sound event ({@code entity.villager.no}, {@code voiceapi:v.a.43})
 * @param pitch   speed 0.5 … 2.0 (one octave down or up)
 * @param volume  volume 0 … 1
 * @param stopMs  when the sound is stopped (ms), or −1 (let it decay)
 * @param segment the index of the sound in the utterance this cue comes from (for display and tests), −1 without
 */
public record Cue(int atMs, String sound, float pitch, float volume, int stopMs, int segment) {

    public Cue {
        if (sound == null || sound.isBlank()) {
            throw new IllegalArgumentException("A cue needs a sound name");
        }
        pitch = Float.isNaN(pitch) ? 1f : Math.max(0.5f, Math.min(2.0f, pitch));
        volume = Float.isNaN(volume) ? 0f : Math.max(0f, Math.min(1f, volume));
        if (stopMs >= 0 && stopMs < atMs) {
            stopMs = atMs;
        }
    }

    /** Whether the sound is stopped. */
    public boolean stops() {
        return stopMs >= 0;
    }

    /** The same cue at another time (the stop moves along). */
    public Cue shifted(int offsetMs) {
        return new Cue(atMs + offsetMs, sound, pitch, volume, stopMs < 0 ? -1 : stopMs + offsetMs, segment);
    }

    /** The same cue with another stop. */
    public Cue stoppedAt(int newStopMs) {
        return new Cue(atMs, sound, pitch, volume, newStopMs, segment);
    }

    /** The same cue with another volume (factor). */
    public Cue scaled(double factor) {
        return new Cue(atMs, sound, pitch, (float) (volume * factor), stopMs, segment);
    }
}
