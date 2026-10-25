package de.passimoritz.voiceapi;

import com.sun.net.httpserver.HttpServer;
import de.passimoritz.voiceapi.pack.SoundDownload;
import de.passimoritz.voiceapi.pack.VoicePack;
import de.passimoritz.voiceapi.song.SongVoices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sounds come from a download instead of the jar: checksum, atomic replacement, offline fallback, listeners. */
class SoundDownloadTest {

    private static final Logger LOG = Logger.getLogger("SoundDownloadTest");

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @TempDir
    Path dir;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
        VoiceApi.disable();
    }

    @Test
    void compiledChecksumMatchesTheSoundsInTheRepository() throws IOException {
        Path zip = Path.of("src/main/resources/pack/VoiceAPI-Sounds.zip");
        assertTrue(Files.isRegularFile(zip), "the sounds belong in the repository (the release asset is made from them)");
        assertEquals(SoundDownload.SHA256, SoundDownload.sha256(zip),
                "pack changed: update SoundDownload.SHA256 and upload the new file to the release voice-pack");
        assertTrue(VoicePack.matches(zip));
    }

    @Test
    void defaultUrlPointsToTheVersionedReleaseAsset() {
        SoundDownload sounds = new SoundDownload(dir.resolve("base.zip"), SoundDownload.SHA256, LOG);
        assertEquals("https://github.com/Passimoritz/MC-VillagerVoice/releases/download/voice-pack/VoiceAPI-Sounds-"
                + SoundDownload.SHA256.substring(0, 12) + ".zip", sounds.url(""));
        assertEquals("http://mirror/x-" + SoundDownload.SHA256 + ".zip", sounds.url("http://mirror/x-{sha256}.zip"));
    }

    @Test
    void downloadsVerifiesAndMovesIntoPlace() throws Exception {
        byte[] data = pack();
        String url = serve(data);
        Path target = dir.resolve("pack").resolve("VoiceAPI-Sounds-base.zip");
        SoundDownload sounds = new SoundDownload(target, sha(data), LOG);
        assertTrue(sounds.usable().isEmpty());

        assertTrue(sounds.fetch(url));
        assertArrayEquals(data, Files.readAllBytes(target));
        assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".part")));
        assertTrue(sounds.current());
        assertEquals(Optional.of(target), sounds.usable());

        assertTrue(sounds.fetch(url), "already there");
        assertEquals(1, requests.get(), "a current file is not downloaded again");
    }

    @Test
    void wrongChecksumKeepsTheOldFile() throws Exception {
        String url = serve(pack());
        Path target = dir.resolve("VoiceAPI-Sounds-base.zip");
        byte[] old = pack();
        Files.write(target, old);
        SoundDownload sounds = new SoundDownload(target, "0".repeat(64), LOG);

        assertFalse(sounds.fetch(url));
        assertArrayEquals(old, Files.readAllBytes(target), "a download with the wrong checksum never replaces the file");
        assertFalse(Files.exists(dir.resolve("VoiceAPI-Sounds-base.zip.part")));
    }

    @Test
    void offlineRunsWithoutSoundsOrWithAHandPlacedFile() throws Exception {
        String offline = "http://127.0.0.1:" + freePort() + "/VoiceAPI-Sounds.zip";
        Path target = dir.resolve("VoiceAPI-Sounds-base.zip");
        SoundDownload sounds = new SoundDownload(target, SoundDownload.SHA256, LOG);

        assertFalse(sounds.fetch(offline));
        assertTrue(sounds.usable().isEmpty(), "no file: VoiceAPI runs without resource pack");
        assertFalse(Files.exists(target));

        Files.write(target, pack());
        assertFalse(sounds.fetch(offline));
        assertEquals(Optional.of(target), sounds.usable(), "a VoiceAPI pack placed by hand is used even with another checksum");

        Files.writeString(target, "not a pack");
        assertTrue(sounds.usable().isEmpty());
    }

    @Test
    void httpErrorsAndBadAddressesFail() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        Path target = dir.resolve("VoiceAPI-Sounds-base.zip");
        assertFalse(new SoundDownload(target, SoundDownload.SHA256, LOG).fetch("http://127.0.0.1:" + server.getAddress().getPort() + "/x.zip"));
        assertFalse(Files.exists(target));
        assertFalse(new SoundDownload(target, SoundDownload.SHA256, LOG).fetch("not a url"));
    }

    @Test
    void arrivingSoundsWriteThePackAndNotifyListeners() throws Exception {
        Path base = dir.resolve("pack").resolve("VoiceAPI-Sounds-base.zip");
        Path soundPack = dir.resolve("VoiceAPI-Sounds.zip");
        SongVoices songs = new SongVoices(dir.resolve("songs"), message -> { });
        org.bukkit.plugin.Plugin plugin = (org.bukkit.plugin.Plugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.Plugin.class}, (proxy, method, args) -> method.getName().equals("getLogger") ? LOG : null);
        VoiceApi.enable(null, null, null, null);
        VoiceApi.enableSongs(plugin, songs, null, null, soundPack);
        AtomicInteger changed = new AtomicInteger();
        VoiceApi.onSoundPackChanged(changed::incrementAndGet);
        assertTrue(VoiceApi.soundPack().isEmpty(), "offline start: no sounds yet");

        Files.createDirectories(base.getParent());
        Files.write(base, pack());
        assertTrue(VoiceApi.basePackArrived(base, Runnable::run));
        assertEquals(1, changed.get(), "NoteAPI learns about the sounds and mixes them in");
        assertEquals(Optional.of(soundPack), VoiceApi.soundPack());
        assertTrue(VoicePack.matches(soundPack));

        VoiceApi.disable();
        assertFalse(VoiceApi.basePackArrived(base, Runnable::run), "a download finishing after the plugin stopped changes nothing");
    }

    /** A tiny pack in the VoiceAPI format (different content on every call). */
    private static byte[] pack() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(VoicePack.MARKER));
            zip.write(("{\"format\":" + VoicePack.FORMAT + ",\"n\":" + System.nanoTime() + "}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/voiceapi/sounds.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private String serve(byte[] data) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/VoiceAPI-Sounds.zip";
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
