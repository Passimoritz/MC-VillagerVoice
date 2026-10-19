package de.passimoritz.voiceapi.sing;

import de.passimoritz.voiceapi.Compat;

/**
 * How it is sung – for the hum notes from the resource pack ({@link Singer}).
 *
 * @param volume         overall volume of the singing (0 … 1; default 0.8)
 * @param dynamics       how strongly the volume of the notes follows the melody (0 = all equally loud, 1 = full variation; default 0.35)
 * @param legatoMs       gaps of up to this many ms between two notes are tied: the note sounds until the next one (default 140)
 * @param overlapMs      tied notes start this many ms earlier and overlap the previous one – no hole, no new attack (default 35)
 * @param glideSemitones from a jump of this many semitones a short passing note is inserted, the transition glides (0 = off; default 3)
 * @param glideMs        length of the passing note (default 45)
 * @param minNoteMs      shorter notes are merged with the previous one instead of being attacked (default 70)
 */
public record SingOptions(double volume, double dynamics, int legatoMs, int overlapMs, double glideSemitones, int glideMs, int minNoteMs) {

    public static final SingOptions DEFAULT = new SingOptions(0.8, 0.35, 140, 35, 3.0, 45, 70);

    /** How the hum notes sounded before these options (every note attacked, full dynamics). */
    public static final SingOptions DETACHED = new SingOptions(0.9, 1.0, 40, 0, 0.0, 45, 0);

    public SingOptions {
        volume = Double.isFinite(volume) ? Compat.clamp(volume, 0.0, 1.0) : 0.8;
        dynamics = Double.isFinite(dynamics) ? Compat.clamp(dynamics, 0.0, 1.0) : 0.35;
        legatoMs = Compat.clamp(legatoMs, 0, 2000);
        overlapMs = Compat.clamp(overlapMs, 0, 200);
        glideSemitones = Double.isFinite(glideSemitones) ? Compat.clamp(glideSemitones, 0.0, 24.0) : 0.0;
        glideMs = Compat.clamp(glideMs, 10, 300);
        minNoteMs = Compat.clamp(minNoteMs, 0, 500);
    }

    public SingOptions withVolume(double value) {
        return new SingOptions(value, dynamics, legatoMs, overlapMs, glideSemitones, glideMs, minNoteMs);
    }

    public SingOptions withDynamics(double value) {
        return new SingOptions(volume, value, legatoMs, overlapMs, glideSemitones, glideMs, minNoteMs);
    }

    public SingOptions withLegato(int gapMs, int overlap) {
        return new SingOptions(volume, dynamics, gapMs, overlap, glideSemitones, glideMs, minNoteMs);
    }

    public SingOptions withGlide(double semitones, int ms) {
        return new SingOptions(volume, dynamics, legatoMs, overlapMs, semitones, ms, minNoteMs);
    }

    public SingOptions withMinNote(int ms) {
        return new SingOptions(volume, dynamics, legatoMs, overlapMs, glideSemitones, glideMs, ms);
    }

    /** Reads options from text: {@code volume=0.7; dynamics=0.3; legato=150; overlap=40; glide=3; glideMs=45; minNote=70} (unknown keys are skipped; the former German keys still work). */
    public static SingOptions parse(String text) {
        SingOptions o = DEFAULT;
        if (text == null || text.isBlank()) {
            return o;
        }
        for (String part : text.split("[;,\\s]+")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).strip().toLowerCase(java.util.Locale.ROOT);
            String value = part.substring(eq + 1).strip();
            try {
                double v = Double.parseDouble(value);
                o = switch (key) {
                    case "volume", "lautstaerke" -> o.withVolume(v);
                    case "dynamics", "dynamik" -> o.withDynamics(v);
                    case "legato", "binden" -> o.withLegato((int) v, o.overlapMs());
                    case "overlap", "ueberlappen" -> o.withLegato(o.legatoMs(), (int) v);
                    case "glide", "gleiten" -> o.withGlide(v, o.glideMs());
                    case "glidems" -> o.withGlide(o.glideSemitones(), (int) v);
                    case "minnote" -> o.withMinNote((int) v);
                    default -> o;
                };
            } catch (NumberFormatException e) {
                // skip
            }
        }
        return o;
    }

    /** The options as text (like {@link #parse}). */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "volume=%.2f; dynamics=%.2f; legato=%d; overlap=%d; glide=%.1f; glideMs=%d; minNote=%d",
                volume, dynamics, legatoMs, overlapMs, glideSemitones, glideMs, minNoteMs);
    }
}
