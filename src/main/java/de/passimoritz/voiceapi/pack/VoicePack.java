package de.passimoritz.voiceapi.pack;

import de.passimoritz.voiceapi.Compat;

import de.passimoritz.voiceapi.lang.Phoneme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sounds of the VoiceAPI resource pack – the same names as {@code tools/build_voice_pack.py}, which builds them:
 * <ul>
 *   <li>Vowels: {@code voiceapi:v.<vowel>.<root>} – every vowel as a held sound (formant synthesis), one sample every {@value #STEP}
 *   semitones from {@value #ROOT_LOW} to {@value #ROOT_HIGH}; the game stretches by at most two semitones, so the formants (and with them the
 *   vowel) stay recognisable.</li>
 *   <li>Voiced consonants (m, n, ŋ, l, r, w, j): {@code voiceapi:c.<sound>.<root>}, also per root.</li>
 *   <li>Noise consonants: {@code voiceapi:c.<sound>} without pitch.</li>
 *   <li>Singing: {@code voiceapi:hum.<voice>.<root>.<length>} – the real "Hmm" of the voice per root, pulled to the semitone, with a natural
 *   onset and a natural decay (several takes per note, the game picks one). Every note is triggered on its own and decays instead of being
 *   stopped – that is how the well-known villager videos sing. Voices without hum samples sing with the villager hum.</li>
 * </ul>
 * The pack is mixed into the NoteAPI resource pack (NoteAPI does it by itself); {@code assets/voiceapi/voiceapi.json} names the format so
 * the plugin recognises an unsuitable pack.
 */
public final class VoicePack {

    /** How the sounds are named; must match {@code SOUND_FORMAT} in build_voice_pack.py. */
    public static final int FORMAT = 2;
    /** Semitones between two roots. */
    public static final int STEP = 4;
    public static final int ROOT_LOW = 33;
    public static final int ROOT_HIGH = 85;
    /** Length (ms) of a vowel sample. */
    public static final int VOWEL_MS = 1500;
    /** Length (ms) of a voiced consonant sample. */
    public static final int SONORANT_MS = 400;
    /** Length (ms) of the standard hum sample. */
    public static final int HUM_MS = 750;
    /** Four lengths (ms in total): short, medium, long, extra long – a note gets the longest one that ends about with it; so there is air
     * between the notes like in the videos, and nothing has to be stopped hard. */
    public static final int[] HUM_LENGTHS_MS = {350, 520, 750, 1700};
    public static final String[] HUM_LENGTH_KEYS = {"s", "m", "l", "x"};

    /** The hum length for a note of this duration. */
    public static String humLength(int durMs) {
        for (int i = 0; i < HUM_LENGTHS_MS.length - 1; i++) {
            if (durMs < HUM_LENGTHS_MS[i] + 90) {
                return HUM_LENGTH_KEYS[i];
            }
        }
        return HUM_LENGTH_KEYS[HUM_LENGTH_KEYS.length - 1];
    }
    /** After this many milliseconds a long note retriggers the hum (before the sample has decayed). */
    public static final int HUM_RETRIGGER_MS = 1450;
    /** The voices the pack has real hum samples for (like {@code HUMS} in build_voice_pack.py). */
    public static final java.util.Set<String> HUM_SPEAKERS = java.util.Set.of("villager", "trader", "witch", "allay", "piglin", "zombie", "evoker",
            "goat", "cat");

    /** The hum sound of a voice at a root and length (voices without their own sample: the villager). */
    public static String humSound(String speaker, int root, String length) {
        return humSound(speaker, root, length, false);
    }

    /**
     * The hum sound; {@code legato}: the variant without a new attack (soft onset from the held part) for notes that follow the previous one
     * directly – so a phrase is tied instead of stuttered.
     */
    public static String humSound(String speaker, int root, String length, boolean legato) {
        return "voiceapi:hum." + (HUM_SPEAKERS.contains(speaker) ? speaker : "villager") + "." + root + "." + length + (legato ? "g" : "");
    }

    /** A note that starts at most this many milliseconds after the end of the previous one is sung tied. */
    public static final int LEGATO_GAP_MS = 45;

    /** The path of the format marker in the pack. */
    public static final String MARKER = "assets/voiceapi/voiceapi.json";

    private VoicePack() {
    }

    /** The name of the vowel in the pack ({@code a}, {@code ii}, {@code schwa} …), or {@code null} if the sound is not a vowel. */
    public static String vowelKey(Phoneme phoneme) {
        return switch (phoneme) {
            case A, AA -> "a";
            case E, AE -> "e";
            case EE -> "ee";
            case I -> "i";
            case II -> "ii";
            case O -> "o";
            case OO -> "oo";
            case U -> "u";
            case UU -> "uu";
            case OE -> "oe";
            case OEE -> "oee";
            case UE -> "ue";
            case UEE -> "uee";
            case AEE -> "ae";
            case AH -> "ah";
            case AW -> "aw";
            case ERR -> "er";
            case SCHWA -> "schwa";
            case ER -> "r";
            default -> null;
        };
    }

    /** The name of a consonant in the pack, or {@code null}. */
    public static String consonantKey(Phoneme phoneme) {
        return switch (phoneme) {
            case P -> "p";
            case B -> "b";
            case T -> "t";
            case D -> "d";
            case K -> "k";
            case G -> "g";
            case F -> "f";
            case V -> "v";
            case S -> "s";
            case Z -> "z";
            case SH -> "sh";
            case ZH -> "zh";
            case TH -> "th";
            case DH -> "dh";
            case H -> "h";
            case X -> "x";
            case XI -> "xi";
            case TS -> "ts";
            case PF -> "pf";
            case CH -> "ch";
            case DJ -> "dj";
            case M -> "m";
            case N -> "n";
            case NG -> "ng";
            case L -> "l";
            case R -> "r";
            case W -> "w";
            case Y -> "j";
            default -> null;
        };
    }

    /** The root closest to a pitch. */
    public static int root(double midi) {
        int root = ROOT_LOW + (int) Math.round((midi - ROOT_LOW) / STEP) * STEP;
        return Math.max(ROOT_LOW, Math.min(ROOT_HIGH, root));
    }

    /** The speed at which a sample of the root hits the pitch. */
    public static float rate(double midi, int root) {
        return (float) Compat.clamp(Math.pow(2.0, (midi - root) / 12.0), 0.5, 2.0);
    }

    /** The sound of a vowel at a root. */
    public static String vowelSound(String key, int root) {
        return "voiceapi:v." + key + "." + root;
    }

    /** The sound of a voiced consonant at a root. */
    public static String sonorantSound(String key, int root) {
        return "voiceapi:c." + key + "." + root;
    }

    /** The sound of a noise consonant. */
    public static String noiseSound(String key) {
        return "voiceapi:c." + key;
    }

    /** All roots. */
    public static List<Integer> roots() {
        List<Integer> roots = new ArrayList<>();
        for (int root = ROOT_LOW; root <= ROOT_HIGH; root += STEP) {
            roots.add(root);
        }
        return roots;
    }

    /** All sound names the pack must have (to compare with a built file). */
    public static List<String> sounds() {
        List<String> all = new ArrayList<>();
        for (String speaker : HUM_SPEAKERS) {
            for (int root : roots()) {
                for (String length : HUM_LENGTH_KEYS) {
                    all.add(humSound(speaker, root, length, false));
                    all.add(humSound(speaker, root, length, true));
                }
            }
        }
        for (Phoneme phoneme : Phoneme.values()) {
            String vowel = vowelKey(phoneme);
            if (vowel != null) {
                for (int root : roots()) {
                    String sound = vowelSound(vowel, root);
                    if (!all.contains(sound)) {
                        all.add(sound);
                    }
                }
            }
            String consonant = consonantKey(phoneme);
            if (consonant != null) {
                if (phoneme.isSonorant()) {
                    for (int root : roots()) {
                        all.add(sonorantSound(consonant, root));
                    }
                } else {
                    all.add(noiseSound(consonant));
                }
            }
        }
        return all;
    }

    /**
     * The format of the VoiceAPI sounds in a pack ({@code assets/voiceapi/voiceapi.json}), −1 if they are missing.
     *
     * @throws IOException if the file cannot be read
     */
    public static int formatIn(Path zip) throws IOException {
        if (zip == null || !Files.isRegularFile(zip)) {
            return -1;
        }
        try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(zip.toFile())) {
            java.util.zip.ZipEntry entry = archive.getEntry(MARKER);
            if (entry == null) {
                return -1;
            }
            String text = new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"format\"\\s*:\\s*(\\d+)").matcher(text);
            return m.find() ? Integer.parseInt(m.group(1)) : -1;
        }
    }

    /** Whether a pack contains the sounds of this plugin version. */
    public static boolean matches(Path zip) {
        try {
            return formatIn(zip) == FORMAT;
        } catch (IOException e) {
            return false;
        }
    }
}
