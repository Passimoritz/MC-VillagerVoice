package de.passimoritz.voiceapi;

import java.util.Locale;
import java.util.Optional;

/**
 * How something is said: tempo, pitch, melody range, power and mood. All values are relative to the voice ({@link Speaker}): {@code pitch}
 * shifts its natural register in semitones, {@code range} stretches or squeezes the speech melody, {@code energy} makes it louder and harder.
 *
 * @param rate   speaking rate (1 = normal; 0.7 slow, 1.4 fast)
 * @param pitch  shift of the register in semitones (−12 … +12)
 * @param range  range of the speech melody (1 = normal; 0 = monotone, 2 = very lively)
 * @param energy power (0.5 = quiet and soft … 1.5 = loud and hard)
 * @param mood   mood (colours melody, tempo and pauses)
 */
public record Style(double rate, double pitch, double range, double energy, Mood mood) {

    /** The moods. */
    public enum Mood {
        /** Matter-of-fact. */
        NEUTRAL,
        /** Happy: higher, livelier, faster. */
        HAPPY,
        /** Sad: lower, flatter, slower, longer pauses. */
        SAD,
        /** Angry: louder, hard, falling accents. */
        ANGRY,
        /** Scared: higher, shaky, fast, short pauses. */
        SCARED,
        /** Calm: even, slow, soft. */
        CALM,
        /** Excited: fast, wide range, loud. */
        EXCITED,
        /** Whisper: quiet, almost monotone. */
        WHISPER,
        /** Robot: monotone, syllables of equal length. */
        ROBOT,
        /** Tired: slow, low, fading. */
        TIRED;

        /** The mood for a name (English, also the German words). */
        public static Optional<Mood> find(String text) {
            if (text == null) {
                return Optional.empty();
            }
            String key = text.strip().toLowerCase(Locale.ROOT);
            return switch (key) {
                case "neutral", "normal", "sachlich" -> Optional.of(NEUTRAL);
                case "happy", "froehlich", "fröhlich", "glücklich", "gluecklich", "froh" -> Optional.of(HAPPY);
                case "sad", "traurig" -> Optional.of(SAD);
                case "angry", "wuetend", "wütend", "boese", "böse", "zornig" -> Optional.of(ANGRY);
                case "scared", "aengstlich", "ängstlich", "angst", "afraid" -> Optional.of(SCARED);
                case "calm", "ruhig", "gelassen" -> Optional.of(CALM);
                case "excited", "aufgeregt", "begeistert" -> Optional.of(EXCITED);
                case "whisper", "fluestern", "flüstern", "leise" -> Optional.of(WHISPER);
                case "robot", "roboter" -> Optional.of(ROBOT);
                case "tired", "muede", "müde" -> Optional.of(TIRED);
                default -> Optional.empty();
            };
        }
    }

    public static final Style DEFAULT = new Style(1.0, 0.0, 1.0, 1.0, Mood.NEUTRAL);

    public Style {
        rate = Double.isFinite(rate) ? Compat.clamp(rate, 0.4, 2.5) : 1.0;
        pitch = Double.isFinite(pitch) ? Compat.clamp(pitch, -24.0, 24.0) : 0.0;
        range = Double.isFinite(range) ? Compat.clamp(range, 0.0, 3.0) : 1.0;
        energy = Double.isFinite(energy) ? Compat.clamp(energy, 0.1, 2.0) : 1.0;
        mood = mood == null ? Mood.NEUTRAL : mood;
    }

    /** A style made only of a mood. */
    public static Style of(Mood mood) {
        return DEFAULT.withMood(mood);
    }

    public Style withRate(double newRate) {
        return new Style(newRate, pitch, range, energy, mood);
    }

    public Style withPitch(double newPitch) {
        return new Style(rate, newPitch, range, energy, mood);
    }

    public Style withRange(double newRange) {
        return new Style(rate, pitch, newRange, energy, mood);
    }

    public Style withEnergy(double newEnergy) {
        return new Style(rate, pitch, range, newEnergy, mood);
    }

    public Style withMood(Mood newMood) {
        return new Style(rate, pitch, range, energy, newMood);
    }

    /** The style as text ({@code rate=1.2; pitch=-3; range=1; energy=1; mood=happy}) as written in {@code .voice} files. */
    public String describe() {
        return String.format(Locale.ROOT, "rate=%.2f; pitch=%.1f; range=%.2f; energy=%.2f; mood=%s", rate, pitch, range, energy,
                mood.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Reads a style from text: pairs {@code name=value} separated by semicolons or spaces; a single word is a mood ({@code happy},
     * {@code sad}). Unknown parts are skipped; the former German keys ({@code tonlage}, {@code umfang}, {@code kraft}, {@code stimmung})
     * still work.
     */
    public static Style parse(String text) {
        Style style = DEFAULT;
        if (text == null || text.isBlank()) {
            return style;
        }
        for (String part : text.split("[;,\\s]+")) {
            String piece = part.strip();
            if (piece.isEmpty()) {
                continue;
            }
            int eq = piece.indexOf('=');
            if (eq < 0) {
                Optional<Mood> mood = Mood.find(piece);
                if (mood.isPresent()) {
                    style = style.withMood(mood.get());
                }
                continue;
            }
            String key = piece.substring(0, eq).strip().toLowerCase(Locale.ROOT);
            String value = piece.substring(eq + 1).strip();
            try {
                switch (key) {
                    case "rate", "tempo", "speed" -> style = style.withRate(Double.parseDouble(value));
                    case "pitch", "tonlage" -> style = style.withPitch(Double.parseDouble(value));
                    case "range", "umfang" -> style = style.withRange(Double.parseDouble(value));
                    case "energy", "kraft", "volume" -> style = style.withEnergy(Double.parseDouble(value));
                    case "mood", "stimmung" -> style = style.withMood(Mood.find(value).orElse(style.mood()));
                    default -> {
                        // unknown: skip
                    }
                }
            } catch (NumberFormatException e) {
                // unreadable value: skip
            }
        }
        return style;
    }
}
