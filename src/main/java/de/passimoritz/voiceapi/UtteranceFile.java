package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Language;
import de.passimoritz.voiceapi.lang.Phoneme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utterances as a text file ({@code voices/<id>.voice}), readable and editable by hand:
 * <pre>
 * # VoiceAPI utterance
 * title: Welcome
 * lang: de
 * speaker: villager
 * kind: sung            (only for sung utterances)
 * style: rate=1.00; pitch=0.0; range=1.00; energy=1.00; mood=happy
 * text: Willkommen im Dorf!
 * ---
 * &gt; Willkommen
 * 0 v 60 42.5 0.55
 * 60 ɪ 70 44.0&gt;45.2 0.85 *
 * 130 l 55 45.2 0.60 *
 * …
 * </pre>
 * Every sound line: start (ms), sound (IPA, see {@link Phoneme}), duration (ms), pitch (MIDI, with {@code >} a glide to the end pitch),
 * volume, optionally {@code *} for stressed. A line {@code > word} starts a new word; {@code _} is a pause.
 */
public final class UtteranceFile {

    public static final String EXTENSION = ".voice";

    private UtteranceFile() {
    }

    /** The utterance as text. */
    public static String write(Utterance utterance) {
        StringBuilder text = new StringBuilder(128 + utterance.segments().size() * 24);
        text.append("# VoiceAPI utterance\n");
        text.append("title: ").append(oneLine(utterance.title())).append('\n');
        text.append("lang: ").append(utterance.language().code()).append('\n');
        text.append("speaker: ").append(utterance.speaker()).append('\n');
        if (utterance.sung()) {
            text.append("kind: sung\n");
        }
        text.append("style: ").append(utterance.style().describe()).append('\n');
        if (!utterance.text().isBlank()) {
            text.append("text: ").append(oneLine(utterance.text())).append('\n');
        }
        text.append("---\n");
        int word = -2;
        String[] words = utterance.text().split("\\s+");
        int spoken = 0;
        for (Segment segment : utterance.segments()) {
            if (segment.word() >= 0 && segment.word() != word) {
                word = segment.word();
                String label = spoken < words.length ? words[spoken] : "";
                spoken++;
                text.append("> ").append(oneLine(label)).append('\n');
            }
            text.append(segment.startMs()).append(' ').append(segment.phoneme().symbol()).append(' ').append(segment.durMs()).append(' ')
                    .append(String.format(Locale.ROOT, "%.1f", segment.pitch()));
            if (Math.abs(segment.pitchEnd() - segment.pitch()) >= 0.05f) {
                text.append('>').append(String.format(Locale.ROOT, "%.1f", segment.pitchEnd()));
            }
            text.append(' ').append(String.format(Locale.ROOT, "%.2f", segment.volume()));
            if (segment.stressed()) {
                text.append(" *");
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * Reads an utterance from text.
     *
     * @param id the id (the file name without extension)
     * @throws IllegalArgumentException if a line cannot be read
     */
    public static Utterance read(String id, String text) {
        String title = id;
        Language language = Language.ENGLISH;
        String speaker = Speaker.VILLAGER.id();
        Style style = Style.DEFAULT;
        String spoken = "";
        boolean sung = false;
        List<Segment> segments = new ArrayList<>();
        boolean body = false;
        int word = -1;
        int lineNumber = 0;
        for (String raw : text.split("\\R")) {
            lineNumber++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!body) {
                if (line.equals("---")) {
                    body = true;
                } else if (line.startsWith("title:")) {
                    title = line.substring(6).strip();
                } else if (line.startsWith("lang:")) {
                    language = Language.find(line.substring(5).strip()).orElse(Language.ENGLISH);
                } else if (line.startsWith("speaker:")) {
                    speaker = line.substring(8).strip().toLowerCase(Locale.ROOT);
                } else if (line.startsWith("style:")) {
                    style = Style.parse(line.substring(6).strip());
                } else if (line.startsWith("text:")) {
                    spoken = line.substring(5).strip();
                } else if (line.startsWith("kind:")) {
                    sung = line.substring(5).strip().equalsIgnoreCase("sung");
                }
                continue;
            }
            if (line.startsWith(">")) {
                word++;
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 5) {
                throw new IllegalArgumentException("Line " + lineNumber + " is not a sound line: " + line);
            }
            try {
                int start = Integer.parseInt(parts[0]);
                Phoneme phoneme = Phoneme.bySymbol(parts[1]);
                if (phoneme == null) {
                    throw new IllegalArgumentException("Line " + lineNumber + ": unknown sound " + parts[1]);
                }
                int dur = Integer.parseInt(parts[2]);
                String[] pitches = parts[3].split(">");
                float pitch = Float.parseFloat(pitches[0]);
                float pitchEnd = pitches.length > 1 ? Float.parseFloat(pitches[1]) : pitch;
                float volume = Float.parseFloat(parts[4]);
                boolean stressed = parts.length > 5 && parts[5].equals("*");
                segments.add(new Segment(start, dur, phoneme, pitch, pitchEnd, volume, phoneme.isPause() ? -1 : Math.max(0, word), stressed));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Line " + lineNumber + " cannot be read: " + line, e);
            }
        }
        return new Utterance(id, title, spoken, language, speaker, style, segments, sung);
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
