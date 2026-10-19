package de.passimoritz.voiceapi.tool;

import de.passimoritz.voiceapi.Compat;

import de.passimoritz.voiceapi.Speaker;
import de.passimoritz.voiceapi.Style;
import de.passimoritz.voiceapi.Utterance;
import de.passimoritz.voiceapi.UtteranceFile;
import de.passimoritz.voiceapi.lang.Language;
import de.passimoritz.voiceapi.lang.Word;
import de.passimoritz.voiceapi.prosody.Prosody;
import de.passimoritz.voiceapi.render.Cue;
import de.passimoritz.voiceapi.render.PackRenderer;
import de.passimoritz.voiceapi.render.VanillaRenderer;
import de.passimoritz.voiceapi.sing.MelodyNote;
import de.passimoritz.voiceapi.sing.Singer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tool without a server: {@code java -jar voiceapi.jar <command> …}
 * <pre>
 * speak "Hello world" [--lang de|en] [--speaker villager] [--style "rate=1.1; mood=happy"] [--out hello.voice] [--cues hello.json] [--pack]
 * sing  "Text" --melody melody.json [--lang …] [--speaker …] [--out …] [--cues …] [--pack]
 * render piece.voice --cues piece.json [--pack]          the sound commands of a file (for tools/voicelab.py)
 * read  "Text" [--lang …]                                 shows how the text is read (syllables, stress)
 * sounds                                                  all vanilla sounds the renderer needs
 * </pre>
 * The cue file is JSON: {@code {"speaker": "villager", "lengthMs": 1234, "cues": [{"at": 0, "sound": "…", "pitch": 1.0, "volume": 0.5, "stop": 120}, …]}}.
 * A melody file is JSON: {@code [{"start": 0, "dur": 400, "pitch": 60, "volume": 0.8, "word": 0}, …]} ({@code word}: optionally the index of the word the note belongs to; or the NoteAPI form {@code "tick"} with 50 ms).
 */
public final class VoiceTool {

