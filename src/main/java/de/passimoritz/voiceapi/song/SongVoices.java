package de.passimoritz.voiceapi.song;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Die umgewandelten Villager-Gesänge ({@code plugins/VoiceAPI/songs/<id>/voice.json} + {@code <n>.ogg}) und das Lautpaket, das sie enthält:
 * Das beigelegte Grundpaket (Hmm-Laute, Vokale, Konsonanten) plus alle Gesänge ergibt {@code VoiceAPI-Sounds.zip}. Die NoteAPI mischt dieses
 * Paket in ihr Ressourcenpaket; weil die Gesänge in {@code assets/voiceapi/voiceapi.json} aufgezählt sind, erkennt sie jede Änderung.
 */
public final class SongVoices {

    private static final String SOUNDS_JSON = "assets/voiceapi/sounds.json";
    private static final String MARKER = "assets/voiceapi/voiceapi.json";

    private final Path folder;
    private final Consumer<String> log;
    private final Map<String, SongVoice> voices = new LinkedHashMap<>();

    public SongVoices(Path folder, Consumer<String> log) {
        this.folder = folder;
        this.log = log == null ? text -> {
        } : log;
    }

    public Path folder() {
        return folder;
    }

    /** Der Ordner eines Gesangs. */
    public Path folder(String id) {
        return folder.resolve(safe(id));
    }

    /** Eine Kennung, wie sie im Paket stehen darf (Kleinbuchstaben, Ziffern, Bindestrich, Unterstrich). */
    public static String safe(String id) {
        String clean = id == null ? "" : id.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return clean.replace("-", "").isEmpty() ? "song" : clean;
    }

    /** Liest alle Gesänge. */
    public synchronized void load() {
        voices.clear();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> dirs = Files.list(folder)) {
            dirs.filter(Files::isDirectory).sorted().forEach(dir -> read(dir).ifPresent(voice -> voices.put(voice.id(), voice)));
        } catch (IOException e) {
            log.accept("Der Ordner " + folder + " ließ sich nicht lesen: " + e.getMessage());
        }
    }

    /** Liest einen Gesang neu (nach einer Umwandlung). */
    public synchronized Optional<SongVoice> reload(String id) {
        Optional<SongVoice> voice = read(folder(id));
        voice.ifPresentOrElse(v -> voices.put(v.id(), v), () -> voices.remove(safe(id)));
        return voice;
    }

    private Optional<SongVoice> read(Path dir) {
        Path meta = dir.resolve("voice.json");
        if (!Files.isRegularFile(meta)) {
            return Optional.empty();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<Integer> silent = new HashSet<>();
            if (json.has("silent")) {
                for (JsonElement element : json.getAsJsonArray("silent")) {
                    silent.add(element.getAsInt());
                }
            }
            String id = safe(json.has("id") ? json.get("id").getAsString() : dir.getFileName().toString());
            return Optional.of(new SongVoice(id, json.get("chunkMs").getAsInt(), json.get("overlapMs").getAsInt(), json.get("count").getAsInt(), silent,
                    json.get("lengthMs").getAsInt(), json.has("model") ? json.get("model").getAsString() : ""));
        } catch (IOException | RuntimeException e) {
            log.accept("Der Gesang in " + dir + " ließ sich nicht lesen: " + e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized Optional<SongVoice> get(String id) {
        return Optional.ofNullable(voices.get(safe(id)));
    }

    public synchronized List<SongVoice> all() {
        return List.copyOf(voices.values());
    }

    /** Löscht einen Gesang (Dateien und Eintrag). @return ob er da war */
    public synchronized boolean delete(String id) throws IOException {
        Path dir = folder(id);
        boolean existed = voices.remove(safe(id)) != null;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path path : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
            existed = true;
        }
        return existed;
    }

    /**
     * Schreibt das Lautpaket: Grundpaket plus alle Gesänge. Ohne Gesänge ist es eine Kopie des Grundpakets.
     *
     * @param base   das beigelegte Grundpaket
     * @param target die fertige Datei ({@code VoiceAPI-Sounds.zip})
     * @return ob die Datei neu geschrieben wurde
     */
    public synchronized boolean writePack(Path base, Path target) throws IOException {
        if (!Files.isRegularFile(base)) {
            return false;
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try (ZipFile source = new ZipFile(base.toFile()); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
            JsonObject sounds = new JsonObject();
            JsonObject marker = new JsonObject();
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(SOUNDS_JSON)) {
                    sounds = JsonParser.parseString(new String(read(source, entry), StandardCharsets.UTF_8)).getAsJsonObject();
                    continue;
                }
                if (entry.getName().equals(MARKER)) {
                    marker = JsonParser.parseString(new String(read(source, entry), StandardCharsets.UTF_8)).getAsJsonObject();
                    continue;
                }
                if (entry.getName().startsWith("assets/voiceapi/sounds/song/")) {
                    continue;
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream in = source.getInputStream(entry)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
            JsonArray songs = new JsonArray();
            for (SongVoice voice : voices.values()) {
                Path dir = folder(voice.id());
                JsonObject info = new JsonObject();
                info.addProperty("id", voice.id());
                info.addProperty("count", voice.count());
                info.addProperty("lengthMs", voice.lengthMs());
                long size = 0;
                for (int n = 0; n < voice.count(); n++) {
                    Path file = dir.resolve(n + ".ogg");
                    if (voice.silent().contains(n) || !Files.isRegularFile(file)) {
                        continue;
                    }
                    String path = "song/" + voice.id() + "/" + n;
                    out.putNextEntry(new ZipEntry("assets/voiceapi/sounds/" + path + ".ogg"));
                    Files.copy(file, out);
                    out.closeEntry();
                    size += Files.size(file);
                    JsonObject sound = new JsonObject();
                    sound.addProperty("name", "voiceapi:" + path);
                    sound.addProperty("stream", true);
                    JsonArray list = new JsonArray();
                    list.add(sound);
                    JsonObject event = new JsonObject();
                    event.add("sounds", list);
                    sounds.add(voice.key(n), event);
                }
                info.addProperty("bytes", size);
                info.addProperty("model", voice.model());
                songs.add(info);
            }
            marker.add("songs", songs);
            put(out, SOUNDS_JSON, sounds.toString());
            put(out, MARKER, marker.toString());
        }
        if (Files.isRegularFile(target) && Files.mismatch(target, temp) == -1) {
            Files.delete(temp);
            return false;
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    private static void put(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static byte[] read(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    /** Wie viele Gesänge da sind. */
    public synchronized int size() {
        return voices.size();
    }

    /** Für Tests: Abschnitte, die zu einem Gesang fehlen. */
    public synchronized List<Integer> missingChunks(String id) {
        List<Integer> missing = new ArrayList<>();
        get(id).ifPresent(voice -> {
            for (int n = 0; n < voice.count(); n++) {
                if (!voice.silent().contains(n) && !Files.isRegularFile(folder(id).resolve(n + ".ogg"))) {
                    missing.add(n);
                }
            }
        });
        return missing;
    }
}
