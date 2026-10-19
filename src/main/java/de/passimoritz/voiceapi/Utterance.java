package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A finished utterance: the text, the language, the voice and the sounds with time, pitch and volume – everything needed for playback.
 * It is made from text ({@link VoiceApi#speak}), from text and melody ({@link VoiceApi#sing}) or from a {@code .voice} file
 * ({@link UtteranceFile}). The sounds are ordered by time and do not overlap.
 *
 * @param id       the id in the library (empty for utterances that only exist in code)
 * @param title    the name as displayed
 * @param text     the spoken text
 * @param language the language
 * @param speaker  the voice ({@link Speaker#id()})
 * @param style    the style the utterance was built with
 * @param segments the sounds, ordered by {@link Segment#startMs()}
 * @param sung     whether the utterance is sung (pitches from a melody): then the renderer holds the pitch more strictly (only the held sound,
 *                 no grunt and scream onsets whose pitch is off)
 */
public record Utterance(String id, String title, String text, Language language, String speaker, Style style, List<Segment> segments, boolean sung) {

    /** A spoken utterance. */
    public Utterance(String id, String title, String text, Language language, String speaker, Style style, List<Segment> segments) {
        this(id, title, text, language, speaker, style, segments, false);
    }

    public Utterance {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
        title = title == null || title.isBlank() ? (text.isBlank() ? (id.isBlank() ? "Untitled" : id) : shorten(text)) : title;
        language = language == null ? Language.ENGLISH : language;
        speaker = speaker == null || speaker.isBlank() ? Speaker.VILLAGER.id() : speaker.toLowerCase(Locale.ROOT);
        style = style == null ? Style.DEFAULT : style;
        List<Segment> sorted = new ArrayList<>(segments);
        sorted.sort(java.util.Comparator.comparingInt(Segment::startMs));
        segments = List.copyOf(sorted);
    }

    private static String shorten(String text) {
        String one = text.replaceAll("\\s+", " ").strip();
        return one.length() > 40 ? one.substring(0, 39).strip() + "…" : one;
    }

    /** The length in milliseconds (end of the last sound). */
    public int lengthMs() {
        int end = 0;
        for (Segment segment : segments) {
            end = Math.max(end, segment.endMs());
        }
        return end;
    }

    /** The duration in seconds. */
    public double seconds() {
        return lengthMs() / 1000.0;
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /** How many words the utterance speaks. */
    public int words() {
        int max = -1;
        for (Segment segment : segments) {
            max = Math.max(max, segment.word());
        }
        return max + 1;
    }

    /** The voice of the utterance. */
    public Speaker voice() {
        return Speaker.find(speaker).orElse(Speaker.VILLAGER);
    }

    /** The same utterance with another id and title. */
    public Utterance named(String newId, String newTitle) {
        return new Utterance(newId, newTitle, text, language, speaker, style, segments, sung);
    }

    /** The same utterance with another voice. */
    public Utterance spokenBy(Speaker newSpeaker) {
        return new Utterance(id, title, text, language, newSpeaker.id(), style, segments, sung);
    }

    /** The same utterance, shifted by semitones. */
    public Utterance transposed(double semitones) {
        return new Utterance(id, title, text, language, speaker, style, segments.stream().map(s -> s.transposed(semitones)).toList(), sung);
    }

    /** The same utterance, stretched or squeezed (factor 2 = twice as long). */
    public Utterance stretched(double factor) {
        List<Segment> changed = new ArrayList<>();
        for (Segment segment : segments) {
            changed.add(new Segment((int) Math.round(segment.startMs() * factor), (int) Math.round(segment.durMs() * factor), segment.phoneme(),
                    segment.pitch(), segment.pitchEnd(), segment.volume(), segment.word(), segment.stressed()));
        }
        return new Utterance(id, title, text, language, speaker, style, changed, sung);
    }

    /** The same utterance starting later (all sounds shifted by {@code offsetMs}). */
    public Utterance delayed(int offsetMs) {
        return new Utterance(id, title, text, language, speaker, style, segments.stream().map(s -> s.at(s.startMs() + offsetMs)).toList(), sung);
    }

    /** Two utterances one after the other (with a pause in between). */
    public Utterance then(Utterance next, int gapMs) {
        List<Segment> all = new ArrayList<>(segments);
        int offset = lengthMs() + Math.max(0, gapMs);
        int wordOffset = words();
        for (Segment segment : next.segments) {
            all.add(new Segment(segment.startMs() + offset, segment.durMs(), segment.phoneme(), segment.pitch(), segment.pitchEnd(), segment.volume(),
                    segment.word() < 0 ? -1 : segment.word() + wordOffset, segment.stressed()));
        }
        return new Utterance(id, title, (text + " " + next.text).strip(), language, speaker, style, all, sung || next.sung);
    }

    /** The phonetic transcription of the utterance (sounds separated at word boundaries), for display. */
    public String transcription() {
        StringBuilder out = new StringBuilder();
        int word = -2;
        for (Segment segment : segments) {
            if (segment.phoneme().isPause()) {
                continue;
            }
            if (segment.word() != word) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                word = segment.word();
            }
            out.append(segment.phoneme().symbol());
        }
        return out.toString();
    }

    /** An id from any text: lower-case letters, digits and dashes, at most 40 characters. */
    public static String slug(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        String slug = lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "voice" : slug;
    }
}
