package de.passimoritz.voiceapi.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The base sounds ({@code plugins/VoiceAPI/pack/VoiceAPI-Sounds-base.zip}, 23 MB) are not part of the jar: on first start they are
 * downloaded from the GitHub release {@code voice-pack} and checked against the SHA-256 compiled in here, so the jar stays small. Without
 * internet VoiceAPI runs "without resource pack" until the file is there; an admin can also place it by hand.
 * <p>
 * Download into {@code <file>.part}, check the checksum, then move it in place – a broken or interrupted download never replaces a good file.
 */
public final class SoundDownload {

    /** SHA-256 of the sounds this plugin version is made for ({@code src/main/resources/pack/VoiceAPI-Sounds.zip}, checked by a test). */
    public static final String SHA256 = "3284456f716157534115d4c3a8ed860c1016221b6ccb19bf7c590feba6661a29";
    /** Where they are downloaded from; {@code {sha256}} and {@code {sha}} (first 12 characters) are filled in. */
    public static final String DEFAULT_URL = "https://github.com/Passimoritz/MC-VillagerVoice/releases/download/voice-pack/VoiceAPI-Sounds-{sha}.zip";
    /** Larger answers are not a sound pack. */
    static final long MAX_BYTES = 128L * 1024 * 1024;

    private final Path target;
    private final String sha256;
    private final Logger log;

    /**
     * @param target where the base pack lives ({@code pack/VoiceAPI-Sounds-base.zip})
     * @param sha256 the expected checksum (normally {@link #SHA256})
     * @param log    messages for the server log
     */
    public SoundDownload(Path target, String sha256, Logger log) {
        this.target = target;
        this.sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
        this.log = log;
    }

    /** The file. */
    public Path target() {
        return target;
    }

    /** The download address of a URL pattern ({@code null}/empty = {@link #DEFAULT_URL}). */
    public String url(String pattern) {
        String url = pattern == null || pattern.isBlank() ? DEFAULT_URL : pattern.strip();
        return url.replace("{sha256}", sha256).replace("{sha}", sha256.substring(0, 12));
    }

    /** Whether the file is there with exactly the expected content. */
    public boolean current() {
        try {
            return Files.isRegularFile(target) && sha256.equals(sha256(target));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * What can be used right now, without a download: the file if it matches the checksum, otherwise if it is at least a VoiceAPI sound
     * pack of this format (placed by hand, or from an older version) – better than no sounds.
     */
    public Optional<Path> usable() {
        if (current()) {
            return Optional.of(target);
        }
        return Files.isRegularFile(target) && VoicePack.matches(target) ? Optional.of(target) : Optional.empty();
    }

    /**
     * Downloads the sounds (blocking – call it on a background thread) unless the file is already current.
     *
     * @param url the address ({@link #url(String)})
     * @return true if the file is current afterwards
     */
    public boolean fetch(String url) {
        if (current()) {
            return true;
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try {
            Files.createDirectories(target.getParent());
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "VoiceAPI").GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String got;
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                got = copy(in, part);
            }
            if (!sha256.equals(got)) {
                throw new IOException("wrong checksum " + got + " (expected " + sha256 + ")");
            }
            try {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Downloaded the VoiceAPI sounds (" + Files.size(target) / 1024 + " KB) from " + url);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            log.warning("Could not download the VoiceAPI sounds from " + url + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + " – running without them. Place the file by hand at " + target + " or restart with internet.");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // a leftover .part is overwritten next time
            }
        }
    }

    /** Copies at most {@link #MAX_BYTES} into a file. @return the SHA-256 of what was copied */
    private static String copy(InputStream in, Path file) throws IOException {
        MessageDigest digest = digest();
        try (OutputStream out = Files.newOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int n; (n = in.read(buffer)) > 0; ) {
                total += n;
                if (total > MAX_BYTES) {
                    throw new IOException("larger than " + MAX_BYTES / 1024 / 1024 + " MB");
                }
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** SHA-256 of a file in lowercase hex. */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
