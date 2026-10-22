package de.passimoritz.voiceapi.song;

import de.passimoritz.voiceapi.Compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Converts the vocal track of a piece with the villager voice model ({@code tools/villager_sing.py --chunks}, RVC via Applio, CPU only) –
 * one song after the other, on its own thread. {@code tools/rvc_setup.py} sets up the Python environment (Windows and Linux).
 */
public final class SongConverter {

    /** How long a conversion may take at most. */
    private static final long TIMEOUT_MINUTES = 60;

    private final Path rvcHome;
    private final Path script;
    private final String model;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VoiceAPI-Conversion");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Process running;

    /**
     * @param rvcHome folder of the voice model environment ({@code ~/.voiceapi/rvc}: venv, Applio, models)
     * @param script  {@code villager_sing.py}
     * @param model   name of the model ({@code villager})
     */
    public SongConverter(Path rvcHome, Path script, String model) {
        this.rvcHome = rvcHome;
        this.script = script;
        this.model = model == null || model.isBlank() ? "villager" : model;
    }

    /** The Python of the environment. */
    public Path python() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return rvcHome.resolve("venv").resolve(windows ? "Scripts/python.exe" : "bin/python");
    }

    /** What is missing for converting (empty = everything is there). */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (!Files.isRegularFile(python())) {
            missing.add("Python environment (" + python() + ")");
        }
        if (!Files.isRegularFile(rvcHome.resolve("Applio").resolve("core.py"))) {
            missing.add("Applio (" + rvcHome.resolve("Applio") + ")");
        }
        if (!Files.isRegularFile(rvcHome.resolve("models").resolve(model + ".pth"))) {
            missing.add("voice model (" + rvcHome.resolve("models").resolve(model + ".pth") + ")");
        }
        if (!Files.isRegularFile(script)) {
            missing.add("tool (" + script + ")");
        }
        return missing;
    }

    public boolean ready() {
        return missing().isEmpty();
    }

    /**
     * Converts a vocal track.
     *
     * @param id       id of the piece
     * @param vocals   the separated vocal track (WAV)
     * @param target   folder for {@code voice.json} and the sections
     * @param progress receives progress lines ("Villager is singing", "Writing sections") – from the conversion thread
     * @return completed with the folder, or with an error (text of the output)
     */
    public CompletableFuture<Path> convert(String id, Path vocals, Path target, Consumer<String> progress) {
        CompletableFuture<Path> result = new CompletableFuture<>();
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            result.completeExceptionally(new IllegalStateException("The voice model is not set up, missing: " + String.join(", ", missing)
                    + " – tools/rvc_setup.py sets it up"));
            return result;
        }
        // remember the job until it is done: if the server stops in between, VoiceAPI continues it on the next start
        Path job = target.resolveSibling(target.getFileName() + ".job");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(job, vocals.toAbsolutePath().toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // only the resume after a restart is lost
        }
        worker.submit(() -> {
            List<String> tail = new ArrayList<>();
            try {
                ProcessBuilder builder = new ProcessBuilder(python().toString(), "-X", "utf8", script.toString(), "--vocals", vocals.toString(),
                        "--chunks", target.toString(), "--id", id, "--model", model);
                builder.environment().put("VOICEAPI_RVC_HOME", rvcHome.toString());
                builder.environment().put("PYTHONIOENCODING", "utf-8");
                builder.redirectErrorStream(true);
                Process process = builder.start();
                running = process;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        tail.add(line);
                        if (tail.size() > 30) {
                            Compat.removeFirst(tail);
                        }
                        if (line.startsWith("@@step ") && progress != null) {
                            progress.accept(line.substring(7));
                        }
                    }
                }
                if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    throw new IOException("Timed out after " + TIMEOUT_MINUTES + " minutes");
                }
                if (process.exitValue() != 0 || !Files.isRegularFile(target.resolve("voice.json"))) {
                    throw new IOException("The conversion failed:\n" + String.join("\n", tail));
                }
                deleteQuietly(job);
                result.complete(target);
            } catch (IOException | RuntimeException e) {
                if (!stopping) {
                    deleteQuietly(job);
                }
                result.completeExceptionally(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(e);
            } finally {
                running = null;
            }
        });
        return result;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // stays; the next start drops it if the vocal track is gone
        }
    }

    /** Set while the plugin shuts down: the job file then stays, so the next start continues the conversion. */
    private volatile boolean stopping;

    /** Cancels the running conversion. */
    public void cancel() {
        Process process = running;
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    public boolean busy() {
        return running != null;
    }

    public void shutdown() {
        stopping = true;
        cancel();
        worker.shutdownNow();
    }
}
