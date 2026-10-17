package de.passimoritz.voiceapi.lang;

import java.util.List;

/**
 * Eine Silbe: ihre Laute und ihre Betonung.
 *
 * @param phonemes die Laute in Sprechfolge (mindestens einer; normalerweise genau ein tragender Vokal)
 * @param stress   0 = unbetont, 1 = Nebenbetonung, 2 = Hauptbetonung des Wortes
 */
public record Syllable(List<Phoneme> phonemes, int stress) {

    public Syllable {
        phonemes = List.copyOf(phonemes);
        stress = Math.max(0, Math.min(2, stress));
    }

    public boolean isStressed() {
        return stress == 2;
    }

    /** Der tragende Vokal (oder der erste klingende Laut, wenn die Silbe keinen hat). */
    public Phoneme nucleus() {
        for (Phoneme phoneme : phonemes) {
            if (phoneme.isVocalic()) {
                return phoneme;
            }
        }
        for (Phoneme phoneme : phonemes) {
            if (phoneme.isSonorant()) {
                return phoneme;
            }
        }
        return phonemes.getFirst();
    }

    /** Dieselbe Silbe mit anderer Betonung. */
    public Syllable stressed(int newStress) {
        return new Syllable(phonemes, newStress);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(stress == 2 ? "ˈ" : stress == 1 ? "ˌ" : "");
        for (Phoneme phoneme : phonemes) {
            text.append(phoneme.symbol());
        }
        return text.toString();
    }
}
