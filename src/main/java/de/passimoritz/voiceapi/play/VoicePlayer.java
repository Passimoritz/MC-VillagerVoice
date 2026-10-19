package de.passimoritz.voiceapi.play;

import de.passimoritz.voiceapi.Compat;

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
 * Plays utterances: its own speech clock (thread, 5 ms steps) starts and stops the sounds to the millisecond – the server tick (50 ms) would
 * be too coarse for consonants. Every server tick the main thread collects listeners, places and pack state again; the clock works with that
 * state. Players with the resource pack (VoiceAPI sounds) get the pack sounds, everyone else the vanilla sounds.
 * <p>
 * The sounds play in the category from {@code speech.category} (default: Voice/Speech), so a player can adjust them in the game.
 */
public final class VoicePlayer {

    /** Nobody hears the speaker further away than this many blocks. */
    public static final double RANGE = 24.0;
    private static final long STEP_MS = 5;

    private final JavaPlugin plugin;
    private final PackBridge pack;
    private final List<Speech> running = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicLong cuesPlayed = new AtomicLong();
    private ScheduledExecutorService clock;
    private de.passimoritz.voiceapi.Scheduling.Repeating syncTask;
    private volatile SoundCategory category = defaultCategory();
    private volatile float master = 1.0f;
    private volatile double rate = 1.0;
    private volatile boolean singRequiresPack = true;
    private volatile boolean speechRequiresPack = true;

    public VoicePlayer(JavaPlugin plugin, PackBridge pack) {
        this.plugin = plugin;
        this.pack = pack;
    }

    /** Takes over the settings ({@code speech.*}). */
    public void configure(String categoryName, double volume, double speechRate) {
        SoundCategory found = defaultCategory();
        if (categoryName != null) {
            for (SoundCategory candidate : SoundCategory.values()) {
                if (candidate.name().equalsIgnoreCase(categoryName.strip())) {
                    found = candidate;
                }
            }
        }
        category = found;
        master = (float) Math.max(0.0, Math.min(1.0, volume));
        rate = Double.isFinite(speechRate) && speechRate > 0 ? Compat.clamp(speechRate, 0.4, 2.5) : 1.0;
    }

    /** Whether singing only plays with the resource pack ({@code sing.require-pack}): with vanilla sounds the villager does not hit the notes
     * (its hum is only stable for ~180 ms and the game randomly picks one of three takes); players without the pack then hear nothing, and
     * the integrating plugin should enforce the pack ({@link de.passimoritz.voiceapi.VoiceApi#needsPack}). */
    public void singRequiresPack(boolean required) {
        singRequiresPack = required;
    }

    public boolean singRequiresPack() {
        return singRequiresPack;
    }

    /** Whether speech also only plays with the resource pack ({@code speech.require-pack}, on by default: the vanilla sounds are only a
     * fallback). */
    public void speechRequiresPack(boolean required) {
        speechRequiresPack = required;
    }

    public boolean speechRequiresPack() {
        return speechRequiresPack;
    }

    /** "Voice", otherwise "Players", otherwise the first category – looked up by name so no server update breaks the plugin. */
    private static SoundCategory defaultCategory() {
        for (String name : new String[]{"VOICE", "PLAYERS", "MASTER"}) {
            for (SoundCategory candidate : SoundCategory.values()) {
                if (candidate.name().equals(name)) {
                    return candidate;
                }
            }
        }
        return SoundCategory.values()[0];
    }

    /** The speaking rate from the settings (applied to every utterance). */
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
            Thread thread = new Thread(r, "VoiceAPI-Clock");
            thread.setDaemon(true);
            return thread;
        });
        clock.scheduleAtFixedRate(this::step, STEP_MS, STEP_MS, TimeUnit.MILLISECONDS);
        syncTask = de.passimoritz.voiceapi.Scheduling.everyTick(plugin, this::sync);
    }

    public void shutdown() {
        stopAll();
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        if (clock != null) {
            clock.shutdownNow();
            clock = null;
        }
    }

    /** See {@link de.passimoritz.voiceapi.VoiceApi#play}. */
    public Speech play(Utterance utterance, Supplier<Location> place, Predicate<Player> listeners, double volume, Runnable whenDone) {
        Utterance spoken = rate == 1.0 ? utterance : utterance.stretched(1.0 / rate);
        boolean packOnly = spoken.sung() ? singRequiresPack : speechRequiresPack;
        List<Cue> vanilla = packOnly ? List.of() : VanillaRenderer.INSTANCE.render(spoken);
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

    /** How many utterances are playing right now. */
    public int active() {
        return running.size();
    }

    /** How many sound commands were sent in total. */
    public long cuesPlayed() {
        return cuesPlayed.get();
    }

    /** All running utterances. */
    public List<Speech> speeches() {
        return List.copyOf(running);
    }

    // ── Main thread: listeners and places per tick ──

    private void sync() {
        for (Speech speech : running) {
            if (speech.done.get()) {
                continue;
            }
            try {
                speech.ears = ears(speech);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not determine the listeners of an utterance", e);
                speech.ears = List.of();
            }
            // update the observers (words, sounds)
            int word = speech.wordDue;
            if (word != speech.lastWord && word >= 0) {
                speech.lastWord = word;
                String text = speech.wordText(word);
                for (var observer : speech.wordObservers) {
                    try {
                        observer.accept(word, text);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "An utterance observer failed", e);
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
                        plugin.getLogger().log(Level.WARNING, "An utterance observer failed", e);
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

    // ── Speech clock ──

    private void step() {
        long now = System.nanoTime();
        for (Speech speech : running) {
            try {
                advance(speech, now);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Speech clock failed", e);
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
            // cut everything that is running
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
        // stops that are due
        while (!speech.stops.isEmpty() && Compat.first(speech.stops).atMs() <= elapsed) {
            Speech.PendingStop stop = Compat.removeFirst(speech.stops);
            for (Speech.Ear ear : ears) {
                if (ear.pack() == stop.pack()) {
                    ear.player().stopSound(stop.sound(), category);
                }
            }
        }
        // sounds that are due
        speech.vanillaIndex = fire(speech, speech.vanilla, speech.vanillaIndex, elapsed, ears, false);
        speech.packIndex = fire(speech, speech.pack, speech.packIndex, elapsed, ears, true);
        // observers: which sound is playing
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
            de.passimoritz.voiceapi.Scheduling.run(plugin, () -> {
                try {
                    speech.whenDone.run();
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "whenDone of an utterance failed", e);
                }
            });
        }
    }
}
