package de.passimoritz.voiceapi.sing;

import de.passimoritz.voiceapi.Compat;

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
 * Sings a text to a melody: every syllable gets a note (or several – a melisma – if the melody has more notes than the text has syllables;
 * several syllables share a note if it is the other way round). The consonants at the start of a syllable are pulled <em>before</em> the
 * note so the vowel lands exactly on the beat (that is how people sing too); those at the end sit in the last part of the note. The pitch of
 * the vowel is that of the note, moved into the range of the voice ({@link Speaker#fit}).
 */
public final class Singer {

    private Singer() {
    }

    /** A syllable with its word. */
    private record Unit(Syllable syllable, int word, boolean lastInWord, Word.Break after) {
    }

    /**
     * Builds a sung utterance.
     *
     * @param text     the text (syllables in the order of the notes)
     * @param language the language
     * @param speaker  the voice
     * @param melody   the notes (ordered by time)
     * @param style    pitch ({@code pitch}, semitones) and power ({@code energy}); tempo and mood do not matter when singing
     */
    public static Utterance sing(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style) {
        return sing(text, language, speaker, melody, style, SingOptions.DEFAULT);
    }

    /** Like {@link #sing(String, Language, Speaker, List, Style)}, with own options (tying, overlapping, gliding, dynamics, volume). */
    public static Utterance sing(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style, SingOptions options) {
        SingOptions o = options == null ? SingOptions.DEFAULT : options;
        Utterance raw = singRaw(text, language, speaker, melody, style);
        return new Utterance(raw.id(), raw.title(), raw.text(), raw.language(), raw.speaker(), raw.style(), smooth(raw.segments(), o), true);
    }

    /**
     * Smooths the singing according to the options: very short notes merge into the previous one, small gaps are tied (the note sounds until
     * the next one), tied notes overlap the previous one, big jumps get a short passing note, the volume is compressed.
     */
    static List<Segment> smooth(List<Segment> segments, SingOptions o) {
        List<Segment> voiced = new ArrayList<>();
        List<Segment> others = new ArrayList<>();
        for (Segment s : segments) {
            if (s.phoneme().isVocalic() || s.phoneme().isSonorant()) {
                voiced.add(s);
            } else {
                others.add(s);
            }
        }
        voiced.sort(Comparator.comparingInt(Segment::startMs));
        // 1. notes that are too short go into the previous one
        List<Segment> merged = new ArrayList<>();
        for (Segment s : voiced) {
            if (!merged.isEmpty() && s.durMs() < o.minNoteMs() && s.startMs() - Compat.last(merged).endMs() <= o.legatoMs()) {
                Segment last = Compat.removeLast(merged);
                merged.add(last.lasting(Math.max(last.durMs(), s.endMs() - last.startMs())));
            } else {
                merged.add(s);
            }
        }
        // 2. volume: mean volume, deviations scaled by dynamics, then volume
        double mean = merged.stream().mapToDouble(Segment::volume).average().orElse(0.8);
        List<Segment> out = new ArrayList<>(others);
        for (int i = 0; i < merged.size(); i++) {
            Segment s = merged.get(i);
            float volume = (float) Compat.clamp((mean + (s.volume() - mean) * o.dynamics()) / Math.max(0.05, mean) * o.volume(), 0.02, 1.0);
            int start = s.startMs();
            int end = s.endMs();
            Segment previous = i > 0 ? merged.get(i - 1) : null;
            Segment next = i + 1 < merged.size() ? merged.get(i + 1) : null;
            boolean boundIn = previous != null && start - previous.endMs() <= o.legatoMs();
            if (next != null && next.startMs() - end <= o.legatoMs()) {
                end = next.startMs(); // durchklingen bis zum nächsten Ton
            }
            if (boundIn) {
                start = Math.max(previous.startMs() + 1, start - o.overlapMs());
            }
            // 3. glide: big jump into a tied note → short passing note halfway
            if (boundIn && o.glideSemitones() > 0 && Math.abs(s.pitch() - previous.pitchEnd()) >= o.glideSemitones() && end - start > o.glideMs() * 2) {
                float mid = (float) ((s.pitch() + previous.pitchEnd()) / 2.0);
                out.add(new Segment(start, o.glideMs(), s.phoneme(), mid, mid, volume * 0.85f, s.word(), s.stressed()));
                start += o.glideMs();
            }
            out.add(new Segment(start, Math.max(20, end - start), s.phoneme(), s.pitch(), s.pitchEnd(), volume, s.word(), s.stressed()));
        }
        out.sort(Comparator.comparingInt(Segment::startMs));
        return out;
    }

    private static Utterance singRaw(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style) {
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
            return new Utterance("", "", text, language, speaker.id(), style, segments, true);
        }
        // one octave register for the whole melody (as high as possible in the range of the voice – like the well-known villager videos)
        double shift = octaveShift(notes, speaker, style.pitch());
        // distribute notes to syllables: notes assigned to a word stay with that word's syllables
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
            MelodyNote first = Compat.first(group);
            MelodyNote last = Compat.last(group);
            boolean stressed = unit.syllable().isStressed();
            float loud = (float) Math.min(1.0, first.volume() * energy * (stressed ? 1.0 : 0.9));
            double firstPitch = clamp(speaker, first.pitch() + shift);
            // onset consonants before the note (not into the previous syllable)
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
            // coda consonants at the end of the last note
            int codaTotal = 0;
            for (int i = nucleus + 1; i < phonemes.size(); i++) {
                codaTotal += consonantMs(phonemes.get(i));
            }
            // legato: the vowel sounds on until the next note if it follows soon (detected notes are often shorter than sung);
            // the coda consonants take at most a third of the note
            int noteEnd = last.endMs();
            int nextStart = nextNoteStart(perUnit, u);
            if (nextStart > noteEnd && nextStart - noteEnd <= LEGATO_GAP_MS) {
                noteEnd = nextStart;
            }
            int noteLength = noteEnd - first.startMs();
            int vowelEnd = noteEnd - Math.min(codaTotal, Math.max(0, noteLength / 3));
            int vowelStart = first.startMs();
            Phoneme vowel = phonemes.get(nucleus);
            // one vowel segment per note (melisma); short gaps between notes stay vowel
            for (int n = 0; n < group.size(); n++) {
                MelodyNote note = group.get(n);
                int from = n == 0 ? vowelStart : note.startMs();
                // until the next note, but only across small gaps (a rest stays a rest)
                int to = n == group.size() - 1 ? vowelEnd : Math.min(vowelEnd, Math.min(group.get(n + 1).startMs(), note.endMs() + LEGATO_GAP_MS));
                if (to <= from) {
                    continue;
                }
                double pitch = clamp(speaker, note.pitch() + shift);
                float volume = (float) Math.min(1.0, note.volume() * energy * (stressed ? 1.0 : 0.9));
                // diphthong: only the first note glides, further notes hold the second vowel
                Phoneme sung = n == 0 ? vowel : (vowel.end() != null ? vowel.end() : vowel);
                segments.add(new Segment(from, to - from, sung, (float) pitch, (float) pitch, volume, unit.word(), stressed));
            }
            at = Math.max(vowelEnd, vowelStart);
            int codaSpace = noteEnd - at;
            for (int i = nucleus + 1; i < phonemes.size(); i++) {
                int dur = codaTotal == 0 ? 0 : Math.max(20, consonantMs(phonemes.get(i)) * Math.max(codaSpace, 20 * (phonemes.size() - nucleus - 1)) / codaTotal);
                double pitch = clamp(speaker, last.pitch() + shift);
                segments.add(new Segment(at, dur, phonemes.get(i), (float) pitch, (float) (pitch - 1), loud * 0.7f, unit.word(), stressed));
                at += dur;
            }
            previousEnd = at;
        }
        return new Utterance("", "", text, language, speaker.id(), style, segments, true);
    }

    /** Gaps of up to this many milliseconds to the next note are tied; larger gaps stay – every note is triggered on its own like in the
     * well-known videos. */
    private static final int LEGATO_GAP_MS = 50;

    /**
     * The shift (whole octaves plus the pitch of the style) that fits the melody into the range of the voice. One shared register for all
     * notes keeps the melody together (notes folded one by one would jump octaves).
     */
    static double octaveShift(List<MelodyNote> notes, Speaker speaker, double stylePitch) {
        if (notes.isEmpty()) {
            return stylePitch;
        }
        double[] pitches = notes.stream().mapToDouble(n -> n.pitch() + stylePitch).sorted().toArray();
        double median = pitches[pitches.length / 2];
        // the octave in which the middle of the melody comes closest to the singing register of the voice (if in doubt the higher one: bright like in the videos)
        return stylePitch + Math.round((speaker.singingPitch() + 2.0 - median) / 12.0) * 12.0;
    }

    /** Singing plays from the pack, whose roots reach far: only clamp roughly. */
    private static double clamp(Speaker speaker, double midi) {
        return Compat.clamp(midi, 30.0, 90.0);
    }

    /** The start of the first note after the syllable {@code index}, or −1. */
    private static int nextNoteStart(List<List<MelodyNote>> perUnit, int index) {
        for (int u = index + 1; u < perUnit.size(); u++) {
            if (!perUnit.get(u).isEmpty()) {
                return Compat.first(perUnit.get(u)).startMs();
            }
        }
        return -1;
    }

    /** How long a consonant is when singing (shorter than when speaking). */
    private static int consonantMs(Phoneme phoneme) {
        return Math.max(25, (int) (phoneme.baseMillis() * 0.75));
    }

    /** Distributes each word's notes to its syllables (notes without a word go to the previous word; words without notes stay silent). */
    static List<List<MelodyNote>> distributeByWord(List<Unit> units, List<MelodyNote> notes) {
        List<List<MelodyNote>> result = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            result.add(new ArrayList<>());
        }
        int words = units.isEmpty() ? 0 : Compat.last(units).word() + 1;
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
     * Distributes the notes to the syllables: with more notes than syllables, stressed syllables and syllables before a pause get the extra
     * notes (melisma); with fewer notes, neighbouring syllables share a note (it is split).
     */
    static List<List<MelodyNote>> distribute(List<Unit> units, List<MelodyNote> notes) {
        List<List<MelodyNote>> result = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            result.add(new ArrayList<>());
        }
        if (notes.size() >= units.size()) {
            int extra = notes.size() - units.size();
            // weights: stressed 3, last syllable before a sentence boundary 2, otherwise 1
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
            // the rest in turn to the heaviest syllables
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
                Compat.last(result).add(notes.get(n++));
            }
            return result;
        }
        // fewer notes: spread syllables evenly over the notes, every note is cut into parts
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
