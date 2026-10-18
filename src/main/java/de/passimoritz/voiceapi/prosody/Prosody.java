package de.passimoritz.voiceapi.prosody;

import de.passimoritz.voiceapi.Compat;

import de.passimoritz.voiceapi.Segment;
import de.passimoritz.voiceapi.Speaker;
import de.passimoritz.voiceapi.Style;
import de.passimoritz.voiceapi.lang.Phoneme;
import de.passimoritz.voiceapi.lang.Syllable;
import de.passimoritz.voiceapi.lang.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Macht aus Wörtern Sprache: gibt jedem Laut seine Zeit, seine Tonhöhe und seine Lautstärke.
 * <ul>
 *   <li><b>Dauer</b>: die natürliche Länge des Lauts, gedehnt in betonten Silben und am Ende einer Phrase, gestaucht in unbetonten;
 *   das Tempo des Stils skaliert alles.</li>
 *   <li><b>Melodie</b>: jeder Satz beginnt etwas höher und sinkt bis zum Ende (Deklination); betonte Silben bekommen einen Akzent-Gipfel,
 *   der innerhalb des Vokals wieder abfällt; am Satzende fällt die Stimme (Punkt), steigt (Frage) oder bleibt offen (Komma, Auslassung).
 *   Hervorgehobene Wörter bekommen einen höheren Gipfel.</li>
 *   <li><b>Lautstärke</b>: betont lauter, unbetont leiser, Satzende leiser; Geräusch-Konsonanten leiser als Vokale.</li>
 *   <li><b>Stimmung</b> ({@link Style.Mood}): färbt Tonlage, Umfang, Tempo, Kraft und Pausen.</li>
 * </ul>
 * Kleine, aus dem Text abgeleitete Zufallsschwankungen (immer dieselben für denselben Text) nehmen der Stimme das Maschinelle.
 */
public final class Prosody {

    private Prosody() {
    }

    /** Die Pausen (Millisekunden) nach einer Grenze bei normalem Tempo. */
    private static int pauseAfter(Word.Break found) {
        return switch (found) {
            case NONE -> 30;
            case COMMA -> 220;
            case SENTENCE -> 380;
            case QUESTION -> 380;
            case EXCLAMATION -> 340;
            case ELLIPSIS -> 650;
        };
    }

    /** Die Stimmungs-Färbung: Verschiebung der Tonlage, Umfang, Tempo, Kraft und Pausen als Faktoren. */
    private record Colour(double pitch, double range, double rate, double energy, double pauses, double jitter, boolean flat) {

        static Colour of(Style.Mood mood) {
            return switch (mood) {
                case NEUTRAL -> new Colour(0, 1.0, 1.0, 1.0, 1.0, 0.35, false);
                case HAPPY -> new Colour(2.0, 1.35, 1.08, 1.05, 0.9, 0.4, false);
                case SAD -> new Colour(-2.0, 0.55, 0.85, 0.85, 1.5, 0.3, false);
                case ANGRY -> new Colour(-0.5, 1.25, 1.05, 1.3, 0.8, 0.5, false);
                case SCARED -> new Colour(3.0, 1.2, 1.15, 0.9, 0.7, 1.1, false);
                case CALM -> new Colour(-0.5, 0.75, 0.9, 0.9, 1.2, 0.2, false);
                case EXCITED -> new Colour(2.5, 1.6, 1.2, 1.2, 0.75, 0.6, false);
                case WHISPER -> new Colour(0, 0.3, 0.95, 0.45, 1.1, 0.3, false);
                case ROBOT -> new Colour(0, 0.0, 1.0, 1.0, 1.0, 0.0, true);
                case TIRED -> new Colour(-1.5, 0.65, 0.8, 0.8, 1.6, 0.3, false);
            };
        }
    }

