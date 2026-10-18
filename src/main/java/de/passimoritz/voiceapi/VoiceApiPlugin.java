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
 * Das Plugin: schaltet die {@link VoiceApi} ein, lädt die Bibliothek, startet die Sprach-Uhr und packt die Werkzeuge
 * ({@code plugins/VoiceAPI/tools/*.py}) aus. Keine Befehle – die NoteAPI (oder ein anderes Plugin) bietet die Sprache an.
 */
public final class VoiceApiPlugin extends JavaPlugin {

    private static final List<String> TOOLS = List.of("voicelab.py", "build_voice_pack.py", "compare.py", "villager_sing.py", "villager_dataset.py",
            "rvc_setup.py", "villager_voice.py", "sing_test.py", "find_hums.py", "hums.json");

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
            getLogger().log(Level.WARNING, "Das Lautpaket ließ sich nicht schreiben", e);
        }
        String home = getConfig().getString("rvc.home", "");
        Path rvcHome = home == null || home.isBlank() ? Path.of(System.getProperty("user.home"), ".voiceapi", "rvc") : Path.of(home.strip());
        converter = new de.passimoritz.voiceapi.song.SongConverter(rvcHome, getDataFolder().toPath().resolve("tools").resolve("villager_sing.py"),
                getConfig().getString("rvc.model", "villager"));
        VoiceApi.enable(player, library, pack, Files.isRegularFile(soundPack) ? soundPack : base);
        VoiceApi.enableSongs(this, songs, converter, base, soundPack);
        getLogger().info("VoiceAPI " + getDescription().getVersion() + " bereit: " + Speaker.values().length + " Stimmen, " + library.size()
                + " gespeicherte Stücke, " + songs.size() + " Villager-Gesänge, Ressourcenpaket " + (pack.available() ? "mit" : "ohne")
                + " VoiceAPI-Laute, Stimmmodell " + (converter.ready() ? "bereit" : "nicht eingerichtet (" + String.join(", ", converter.missing()) + ")") + ".");
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
     * Legt die beigelegten Laute als Grundpaket nach {@code pack/VoiceAPI-Sounds-base.zip} (nur, wenn sie sich geändert haben); das fertige
     * {@code VoiceAPI-Sounds.zip} entsteht daraus plus den Villager-Gesängen ({@link de.passimoritz.voiceapi.song.SongVoices#writePack}).
     * @return die Datei, oder null
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
            getLogger().log(Level.WARNING, "Die Laute ließen sich nicht auspacken", e);
            return Files.isRegularFile(target) ? target : null;
        }
    }

    /** Legt die Python-Werkzeuge aus dem Jar in den Datenordner (immer der Stand des Jars). */
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
            getLogger().log(Level.WARNING, "Die Werkzeuge ließen sich nicht auspacken", e);
        }
    }
}
