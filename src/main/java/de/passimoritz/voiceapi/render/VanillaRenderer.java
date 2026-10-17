package de.passimoritz.voiceapi.render;

import de.passimoritz.voiceapi.Segment;
import de.passimoritz.voiceapi.Speaker;
import de.passimoritz.voiceapi.Utterance;
import de.passimoritz.voiceapi.lang.Phoneme;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Sprache aus Vanilla-Klängen – ohne Ressourcenpaket, für jeden Spieler.
 * <p>
 * <b>Vokale</b> kommen aus den Lauten der Stimme ({@link Speaker}): Das gehaltene „Hmm“ wird mit der Tonhöhe der Silbe gestartet (etwas
 * früher, weil der Laut im Klang erst nach einigen Millisekunden einsetzt) und am Ende des Vokals gestoppt – so entsteht aus einem Brummen
 * jede gewünschte Silbenlänge. Helle Vokale (i, e) spielen etwas höher, dunkle (u, o) etwas tiefer: Mit der Tonhöhe wandern auch die
 * Formanten, und das Ohr hört den Vokal mit. Offene betonte Vokale (a) beginnen mit dem kurzen offenen Laut („Uh!“), Ausrufe mit dem Schrei.
 * Diphthonge und Gleiten der Melodie innerhalb eines Vokals werden in zwei Stufen gespielt.
 * <p>
 * <b>Konsonanten</b> sind Geräusche von Blöcken und Tieren, die den Mundgeräuschen am nächsten kommen: Sand für s und sch, Kies für den
 * ach-Laut, ein Pferdeatmen für h und f, ein Knopfklick für t, ein Lavablubb für p, Steinsetzen für k und g … Stimmhafte Konsonanten
 * (b, d, g, v, z, w) bekommen dazu ein leises, kurzes „Hmm“ als Stimmton. Nasale, l, r, w und j sind kurze Hmm-Laute in passender Färbung.
 */
public final class VanillaRenderer implements Renderer {

    public static final VanillaRenderer INSTANCE = new VanillaRenderer();

    /** Wie stark Helligkeit und Dunkelheit eines Vokals die Tonhöhe färben (Halbtöne von u bis i). */
    private static final double COLOUR_SEMITONES = 5.0;
    /** Zweite Stufe eines Gleitens: wenn ein Vokal mindestens so lang ist (ms). */
    private static final int GLIDE_MIN_MS = 110;

    /**
     * Ein Geräusch für einen Konsonanten.
     *
     * @param sound  das Klang-Ereignis
     * @param rate   die Geschwindigkeit (färbt: schneller = heller und kürzer)
     * @param gainDb wie viel lauter als das Dorfbewohner-„Hmm“ der Klang von Natur aus ist (wird ausgeglichen)
     * @param maxMs  wie lange der Klang bei Geschwindigkeit 1 nutzbar ist (danach Stopp)
     * @param voiced ob dazu ein Stimmton (leises Hmm) spielt
     */
    public record Noise(String sound, float rate, double gainDb, int maxMs, boolean voiced) {

        /** Der Lautstärke-Faktor, der das Geräusch auf das Dorfbewohner-„Hmm“ bringt. */
        float gain() {
            return (float) Math.pow(10.0, -gainDb / 20.0);
        }
    }

    /** Welches Geräusch jeder Konsonant ist (Affrikaten: zwei nacheinander). */
    public static final Map<Phoneme, List<Noise>> NOISES = new EnumMap<>(Phoneme.class);

