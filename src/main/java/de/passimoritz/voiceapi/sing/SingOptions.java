package de.passimoritz.voiceapi.sing;

import de.passimoritz.voiceapi.Compat;

/**
 * Wie gesungen wird – für die Hmm-Noten aus dem Ressourcenpaket ({@link Singer}).
 *
 * @param volume         Gesamt-Lautstärke des Gesangs (0 … 1; Standard 0,8)
 * @param dynamics       wie stark die Lautstärke der Töne der Melodie folgt (0 = alle gleich laut, 1 = volle Schwankung; Standard 0,35)
 * @param legatoMs       Lücken bis zu so vielen ms zwischen zwei Tönen werden gebunden: der Ton klingt bis zum nächsten durch (Standard 140)
 * @param overlapMs      gebundene Töne setzen so viele ms früher ein und überlappen den vorigen – kein Loch, kein Neuanschlag (Standard 35)
 * @param glideSemitones ab so vielen Halbtönen Sprung wird ein kurzer Zwischenton eingeschoben, der Übergang gleitet (0 = aus; Standard 3)
 * @param glideMs        Länge des Zwischentons (Standard 45)
 * @param minNoteMs      kürzere Töne werden mit dem vorigen zusammengelegt statt angeschlagen (Standard 70)
 */
public record SingOptions(double volume, double dynamics, int legatoMs, int overlapMs, double glideSemitones, int glideMs, int minNoteMs) {

    public static final SingOptions DEFAULT = new SingOptions(0.8, 0.35, 140, 35, 3.0, 45, 70);

    /** Wie die Hmm-Noten vor diesen Optionen klangen (jeder Ton angeschlagen, volle Dynamik). */
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

    /** Liest Optionen aus Text: {@code volume=0.7; dynamics=0.3; legato=150; overlap=40; glide=3; glideMs=45; minNote=70} (Unbekanntes wird überlesen). */
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
                // ueberlesen
            }
        }
        return o;
    }

    /** Die Optionen als Text (wie {@link #parse}). */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "volume=%.2f; dynamics=%.2f; legato=%d; overlap=%d; glide=%.1f; glideMs=%d; minNote=%d",
                volume, dynamics, legatoMs, overlapMs, glideSemitones, glideMs, minNoteMs);
    }
}
