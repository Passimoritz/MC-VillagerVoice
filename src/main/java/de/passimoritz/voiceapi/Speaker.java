package de.passimoritz.voiceapi;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The voices: which vanilla sounds of a creature carry the vowels. Every voice has a natural pitch (the MIDI number at which the creature
 * hums in the game – the villager at 43.6 ≈ 100 Hz) and three carriers:
 * <ul>
 *   <li>{@link #hum()}: the held sound ("Hmm") that vowels, nasals and glides are cut from (it is started and stopped after the duration of
 *   the sound);</li>
 *   <li>{@link #open()}: the short open sound ("Uh!") that open, stressed vowels start with;</li>
 *   <li>{@link #cry()}: the loud sound (scream) for exclamations and emphasised words.</li>
 * </ul>
 * Minecraft plays a sound between half and double speed, so ±12 semitones around the natural pitch: that is the range in which the voice
 * speaks and sings ({@link #lowest()} … {@link #highest()}). The numbers come from measuring the Minecraft 26.3 sounds
 * ({@code tools/voicelab.py --atlas}).
 */
public enum Speaker {

    VILLAGER("villager", "Villager", 43.6, 1.0,
            new Carrier("entity.villager.no", 54, 150, 329, 64, 0.0),
            new Carrier("entity.villager.hurt", 19, 40, 151, 24, 4.8),
            new Carrier("entity.villager.death", 19, 60, 284, 34, 7.0),
            List.of(new Carrier("entity.villager.ambient", 84, 300, 473, 269, -1.4), new Carrier("entity.villager.trade", 64, 200, 239, 189, 2.2))),

    TRADER("trader", "Wandering trader", 55.0, 1.1,
            new Carrier("entity.wandering_trader.no", 94, 180, 548, 114, 6.2),
            new Carrier("entity.wandering_trader.hurt", 17, 60, 254, 34, 8.5),
            new Carrier("entity.wandering_trader.yes", 114, 200, 508, 136, 9.8),
            List.of(new Carrier("entity.wandering_trader.ambient", 139, 250, 314, 234, 2.6), new Carrier("entity.wandering_trader.trade", 69, 200, 628, 234, 1.9))),

    WITCH("witch", "Witch", 64.0, 1.25,
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

    ZOMBIE("zombie", "Zombie villager", 40.0, 0.6,
            new Carrier("entity.zombie_villager.ambient", 104, 250, 1032, 254, 0.3),
            new Carrier("entity.zombie_villager.death", 9, 80, 563, 84, 3.7),
            new Carrier("entity.zombie_villager.hurt", 217, 250, 550, 126, 5.1),
            List.of(new Carrier("entity.zombified_piglin.ambient", 29, 120, 927, 101, -2.4))),

    EVOKER("evoker", "Evoker", 46.0, 1.0,
            new Carrier("entity.evoker.prepare_wololo", 29, 60, 1785, 453, -2.6),
            new Carrier("entity.evoker.ambient", 37, 120, 725, 176, 4.3),
            new Carrier("entity.evoker.hurt", 17, 40, 149, 34, 7.5),
            List.of(new Carrier("entity.evoker.prepare_attack", 14, 80, 2052, 872, -5.0))),

    GOAT("goat", "Screaming goat", 64.0, 1.3,
            new Carrier("entity.goat.screaming.ambient", 69, 200, 763, 354, -0.8),
            new Carrier("entity.goat.screaming.hurt", 0, 120, 738, 349, 3.0),
            new Carrier("entity.goat.screaming.hurt", 0, 120, 738, 349, 3.0),
            List.of(new Carrier("entity.goat.ambient", 99, 250, 598, 316, -7.6))),

    CAT("cat", "Cat", 72.0, 1.2,
            new Carrier("entity.cat.ambient", 59, 150, 488, 189, 2.0),
            new Carrier("entity.cat.hurt", 9, 80, 409, 159, -0.1),
            new Carrier("entity.cat.hurt", 9, 80, 409, 159, -0.1),
            List.of(new Carrier("entity.cat.purreow", 194, 300, 710, 351, -15.6)));

    /**
     * A vanilla sound that carries vowels.
     *
     * @param sound    the name of the sound event
     * @param onsetMs  after this many milliseconds (at normal speed) the sound is audible – the renderer starts it this much earlier
     * @param stableMs after this many milliseconds its pitch is stable (before that it slides) – when singing the renderer starts it this much earlier
     * @param lengthMs this long the sound is usable (after that it is over or decays); longer vowels restart it
     * @param attackMs how softly the sound starts (time to full volume)
     * @param gainDb   how much louder than the villager "Hmm" the sound is (compensated on playback)
     */
    public record Carrier(String sound, int onsetMs, int stableMs, int lengthMs, int attackMs, double gainDb) {

        /** The volume factor that brings the sound to the level of the villager "Hmm" (at most 1: the game cannot play louder than 1). */
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

    /** The id ({@code villager}) as written in {@code .voice} files. */
    public String id() {
        return id;
    }

    /** The display name. */
    public String label() {
        return label;
    }

    /** The natural pitch as a MIDI number (at speed 1.0). */
    public double naturalPitch() {
        return naturalPitch;
    }

    /** The pitch the voice speaks at (a little below the middle of its range, so accents have room upwards). */
    public double speakingPitch() {
        return naturalPitch - 1.0;
    }

    /** The register the voice sings in: about five semitones above its hum (fundamental around 130 Hz) – that is where the singing sits in
     * the well-known villager videos (measured on the spectrum of their vocal track). */
    public double singingPitch() {
        return naturalPitch + 5.0;
    }

    /** The lowest note (half speed). */
    public double lowest() {
        return naturalPitch - 12.0;
    }

    /** The highest note (double speed). */
    public double highest() {
        return naturalPitch + 12.0;
    }

    /** How lively the speech melody of this voice is (1 = normal). */
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

    /** More held sounds (for variety in long texts). */
    public List<Carrier> variants() {
        return variants;
    }

    /** All sound events of this voice. */
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

    /** The speed (0.5 … 2.0) at which this voice reaches a pitch; outside the range it is clamped. */
    public float rateFor(double midi) {
        return (float) Compat.clamp(Math.pow(2.0, (midi - naturalPitch) / 12.0), 0.5, 2.0);
    }

    /** Moves a note by whole octaves into the range of the voice (for singing). */
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

    /** The voice for an id or a name (also the former German names). */
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
            case "haendler", "händler", "wandering_trader", "wanderhändler", "fahrender händler" -> Optional.of(TRADER);
            case "hexe" -> Optional.of(WITCH);
            case "zombie_villager", "zombiedorfbewohner", "zombie-dorfbewohner" -> Optional.of(ZOMBIE);
            case "magier", "beschwoerer", "beschwörer" -> Optional.of(EVOKER);
            case "ziege", "screaming_goat", "schreiende ziege" -> Optional.of(GOAT);
            case "katze" -> Optional.of(CAT);
            default -> Optional.empty();
        };
    }
}
