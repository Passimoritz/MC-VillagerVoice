package de.passimoritz.voiceapi.lang;

import java.util.Locale;
import java.util.Optional;

/** The languages VoiceAPI speaks. */
public enum Language {
    GERMAN("de", "German"),
    ENGLISH("en", "English");

    private final String code;
    private final String label;

    Language(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** The code ({@code de}, {@code en}). */
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** The translator from spelling to sounds. */
    public Phonemizer phonemizer() {
        return this == GERMAN ? GermanPhonemizer.INSTANCE : EnglishPhonemizer.INSTANCE;
    }

    /** The language for a code or name ({@code de}, {@code german}, {@code deutsch}, {@code en}, {@code english} …). */
    public static Optional<Language> find(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String key = text.strip().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "de", "deutsch", "german", "de-de", "ger" -> Optional.of(GERMAN);
            case "en", "englisch", "english", "en-us", "en-gb", "eng" -> Optional.of(ENGLISH);
            default -> Optional.empty();
        };
    }

    /**
     * Guesses the language of a text: German for umlauts/ß and typical German words, otherwise by the share of typical words of both
     * languages (English on a tie).
     */
    public static Language guess(String text) {
        if (text == null || text.isBlank()) {
            return ENGLISH;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*[äöüß].*")) {
            return GERMAN;
        }
        int german = 0;
        int english = 0;
        for (String word : lower.split("[^a-z']+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (GERMAN_WORDS.contains(" " + word + " ")) {
                german++;
            }
            if (ENGLISH_WORDS.contains(" " + word + " ")) {
                english++;
            }
        }
        return german > english ? GERMAN : ENGLISH;
    }

    private static final String GERMAN_WORDS = " der die das und ist nicht ich du er sie es wir ihr ein eine einen dem den des mit auf für von zu im am "
            + "ja nein hallo bitte danke gut sehr auch noch wie was wer wo warum wenn aber oder doch hier dort heute morgen jetzt schon mein dein sein "
            + "kein nur dass sich hat habe hast sind war wird kann muss soll will spiel spieler willkommen tschüss ";
    private static final String ENGLISH_WORDS = " the and is not i you he she it we they a an to of in on for with at by from as be are was were "
            + "have has had do does did will would can could should this that these those my your his her its our their what who where when why how "
            + "yes no hello please thanks thank good very also still here there now today tomorrow welcome bye player game ";
}
