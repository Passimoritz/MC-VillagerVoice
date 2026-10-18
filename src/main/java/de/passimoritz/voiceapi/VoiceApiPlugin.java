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

    private static final List<String> TOOLS = List.of("voicelab.py", "build_voice_pack.py", "compare.py");

    private VoicePlayer player;

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
        VoiceApi.enable(player, library, pack, extractSounds());
        getLogger().info("VoiceAPI " + getDescription().getVersion() + " bereit: " + Speaker.values().length + " Stimmen, " + library.size()
                + " gespeicherte Stücke, Ressourcenpaket " + (pack.available() ? "mit" : "ohne") + " VoiceAPI-Laute.");
    }

    @Override
    public void onDisable() {
        VoiceApi.disable();
        player = null;
    }

    /** Legt die beigelegten Laute nach {@code VoiceAPI-Sounds.zip} (nur, wenn sie sich geändert haben). @return die Datei, oder null */
    private Path extractSounds() {
        Path target = getDataFolder().toPath().resolve("VoiceAPI-Sounds.zip");
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
