package de.passimoritz.voiceapi.pack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * The connection to NoteAPI's resource packs: NoteAPI sends VoiceAPI's sounds as a pack of their own ({@code pack/VoiceAPI-Base.zip},
 * newer NoteAPI) or mixed into {@code NoteAPI-Sounds.zip} (older NoteAPI, servers before 1.20.3). This class knows whether one of these files
 * contains the sounds ({@link #available()}) and which player has loaded them – it asks NoteAPI for that ({@code MusicApi.player()
 * .hasVoiceSound(uuid)}, with older NoteAPI {@code hasRealSound}, without a compile-time dependency), or a plugin decides it itself
 * ({@link #probe(Predicate)}).
 */
public final class PackBridge {

    private static final long RECHECK_MS = 30_000;

    private final JavaPlugin plugin;
    private volatile boolean enabled = true;
    private volatile Path file;
    /** NoteAPI's own pack with only the VoiceAPI sounds (newer NoteAPI). */
    private volatile Path voiceFile;
    private volatile boolean available;
    private volatile long checkedAt = -RECHECK_MS;
    private volatile Predicate<Player> probe;
    private volatile Method hasRealSound;
    private volatile Object musicPlayer;
    private volatile long noteApiCheckedAt = -RECHECK_MS;

    public PackBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Takes over the settings ({@code pack.use}, {@code pack.file}). */
    public void configure(boolean use, String path) {
        enabled = use;
        Path noteApi = plugin.getDataFolder().toPath().getParent().resolve("NoteAPI").resolve("pack");
        file = path == null || path.isBlank() ? noteApi.resolve("NoteAPI-Sounds.zip") : Path.of(path.strip());
        voiceFile = path == null || path.isBlank() ? noteApi.resolve("VoiceAPI-Base.zip") : null;
        checkedAt = -RECHECK_MS;
        refresh();
    }

    /** The pack file in which the sounds are expected (NoteAPI's voice pack if it has one, otherwise its sound pack). */
    public Path file() {
        Path voice = voiceFile;
        return voice != null && Files.isRegularFile(voice) ? voice : file;
    }

    /** Checks the pack file again (at most every 30 seconds). */
    public synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < RECHECK_MS) {
            return;
        }
        checkedAt = now;
        Path voice = voiceFile;
        Path where = enabled && voice != null && Files.isRegularFile(voice) && VoicePack.matches(voice) ? voice
                : enabled && file != null && Files.isRegularFile(file) && VoicePack.matches(file) ? file : null;
        boolean found = where != null;
        if (found != available) {
            plugin.getLogger().info(found ? "VoiceAPI sounds found in the resource pack: " + where
                    : "Resource pack without VoiceAPI sounds (" + file + ") – players hear villager sounds. NoteAPI adds them by itself.");
        }
        available = found;
    }

    /** Whether the pack file contains the sounds of this plugin version. */
    public boolean available() {
        if (System.currentTimeMillis() - checkedAt >= RECHECK_MS) {
            refresh();
        }
        return available;
    }

    /** Decides which players hear the pack sounds ({@code null}: ask NoteAPI). */
    public void probe(Predicate<Player> newProbe) {
        probe = newProbe;
    }

    /** Whether a player hears the pack sounds: the pack exists and the player has loaded it. */
    public boolean hears(Player player) {
        if (!available()) {
            return false;
        }
        Predicate<Player> custom = probe;
        if (custom != null) {
            try {
                return custom.test(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "A plugin's pack probe failed", e);
                return false;
            }
        }
        return noteApiHasPack(player.getUniqueId());
    }

    /** Asks NoteAPI whether the player has loaded its pack (via reflection: VoiceAPI does not need NoteAPI to compile). */
    private boolean noteApiHasPack(UUID player) {
        Method method = hasRealSound;
        Object target = musicPlayer;
        long now = System.currentTimeMillis();
        if ((method == null || target == null) && now - noteApiCheckedAt >= RECHECK_MS) {
            noteApiCheckedAt = now;
            try {
                Plugin noteApi = Bukkit.getPluginManager().getPlugin("NoteAPI");
                if (noteApi != null && noteApi.isEnabled()) {
                    Class<?> api = Class.forName("de.passimoritz.noteapi.MusicApi", true, noteApi.getClass().getClassLoader());
                    Object music = api.getMethod("player").invoke(null);
                    Method found;
                    try {
                        found = music.getClass().getMethod("hasVoiceSound", UUID.class); // newer NoteAPI: the voice pack of its own
                    } catch (NoSuchMethodException older) {
                        found = music.getClass().getMethod("hasRealSound", UUID.class);
                    }
                    musicPlayer = music;
                    hasRealSound = found;
                    method = found;
                    target = music;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "Cannot ask NoteAPI for the pack state", e);
            }
        }
        if (method == null || target == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(target, player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            hasRealSound = null;
            musicPlayer = null;
            return false;
        }
    }
}
