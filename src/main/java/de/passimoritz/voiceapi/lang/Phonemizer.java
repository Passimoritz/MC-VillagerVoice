package de.passimoritz.voiceapi.lang;

import de.passimoritz.voiceapi.Compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates spelling into sounds: a text becomes words with syllables, stress and sentence boundaries.
 * <p>
 * The shared part ({@link #read(String)}) splits the text into words and punctuation, writes numbers as words, detects emphasis
 * (CAPS or *like this*) and calls the language's {@link #word(String)} for every word.
 */
public interface Phonemizer {

    /** The sounds of a single word (letters only, lower case), as syllables with stress. */
    List<Syllable> word(String word);

    /** Numbers as words ({@code 42} → "forty-two" / "zweiundvierzig"). */
    String number(long value);

    /** "point" or "Komma". */
    String decimalPoint();

    /** A whole text as words. */
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
                // number: whole and decimal digits as words
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
                Word last = Compat.last(words);
                if (last.after() == Word.Break.NONE || found.ordinal() > last.after().ordinal()) {
                    words.set(words.size() - 1, last.followedBy(found));
                }
            }
        }
        markEmphasis(clean, words);
        if (!words.isEmpty() && !Compat.last(words).after().endsSentence()) {
            words.set(words.size() - 1, Compat.last(words).followedBy(Word.Break.SENTENCE));
        }
        return words;
    }

    /** Words between asterisks ({@code *like this*}) are emphasised. */
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

    /** Digits one by one ({@code 05} → "zero five"). */
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

    // ── Helpers for the languages ─────────────────────────────────

    /**
     * Splits a sound sequence into syllables: one vowel per syllable; consonants between two vowels go to the following syllable as far as
     * the language allows the onset ({@code onsets}); at least one stays if there are two or more (Wasser → was.ser).
     */
    static List<List<Phoneme>> syllabify(List<Phoneme> phonemes, Set<String> onsets) {
        List<List<Phoneme>> syllables = new ArrayList<>();
        List<Integer> nuclei = new ArrayList<>();
        for (int i = 0; i < phonemes.size(); i++) {
            // a vocalic r (ɐ) right after a vowel belongs to that vowel's syllable (wir, Uhr, hier)
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

    /** The sounds as a string (for onset tables). */
    static String key(List<Phoneme> phonemes) {
        StringBuilder text = new StringBuilder();
        for (Phoneme phoneme : phonemes) {
            text.append(phoneme.symbol());
        }
        return text.toString();
    }

    /**
     * Reads a transcription like {@code ə.ˈbaʊt} (syllables separated by dots, ˈ before the stressed syllable, sounds as IPA characters
     * from {@link Phoneme}).
     *
     * @throws IllegalArgumentException if a character is not a sound
     */
    static List<Syllable> transcription(String ipa) {
        List<Syllable> syllables = new ArrayList<>();
        for (String part : ipa.strip().split("\\.")) {
            int stress = part.startsWith("ˈ") ? 2 : part.startsWith("ˌ") ? 1 : 0;
            String body = stress > 0 ? part.substring(1) : part;
            // common spellings of other dictionaries mapped onto our inventory
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
                    // close vowels without a length mark (Hallo = ˈha.lo, many = ˈmɛ.ni): like the long vowels
                    found = switch (body.charAt(i)) {
                        case 'o' -> Phoneme.OO;
                        case 'e' -> Phoneme.EE;
                        case 'i' -> Phoneme.II;
                        case 'u' -> Phoneme.UU;
                        case 'y' -> Phoneme.UEE;
                        default -> null;
                    };
                    if (found == null) {
                        throw new IllegalArgumentException("Not a sound: " + body.substring(i) + " in " + ipa);
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

    /** Puts the primary stress on one syllable (the others become unstressed). */
    static List<Syllable> stress(List<List<Phoneme>> parts, int stressed) {
        List<Syllable> syllables = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            syllables.add(new Syllable(parts.get(i), i == stressed ? 2 : 0));
        }
        return syllables;
    }
}
