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
        de.passimoritz.voiceapi.pack.SoundDownload sounds = new de.passimoritz.voiceapi.pack.SoundDownload(
                getDataFolder().toPath().resolve("pack").resolve("VoiceAPI-Sounds-base.zip"), de.passimoritz.voiceapi.pack.SoundDownload.SHA256,
                getLogger());
        boolean bundled = extractSounds(sounds.target());
        boolean current = sounds.current();
        Path base = current ? sounds.target() : sounds.usable().orElse(null);
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
        if (!bundled && !current) {
            if (getConfig().getBoolean("pack.download", true)) {
                downloadSounds(sounds, sounds.url(getConfig().getString("pack.download-url", "")));
            } else if (base == null) {
                getLogger().warning("No VoiceAPI sounds at " + sounds.target() + " and pack.download is off – running without resource pack. Place "
                        + "the file from " + sounds.url("") + " there and restart.");
            }
        }
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
     * Places sounds bundled with the jar (only a jar built with {@code -P bundled-pack} has them) as the base pack, only if they changed;
     * the finished {@code VoiceAPI-Sounds.zip} is made from it plus the villager songs ({@link de.passimoritz.voiceapi.song.SongVoices#writePack}).
     * @return whether the jar carries the sounds (then nothing is downloaded)
     */
    private boolean extractSounds(Path target) {
        try (InputStream in = getResource("pack/VoiceAPI-Sounds.zip")) {
            if (in == null) {
                return false;
            }
            byte[] data = in.readAllBytes();
            if (!Files.isRegularFile(target) || !java.util.Arrays.equals(Files.readAllBytes(target), data)) {
                Files.createDirectories(target.getParent());
                Files.write(target, data);
            }
            return true;
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not unpack the sounds", e);
            return false;
        }
    }

    /**
     * Downloads the base sounds in the background (never holds up the server start); until they are there VoiceAPI runs without resource
     * pack. Afterwards the sound pack is written and {@link VoiceApi#onSoundPackChanged} listeners mix it into the resource pack.
     */
    private void downloadSounds(de.passimoritz.voiceapi.pack.SoundDownload sounds, String url) {
        getLogger().info("Downloading the VoiceAPI sounds in the background from " + url);
        Thread thread = new Thread(() -> {
            if (sounds.fetch(url)) {
                VoiceApi.basePackArrived(sounds.target());
            }
        }, "VoiceAPI sound download");
        thread.setDaemon(true);
        thread.start();
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
