package de.passimoritz.voiceapi;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.passimoritz.voiceapi.render.Cue;
import de.passimoritz.voiceapi.song.SongVoice;
import de.passimoritz.voiceapi.song.SongVoices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongVoicesTest {

    @Test
    void cuesStartOnTheChunkGridAndSkipSilentChunks() {
        SongVoice voice = new SongVoice("lied", 4000, 60, 4, Set.of(2), 15000, "villager");
        List<Cue> cues = voice.cues();
        assertEquals(List.of(0, 3940, 11940), cues.stream().map(Cue::atMs).toList());
        assertEquals("voiceapi:song.lied.3", Compat.last(cues).sound());
        assertTrue(cues.stream().allMatch(c -> c.pitch() == 1.0f && !c.stops()));
        assertEquals(List.of(11940), voice.cuesFrom(5000).stream().map(Cue::atMs).toList());
    }

    @Test
    void idsAreSafeForThePack() {
        assertEquals("cartoon-on---on", SongVoices.safe("Cartoon On & On"));
        assertEquals("song", SongVoices.safe(" "));
    }

    @Test
    void thePackContainsBaseSoundsAndAllSongChunks(@TempDir Path dir) throws IOException {
        Path base = dir.resolve("base.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(base))) {
            put(out, "assets/voiceapi/sounds.json", "{\"hum.villager.45.l\":{\"sounds\":[{\"name\":\"voiceapi:hum/villager/45_l0\"}]}}");
            put(out, "assets/voiceapi/voiceapi.json", "{\"format\":2}");
            put(out, "assets/voiceapi/sounds/hum/villager/45_l0.ogg", "ogg");
        }
        Path songs = dir.resolve("songs");
        Path song = songs.resolve("lied");
        Files.createDirectories(song);
        Files.writeString(song.resolve("voice.json"),
                "{\"id\":\"lied\",\"chunkMs\":4000,\"overlapMs\":60,\"count\":3,\"silent\":[1],\"lengthMs\":10000,\"model\":\"villager\"}");
        Files.writeString(song.resolve("0.ogg"), "a");
        Files.writeString(song.resolve("2.ogg"), "c");
        SongVoices voices = new SongVoices(songs, null);
        voices.load();
        assertEquals(1, voices.size());
        assertTrue(voices.missingChunks("lied").isEmpty());

        Path target = dir.resolve("VoiceAPI-Sounds.zip");
        assertTrue(voices.writePack(base, target));
        assertFalse(voices.writePack(base, target), "unverändert: nicht neu schreiben");
        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertNotNull(zip.getEntry("assets/voiceapi/sounds/hum/villager/45_l0.ogg"));
            assertNotNull(zip.getEntry("assets/voiceapi/sounds/song/lied/0.ogg"));
            assertNull(zip.getEntry("assets/voiceapi/sounds/song/lied/1.ogg"));
            JsonObject sounds = Compat.json(read(zip, "assets/voiceapi/sounds.json")).getAsJsonObject();
            assertTrue(sounds.has("hum.villager.45.l"));
            assertTrue(sounds.has("song.lied.2"));
            assertTrue(sounds.getAsJsonObject("song.lied.0").getAsJsonArray("sounds").get(0).getAsJsonObject().get("stream").getAsBoolean());
            JsonObject marker = Compat.json(read(zip, "assets/voiceapi/voiceapi.json")).getAsJsonObject();
            assertEquals(2, marker.get("format").getAsInt());
            assertEquals("lied", marker.getAsJsonArray("songs").get(0).getAsJsonObject().get("id").getAsString());
        }

        assertTrue(voices.delete("lied"));
        assertTrue(voices.writePack(base, target));
        try (ZipFile zip = new ZipFile(target.toFile())) {
            assertNull(zip.getEntry("assets/voiceapi/sounds/song/lied/0.ogg"));
            assertEquals(0, Compat.json(read(zip, "assets/voiceapi/voiceapi.json")).getAsJsonObject().getAsJsonArray("songs").size());
        }
    }

    private static void put(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static String read(ZipFile zip, String name) throws IOException {
        return new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
    }
}
