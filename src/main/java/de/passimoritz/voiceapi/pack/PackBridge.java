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
 * Die Anbindung an das Ressourcenpaket der NoteAPI: VoiceAPI hat kein eigenes Paket, seine Laute werden in {@code NoteAPI-Sounds.zip}
 * gemischt ({@code tools/build_voice_pack.py --merge}). Diese Klasse weiß, ob die Paketdatei die Laute enthält ({@link #available()}) und
 * welcher Spieler das Paket geladen hat – das fragt sie die NoteAPI ({@code MusicApi.player().hasRealSound(uuid)}, ohne Abhängigkeit zur
 * Übersetzungszeit), oder ein Plugin bestimmt es selbst ({@link #probe(Predicate)}).
 */
public final class PackBridge {

    private static final long RECHECK_MS = 30_000;

    private final JavaPlugin plugin;
    private volatile boolean enabled = true;
    private volatile Path file;
    private volatile boolean available;
    private volatile long checkedAt = -RECHECK_MS;
    private volatile Predicate<Player> probe;
    private volatile Method hasRealSound;
    private volatile Object musicPlayer;
    private volatile long noteApiCheckedAt = -RECHECK_MS;

    public PackBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Übernimmt die Einstellungen ({@code pack.use}, {@code pack.file}). */
    public void configure(boolean use, String path) {
        enabled = use;
        file = path == null || path.isBlank() ? plugin.getDataFolder().toPath().getParent().resolve("NoteAPI").resolve("pack").resolve("NoteAPI-Sounds.zip")
                : Path.of(path.strip());
        checkedAt = -RECHECK_MS;
        refresh();
    }

    /** Die Paketdatei, in der die Laute erwartet werden. */
    public Path file() {
        return file;
    }

    /** Prüft die Paketdatei neu (höchstens alle 30 Sekunden, außer erzwungen). */
    public synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < RECHECK_MS) {
            return;
        }
        checkedAt = now;
        boolean found = enabled && file != null && Files.isRegularFile(file) && VoicePack.matches(file);
        if (found != available) {
            plugin.getLogger().info(found ? "VoiceAPI-Laute im Ressourcenpaket gefunden: " + file
                    : "Ressourcenpaket ohne VoiceAPI-Laute (" + file + ") – Spieler hören Dorfbewohner-Laute. tools/build_voice_pack.py --merge baut sie hinein.");
        }
        available = found;
    }

    /** Ob die Paketdatei die Laute dieser Plugin-Version enthält. */
    public boolean available() {
        if (System.currentTimeMillis() - checkedAt >= RECHECK_MS) {
            refresh();
        }
        return available;
    }

    /** Legt fest, welche Spieler die Paket-Laute hören ({@code null}: die NoteAPI fragen). */
    public void probe(Predicate<Player> newProbe) {
        probe = newProbe;
    }

    /** Ob ein Spieler die Paket-Laute hört: Paket vorhanden und beim Spieler geladen. */
    public boolean hears(Player player) {
        if (!available()) {
            return false;
        }
        Predicate<Player> custom = probe;
        if (custom != null) {
            try {
                return custom.test(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Paket-Abfrage eines Plugins", e);
                return false;
            }
        }
        return noteApiHasPack(player.getUniqueId());
    }

    /** Fragt die NoteAPI, ob der Spieler ihr Paket geladen hat (per Reflexion: VoiceAPI braucht die NoteAPI nicht zum Übersetzen). */
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
                    Method found = music.getClass().getMethod("hasRealSound", UUID.class);
                    musicPlayer = music;
                    hasRealSound = found;
                    method = found;
                    target = music;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "NoteAPI-Paketstand nicht abfragbar", e);
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
