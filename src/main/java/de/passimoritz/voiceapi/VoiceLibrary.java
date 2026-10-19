package de.passimoritz.voiceapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** The saved utterances ({@code plugins/VoiceAPI/voices/<id>.voice}). */
public final class VoiceLibrary {

    private final Path folder;
    private final Consumer<String> log;
    private final Map<String, Utterance> utterances = new LinkedHashMap<>();

    public VoiceLibrary(Path folder, Consumer<String> log) {
        this.folder = folder;
        this.log = log == null ? text -> {
        } : log;
    }

    public Path folder() {
        return folder;
    }

    /** Reads all utterances from the folder. */
    public synchronized void load() {
        utterances.clear();
        if (!Files.isDirectory(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                log.accept("Could not create the folder " + folder + ": " + e.getMessage());
            }
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> path.getFileName().toString().endsWith(UtteranceFile.EXTENSION)).sorted().forEach(path -> {
                String id = path.getFileName().toString();
                id = id.substring(0, id.length() - UtteranceFile.EXTENSION.length());
                try {
                    utterances.put(id, UtteranceFile.read(id, Files.readString(path, StandardCharsets.UTF_8)));
                } catch (IOException | IllegalArgumentException e) {
                    log.accept("Could not read the utterance " + path.getFileName() + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            log.accept("Could not read the folder " + folder + ": " + e.getMessage());
        }
    }

    /** All utterances (by id). */
    public synchronized List<Utterance> all() {
        return List.copyOf(utterances.values());
    }

    public synchronized Optional<Utterance> get(String id) {
        return Optional.ofNullable(utterances.get(id == null ? "" : id.toLowerCase(Locale.ROOT)));
    }

    /**
     * Saves an utterance (the id from its title if it has none; an existing file is overwritten).
     *
     * @return the saved utterance with its id
     * @throws IOException if the file cannot be written
     */
    public synchronized Utterance save(Utterance utterance) throws IOException {
        String id = utterance.id().isBlank() ? Utterance.slug(utterance.title()) : Utterance.slug(utterance.id());
        Utterance named = utterance.named(id, utterance.title());
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(id + UtteranceFile.EXTENSION), UtteranceFile.write(named), StandardCharsets.UTF_8);
        utterances.put(id, named);
        return named;
    }

    /** Deletes an utterance. @return whether it existed */
    public synchronized boolean delete(String id) throws IOException {
        String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (utterances.remove(key) == null) {
            return false;
        }
        Files.deleteIfExists(folder.resolve(key + UtteranceFile.EXTENSION));
        return true;
    }

    public synchronized int size() {
        return utterances.size();
    }
}