    /**
     * Baut die Laute eines Textes.
     *
     * @param words   die Wörter mit Silben und Grenzen
     * @param style   der Stil
     * @param speaker die Stimme (ihre natürliche Tonlage)
     * @return die Laute, nach der Zeit geordnet
     */
    public static List<Segment> compile(List<Word> words, Style style, Speaker speaker) {
        List<Segment> out = new ArrayList<>();
        if (words.isEmpty()) {
            return out;
        }
        Colour colour = Colour.of(style.mood());
        double base = speaker.speakingPitch() + style.pitch() + colour.pitch();
        double range = style.range() * colour.range() * speaker.rangeScale();
        double rate = style.rate() * colour.rate();
        double energy = style.energy() * colour.energy();
        Random random = new Random(seed(words));
        int time = 0;
        int sentenceStart = 0;
        // Sätze: von Satzanfang bis zu einer Grenze, die den Satz beendet
        while (sentenceStart < words.size()) {
            int sentenceEnd = sentenceStart;
            while (sentenceEnd < words.size() - 1 && !words.get(sentenceEnd).after().endsSentence()) {
                sentenceEnd++;
            }
            List<Word> sentence = words.subList(sentenceStart, sentenceEnd + 1);
            Word.Break ending = Compat.last(sentence).after();
            // Silben des Satzes zählen (für die Deklination)
            int syllables = 0;
            for (Word word : sentence) {
                syllables += word.syllables().size();
            }
            int lastStressedIndex = -1;
            int index = 0;
            for (Word word : sentence) {
                for (Syllable syllable : word.syllables()) {
                    if (syllable.isStressed() || word.syllables().size() == 1 && !isFunctionWord(word)) {
                        lastStressedIndex = index;
                    }
                    index++;
                }
            }
            if (lastStressedIndex < 0) {
                lastStressedIndex = syllables - 1;
            }
            index = 0;
            for (int w = 0; w < sentence.size(); w++) {
                Word word = sentence.get(w);
                int wordNumber = sentenceStart + w;
                boolean lastWord = w == sentence.size() - 1;
                boolean function = isFunctionWord(word) && !word.emphasis();
                for (int s = 0; s < word.syllables().size(); s++) {
                    Syllable syllable = word.syllables().get(s);
                    boolean lastSyllable = lastWord && s == word.syllables().size() - 1;
                    boolean stressed = syllable.isStressed() && !function || word.emphasis() && (syllable.isStressed() || word.syllables().size() == 1);
                    boolean nuclear = index == lastStressedIndex;
                    double position = syllables <= 1 ? 1.0 : index / (double) (syllables - 1);
                    // Deklination: von +2,5 auf −2,5 Halbtöne
                    double line = colour.flat() ? 0 : (2.5 - 5.0 * position) * range;
                    double accent = 0;
                    double fall = 0;
                    if (!colour.flat()) {
                        if (stressed) {
                            accent = (word.emphasis() ? 5.0 : 3.2) * range;
                            fall = 1.5 * range;
                        } else if (syllable.stress() == 1) {
                            accent = 1.2 * range;
                        } else {
                            accent = -0.6 * range;
                        }
                        if (nuclear) {
                            switch (ending) {
                                case SENTENCE -> fall = 4.0 * range;
                                case EXCLAMATION -> {
                                    accent += 1.5 * range;
                                    fall = 5.0 * range;
                                }
                                case QUESTION -> {
                                    accent -= 1.0 * range;
                                    fall = -3.0 * range;
                                }
                                default -> {
                                    // Komma/Auslassung: offen
                                }
                            }
                        }
                        if (lastSyllable && index != lastStressedIndex) {
                            switch (ending) {
                                case SENTENCE, EXCLAMATION -> {
                                    accent -= 2.5 * range;
                                    fall = 2.0 * range;
                                }
                                case QUESTION -> {
                                    accent += 4.0 * range;
                                    fall = -3.0 * range;
                                }
                                case COMMA -> accent += 1.0 * range;
                                default -> {
                                    // offen
                                }
                            }
                        }
                    }
                    double jitter = colour.jitter() * (random.nextDouble() * 2 - 1);
                    double pitchStart = base + line + accent + jitter;
                    double pitchEnd = pitchStart - fall;
                    // Dauer-Faktoren
                    double stretch = 1.0;
                    if (stressed) {
                        stretch *= word.emphasis() ? 1.5 : 1.3;
                    } else if (!syllable.isStressed() && word.syllables().size() > 1) {
                        stretch *= 0.82;
                    }
                    if (function) {
                        stretch *= 0.85;
                    }
                    if (lastSyllable) {
                        stretch *= ending == Word.Break.COMMA ? 1.2 : 1.35;
                    }
                    if (colour.flat()) {
                        stretch = 1.0;
                    }
                    stretch *= 1.0 + 0.05 * (random.nextDouble() * 2 - 1);
                    // Lautstärke
                    double loud = 0.78 * energy;
                    if (stressed) {
                        loud *= word.emphasis() ? 1.3 : 1.15;
                    } else if (!syllable.isStressed()) {
                        loud *= 0.86;
                    }
                    if (lastSyllable && ending != Word.Break.EXCLAMATION && ending != Word.Break.QUESTION) {
                        loud *= 0.85;
                    }
                    if (ending == Word.Break.EXCLAMATION) {
                        loud *= 1.1;
                    }
                    time = syllable(out, syllable, time, pitchStart, pitchEnd, stretch / rate, loud, wordNumber, stressed, colour.flat());
                    index++;
                }
                int gap = (int) Math.round(pauseAfter(word.after()) * colour.pauses() / Math.sqrt(rate));
                time += gap;
            }
            sentenceStart = sentenceEnd + 1;
        }
        return out;
    }