    private VoiceTool() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        try {
            System.exit(run(args, out));
        } catch (Exception e) {
            out.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    static int run(String[] args, PrintStream out) throws IOException {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            out.println("VoiceAPI tool: speak | sing | render | read | sounds (see README)");
            return 0;
        }
        String command = args[0];
        List<String> rest = new ArrayList<>(List.of(args).subList(1, args.length));
        String lang = option(rest, "--lang");
        String speakerName = option(rest, "--speaker");
        String styleText = option(rest, "--style");
        String outPath = option(rest, "--out");
        String cuesPath = option(rest, "--cues");
        String melodyPath = option(rest, "--melody");
        boolean pack = rest.remove("--pack");
        Language language = lang == null ? null : Language.find(lang).orElseThrow(() -> new IllegalArgumentException("Unknown language: " + lang));
        Speaker speaker = speakerName == null ? Speaker.VILLAGER : Speaker.find(speakerName).orElseThrow(() -> new IllegalArgumentException("Unknown voice: " + speakerName));
        Style style = Style.parse(styleText);
        switch (command) {
            case "speak", "sing" -> {
                String text = String.join(" ", rest);
                if (text.isBlank()) {
                    throw new IllegalArgumentException("No text");
                }
                Language found = language == null ? Language.guess(text) : language;
                Utterance utterance;
                if (command.equals("sing")) {
                    if (melodyPath == null) {
                        throw new IllegalArgumentException("sing needs --melody <file.json>");
                    }
                    utterance = Singer.sing(text, found, speaker, melody(Files.readString(Path.of(melodyPath), StandardCharsets.UTF_8)), style);
                } else {
                    List<Word> words = found.phonemizer().read(text);
                    utterance = new Utterance("", "", text, found, speaker.id(), style, Prosody.compile(words, style, speaker));
                }
                out.println(found.label() + ", " + speaker.label() + ": " + utterance.transcription() + "  (" + utterance.lengthMs() + " ms)");
                if (outPath != null) {
                    Files.writeString(Path.of(outPath), UtteranceFile.write(utterance), StandardCharsets.UTF_8);
                    out.println("saved: " + outPath);
                }
                if (cuesPath != null) {
                    writeCues(utterance, pack, Path.of(cuesPath));
                    out.println("sound commands: " + cuesPath);
                }
                return 0;
            }
            case "render" -> {
                if (rest.isEmpty()) {
                    throw new IllegalArgumentException("render needs a .voice file");
                }
                Path file = Path.of(Compat.first(rest));
                String id = file.getFileName().toString().replace(UtteranceFile.EXTENSION, "");
                Utterance utterance = UtteranceFile.read(id, Files.readString(file, StandardCharsets.UTF_8));
                if (cuesPath == null) {
                    for (Cue cue : (pack ? PackRenderer.INSTANCE : VanillaRenderer.INSTANCE).render(utterance)) {
                        out.println(String.format(Locale.ROOT, "%6d %-36s %.3f %.2f %s", cue.atMs(), cue.sound(), cue.pitch(), cue.volume(),
                                cue.stops() ? "stop " + cue.stopMs() : ""));
                    }
                } else {
                    writeCues(utterance, pack, Path.of(cuesPath));
                    out.println("sound commands: " + cuesPath);
                }
                return 0;
            }
            case "read" -> {
                String text = String.join(" ", rest);
                Language found = language == null ? Language.guess(text) : language;
                for (Word word : found.phonemizer().read(text)) {
                    out.println(word + (word.after() != Word.Break.NONE ? " " + word.after() : "") + (word.emphasis() ? " !" : ""));
                }
                return 0;
            }
            case "sounds" -> {
                for (String sound : VanillaRenderer.sounds()) {
                    out.println(sound);
                }
                return 0;
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static String option(List<String> args, String name) {
        int at = args.indexOf(name);
        if (at < 0 || at + 1 >= args.size()) {
            if (at >= 0) {
                args.remove(at);
            }
            return null;
        }
        String value = args.get(at + 1);
        args.remove(at + 1);
        args.remove(at);
        return value;
    }

    /** Writes the sound commands as JSON (for voicelab.py). */
    public static void writeCues(Utterance utterance, boolean pack, Path file) throws IOException {
        Files.writeString(file, cuesJson(utterance, pack), StandardCharsets.UTF_8);
    }

    public static String cuesJson(Utterance utterance, boolean pack) {
        List<Cue> cues = (pack ? PackRenderer.INSTANCE : VanillaRenderer.INSTANCE).render(utterance);
        StringBuilder json = new StringBuilder(64 + cues.size() * 90);
        json.append("{\"speaker\": \"").append(utterance.speaker()).append("\", \"renderer\": \"").append(pack ? "pack" : "vanilla")
                .append("\", \"text\": \"").append(escape(utterance.text())).append("\", \"lengthMs\": ").append(utterance.lengthMs()).append(", \"cues\": [\n");
        for (int i = 0; i < cues.size(); i++) {
            Cue cue = cues.get(i);
            json.append(String.format(Locale.ROOT, " {\"at\": %d, \"sound\": \"%s\", \"pitch\": %.4f, \"volume\": %.3f, \"stop\": %d, \"segment\": %d}",
                    cue.atMs(), cue.sound(), cue.pitch(), cue.volume(), cue.stopMs(), cue.segment()));
            json.append(i + 1 < cues.size() ? ",\n" : "\n");
        }
        json.append("]}\n");
        return json.toString();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    /** Reads a melody from JSON ({@code start/dur/pitch/volume} in ms, or {@code tick/pitch} with 50 ms per tick). */
    public static List<MelodyNote> melody(String json) {
        List<MelodyNote> notes = new ArrayList<>();
        Matcher objects = Pattern.compile("\\{[^{}]*}").matcher(json);
        while (objects.find()) {
            String object = objects.group();
            Double start = number(object, "start");
            Double dur = number(object, "dur");
            Double pitch = number(object, "pitch");
            Double volume = number(object, "volume");
            Double tick = number(object, "tick");
            if (pitch == null) {
                continue;
            }
            if (start == null && tick != null) {
                start = tick * 50.0;
                Double length = number(object, "length");
                dur = length != null ? length * 50.0 : 200.0;
            }
            if (start == null || dur == null) {
                continue;
            }
            Double word = number(object, "word");
            notes.add(new MelodyNote((int) Math.round(start), (int) Math.round(dur), pitch, volume == null ? 0.85f : volume.floatValue(),
                    word == null ? -1 : word.intValue()));
        }
        return notes;
    }

    private static Double number(String object, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(\\.\\d+)?)").matcher(object);
        return m.find() ? Double.parseDouble(m.group(1)) : null;
    }
}
