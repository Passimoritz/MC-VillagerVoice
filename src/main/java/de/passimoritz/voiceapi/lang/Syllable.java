package de.passimoritz.voiceapi.lang;

import de.passimoritz.voiceapi.Compat;

import java.util.List;

/**
 * A syllable: its sounds and its stress.
 *
 * @param phonemes the sounds in speaking order (at least one; normally exactly one carrying vowel)
 * @param stress   0 = unstressed, 1 = secondary stress, 2 = primary stress of the word
 */
public record Syllable(List<Phoneme> phonemes, int stress) {

    public Syllable {
        phonemes = List.copyOf(phonemes);
        stress = Math.max(0, Math.min(2, stress));
    }

    public boolean isStressed() {
        return stress == 2;
    }

    /** The carrying vowel (or the first voiced sound if the syllable has none). */
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
        return Compat.first(phonemes);
    }

    /** The same syllable with another stress. */
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
