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
 * Wandelt die Gesangsspur eines Stücks mit dem Villager-Stimmmodell um ({@code tools/villager_sing.py --chunks}, RVC über Applio, nur CPU) –
 * ein Lied nach dem anderen, in einem eigenen Thread. Die Python-Umgebung richtet {@code tools/rvc_setup.py} ein (Windows und Linux).
 */
public final class SongConverter {

    /** Wie lange eine Umwandlung höchstens dauern darf. */
    private static final long TIMEOUT_MINUTES = 60;

    private final Path rvcHome;
    private final Path script;
    private final String model;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VoiceAPI-Umwandlung");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Process running;

    /**
     * @param rvcHome Ordner der Stimmmodell-Umgebung ({@code ~/.voiceapi/rvc}: venv, Applio, models)
     * @param script  {@code villager_sing.py}
     * @param model   Name des Modells ({@code villager})
     */
    public SongConverter(Path rvcHome, Path script, String model) {
        this.rvcHome = rvcHome;
        this.script = script;
        this.model = model == null || model.isBlank() ? "villager" : model;
    }

    /** Das Python der Umgebung. */
    public Path python() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return rvcHome.resolve("venv").resolve(windows ? "Scripts/python.exe" : "bin/python");
    }

    /** Was fehlt, damit umgewandelt werden kann (leer = alles da). */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (!Files.isRegularFile(python())) {
            missing.add("Python-Umgebung (" + python() + ")");
        }
        if (!Files.isRegularFile(rvcHome.resolve("Applio").resolve("core.py"))) {
            missing.add("Applio (" + rvcHome.resolve("Applio") + ")");
        }
        if (!Files.isRegularFile(rvcHome.resolve("models").resolve(model + ".pth"))) {
            missing.add("Stimmmodell (" + rvcHome.resolve("models").resolve(model + ".pth") + ")");
        }
        if (!Files.isRegularFile(script)) {
            missing.add("Werkzeug (" + script + ")");
        }
        return missing;
    }

    public boolean ready() {
        return missing().isEmpty();
    }

    /**
     * Wandelt eine Gesangsspur um.
     *
     * @param id       Kennung des Stücks
     * @param vocals   die getrennte Gesangsspur (WAV)
     * @param target   Ordner für {@code voice.json} und die Abschnitte
     * @param progress bekommt Fortschritts-Zeilen („Villager singt“, „Abschnitte“) – aus dem Umwandlungs-Thread
     * @return wird mit dem Ordner erfüllt, oder mit einem Fehler (Text der Ausgabe)
     */
    public CompletableFuture<Path> convert(String id, Path vocals, Path target, Consumer<String> progress) {
        CompletableFuture<Path> result = new CompletableFuture<>();
        List<String> missing = missing();
        if (!missing.isEmpty()) {
            result.completeExceptionally(new IllegalStateException("Das Stimmmodell ist nicht eingerichtet, es fehlt: " + String.join(", ", missing)
                    + " – tools/rvc_setup.py richtet es ein"));
            return result;
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
                    throw new IOException("Zeitüberschreitung nach " + TIMEOUT_MINUTES + " Minuten");
                }
                if (process.exitValue() != 0 || !Files.isRegularFile(target.resolve("voice.json"))) {
                    throw new IOException("Die Umwandlung ist fehlgeschlagen:\n" + String.join("\n", tail));
                }
                result.complete(target);
            } catch (IOException | RuntimeException e) {
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

    /** Bricht die laufende Umwandlung ab. */
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
        cancel();
        worker.shutdownNow();
    }
}
