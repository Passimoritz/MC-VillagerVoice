package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.pack.PackBridge;
import de.passimoritz.voiceapi.play.VoicePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

/**
 * The plugin: enables {@link VoiceApi}, loads the library, starts the speech clock and unpacks the tools
 * ({@code plugins/VoiceAPI/tools/*.py}). No commands – NoteAPI (or another plugin) offers the voices.
 */
public final class VoiceApiPlugin extends JavaPlugin {

    private static final List<String> TOOLS = List.of("voicelab.py", "build_voice_pack.py", "compare.py", "villager_sing.py", "villager_dataset.py",
            "rvc_setup.py", "villager_voice.py", "sing_test.py", "find_hums.py", "hums.json", "rvc_infer.py");

    private VoicePlayer player;
    private de.passimoritz.voiceapi.song.SongConverter converter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        extractTools();
        VoiceLibrary library = new VoiceLibrary(getDataFolder().toPath().resolve("voices"), message -> getLogger().warning(message));
        library.load();
        PackBridge pack = new PackBridge(this);
        pack.configure(getConfig().getBoolean("pack.use", true), getConfig().getString("pack.file", ""));
        player = new VoicePlayer(this, pack);
        player.configure(getConfig().getString("speech.category", "VOICE"), getConfig().getDouble("speech.volume", 1.0), getConfig().getDouble("speech.rate", 1.0));
        player.singRequiresPack(getConfig().getBoolean("sing.require-pack", true));
        player.speechRequiresPack(getConfig().getBoolean("speech.require-pack", true));
        player.start();
        Path base = extractSounds();
        de.passimoritz.voiceapi.song.SongVoices songs = new de.passimoritz.voiceapi.song.SongVoices(getDataFolder().toPath().resolve("songs"),
                message -> getLogger().warning(message));
        songs.load();
        Path soundPack = getDataFolder().toPath().resolve("VoiceAPI-Sounds.zip");
        try {
            if (base != null) {
                songs.writePack(base, soundPack);
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not write the sound pack", e);
        }
        String home = getConfig().getString("rvc.home", "");
        Path rvcHome = home == null || home.isBlank() ? Path.of(System.getProperty("user.home"), ".voiceapi", "rvc") : Path.of(home.strip());
        converter = new de.passimoritz.voiceapi.song.SongConverter(rvcHome, getDataFolder().toPath().resolve("tools").resolve("villager_sing.py"),
                getConfig().getString("rvc.model", "villager"));
        converter.options(timbreOptions());
        VoiceApi.enable(player, library, pack, Files.isRegularFile(soundPack) ? soundPack : base);
        VoiceApi.enableSongs(this, songs, converter, base, soundPack);
        if (getConfig().getBoolean("rvc.resume", true)) {
            int resumed = VoiceApi.resumeInterrupted();
            if (resumed > 0) {
                getLogger().info("Continuing " + resumed + " conversion(s) a server stop interrupted.");
            }
        }
        getLogger().info("VoiceAPI " + getDescription().getVersion() + " ready: " + Speaker.values().length + " voices, " + library.size()
                + " saved utterances, " + songs.size() + " villager songs, resource pack " + (pack.available() ? "with" : "without")
                + " VoiceAPI sounds, voice model " + (converter.ready() ? "ready" : "not set up (" + String.join(", ", converter.missing()) + ")") + ".");
    }

    /**
     * The optional timbre transfer ({@code rvc.timbre-follow}): off by default; with a strength above 0 the villager takes over the
     * singer's timbre movement (livelier, a little less villager).
     */
    private List<String> timbreOptions() {
        double strength = getConfig().getDouble("rvc.timbre-follow.strength", 0.0);
        if (!(strength > 0)) {
            return List.of();
        }
        java.util.function.DoubleFunction<String> num = v -> String.format(java.util.Locale.ROOT, "%.3f", v);
        return List.of("--timbre-follow", num.apply(Math.min(1.0, strength)),
                "--timbre-max-db", num.apply(Compat.clamp(getConfig().getDouble("rvc.timbre-follow.max-db", 6.0), 0.5, 18.0)),
                "--timbre-window-ms", num.apply(Compat.clamp(getConfig().getDouble("rvc.timbre-follow.window-ms", 400.0), 100.0, 2000.0)),
                "--timbre-smooth-ms", num.apply(Compat.clamp(getConfig().getDouble("rvc.timbre-follow.smooth-ms", 40.0), 0.0, 200.0)));
    }

    @Override
    public void onDisable() {
        if (converter != null) {
            converter.shutdown();
        }
        VoiceApi.disable();
        player = null;
    }

    /**
     * Places the bundled sounds as the base pack at {@code pack/VoiceAPI-Sounds-base.zip} (only if they changed); the finished
     * {@code VoiceAPI-Sounds.zip} is made from it plus the villager songs ({@link de.passimoritz.voiceapi.song.SongVoices#writePack}).
     * @return the file, or null
     */
    private Path extractSounds() {
        Path target = getDataFolder().toPath().resolve("pack").resolve("VoiceAPI-Sounds-base.zip");
        try (InputStream in = getResource("pack/VoiceAPI-Sounds.zip")) {
            if (in == null) {
                return Files.isRegularFile(target) ? target : null;
            }
            byte[] data = in.readAllBytes();
            if (!Files.isRegularFile(target) || !java.util.Arrays.equals(Files.readAllBytes(target), data)) {
                Files.createDirectories(target.getParent());
                Files.write(target, data);
            }
            return target;
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not unpack the sounds", e);
            return Files.isRegularFile(target) ? target : null;
        }
    }

    /** Places the Python tools from the jar in the data folder (always the jar's version). */
    private void extractTools() {
        Path folder = getDataFolder().toPath().resolve("tools");
        try {
            Files.createDirectories(folder);
            for (String name : TOOLS) {
                try (InputStream in = getResource("tools/" + name)) {
                    if (in == null) {
                        continue;
                    }
                    Files.copy(in, folder.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not unpack the tools", e);
        }
    }
}
