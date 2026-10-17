package de.passimoritz.voiceapi.play;

import de.passimoritz.voiceapi.Segment;
import de.passimoritz.voiceapi.Utterance;
import de.passimoritz.voiceapi.pack.PackBridge;
import de.passimoritz.voiceapi.render.Cue;
import de.passimoritz.voiceapi.render.PackRenderer;
import de.passimoritz.voiceapi.render.VanillaRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Spielt Sprechstücke: eine eigene Sprach-Uhr (Thread, 5-ms-Schritte) startet und stoppt die Klänge auf die Millisekunde – der Server-Tick
 * (50 ms) wäre für Konsonanten zu grob. Je Server-Tick werden im Hauptthread Zuhörer, Orte und Paket-Stand neu erhoben; die Uhr arbeitet
 * mit diesem Stand. Spieler mit dem Ressourcenpaket (VoiceAPI-Laute) bekommen die Paket-Klänge, alle anderen die Vanilla-Klänge.
 * <p>
 * Die Klänge laufen in der Kategorie aus {@code speech.category} (Standard: Stimme/Sprache), ein Spieler kann sie also im Spiel regeln.
 */
public final class VoicePlayer {

    /** Weiter als so viele Blöcke vom Sprecher hört niemand mehr. */
    public static final double RANGE = 24.0;
    private static final long STEP_MS = 5;

    private final JavaPlugin plugin;
    private final PackBridge pack;
    private final List<Speech> running = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicLong cuesPlayed = new AtomicLong();
    private ScheduledExecutorService clock;
    private int syncTask = -1;
    private volatile SoundCategory category = SoundCategory.VOICE;
    private volatile float master = 1.0f;
    private volatile double rate = 1.0;
    private volatile boolean singRequiresPack = true;

    public VoicePlayer(JavaPlugin plugin, PackBridge pack) {
        this.plugin = plugin;
        this.pack = pack;
    }

    /** Übernimmt die Einstellungen ({@code speech.*}). */
    public void configure(String categoryName, double volume, double speechRate) {
        SoundCategory found = SoundCategory.VOICE;
        if (categoryName != null) {
            for (SoundCategory candidate : SoundCategory.values()) {
                if (candidate.name().equalsIgnoreCase(categoryName.strip())) {
                    found = candidate;
                }
            }
        }
        category = found;
        master = (float) Math.max(0.0, Math.min(1.0, volume));
        rate = Double.isFinite(speechRate) && speechRate > 0 ? Math.clamp(speechRate, 0.4, 2.5) : 1.0;
    }

    /** Ob Gesang nur mit dem Ressourcenpaket gespielt wird ({@code sing.require-pack}): Mit Vanilla-Klängen trifft der Dorfbewohner die Töne
     * nicht (sein Brummen steht nur ~180 ms stabil und das Spiel wählt zufällig eine von drei Aufnahmen); Spieler ohne Paket hören dann nichts,
     * und das einbindende Plugin soll das Paket erzwingen ({@link de.passimoritz.voiceapi.VoiceApi#needsPack}). */
    public void singRequiresPack(boolean required) {
        singRequiresPack = required;
    }

    public boolean singRequiresPack() {
        return singRequiresPack;
    }

    /** Das Sprechtempo aus der Einstellung (wird auf jedes Stück angewendet). */
    public double rate() {
        return rate;
    }

    public SoundCategory category() {
        return category;
    }

