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
 * Speech from vanilla sounds – without a resource pack, for every player.
 * <p>
 * <b>Vowels</b> come from the sounds of the voice ({@link Speaker}): the held "Hmm" is started at the pitch of the syllable (a little earlier,
 * because the sound only sets in after a few milliseconds) and stopped at the end of the vowel – so a hum gives any syllable length. Bright
 * vowels (i, e) play a little higher, dark ones (u, o) a little lower: the formants move with the pitch, and the ear hears the vowel along.
 * Open stressed vowels (a) start with the short open sound ("Uh!"), exclamations with the scream. Diphthongs and melody glides within a
 * vowel are played in two steps.
 * <p>
 * <b>Consonants</b> are block and animal noises that come closest to mouth noises: sand for s and sh, gravel for the "ach" sound, a horse
 * breathing for h and f, a button click for t, a lava pop for p, stone placing for k and g … Voiced consonants (b, d, g, v, z, w) also get a
 * quiet, short "Hmm" as voicing. Nasals, l, r, w and j are short hum sounds in a matching colour.
 */
public final class VanillaRenderer implements Renderer {

    public static final VanillaRenderer INSTANCE = new VanillaRenderer();

    /** How strongly brightness and darkness of a vowel colour the pitch (semitones from u to i). */
    private static final double COLOUR_SEMITONES = 5.0;
    /** Second step of a glide: if a vowel is at least this long (ms). */
    private static final int GLIDE_MIN_MS = 110;

    /**
     * A noise for a consonant.
     *
     * @param sound  the sound event
     * @param rate   the speed (colours it: faster = brighter and shorter)
     * @param gainDb how much louder than the villager "Hmm" the sound naturally is (compensated)
     * @param maxMs  how long the sound is usable at speed 1 (then stop)
     * @param voiced whether a voicing tone (quiet hum) plays with it
     */
    public record Noise(String sound, float rate, double gainDb, int maxMs, boolean voiced) {

        /** The volume factor that brings the noise to the level of the villager "Hmm". */
        float gain() {
            return (float) Math.pow(10.0, -gainDb / 20.0);
        }
    }

    /** Which noise every consonant is (affricates: two in a row). */
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

    /** The colouring (semitones) of the voiced consonants relative to the syllable pitch. */
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

    /** All sound events this renderer uses (for checks against the vanilla list). */
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
                // no noises when singing: villagers sing "Hmm" (like in the well-known videos) – the melody is what counts
                consonant(cues, speaker, segment, i);
            }
        }
        return Renderer.finish(cues);
    }

    // ── Vowels ──

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
        // onset: open sound or scream for open stressed vowels
        // grunt and scream onsets have no reliable pitch: when singing only the held hum
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
            // the rest of the vowel on the hum, slightly overlapping
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
     * A held sound from {@code from} to {@code to} (ms): started earlier by its onset (when singing until the pitch is stable – the slide
     * before becomes a portamento into the note), restarted if needed, stopped at the end.
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
            // short vowels do not reach the full volume of the soft attack: raise them a little
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

    // ── Voiced consonants ──

    private static void sonorant(List<Cue> cues, Speaker speaker, Segment segment, int index, boolean sung) {
        double pitch = segment.midPitch() + (sung ? 0 : colour(segment.phoneme()));
        float volume = segment.volume() * MASTER * (sung ? 1f : segment.phoneme().kind() == Phoneme.Kind.NASAL ? 0.85f : 0.75f);
        hum(cues, speaker, speaker.hum(), pitch, segment.startMs(), segment.endMs(), volume, index, sung);
    }

    // ── Noise consonants ──

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
                // voicing: quiet hum under the noise
                hum(cues, speaker, speaker.hum(), segment.midPitch() - 2.0, from, to, segment.volume() * MASTER * 0.35f, index);
            }
        }
    }
}
