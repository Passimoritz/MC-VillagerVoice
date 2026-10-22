package de.passimoritz.voiceapi.song;

import de.passimoritz.voiceapi.Compat;

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
 * The converted villager songs ({@code plugins/VoiceAPI/songs/<id>/voice.json} + {@code <n>.ogg}) and the sound pack that contains them:
 * the bundled base pack (hum sounds, vowels, consonants) plus all songs makes {@code VoiceAPI-Sounds.zip}. NoteAPI mixes this pack into its
 * resource pack; because the songs are listed in {@code assets/voiceapi/voiceapi.json}, it notices every change.
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

    /** The folder of a song voice. */
    public Path folder(String id) {
        return folder.resolve(safe(id));
    }

    /** An id as it may appear in the pack (lower-case letters, digits, dash, underscore). */
    public static String safe(String id) {
        String clean = id == null ? "" : id.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return clean.replace("-", "").isEmpty() ? "song" : clean;
    }

    /** Reads all song voices and removes what interrupted conversions left behind ({@code <id>.part}, {@code <id>.old}, temporary WAVs). */
    public synchronized void load() {
        voices.clear();
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> dirs = Files.list(folder)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                String name = dir.getFileName().toString();
                if (name.endsWith(".part") || name.endsWith(".old")) {
                    removeTree(dir);
                    log.accept("Removed the leftovers of an interrupted conversion: " + name);
                    continue;
                }
                cleanTemporary(dir);
                read(dir).ifPresent(voice -> voices.put(voice.id(), voice));
            }
        } catch (IOException e) {
            log.accept("Could not read the folder " + folder + ": " + e.getMessage());
        }
    }

    /** Temporary WAVs of older versions (written next to the sections); a folder that held nothing else is removed. */
    private void cleanTemporary(Path dir) throws IOException {
        boolean removed = false;
        for (String temp : new String[]{"_vocals.wav", "_villager.wav", "_gesang.wav"}) {
            removed |= Files.deleteIfExists(dir.resolve(temp));
        }
        if (removed && !Files.isRegularFile(dir.resolve("voice.json"))) {
            removeTree(dir);
            log.accept("Removed the leftovers of an interrupted conversion: " + dir.getFileName());
        }
    }

    private static void removeTree(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path path : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** The job file of a conversion in progress ({@code <id>.job}: the path of the vocal track) – left behind if the server stopped. */
    public Path job(String id) {
        return folder.resolve(safe(id) + ".job");
    }

    /** Conversions a server stop interrupted: id → vocal track (only those whose vocal track still exists; the others are dropped). */
    public synchronized Map<String, Path> interrupted() {
        Map<String, Path> out = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".job")).sorted().toList()) {
                String id = file.getFileName().toString().replaceFirst("\\.job$", "");
                Path vocals = Path.of(Files.readString(file, StandardCharsets.UTF_8).strip());
                if (Files.isRegularFile(vocals)) {
                    out.put(id, vocals);
                } else {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException | RuntimeException e) {
            log.accept("Could not read the interrupted conversions: " + e.getMessage());
        }
        return out;
    }

    /** Reads a song voice again (after a conversion). */
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
            JsonObject json = Compat.json(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<Integer> silent = new HashSet<>();
            if (json.has("silent")) {
                for (JsonElement element : json.getAsJsonArray("silent")) {
                    silent.add(element.getAsInt());
                }
            }
            String id = safe(json.has("id") ? json.get("id").getAsString() : dir.getFileName().toString());
            List<Integer> starts = new ArrayList<>();
            if (json.has("starts")) {
                for (JsonElement element : json.getAsJsonArray("starts")) {
                    starts.add(element.getAsInt());
                }
            }
            return Optional.of(new SongVoice(id, json.get("chunkMs").getAsInt(), json.get("overlapMs").getAsInt(), json.get("count").getAsInt(), silent,
                    json.get("lengthMs").getAsInt(), json.has("model") ? json.get("model").getAsString() : "", starts));
        } catch (IOException | RuntimeException e) {
            log.accept("Could not read the song voice in " + dir + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public synchronized Optional<SongVoice> get(String id) {
        return Optional.ofNullable(voices.get(safe(id)));
    }

    public synchronized List<SongVoice> all() {
        return List.copyOf(voices.values());
    }

    /** Deletes a song voice (files and entry). @return whether it existed */
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
     * Writes the sound pack: base pack plus all songs. Without songs it is a copy of the base pack.
     *
     * @param base   the bundled base pack
     * @param target the finished file ({@code VoiceAPI-Sounds.zip})
     * @return whether the file was rewritten
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
                    sounds = Compat.json(new String(read(source, entry), StandardCharsets.UTF_8)).getAsJsonObject();
                    continue;
                }
                if (entry.getName().equals(MARKER)) {
                    marker = Compat.json(new String(read(source, entry), StandardCharsets.UTF_8)).getAsJsonObject();
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

    /** How many song voices there are. */
    public synchronized int size() {
        return voices.size();
    }

    /** For tests: sections missing for a song voice. */
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
