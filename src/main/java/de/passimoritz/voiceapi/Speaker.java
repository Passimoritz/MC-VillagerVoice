package de.passimoritz.voiceapi;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Die Stimmen: welche Vanilla-Laute eines Wesens die Vokale tragen. Jede Stimme hat eine natürliche Tonlage (die MIDI-Nummer, in der das
 * Wesen im Spiel brummt – der Dorfbewohner bei 43,6 ≈ 100 Hz) und drei Träger:
 * <ul>
 *   <li>{@link #hum()}: der gehaltene Laut („Hmm“), aus dem Vokale, Nasale und Gleitlaute geschnitten werden (er wird gestartet und nach der
 *   Dauer des Lauts gestoppt);</li>
 *   <li>{@link #open()}: der kurze offene Laut („Uh!“), mit dem offene, betonte Vokale beginnen;</li>
 *   <li>{@link #cry()}: der laute Laut (Schrei) für Ausrufe und hervorgehobene Wörter.</li>
 * </ul>
 * Minecraft spielt einen Klang zwischen halber und doppelter Geschwindigkeit, also ±12 Halbtöne um die natürliche Tonlage: das ist der
 * Umfang, in dem die Stimme spricht und singt ({@link #lowest()} … {@link #highest()}). Die Zahlen stammen aus der Vermessung der
 * Minecraft-26.3-Klänge ({@code tools/voicelab.py --atlas}).
 */
public enum Speaker {

    VILLAGER("villager", "Dorfbewohner", 43.6, 1.0,
            new Carrier("entity.villager.no", 54, 150, 329, 64, 0.0),
            new Carrier("entity.villager.hurt", 19, 40, 151, 24, 4.8),
            new Carrier("entity.villager.death", 19, 60, 284, 34, 7.0),
            List.of(new Carrier("entity.villager.ambient", 84, 300, 473, 269, -1.4), new Carrier("entity.villager.trade", 64, 200, 239, 189, 2.2))),

    TRADER("trader", "Fahrender Händler", 55.0, 1.1,
            new Carrier("entity.wandering_trader.no", 94, 180, 548, 114, 6.2),
            new Carrier("entity.wandering_trader.hurt", 17, 60, 254, 34, 8.5),
            new Carrier("entity.wandering_trader.yes", 114, 200, 508, 136, 9.8),
            List.of(new Carrier("entity.wandering_trader.ambient", 139, 250, 314, 234, 2.6), new Carrier("entity.wandering_trader.trade", 69, 200, 628, 234, 1.9))),

    WITCH("witch", "Hexe", 64.0, 1.25,
            new Carrier("entity.witch.ambient", 117, 200, 436, 59, 5.7),
            new Carrier("entity.witch.hurt", 29, 60, 259, 39, 6.1),
            new Carrier("entity.witch.celebrate", 104, 200, 473, 59, 7.6),
            List.of()),

    ALLAY("allay", "Allay", 75.0, 1.1,
            new Carrier("entity.allay.ambient_with_item", 72, 150, 760, 159, -2.2),
            new Carrier("entity.allay.hurt", 104, 150, 545, 114, 7.6),
            new Carrier("entity.allay.item_given", 0, 100, 678, 59, -10.0),
            List.of()),

    PIGLIN("piglin", "Piglin", 41.5, 0.9,
            new Carrier("entity.piglin.ambient", 39, 150, 508, 194, 2.9),
            new Carrier("entity.piglin.hurt", 9, 60, 264, 109, 2.5),
            new Carrier("entity.piglin.angry", 39, 150, 683, 141, 4.6),
            List.of(new Carrier("entity.piglin.jealous", 54, 150, 379, 209, 6.8))),

    ZOMBIE("zombie", "Zombie-Dorfbewohner", 40.0, 0.6,
            new Carrier("entity.zombie_villager.ambient", 104, 250, 1032, 254, 0.3),
            new Carrier("entity.zombie_villager.death", 9, 80, 563, 84, 3.7),
            new Carrier("entity.zombie_villager.hurt", 217, 250, 550, 126, 5.1),
            List.of(new Carrier("entity.zombified_piglin.ambient", 29, 120, 927, 101, -2.4))),

    EVOKER("evoker", "Magier", 46.0, 1.0,
            new Carrier("entity.evoker.prepare_wololo", 29, 60, 1785, 453, -2.6),
            new Carrier("entity.evoker.ambient", 37, 120, 725, 176, 4.3),
            new Carrier("entity.evoker.hurt", 17, 40, 149, 34, 7.5),
            List.of(new Carrier("entity.evoker.prepare_attack", 14, 80, 2052, 872, -5.0))),

    GOAT("goat", "Schreiende Ziege", 64.0, 1.3,
            new Carrier("entity.goat.screaming.ambient", 69, 200, 763, 354, -0.8),
            new Carrier("entity.goat.screaming.hurt", 0, 120, 738, 349, 3.0),
            new Carrier("entity.goat.screaming.hurt", 0, 120, 738, 349, 3.0),
            List.of(new Carrier("entity.goat.ambient", 99, 250, 598, 316, -7.6))),

    CAT("cat", "Katze", 72.0, 1.2,
            new Carrier("entity.cat.ambient", 59, 150, 488, 189, 2.0),
            new Carrier("entity.cat.hurt", 9, 80, 409, 159, -0.1),
            new Carrier("entity.cat.hurt", 9, 80, 409, 159, -0.1),
            List.of(new Carrier("entity.cat.purreow", 194, 300, 710, 351, -15.6)));

    /**
     * Ein Vanilla-Klang, der Vokale trägt.
     *
     * @param sound    der Name des Klang-Ereignisses
     * @param onsetMs  nach so vielen Millisekunden (bei normaler Geschwindigkeit) ist der Laut hörbar – der Renderer startet ihn so viel früher
     * @param stableMs nach so vielen Millisekunden steht seine Tonhöhe (davor rutscht sie) – beim Singen startet der Renderer so viel früher
     * @param lengthMs so lange ist der Laut nutzbar (danach ist er aus oder klingt ab); längere Vokale starten ihn erneut
     * @param attackMs wie weich der Laut einsetzt (Zeit bis zur vollen Lautstärke)
     * @param gainDb   wie viel lauter als das Dorfbewohner-„Hmm“ der Klang ist (wird beim Abspielen ausgeglichen)
     */
    public record Carrier(String sound, int onsetMs, int stableMs, int lengthMs, int attackMs, double gainDb) {

        /** Der Lautstärke-Faktor, der den Klang auf das Dorfbewohner-„Hmm“ bringt (höchstens 1: lauter als 1 geht im Spiel nicht). */
        public float gain() {
            return (float) Math.min(1.0, Math.pow(10.0, -gainDb / 20.0));
        }
    }

    private final String id;
    private final String label;
    private final double naturalPitch;
    private final double rangeScale;
    private final Carrier hum;
    private final Carrier open;
    private final Carrier cry;
    private final List<Carrier> variants;

    Speaker(String id, String label, double naturalPitch, double rangeScale, Carrier hum, Carrier open, Carrier cry, List<Carrier> variants) {
        this.id = id;
        this.label = label;
        this.naturalPitch = naturalPitch;
        this.rangeScale = rangeScale;
        this.hum = hum;
        this.open = open;
        this.cry = cry;
        this.variants = variants;
    }

    /** Die Kennung ({@code villager}), wie sie in {@code .voice}-Dateien steht. */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** Die natürliche Tonlage als MIDI-Nummer (bei Geschwindigkeit 1,0). */
    public double naturalPitch() {
        return naturalPitch;
    }

    /** Die Tonlage, in der die Stimme spricht (etwas unter der Mitte ihres Umfangs, damit Akzente Platz nach oben haben). */
    public double speakingPitch() {
        return naturalPitch - 1.0;
    }

    /** Die Lage, in der die Stimme singt: eine Oktave über ihrem Brummen – so klingen die bekannten Dorfbewohner-Videos (hell, flink). */
    public double singingPitch() {
        return naturalPitch + 12.0;
    }

    /** Der tiefste Ton (halbe Geschwindigkeit). */
    public double lowest() {
        return naturalPitch - 12.0;
    }

    /** Der höchste Ton (doppelte Geschwindigkeit). */
    public double highest() {
        return naturalPitch + 12.0;
    }

    /** Wie lebhaft die Sprachmelodie dieser Stimme ist (1 = normal). */
    public double rangeScale() {
        return rangeScale;
    }

    public Carrier hum() {
        return hum;
    }

    public Carrier open() {
        return open;
    }

    public Carrier cry() {
        return cry;
    }

    /** Weitere gehaltene Laute (für Abwechslung bei langen Texten). */
    public List<Carrier> variants() {
        return variants;
    }

    /** Alle Klang-Ereignisse dieser Stimme. */
    public List<String> sounds() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        all.add(hum.sound());
        all.add(open.sound());
        all.add(cry.sound());
        for (Carrier carrier : variants) {
            all.add(carrier.sound());
        }
        return List.copyOf(all);
    }

    /** Die Geschwindigkeit (0,5 … 2,0), mit der diese Stimme eine Tonhöhe erreicht; außerhalb des Umfangs wird begrenzt. */
    public float rateFor(double midi) {
        return (float) Math.clamp(Math.pow(2.0, (midi - naturalPitch) / 12.0), 0.5, 2.0);
    }

    /** Rückt einen Ton um ganze Oktaven in den Umfang der Stimme (zum Singen). */
    public double fit(double midi) {
        double fitted = midi;
        while (fitted < lowest()) {
            fitted += 12;
        }
        while (fitted > highest()) {
            fitted -= 12;
        }
        return fitted;
    }

    /** Die Stimme zu einer Kennung oder einem Namen. */
    public static Optional<Speaker> find(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String key = text.strip().toLowerCase(Locale.ROOT);
        for (Speaker speaker : values()) {
            if (speaker.id.equals(key) || speaker.name().equalsIgnoreCase(key) || speaker.label.toLowerCase(Locale.ROOT).equals(key)) {
                return Optional.of(speaker);
            }
        }
        return switch (key) {
            case "dorfbewohner", "villager_de", "bauer" -> Optional.of(VILLAGER);
            case "haendler", "händler", "wandering_trader", "wanderhändler" -> Optional.of(TRADER);
            case "hexe" -> Optional.of(WITCH);
            case "zombie_villager", "zombiedorfbewohner" -> Optional.of(ZOMBIE);
            case "magier", "beschwoerer", "beschwörer" -> Optional.of(EVOKER);
            case "ziege", "screaming_goat" -> Optional.of(GOAT);
            case "katze" -> Optional.of(CAT);
            default -> Optional.empty();
        };
    }
}
