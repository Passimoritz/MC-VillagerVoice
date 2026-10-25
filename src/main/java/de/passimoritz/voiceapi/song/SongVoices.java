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
        jobs().forEach((id, job) -> out.put(id, job.input()));
        return out;
    }

    /** An interrupted conversion: its recording and whether it was speech ({@code VoiceApi.convertSpeech}) or singing. */
    public record Job(Path input, boolean speech) {
    }

    /**
     * The interrupted conversions with their mode. A job file holds the recording's path on the first line and {@code speech} on a second
     * line for the speech mode (files of older versions have only the path: singing).
     */
    public synchronized Map<String, Job> jobs() {
        Map<String, Job> out = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".job")).sorted().toList()) {
                String id = file.getFileName().toString().replaceFirst("\\.job$", "");
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8).stream().map(String::strip).filter(l -> !l.isEmpty()).toList();
                Path input = lines.isEmpty() ? null : Path.of(lines.get(0));
                if (input != null && Files.isRegularFile(input)) {
                    out.put(id, new Job(input, lines.size() > 1 && lines.get(1).equals("speech")));
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
     * Writes the sound pack: base pack plus all songs. Without songs it is a copy of the base pack. Reproducible ({@link
     * de.passimoritz.voiceapi.pack.PackZip}): the same sounds give the same file – before, every start wrote new timestamps, and packs built
     * from it got a new checksum (a new download for every player).
     *
     * @param base   the bundled base pack
     * @param target the finished file ({@code VoiceAPI-Sounds.zip})
     * @return whether the file was rewritten
     */
    public synchronized boolean writePack(Path base, Path target) throws IOException {
        if (!Files.isRegularFile(base)) {
            return false;
        }
        java.util.SortedMap<String, byte[]> entries = new java.util.TreeMap<>();
        JsonObject sounds = new JsonObject();
        JsonObject marker = new JsonObject();
        try (ZipFile source = new ZipFile(base.toFile())) {
            var list = source.entries();
            while (list.hasMoreElements()) {
                ZipEntry entry = list.nextElement();
                if (entry.isDirectory() || entry.getName().startsWith("assets/voiceapi/sounds/song/")) {
                    continue;
                }
                byte[] data = read(source, entry);
                if (entry.getName().equals(SOUNDS_JSON)) {
                    sounds = Compat.json(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
                } else if (entry.getName().equals(MARKER)) {
                    marker = Compat.json(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
                } else {
                    entries.put(entry.getName(), data);
                }
            }
        }
        JsonArray songs = new JsonArray();
        List<SongVoice> sorted = new ArrayList<>(voices.values());
        sorted.sort(java.util.Comparator.comparing(SongVoice::id));
        for (SongVoice voice : sorted) {
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
                byte[] data = Files.readAllBytes(file);
                entries.put("assets/voiceapi/sounds/" + path + ".ogg", data);
                size += data.length;
                JsonObject sound = new JsonObject();
                sound.addProperty("name", "voiceapi:" + path);
                sound.addProperty("stream", true);
                JsonArray one = new JsonArray();
                one.add(sound);
                JsonObject event = new JsonObject();
                event.add("sounds", one);
                sounds.add(voice.key(n), event);
            }
            info.addProperty("bytes", size);
            info.addProperty("model", voice.model());
            songs.add(info);
        }
        marker.add("songs", songs);
        entries.put(SOUNDS_JSON, sounds.toString().getBytes(StandardCharsets.UTF_8));
        entries.put(MARKER, marker.toString().getBytes(StandardCharsets.UTF_8));
        return de.passimoritz.voiceapi.pack.PackZip.write(target, entries);
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