    static {
        noise(Phoneme.P, new Noise("block.lava.pop", 1.3f, -1.7, 84, false));
        noise(Phoneme.B, new Noise("block.wool.hit", 0.9f, -9.5, 116, true));
        noise(Phoneme.T, new Noise("block.wooden_button.click_on", 1.2f, -9.3, 49, false));
        noise(Phoneme.D, new Noise("entity.generic.small_fall", 1.1f, -0.1, 64, true));
        noise(Phoneme.K, new Noise("block.calcite.place", 0.9f, -8.1, 69, false));
        noise(Phoneme.G, new Noise("block.stone.place", 0.8f, -7.7, 71, true));
        noise(Phoneme.F, new Noise("entity.horse.breathe", 1.5f, -11.8, 419, false));
        noise(Phoneme.V, new Noise("entity.horse.breathe", 1.2f, -11.8, 419, true));
        noise(Phoneme.S, new Noise("block.suspicious_sand.step", 1.3f, -1.5, 239, false));
        noise(Phoneme.Z, new Noise("block.suspicious_sand.step", 1.3f, -1.5, 239, true));
        noise(Phoneme.SH, new Noise("block.sand.step", 0.8f, -2.9, 169, false));
        noise(Phoneme.ZH, new Noise("block.sand.step", 0.8f, -2.9, 169, true));
        noise(Phoneme.TH, new Noise("block.sand.step", 0.7f, -2.9, 169, false));
        noise(Phoneme.DH, new Noise("block.sand.step", 0.7f, -2.9, 169, true));
        noise(Phoneme.H, new Noise("entity.horse.breathe", 1.0f, -11.8, 419, false));
        noise(Phoneme.X, new Noise("block.gravel.step", 0.8f, -7.0, 226, false));
        noise(Phoneme.XI, new Noise("block.sand.step", 1.6f, -2.9, 169, false));
        NOISES.put(Phoneme.TS, List.of(new Noise("block.wooden_button.click_on", 1.2f, -9.3, 49, false), new Noise("block.suspicious_sand.step", 1.3f, -1.5, 239, false)));
        NOISES.put(Phoneme.PF, List.of(new Noise("block.lava.pop", 1.3f, -1.7, 84, false), new Noise("entity.horse.breathe", 1.5f, -11.8, 419, false)));
        NOISES.put(Phoneme.CH, List.of(new Noise("block.wooden_button.click_on", 1.2f, -9.3, 49, false), new Noise("block.sand.step", 0.8f, -2.9, 169, false)));
        NOISES.put(Phoneme.DJ, List.of(new Noise("entity.generic.small_fall", 1.1f, -0.1, 64, true), new Noise("block.sand.step", 0.8f, -2.9, 169, true)));
    }

    private static void noise(Phoneme phoneme, Noise noise) {
        NOISES.put(phoneme, List.of(noise));
    }

    /** Die Färbung (Halbtöne) der klingenden Konsonanten gegenüber der Silben-Tonhöhe. */
    private static double colour(Phoneme phoneme) {
        return switch (phoneme) {
            case M -> -2.0;
            case N -> 0.0;
            case NG -> -1.0;
            case L -> -1.0;
            case R -> -2.5;
            case W -> -3.5;
            case Y -> 4.0;
            default -> (phoneme.brightness() - 0.5) * COLOUR_SEMITONES;
        };
    }

