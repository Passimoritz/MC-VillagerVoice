package de.passimoritz.voiceapi;

import java.util.Locale;
import java.util.Optional;

/**
 * Wie gesprochen wird: Tempo, Tonlage, Melodie-Umfang, Kraft und Stimmung. Alle Werte sind relativ zur Stimme ({@link Speaker}): {@code pitch}
 * verschiebt ihre natürliche Lage in Halbtönen, {@code range} dehnt oder staucht die Sprachmelodie, {@code energy} macht lauter und härter.
 *
 * @param rate   Sprechtempo (1 = normal; 0,7 langsam, 1,4 schnell)
 * @param pitch  Verschiebung der Tonlage in Halbtönen (−12 … +12)
 * @param range  Umfang der Sprachmelodie (1 = normal; 0 = monoton, 2 = sehr lebhaft)
 * @param energy Kraft (0,5 = leise und weich … 1,5 = laut und hart)
 * @param mood   Stimmung (färbt Melodie, Tempo und Pausen)
 */
public record Style(double rate, double pitch, double range, double energy, Mood mood) {

    /** Die Stimmungen. */
    public enum Mood {
        /** Sachlich. */
        NEUTRAL,
        /** Fröhlich: höher, lebhafter, schneller. */
        HAPPY,
        /** Traurig: tiefer, flacher, langsamer, längere Pausen. */
        SAD,
        /** Wütend: lauter, hart, fallende Akzente. */
        ANGRY,
        /** Ängstlich: höher, zittrig, schnell, kurze Pausen. */
        SCARED,
        /** Ruhig: gleichmäßig, langsam, weich. */
        CALM,
        /** Aufgeregt: schnell, großer Umfang, laut. */
        EXCITED,
        /** Flüstern: leise, fast monoton. */
        WHISPER,
        /** Roboter: monoton, gleich lange Silben. */
        ROBOT,
        /** Müde: langsam, tief, nachlassend. */
        TIRED;

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
        rate = Double.isFinite(rate) ? Math.clamp(rate, 0.4, 2.5) : 1.0;
        pitch = Double.isFinite(pitch) ? Math.clamp(pitch, -24.0, 24.0) : 0.0;
        range = Double.isFinite(range) ? Math.clamp(range, 0.0, 3.0) : 1.0;
        energy = Double.isFinite(energy) ? Math.clamp(energy, 0.1, 2.0) : 1.0;
        mood = mood == null ? Mood.NEUTRAL : mood;
    }

    /** Ein Stil nur aus einer Stimmung. */
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

    /** Der Stil als Text ({@code rate=1.2; pitch=-3; range=1; energy=1; mood=happy}), wie er in {@code .voice}-Dateien steht. */
    public String describe() {
        return String.format(Locale.ROOT, "rate=%.2f; pitch=%.1f; range=%.2f; energy=%.2f; mood=%s", rate, pitch, range, energy,
                mood.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Liest einen Stil aus Text: Paare {@code name=wert} durch Semikolon oder Leerzeichen getrennt; ein einzelnes Wort ist eine Stimmung
     * ({@code happy}, {@code traurig}). Unbekanntes wird überlesen.
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
                        // unbekannt: überlesen
                    }
                }
            } catch (NumberFormatException e) {
                // unlesbarer Wert: überlesen
            }
        }
        return style;
    }
}
