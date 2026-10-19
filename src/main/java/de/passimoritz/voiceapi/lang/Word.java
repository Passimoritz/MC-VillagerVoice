package de.passimoritz.voiceapi.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * A spoken word: its spelling, its syllables and what comes after it (pause, end of sentence, question …).
 *
 * @param text      the word as it was written
 * @param syllables the syllables
 * @param after     the boundary after the word
 * @param emphasis  whether the word is emphasised (written in CAPS or *like this*)
 */
public record Word(String text, List<Syllable> syllables, Break after, boolean emphasis) {

    /** What follows a word. */
    public enum Break {
        /** The next word follows directly. */
        NONE,
        /** Comma, dash, colon: short pause, the melody stays open. */
        COMMA,
        /** Full stop: end of sentence, the melody falls. */
        SENTENCE,
        /** Question mark: the melody rises. */
        QUESTION,
        /** Exclamation mark: stronger, the melody falls low. */
        EXCLAMATION,
        /** Ellipsis: long pause, the melody hovers. */
        ELLIPSIS;

        public boolean endsSentence() {
            return this == SENTENCE || this == QUESTION || this == EXCLAMATION || this == ELLIPSIS;
        }
    }

    public Word {
        text = text == null ? "" : text;
        syllables = List.copyOf(syllables);
        after = after == null ? Break.NONE : after;
    }

    public Word(String text, List<Syllable> syllables) {
        this(text, syllables, Break.NONE, false);
    }

    /** The same word with another boundary. */
    public Word followedBy(Break newBreak) {
        return new Word(text, syllables, newBreak, emphasis);
    }

    public Word emphasized(boolean strong) {
        return new Word(text, syllables, after, strong);
    }

    /** All sounds of the word in speaking order. */
    public List<Phoneme> phonemes() {
        List<Phoneme> all = new ArrayList<>();
        for (Syllable syllable : syllables) {
            all.addAll(syllable.phonemes());
        }
        return all;
    }

    /** The index of the syllable with primary stress (0 if none is marked). */
    public int stressedSyllable() {
        for (int i = 0; i < syllables.size(); i++) {
            if (syllables.get(i).isStressed()) {
                return i;
            }
        }
        return 0;
    }

    /** The transcription ({@code ˈha.lo}). */
    public String transcription() {
        return String.join(".", syllables.stream().map(Syllable::toString).toList());
    }

    @Override
    public String toString() {
        return text + " [" + transcription() + "]";
    }
}
