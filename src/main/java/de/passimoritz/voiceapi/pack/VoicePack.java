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
 * Die Laute des VoiceAPI-Ressourcenpakets – dieselben Namen wie {@code tools/build_voice_pack.py}, das sie baut:
 * <ul>
 *   <li>Vokale: {@code voiceapi:v.<vokal>.<grundton>} – jeder Vokal als gehaltener Klang (Formant-Synthese), ein Sample alle {@value #STEP}
 *   Halbtöne von {@value #ROOT_LOW} bis {@value #ROOT_HIGH}; das Spiel streckt höchstens zwei Halbtöne, so bleiben die Formanten (und damit der
 *   Vokal) erkennbar.</li>
 *   <li>Klingende Konsonanten (m, n, ŋ, l, r, w, j): {@code voiceapi:c.<laut>.<grundton>}, ebenso je Grundton.</li>
 *   <li>Geräusch-Konsonanten: {@code voiceapi:c.<laut>} ohne Tonhöhe.</li>
 *   <li>Gesang: {@code voiceapi:hum.<stimme>.<grundton>} – das echte „Hmm“ der Stimme je Grundton, auf den Halbton gezogen, mit natürlichem
 *   Einsatz, {@value #HUM_MS} ms lang und natürlich ausklingend (mehrere Aufnahmen je Ton, das Spiel wählt). Jeder Ton wird einzeln
 *   angestoßen und klingt aus statt gestoppt zu werden – so singen die bekannten Dorfbewohner-Videos. Stimmen ohne Hmm-Samples singen mit dem
 *   Dorfbewohner-Hmm.</li>
 * </ul>
 * Das Paket wird in das NoteAPI-Ressourcenpaket gemischt ({@code build_voice_pack.py --merge}); {@code assets/voiceapi/voiceapi.json}
 * nennt das Format, damit das Plugin ein unpassendes Paket erkennt.
 */
public final class VoicePack {

    /** Wie die Klänge heißen; muss zu {@code SOUND_FORMAT} in build_voice_pack.py passen. */
    public static final int FORMAT = 2;
    /** Halbtöne zwischen zwei Grundtönen. */
    public static final int STEP = 4;
    public static final int ROOT_LOW = 33;
    public static final int ROOT_HIGH = 85;
    /** So lang (ms) ist ein Vokal-Sample. */
    public static final int VOWEL_MS = 1500;
    /** So lang (ms) ist ein Sample eines klingenden Konsonanten. */
    public static final int SONORANT_MS = 400;
    /** So lang (ms) ist ein Hmm-Sample. */
    public static final int HUM_MS = 750;
    /** Drei Längen (ms gesamt): kurz, mittel, lang – ein Ton bekommt die längste, die ungefähr mit ihm endet; so bleibt zwischen den Tönen
     * Luft wie in den Videos, und nichts muss hart gestoppt werden. */
    public static final int[] HUM_LENGTHS_MS = {350, 520, 750, 1700};
    public static final String[] HUM_LENGTH_KEYS = {"s", "m", "l", "x"};

    /** Die Länge des Hmm für einen Ton dieser Dauer. */
    public static String humLength(int durMs) {
        for (int i = 0; i < HUM_LENGTHS_MS.length - 1; i++) {
            if (durMs < HUM_LENGTHS_MS[i] + 90) {
                return HUM_LENGTH_KEYS[i];
            }
        }
        return HUM_LENGTH_KEYS[HUM_LENGTH_KEYS.length - 1];
    }
    /** Nach so vielen Millisekunden setzt ein langer Ton das Hmm neu an (bevor das Sample ausgeklungen ist). */
    public static final int HUM_RETRIGGER_MS = 1450;
    /** Die Stimmen, für die das Paket echte Hmm-Samples hat (wie {@code HUMS} in build_voice_pack.py). */
    public static final java.util.Set<String> HUM_SPEAKERS = java.util.Set.of("villager", "trader", "witch", "allay", "piglin", "zombie", "evoker",
            "goat", "cat");

    /** Der Klang des Hmm einer Stimme bei einer Tonhöhe und Länge (Stimmen ohne eigenes Sample: der Dorfbewohner). */
    public static String humSound(String speaker, int root, String length) {
        return humSound(speaker, root, length, false);
    }

    /**
     * Der Klang des Hmm; {@code legato}: die Variante ohne Neuansatz (weicher Einsatz aus dem gehaltenen Teil) für Töne, die direkt an den
     * vorigen anschließen – so wird eine Phrase gebunden statt gestottert.
     */
    public static String humSound(String speaker, int root, String length, boolean legato) {
        return "voiceapi:hum." + (HUM_SPEAKERS.contains(speaker) ? speaker : "villager") + "." + root + "." + length + (legato ? "g" : "");
    }

    /** Schließt ein Ton höchstens so viele Millisekunden nach dem Ende des vorigen an, wird er gebunden gesungen. */
    public static final int LEGATO_GAP_MS = 45;

    /** Der Pfad des Format-Steckbriefs im Paket. */
    public static final String MARKER = "assets/voiceapi/voiceapi.json";

    private VoicePack() {
    }

    /** Der Name des Vokals im Paket ({@code a}, {@code ii}, {@code schwa} …), oder {@code null}, wenn der Laut keiner ist. */
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

    /** Der Name eines Konsonanten im Paket, oder {@code null}. */
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

    /** Der Grundton, der einer Tonhöhe am nächsten liegt. */
    public static int root(double midi) {
        int root = ROOT_LOW + (int) Math.round((midi - ROOT_LOW) / STEP) * STEP;
        return Math.max(ROOT_LOW, Math.min(ROOT_HIGH, root));
    }

    /** Die Geschwindigkeit, mit der ein Sample des Grundtons die Tonhöhe trifft. */
    public static float rate(double midi, int root) {
        return (float) Compat.clamp(Math.pow(2.0, (midi - root) / 12.0), 0.5, 2.0);
    }

    /** Der Klang eines Vokals bei einer Tonhöhe. */
    public static String vowelSound(String key, int root) {
        return "voiceapi:v." + key + "." + root;
    }

    /** Der Klang eines klingenden Konsonanten bei einer Tonhöhe. */
    public static String sonorantSound(String key, int root) {
        return "voiceapi:c." + key + "." + root;
    }

    /** Der Klang eines Geräusch-Konsonanten. */
    public static String noiseSound(String key) {
        return "voiceapi:c." + key;
    }

    /** Alle Grundtöne. */
    public static List<Integer> roots() {
        List<Integer> roots = new ArrayList<>();
        for (int root = ROOT_LOW; root <= ROOT_HIGH; root += STEP) {
            roots.add(root);
        }
        return roots;
    }

    /** Alle Klangnamen, die das Paket haben muss (für den Abgleich mit einer gebauten Datei). */
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
     * Das Format der VoiceAPI-Laute in einem Paket ({@code assets/voiceapi/voiceapi.json}), −1 wenn sie fehlen.
     *
     * @throws IOException wenn die Datei nicht zu lesen ist
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

    /** Ob ein Paket die Laute dieser Plugin-Version enthält. */
    public static boolean matches(Path zip) {
        try {
            return formatIn(zip) == FORMAT;
        } catch (IOException e) {
            return false;
        }
    }
}
