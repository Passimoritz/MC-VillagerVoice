package de.passimoritz.voiceapi.sing;

import de.passimoritz.voiceapi.Segment;
import de.passimoritz.voiceapi.Speaker;
import de.passimoritz.voiceapi.Style;
import de.passimoritz.voiceapi.Utterance;
import de.passimoritz.voiceapi.lang.Language;
import de.passimoritz.voiceapi.lang.Phoneme;
import de.passimoritz.voiceapi.lang.Syllable;
import de.passimoritz.voiceapi.lang.Word;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Singt einen Text auf eine Melodie: Jede Silbe bekommt einen Ton (oder mehrere – ein Melisma – wenn die Melodie mehr Töne hat als der Text
 * Silben; mehrere Silben teilen sich einen Ton, wenn es umgekehrt ist). Die Konsonanten am Silbenanfang werden <em>vor</em> den Ton gezogen,
 * damit der Vokal genau auf dem Schlag liegt (so singen auch Menschen); die am Ende sitzen im letzten Stück des Tons. Die Tonhöhe des Vokals
 * ist die des Tons, in den Umfang der Stimme gerückt ({@link Speaker#fit}).
 */
public final class Singer {

    private Singer() {
    }

    /** Eine Silbe mit ihrem Wort. */
    private record Unit(Syllable syllable, int word, boolean lastInWord, Word.Break after) {
    }

    /**
     * Baut ein gesungenes Stück.
     *
     * @param text     der Text (Silben in Reihenfolge der Töne)
     * @param language die Sprache
     * @param speaker  die Stimme
     * @param melody   die Töne (werden nach der Zeit geordnet)
     * @param style    Tonlage ({@code pitch}, Halbtöne) und Kraft ({@code energy}); Tempo und Stimmung spielen beim Singen keine Rolle
     */
    public static Utterance sing(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style) {
        List<MelodyNote> notes = new ArrayList<>(melody);
        notes.sort(Comparator.comparingInt(MelodyNote::startMs));
        List<Word> words = language.phonemizer().read(text);
        List<Unit> units = new ArrayList<>();
        for (int w = 0; w < words.size(); w++) {
            Word word = words.get(w);
            for (int s = 0; s < word.syllables().size(); s++) {
                units.add(new Unit(word.syllables().get(s), w, s == word.syllables().size() - 1, word.after()));
            }
        }
        List<Segment> segments = new ArrayList<>();
        if (units.isEmpty() || notes.isEmpty()) {
            return new Utterance("", "", text, language, speaker.id(), style, segments);
        }
        // Töne auf Silben verteilen: Töne mit Wort-Zuordnung bleiben bei den Silben ihres Wortes
        List<List<MelodyNote>> perUnit = notes.stream().anyMatch(n -> n.word() >= 0) ? distributeByWord(units, notes) : distribute(units, notes);
        double energy = style.energy();
        int previousEnd = 0;
        for (int u = 0; u < units.size(); u++) {
            Unit unit = units.get(u);
            List<MelodyNote> group = perUnit.get(u);
            if (group.isEmpty()) {
                continue;
            }
            List<Phoneme> phonemes = unit.syllable().phonemes();
            int nucleus = -1;
            for (int i = 0; i < phonemes.size(); i++) {
                if (phonemes.get(i).isVocalic()) {
                    nucleus = i;
                    break;
                }
            }
            MelodyNote first = group.getFirst();
            MelodyNote last = group.getLast();
            boolean stressed = unit.syllable().isStressed();
            float loud = (float) Math.min(1.0, first.volume() * energy * (stressed ? 1.0 : 0.9));
            double firstPitch = speaker.fit(first.pitch() + style.pitch());
            // Anlaut-Konsonanten vor dem Ton (nicht in die vorige Silbe hinein)
            int onsetTotal = 0;
            for (int i = 0; i < (nucleus < 0 ? phonemes.size() : nucleus); i++) {
                onsetTotal += consonantMs(phonemes.get(i));
            }
            int onsetStart = Math.max(previousEnd, first.startMs() - onsetTotal);
            int at = onsetStart;
            int available = first.startMs() - onsetStart;
            for (int i = 0; i < (nucleus < 0 ? phonemes.size() : nucleus); i++) {
                int dur = onsetTotal == 0 ? 0 : Math.max(20, consonantMs(phonemes.get(i)) * available / onsetTotal);
                segments.add(new Segment(at, dur, phonemes.get(i), (float) (firstPitch - 1), (float) firstPitch, loud * 0.75f, unit.word(), stressed));
                at += dur;
            }
            if (nucleus < 0) {
                previousEnd = Math.max(previousEnd, at);
                continue;
            }
            // Auslaut-Konsonanten am Ende des letzten Tons
            int codaTotal = 0;
            for (int i = nucleus + 1; i < phonemes.size(); i++) {
                codaTotal += consonantMs(phonemes.get(i));
            }
            int vowelEnd = last.endMs() - Math.min(codaTotal, Math.max(0, last.durMs() - 40));
            int vowelStart = first.startMs();
            Phoneme vowel = phonemes.get(nucleus);
            // ein Vokal-Abschnitt je Ton (Melisma); kurze Lücken zwischen Tönen bleiben Vokal
            for (int n = 0; n < group.size(); n++) {
                MelodyNote note = group.get(n);
                int from = n == 0 ? vowelStart : note.startMs();
                int to = n == group.size() - 1 ? vowelEnd : Math.min(vowelEnd, group.get(n + 1).startMs());
                if (to <= from) {
                    continue;
                }
                double pitch = speaker.fit(note.pitch() + style.pitch());
                float volume = (float) Math.min(1.0, note.volume() * energy * (stressed ? 1.0 : 0.9));
                // Diphthong: nur der erste Ton gleitet, weitere Töne halten den zweiten Vokal
                Phoneme sung = n == 0 ? vowel : (vowel.end() != null ? vowel.end() : vowel);
                segments.add(new Segment(from, to - from, sung, (float) pitch, (float) pitch, volume, unit.word(), stressed));
            }
            at = Math.max(vowelEnd, vowelStart);
            int codaSpace = last.endMs() - at;
            for (int i = nucleus + 1; i < phonemes.size(); i++) {
                int dur = codaTotal == 0 ? 0 : Math.max(20, consonantMs(phonemes.get(i)) * Math.max(codaSpace, 20 * (phonemes.size() - nucleus - 1)) / codaTotal);
                double pitch = speaker.fit(last.pitch() + style.pitch());
                segments.add(new Segment(at, dur, phonemes.get(i), (float) pitch, (float) (pitch - 1), loud * 0.7f, unit.word(), stressed));
                at += dur;
            }
            previousEnd = at;
        }
        return new Utterance("", "", text, language, speaker.id(), style, segments);
    }

    /** Wie lang ein Konsonant beim Singen ist (kürzer als beim Sprechen). */
    private static int consonantMs(Phoneme phoneme) {
        return Math.max(25, (int) (phoneme.baseMillis() * 0.75));
    }

    /** Verteilt je Wort dessen Töne auf dessen Silben (Töne ohne Wort gehen zum vorigen Wort; Wörter ohne Töne bleiben stumm). */
    static List<List<MelodyNote>> distributeByWord(List<Unit> units, List<MelodyNote> notes) {
        List<List<MelodyNote>> result = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            result.add(new ArrayList<>());
        }
        int words = units.isEmpty() ? 0 : units.getLast().word() + 1;
        List<List<MelodyNote>> perWord = new ArrayList<>();
        for (int w = 0; w < words; w++) {
            perWord.add(new ArrayList<>());
        }
        int current = 0;
        for (MelodyNote note : notes) {
            if (note.word() >= 0) {
                current = Math.min(note.word(), Math.max(0, words - 1));
            }
            if (words > 0) {
                perWord.get(current).add(note);
            }
        }
        for (int w = 0; w < words; w++) {
            List<Unit> own = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < units.size(); i++) {
                if (units.get(i).word() == w) {
                    own.add(units.get(i));
                    indices.add(i);
                }
            }
            if (own.isEmpty() || perWord.get(w).isEmpty()) {
                continue;
            }
            List<List<MelodyNote>> parts = distribute(own, perWord.get(w));
            for (int k = 0; k < own.size(); k++) {
                result.set(indices.get(k), parts.get(k));
            }
        }
        return result;
    }

    /**
     * Verteilt die Töne auf die Silben: bei mehr Tönen als Silben bekommen betonte Silben und Silben vor einer Pause die zusätzlichen Töne
     * (Melisma); bei weniger Tönen teilen sich benachbarte Silben einen Ton (er wird aufgeteilt).
     */
    static List<List<MelodyNote>> distribute(List<Unit> units, List<MelodyNote> notes) {
        List<List<MelodyNote>> result = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            result.add(new ArrayList<>());
        }
        if (notes.size() >= units.size()) {
            int extra = notes.size() - units.size();
            // Gewichte: betont 3, letzte Silbe vor Satzgrenze 2, sonst 1
            int[] weight = new int[units.size()];
            int total = 0;
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i);
                weight[i] = (unit.syllable().isStressed() ? 3 : 1) + (unit.lastInWord() && unit.after() != Word.Break.NONE ? 2 : 0);
                total += weight[i];
            }
            int[] count = new int[units.size()];
            int given = 0;
            for (int i = 0; i < units.size(); i++) {
                count[i] = 1 + extra * weight[i] / total;
                given += count[i] - 1;
            }
            // Rest der Reihe nach an die schwersten Silben
            for (int i = 0; given < extra; i = (i + 1) % units.size()) {
                if (weight[i] >= 3 || given + units.size() > extra) {
                    count[i]++;
                    given++;
                }
            }
            int n = 0;
            for (int i = 0; i < units.size(); i++) {
                for (int k = 0; k < count[i] && n < notes.size(); k++) {
                    result.get(i).add(notes.get(n++));
                }
            }
            while (n < notes.size()) {
                result.getLast().add(notes.get(n++));
            }
            return result;
        }
        // weniger Töne: Silben gleichmäßig auf Töne verteilen, jeder Ton wird in Teile geschnitten
        double perNote = units.size() / (double) notes.size();
        int u = 0;
        for (int n = 0; n < notes.size(); n++) {
            MelodyNote note = notes.get(n);
            int until = n == notes.size() - 1 ? units.size() : (int) Math.round((n + 1) * perNote);
            int parts = Math.max(1, until - u);
            int slice = note.durMs() / parts;
            for (int p = 0; p < parts && u < units.size(); p++, u++) {
                int start = note.startMs() + p * slice;
                int dur = p == parts - 1 ? note.endMs() - start : slice;
                result.get(u).add(new MelodyNote(start, dur, note.pitch(), note.volume()));
            }
        }
        return result;
    }
}