    public void start() {
        if (clock != null) {
            return;
        }
        clock = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "VoiceAPI-Uhr");
            thread.setDaemon(true);
            return thread;
        });
        clock.scheduleAtFixedRate(this::step, STEP_MS, STEP_MS, TimeUnit.MILLISECONDS);
        syncTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::sync, 1L, 1L);
    }

    public void shutdown() {
        stopAll();
        if (syncTask >= 0) {
            Bukkit.getScheduler().cancelTask(syncTask);
            syncTask = -1;
        }
        if (clock != null) {
            clock.shutdownNow();
            clock = null;
        }
    }

    /** Siehe {@link de.passimoritz.voiceapi.VoiceApi#play}. */
    public Speech play(Utterance utterance, Supplier<Location> place, Predicate<Player> listeners, double volume, Runnable whenDone) {
        Utterance spoken = rate == 1.0 ? utterance : utterance.stretched(1.0 / rate);
        List<Cue> vanilla = spoken.sung() && singRequiresPack ? List.of() : VanillaRenderer.INSTANCE.render(spoken);
        List<Cue> packCues = pack != null && pack.available() ? PackRenderer.INSTANCE.render(spoken) : List.of();
        Speech speech = new Speech(ids.incrementAndGet(), spoken, vanilla, packCues, place, listeners, volume, whenDone);
        if (Bukkit.isPrimaryThread()) {
            speech.ears = ears(speech);
        }
        running.add(speech);
        if (vanilla.isEmpty() && packCues.isEmpty()) {
            finish(speech);
        }
        return speech;
    }

    public void stopAll() {
        for (Speech speech : running) {
            speech.stop();
        }
    }

    /** Wie viele Stücke gerade laufen. */
    public int active() {
        return running.size();
    }

    /** Wie viele Klang-Befehle insgesamt gesendet wurden. */
    public long cuesPlayed() {
        return cuesPlayed.get();
    }

    /** Alle laufenden Stücke. */
    public List<Speech> speeches() {
        return List.copyOf(running);
    }

    // ── Hauptthread: Zuhörer und Orte je Tick ──

    private void sync() {
        for (Speech speech : running) {
            if (speech.done.get()) {
                continue;
            }
            try {
                speech.ears = ears(speech);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Zuhörer eines Sprechstücks nicht zu bestimmen", e);
                speech.ears = List.of();
            }
            // Beobachter nachziehen (Wörter, Laute)
            int word = speech.wordDue;
            if (word != speech.lastWord && word >= 0) {
                speech.lastWord = word;
                String text = speech.wordText(word);
                for (var observer : speech.wordObservers) {
                    try {
                        observer.accept(word, text);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Beobachter eines Sprechstücks", e);
                    }
                }
            }
            int segment = speech.segmentDue;
            if (segment != speech.lastSegment && segment >= 0 && segment < speech.utterance.segments().size()) {
                speech.lastSegment = segment;
                Segment current = speech.utterance.segments().get(segment);
                for (var observer : speech.segmentObservers) {
                    try {
                        observer.accept(current);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Beobachter eines Sprechstücks", e);
                    }
                }
            }
        }
    }

    private List<Speech.Ear> ears(Speech speech) {
        Location origin = speech.place == null ? null : speech.place.get();
        List<Speech.Ear> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!speech.hears(player)) {
                continue;
            }
            Location at;
            if (origin == null) {
                at = player.getEyeLocation();
            } else {
                if (origin.getWorld() == null || !origin.getWorld().equals(player.getWorld()) || origin.distanceSquared(player.getLocation()) > RANGE * RANGE) {
                    continue;
                }
                at = origin.clone();
            }
            result.add(new Speech.Ear(player, at, pack != null && pack.hears(player)));
        }
        return result;
    }

    // ── Sprach-Uhr ──

    private void step() {
        long now = System.nanoTime();
        for (Speech speech : running) {
            try {
                advance(speech, now);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Sprach-Uhr", e);
                finish(speech);
            }
        }
    }

    private void advance(Speech speech, long now) {
        if (speech.done.get()) {
            return;
        }
        if (speech.startNanos < 0) {
            speech.startNanos = now;
        }
        int elapsed = (int) ((now - speech.startNanos) / 1_000_000L);
        List<Speech.Ear> ears = speech.ears;
        if (speech.stopped.get()) {
            // alles Laufende abbrechen
            for (Speech.PendingStop stop : speech.stops) {
                for (Speech.Ear ear : ears) {
                    if (ear.pack() == stop.pack()) {
                        ear.player().stopSound(stop.sound(), category);
                    }
                }
            }
            speech.stops.clear();
            finish(speech);
            return;
        }
        // Stopps, die fällig sind
        while (!speech.stops.isEmpty() && speech.stops.getFirst().atMs() <= elapsed) {
            Speech.PendingStop stop = speech.stops.removeFirst();
            for (Speech.Ear ear : ears) {
                if (ear.pack() == stop.pack()) {
                    ear.player().stopSound(stop.sound(), category);
                }
            }
        }
        // Klänge, die fällig sind
        speech.vanillaIndex = fire(speech, speech.vanilla, speech.vanillaIndex, elapsed, ears, false);
        speech.packIndex = fire(speech, speech.pack, speech.packIndex, elapsed, ears, true);
        // Beobachter: welcher Laut läuft
        List<Segment> segments = speech.utterance.segments();
        int current = speech.segmentDue;
        while (current + 1 < segments.size() && segments.get(current + 1).startMs() <= elapsed) {
            current++;
        }
        if (current != speech.segmentDue && current >= 0) {
            speech.segmentDue = current;
            int word = segments.get(current).word();
            if (word >= 0) {
                speech.wordDue = word;
            }
        }
        boolean cuesDone = speech.vanillaIndex >= speech.vanilla.size() && speech.packIndex >= speech.pack.size();
        if (speech.vanilla.isEmpty() && speech.pack.isEmpty()) {
            finish(speech);
            return;
        }
        if (cuesDone && speech.stops.isEmpty() && elapsed >= speech.lengthMs()) {
            finish(speech);
        }
    }

    private int fire(Speech speech, List<Cue> cues, int index, int elapsed, List<Speech.Ear> ears, boolean packCues) {
        while (index < cues.size() && cues.get(index).atMs() <= elapsed) {
            Cue cue = cues.get(index++);
            float volume = Math.min(1f, cue.volume() * speech.volume * master);
            if (volume <= 0.005f) {
                continue;
            }
            for (Speech.Ear ear : ears) {
                if (ear.pack() != packCues) {
                    continue;
                }
                ear.player().playSound(ear.place(), cue.sound(), category, volume, cue.pitch());
                cuesPlayed.incrementAndGet();
            }
            if (cue.stops()) {
                insertStop(speech, new Speech.PendingStop(cue.stopMs(), cue.sound(), packCues));
            }
        }
        return index;
    }

    private static void insertStop(Speech speech, Speech.PendingStop stop) {
        int at = speech.stops.size();
        while (at > 0 && speech.stops.get(at - 1).atMs() > stop.atMs()) {
            at--;
        }
        speech.stops.add(at, stop);
    }

    private void finish(Speech speech) {
        if (!speech.done.compareAndSet(false, true)) {
            return;
        }
        running.remove(speech);
        if (speech.whenDone != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    speech.whenDone.run();
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "whenDone eines Sprechstücks", e);
                }
            });
        }
    }
}
