package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ein fertiges Sprechstück: der Text, die Sprache, die Stimme und die Laute mit Zeit, Tonhöhe und Lautstärke – alles, was zum Abspielen nötig ist.
 * Es entsteht aus Text ({@link VoiceApi#speak}), aus Text und Melodie ({@link VoiceApi#sing}) oder aus einer {@code .voice}-Datei
 * ({@link UtteranceFile}). Die Laute sind nach der Zeit geordnet und überlappen sich nicht.
 *
 * @param id       die Kennung in der Bibliothek (leer bei Stücken, die nur im Code entstehen)
 * @param title    der Name, wie er angezeigt wird
 * @param text     der gesprochene Text
 * @param language die Sprache
 * @param speaker  die Stimme ({@link Speaker#id()})
 * @param style    der Stil, mit dem das Stück gebaut wurde
 * @param segments die Laute, nach {@link Segment#startMs()} geordnet
 * @param sung     ob das Stück gesungen ist (Tonhöhen aus einer Melodie): dann hält der Renderer die Tonhöhe strenger (nur der gehaltene Laut,
 *                 keine Grunz- und Schrei-Einsätze, deren Tonhöhe nicht stimmt)
 */
public record Utterance(String id, String title, String text, Language language, String speaker, Style style, List<Segment> segments, boolean sung) {

    /** Ein gesprochenes Stück. */
    public Utterance(String id, String title, String text, Language language, String speaker, Style style, List<Segment> segments) {
        this(id, title, text, language, speaker, style, segments, false);
    }

    public Utterance {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
        title = title == null || title.isBlank() ? (text.isBlank() ? (id.isBlank() ? "Ohne Titel" : id) : shorten(text)) : title;
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

    /** Die Länge in Millisekunden (Ende des letzten Lauts). */
    public int lengthMs() {
        int end = 0;
        for (Segment segment : segments) {
            end = Math.max(end, segment.endMs());
        }
        return end;
    }

    /** Die Dauer in Sekunden. */
    public double seconds() {
        return lengthMs() / 1000.0;
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /** Wie viele Wörter das Stück spricht. */
    public int words() {
        int max = -1;
        for (Segment segment : segments) {
            max = Math.max(max, segment.word());
        }
        return max + 1;
    }

    /** Die Stimme des Stücks. */
    public Speaker voice() {
        return Speaker.find(speaker).orElse(Speaker.VILLAGER);
    }

    /** Dasselbe Stück mit anderer Kennung und anderem Titel. */
    public Utterance named(String newId, String newTitle) {
        return new Utterance(newId, newTitle, text, language, speaker, style, segments, sung);
    }

    /** Dasselbe Stück mit anderer Stimme. */
    public Utterance spokenBy(Speaker newSpeaker) {
        return new Utterance(id, title, text, language, newSpeaker.id(), style, segments, sung);
    }

    /** Dasselbe Stück, um Halbtöne verschoben. */
    public Utterance transposed(double semitones) {
        return new Utterance(id, title, text, language, speaker, style, segments.stream().map(s -> s.transposed(semitones)).toList(), sung);
    }

    /** Dasselbe Stück, gedehnt oder gestaucht (Faktor 2 = doppelt so lang). */
    public Utterance stretched(double factor) {
        List<Segment> changed = new ArrayList<>();
        for (Segment segment : segments) {
            changed.add(new Segment((int) Math.round(segment.startMs() * factor), (int) Math.round(segment.durMs() * factor), segment.phoneme(),
                    segment.pitch(), segment.pitchEnd(), segment.volume(), segment.word(), segment.stressed()));
        }
        return new Utterance(id, title, text, language, speaker, style, changed, sung);
    }

    /** Dasselbe Stück ab einem späteren Beginn (alle Laute um {@code offsetMs} verschoben). */
    public Utterance delayed(int offsetMs) {
        return new Utterance(id, title, text, language, speaker, style, segments.stream().map(s -> s.at(s.startMs() + offsetMs)).toList(), sung);
    }

    /** Zwei Stücke hintereinander (mit einer Pause dazwischen). */
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

    /** Die Lautschrift des Stücks (Laute durch Wortgrenzen getrennt), für Anzeigen. */
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

    /** Eine Kennung aus einem beliebigen Text: Kleinbuchstaben, Ziffern und Bindestriche, höchstens 40 Zeichen. */
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