    /** Baut die Laute einer Silbe ab {@code time}; liefert die Zeit nach der Silbe. */
    private static int syllable(List<Segment> out, Syllable syllable, int time, double pitchStart, double pitchEnd, double stretch, double loud,
                                int word, boolean stressed, boolean flat) {
        List<Phoneme> phonemes = syllable.phonemes();
        int nucleus = -1;
        for (int i = 0; i < phonemes.size(); i++) {
            if (phonemes.get(i).isVocalic()) {
                nucleus = i;
                break;
            }
        }
        for (int i = 0; i < phonemes.size(); i++) {
            Phoneme phoneme = phonemes.get(i);
            double factor = stretch;
            // Konsonanten dehnen sich weniger als Vokale
            if (!phoneme.isVocalic()) {
                factor = 1.0 + (stretch - 1.0) * 0.4;
            }
            if (phoneme == Phoneme.SCHWA || phoneme == Phoneme.ER) {
                factor *= 0.85;
            }
            int dur = (int) Math.round(phoneme.baseMillis() * factor);
            if (flat && phoneme.isVocalic()) {
                dur = (int) Math.round(130 * stretch);
            }
            dur = Math.max(20, dur);
            float volume = (float) loud;
            if (phoneme.isNoise()) {
                volume *= phoneme.kind() == Phoneme.Kind.PLOSIVE ? 0.75f : 0.65f;
            } else if (phoneme.isSonorant()) {
                volume *= 0.8f;
            } else if (phoneme.isConsonant()) {
                volume *= 0.7f;
            }
            float start;
            float end;
            if (i == nucleus) {
                start = (float) pitchStart;
                end = (float) pitchEnd;
            } else if (nucleus >= 0 && i < nucleus) {
                start = (float) (pitchStart - 0.8);
                end = (float) pitchStart;
            } else {
                start = (float) pitchEnd;
                end = (float) (pitchEnd - 0.5);
            }
            out.add(new Segment(time, dur, phoneme, start, end, volume, word, stressed));
            time += dur;
        }
        return time;
    }

    /** Funktionswörter (Artikel, Pronomen, Präpositionen …) sprechen leiser und ohne Akzent. */
    static boolean isFunctionWord(Word word) {
        String text = word.text().toLowerCase(java.util.Locale.ROOT);
        return FUNCTION_WORDS.contains(" " + text + " ");
    }

    private static final String FUNCTION_WORDS = " der die das dem den des ein eine einen einem einer und oder aber zu zum zur in im am an auf für von mit bei "
            + "ist sind war es er sie wir ihr ich du dich mich sich ja so wie als dass ob the a an and or but to of in on at for from with by is are was were "
            + "it he she we they i you me him her us them this that as if so than ";

    /** Ein Startwert aus dem Text: derselbe Text schwankt immer gleich. */
    private static long seed(List<Word> words) {
        long hash = 1125899906842597L;
        for (Word word : words) {
            for (char c : word.text().toCharArray()) {
                hash = 31 * hash + c;
            }
            hash = 31 * hash + word.after().ordinal();
        }
        return hash;
    }
}