    /** Alle Klang-Ereignisse, die dieser Renderer benutzt (für Prüfungen gegen die Vanilla-Liste). */
    public static List<String> sounds() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        for (Speaker speaker : Speaker.values()) {
            all.addAll(speaker.sounds());
        }
        for (List<Noise> noises : NOISES.values()) {
            for (Noise noise : noises) {
                all.add(noise.sound());
            }
        }
        return List.copyOf(all);
    }

    private VanillaRenderer() {
    }

    @Override
    public String name() {
        return "vanilla";
    }

    @Override
    public List<Cue> render(Utterance utterance) {
        Speaker speaker = utterance.voice();
        List<Cue> cues = new ArrayList<>(utterance.segments().size() * 2);
        List<Segment> segments = utterance.segments();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.isPause()) {
                continue;
            }
            Phoneme phoneme = segment.phoneme();
            if (phoneme.isVocalic()) {
                vowel(cues, speaker, segment, i, utterance.sung());
            } else if (phoneme.isSonorant()) {
                sonorant(cues, speaker, segment, i, utterance.sung());
            } else if (phoneme.isConsonant() && !utterance.sung()) {
                // beim Singen keine Geräusche: Dorfbewohner singen „Hmm“ (wie in den bekannten Videos) – die Melodie zählt
                consonant(cues, speaker, segment, i);
            }
        }
        return Renderer.finish(cues);
    }

    // ── Vokale ──

    private static void vowel(List<Cue> cues, Speaker speaker, Segment segment, int index, boolean sung) {
        Phoneme phoneme = segment.phoneme();
        int start = segment.startMs();
        int end = segment.endMs();
        int dur = segment.durMs();
        double colourStart = sung ? 0 : colour(phoneme.start() != null ? phoneme.start() : phoneme);
        double colourEnd = sung ? 0 : colour(phoneme.end() != null ? phoneme.end() : phoneme);
        double pitchStart = segment.pitch() + colourStart;
        double pitchEnd = segment.pitchEnd() + colourEnd;
        boolean glide = dur >= GLIDE_MIN_MS && (phoneme.isDiphthong() || Math.abs(pitchEnd - pitchStart) >= 1.0);
        float volume = segment.volume() * MASTER;
        // Beginn: offener Laut oder Schrei bei offenen betonten Vokalen
        // Grunz- und Schrei-Einsätze haben keine verlässliche Tonhöhe: beim Singen nur das gehaltene Hmm
        boolean open = !sung && phoneme.openness() >= 0.6 && segment.stressed();
        Speaker.Carrier onset = null;
        if (open && segment.volume() >= 0.95f) {
            onset = speaker.cry();
        } else if (open) {
            onset = speaker.open();
        }
        if (onset != null) {
            float rate = speaker.rateFor(pitchStart);
            int usable = (int) ((onset.lengthMs() - onset.onsetMs()) / rate);
            int stop = Math.min(end, start + usable);
            cues.add(new Cue(start - (int) (onset.onsetMs() / rate), onset.sound(), rate, volume * onset.gain(), stop, index));
            if (dur <= usable * 0.8) {
                return;
            }
            // der Rest des Vokals auf dem Hmm, leicht überlappend
            start = start + Math.min(usable, dur) / 2;
            volume *= 0.9f;
        }
        if (!glide) {
            hum(cues, speaker, speaker.hum(), (pitchStart + pitchEnd) / 2, start, end, volume, index, sung);
            return;
        }
        int middle = start + (end - start) / 2;
        hum(cues, speaker, speaker.hum(), pitchStart, start, middle, volume, index, sung);
        hum(cues, speaker, speaker.hum(), pitchEnd, middle, end, volume, index, sung);
    }

    private static void hum(List<Cue> cues, Speaker speaker, Speaker.Carrier carrier, double pitch, int from, int to, float volume, int index) {
        hum(cues, speaker, carrier, pitch, from, to, volume, index, false);
    }

    /**
     * Ein gehaltener Laut von {@code from} bis {@code to} (ms): vorgezogen um den Einsatz (beim Singen bis die Tonhöhe steht – der Rutscher davor
     * wird zum Portamento in den Ton), bei Bedarf neu gestartet, am Ende gestoppt.
     */
    private static void hum(List<Cue> cues, Speaker speaker, Speaker.Carrier carrier, double pitch, int from, int to, float volume, int index, boolean sung) {
        if (to <= from) {
            return;
        }
        float rate = speaker.rateFor(pitch);
        int lead = (int) ((sung ? Math.max(carrier.onsetMs(), carrier.stableMs()) : carrier.onsetMs()) / rate);
        int usable = Math.max(60, (int) ((carrier.lengthMs() - (sung ? Math.max(carrier.onsetMs(), carrier.stableMs()) : carrier.onsetMs())) / rate));
        float gain = volume * carrier.gain();
        int dur = to - from;
        if (dur < carrier.attackMs() / rate) {
            // kurze Vokale erreichen die volle Lautstärke des weichen Einsatzes nicht: etwas anheben
            gain *= 1.25f;
        }
        int at = from;
        while (at < to) {
            int stop = Math.min(to, at + usable);
            cues.add(new Cue(at - lead, carrier.sound(), rate, Math.min(1f, gain), stop, index));
            at = stop - Math.min(30, usable / 4);
            if (stop >= to) {
                break;
            }
        }
    }

    // ── klingende Konsonanten ──

    private static void sonorant(List<Cue> cues, Speaker speaker, Segment segment, int index, boolean sung) {
        double pitch = segment.midPitch() + (sung ? 0 : colour(segment.phoneme()));
        float volume = segment.volume() * MASTER * (sung ? 1f : segment.phoneme().kind() == Phoneme.Kind.NASAL ? 0.85f : 0.75f);
        hum(cues, speaker, speaker.hum(), pitch, segment.startMs(), segment.endMs(), volume, index, sung);
    }

    // ── Geräusch-Konsonanten ──

    private static void consonant(List<Cue> cues, Speaker speaker, Segment segment, int index) {
        List<Noise> noises = NOISES.get(segment.phoneme());
        if (noises == null || noises.isEmpty()) {
            return;
        }
        int start = segment.startMs();
        int share = Math.max(20, segment.durMs() / noises.size());
        for (int n = 0; n < noises.size(); n++) {
            Noise noise = noises.get(n);
            int from = start + n * share;
            int to = n == noises.size() - 1 ? segment.endMs() : from + share;
            int natural = (int) (noise.maxMs() / noise.rate());
            int stop = natural > to - from + 20 ? to : -1;
            float volume = Math.min(1f, segment.volume() * MASTER * noise.gain());
            cues.add(new Cue(from, noise.sound(), noise.rate(), volume, stop, index));
            if (noise.voiced()) {
                // Stimmton: leises Hmm unter dem Geräusch
                hum(cues, speaker, speaker.hum(), segment.midPitch() - 2.0, from, to, segment.volume() * MASTER * 0.35f, index);
            }
        }
    }
}
