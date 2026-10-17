package de.passimoritz.voiceapi.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Übersetzt Schrift in Laute: ein Text wird zu Wörtern mit Silben, Betonung und Satzgrenzen.
 * <p>
 * Der gemeinsame Teil ({@link #read(String)}) zerlegt den Text in Wörter und Satzzeichen, schreibt Zahlen in Worte, erkennt Hervorhebungen
 * (GROSS oder *so*) und ruft für jedes Wort {@link #word(String)} der Sprache auf.
 */
public interface Phonemizer {

    /** Die Laute eines einzelnen Wortes (nur Buchstaben, klein geschrieben), als Silben mit Betonung. */
    List<Syllable> word(String word);

    /** Zahlen in Worte ({@code 42} → „zweiundvierzig“). */
    String number(long value);

    /** „Komma“ bzw. „point“. */
    String decimalPoint();

    /** Ein ganzer Text als Wörter. */
    default List<Word> read(String text) {
        List<Word> words = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return words;
        }
        String clean = text.replace('’', '\'').replace('‘', '\'');
        Matcher matcher = TOKEN.matcher(clean);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.matches("\\d+([.,]\\d+)?")) {
                // Zahl: ganze und Nachkomma-Stellen als Wörter
                String[] parts = token.split("[.,]");
                String whole = parts[0].length() > 15 ? parts[0].substring(0, 15) : parts[0];
                String spoken = number(Long.parseLong(whole));
                if (parts.length > 1) {
                    spoken += " " + decimalPoint() + " " + digits(parts[1]);
                }
                for (String piece : spoken.split("\\s+")) {
                    add(words, piece, false);
                }
                continue;
            }
            if (token.matches("[\\p{L}'\\-]+")) {
                boolean emphasis = token.length() > 1 && token.equals(token.toUpperCase(Locale.ROOT))
                        && !token.equals(token.toLowerCase(Locale.ROOT));
                for (String piece : token.split("-")) {
                    if (!piece.isEmpty()) {
                        add(words, piece, emphasis);
                    }
                }
                continue;
            }
            Word.Break found = switch (token) {
                case ".", ";" -> Word.Break.SENTENCE;
                case "?" -> Word.Break.QUESTION;
                case "!" -> Word.Break.EXCLAMATION;
                case ",", ":", "-", "–", "—", "(", ")" -> Word.Break.COMMA;
                case "...", "…" -> Word.Break.ELLIPSIS;
                default -> null;
            };
            if (found != null && !words.isEmpty()) {
                Word last = words.getLast();
                if (last.after() == Word.Break.NONE || found.ordinal() > last.after().ordinal()) {
                    words.set(words.size() - 1, last.followedBy(found));
                }
            }
        }
        markEmphasis(clean, words);
        if (!words.isEmpty() && !words.getLast().after().endsSentence()) {
            words.set(words.size() - 1, words.getLast().followedBy(Word.Break.SENTENCE));
        }
        return words;
    }

    /** Wörter zwischen Sternen ({@code *so*}) werden hervorgehoben. */
    private static void markEmphasis(String text, List<Word> words) {
        Matcher starred = Pattern.compile("\\*([^*]+)\\*").matcher(text);
        int from = 0;
        while (starred.find()) {
            for (String piece : starred.group(1).toLowerCase(Locale.ROOT).split("[^\\p{L}']+")) {
                if (piece.isEmpty()) {
                    continue;
                }
                for (int i = from; i < words.size(); i++) {
                    if (words.get(i).text().toLowerCase(Locale.ROOT).equals(piece)) {
                        words.set(i, words.get(i).emphasized(true));
                        from = i + 1;
                        break;
                    }
                }
            }
        }
    }

    private void add(List<Word> words, String token, boolean emphasis) {
        String clean = token.toLowerCase(Locale.ROOT).replaceAll("^'+|'+$", "");
        if (clean.isEmpty()) {
            return;
        }
        List<Syllable> syllables = word(clean);
        if (syllables.isEmpty()) {
            return;
        }
        words.add(new Word(token, syllables, Word.Break.NONE, emphasis));
    }

    /** Ziffern einzeln ({@code 05} → „null fünf“). */
    default String digits(String text) {
        StringBuilder spoken = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                spoken.append(spoken.isEmpty() ? "" : " ").append(number(c - '0'));
            }
        }
        return spoken.toString();
    }

    Pattern TOKEN = Pattern.compile("\\d+([.,]\\d+)?|[\\p{L}'\\-]+|\\.\\.\\.|[.,;:!?…\\-–—()]");

    // ── Hilfen für die Sprachen ─────────────────────────────────

    /**
     * Zerlegt eine Lautfolge in Silben: ein Vokal je Silbe; Konsonanten zwischen zwei Vokalen gehen so weit wie möglich zur folgenden Silbe,
     * solange die Sprache den Anlaut erlaubt ({@code onsets}); mindestens einer bleibt, wenn es zwei oder mehr sind (Wasser → was.ser).
     */
    static List<List<Phoneme>> syllabify(List<Phoneme> phonemes, Set<String> onsets) {
        List<List<Phoneme>> syllables = new ArrayList<>();
        List<Integer> nuclei = new ArrayList<>();
        for (int i = 0; i < phonemes.size(); i++) {
            // ein vokalisches r (ɐ) direkt nach einem Vokal gehört zu dessen Silbe (wir, Uhr, hier)
            if (phonemes.get(i).isVocalic() && !(phonemes.get(i) == Phoneme.ER && i > 0 && phonemes.get(i - 1).isVocalic())) {
                nuclei.add(i);
            }
        }
        if (nuclei.isEmpty()) {
            syllables.add(new ArrayList<>(phonemes));
            return syllables;
        }
        int start = 0;
        for (int n = 0; n < nuclei.size(); n++) {
            int end;
            if (n + 1 < nuclei.size()) {
                int next = nuclei.get(n + 1);
                int consonants = next - nuclei.get(n) - 1;
                int onset = consonants;
                if (consonants >= 2) {
                    onset = consonants - 1;
                }
                while (onset > 1 && !onsets.contains(key(phonemes.subList(next - onset, next)))) {
                    onset--;
                }
                end = next - onset;
            } else {
                end = phonemes.size();
            }
            syllables.add(new ArrayList<>(phonemes.subList(start, end)));
            start = end;
        }
        return syllables;
    }

    /** Die Laute als Zeichenkette (für Anlaut-Tabellen). */
    static String key(List<Phoneme> phonemes) {
        StringBuilder text = new StringBuilder();
        for (Phoneme phoneme : phonemes) {
            text.append(phoneme.symbol());
        }
        return text.toString();
    }

    /**
     * Liest Lautschrift wie {@code ə.ˈbaʊt} (Silben durch Punkt, ˈ vor der betonten Silbe, Laute als IPA-Zeichen aus {@link Phoneme}).
     *
     * @throws IllegalArgumentException wenn ein Zeichen kein Laut ist
     */
    static List<Syllable> transcription(String ipa) {
        List<Syllable> syllables = new ArrayList<>();
        for (String part : ipa.strip().split("\\.")) {
            int stress = part.startsWith("ˈ") ? 2 : part.startsWith("ˌ") ? 1 : 0;
            String body = stress > 0 ? part.substring(1) : part;
            // gängige Schreibweisen anderer Wörterbücher auf unseren Vorrat
            body = body.replace("ɔɪ", "ɔʏ").replace("ɚ", "ɐ").replace("ɝ", "ɜː")
                    .replace("ã", "aŋ").replace("ɑː", "aː").replace("ɑ", "a").replace("ɹ", "r").replace("ɡ", "g");
            List<Phoneme> phonemes = new ArrayList<>();
            int i = 0;
            while (i < body.length()) {
                Phoneme found = null;
                for (int len = Math.min(2, body.length() - i); len >= 1 && found == null; len--) {
                    found = Phoneme.bySymbol(body.substring(i, i + len));
                    if (found != null) {
                        i += len;
                    }
                }
                if (found == null) {
                    // geschlossene Vokale ohne Längenzeichen (Hallo = ˈha.lo, many = ˈmɛ.ni): wie die langen Vokale
                    found = switch (body.charAt(i)) {
                        case 'o' -> Phoneme.OO;
                        case 'e' -> Phoneme.EE;
                        case 'i' -> Phoneme.II;
                        case 'u' -> Phoneme.UU;
                        case 'y' -> Phoneme.UEE;
                        default -> null;
                    };
                    if (found == null) {
                        throw new IllegalArgumentException("Kein Laut: " + body.substring(i) + " in " + ipa);
                    }
                    i++;
                }
                phonemes.add(found);
            }
            if (!phonemes.isEmpty()) {
                syllables.add(new Syllable(phonemes, stress));
            }
        }
        return syllables;
    }

    /** Setzt die Hauptbetonung auf eine Silbe (die anderen werden unbetont), wenn noch keine gesetzt ist. */
    static List<Syllable> stress(List<List<Phoneme>> parts, int stressed) {
        List<Syllable> syllables = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            syllables.add(new Syllable(parts.get(i), i == stressed ? 2 : 0));
        }
        return syllables;
    }
}
