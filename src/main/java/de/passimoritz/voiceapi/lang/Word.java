package de.passimoritz.voiceapi.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein gesprochenes Wort: seine Schreibung, seine Silben und was nach ihm kommt (Pause, Satzende, Frage …).
 *
 * @param text      das Wort, wie es geschrieben stand
 * @param syllables die Silben
 * @param after     die Grenze nach dem Wort
 * @param emphasis  ob das Wort hervorgehoben ist (GROSS geschrieben oder *so*)
 */
public record Word(String text, List<Syllable> syllables, Break after, boolean emphasis) {

    /** Was nach einem Wort folgt. */
    public enum Break {
        /** Das nächste Wort folgt direkt. */
        NONE,
        /** Komma, Gedankenstrich, Doppelpunkt: kurze Pause, die Melodie bleibt offen. */
        COMMA,
        /** Punkt: Satzende, die Melodie fällt. */
        SENTENCE,
        /** Fragezeichen: die Melodie steigt. */
        QUESTION,
        /** Ausrufezeichen: stärker, Melodie fällt tief. */
        EXCLAMATION,
        /** Auslassungspunkte: lange Pause, Melodie schwebt. */
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

    /** Dasselbe Wort mit anderer Grenze. */
    public Word followedBy(Break newBreak) {
        return new Word(text, syllables, newBreak, emphasis);
    }

    public Word emphasized(boolean strong) {
        return new Word(text, syllables, after, strong);
    }

    /** Alle Laute des Wortes in Sprechfolge. */
    public List<Phoneme> phonemes() {
        List<Phoneme> all = new ArrayList<>();
        for (Syllable syllable : syllables) {
            all.addAll(syllable.phonemes());
        }
        return all;
    }

    /** Die Nummer der Silbe mit Hauptbetonung (0, wenn keine markiert ist). */
    public int stressedSyllable() {
        for (int i = 0; i < syllables.size(); i++) {
            if (syllables.get(i).isStressed()) {
                return i;
            }
        }
        return 0;
    }

    /** Die Lautschrift ({@code ˈha.lo}). */
    public String transcription() {
        return String.join(".", syllables.stream().map(Syllable::toString).toList());
    }

    @Override
    public String toString() {
        return text + " [" + transcription() + "]";
    }
}
