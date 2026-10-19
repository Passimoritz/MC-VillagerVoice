package de.passimoritz.voiceapi.play;

import de.passimoritz.voiceapi.Segment;
import de.passimoritz.voiceapi.Utterance;
import de.passimoritz.voiceapi.render.Cue;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A running utterance: stop it, look up how far it is, and follow when which word starts (for subtitles).
 * <p>
 * The listeners and the place are queried once per server tick on the main thread; the speech clock (own thread, 5 ms) plays the sounds
 * from the latest state. Observers and {@code whenDone} run on the main thread.
 */
public final class Speech {

    /** A listener state: player, place (ear or speaker) and whether the player hears the pack. */
    record Ear(Player player, Location place, boolean pack) {
    }

    final Utterance utterance;
    final List<Cue> vanilla;
    final List<Cue> pack;
    final Supplier<Location> place;
    final Predicate<Player> listeners;
    final float volume;
    final Runnable whenDone;
    final long id;

    /** Next sound per list. */
    int vanillaIndex;
    int packIndex;
    /** Pending stops (sound, time, pack?), inserted sorted by time. */
    final List<PendingStop> stops = new ArrayList<>();
    volatile List<Ear> ears = List.of();
    volatile long startNanos = -1;
    final AtomicBoolean done = new AtomicBoolean();
    final AtomicBoolean stopped = new AtomicBoolean();
    /** The last reported word (for the observers). */
    volatile int lastWord = -1;
    volatile int wordDue = -1;
    final List<BiConsumer<Integer, String>> wordObservers = new CopyOnWriteArrayList<>();
    final List<java.util.function.Consumer<Segment>> segmentObservers = new CopyOnWriteArrayList<>();
    volatile int lastSegment = -1;
    volatile int segmentDue = -1;

    record PendingStop(int atMs, String sound, boolean pack) {
    }

    Speech(long id, Utterance utterance, List<Cue> vanilla, List<Cue> pack, Supplier<Location> place, Predicate<Player> listeners, double volume,
           Runnable whenDone) {
        this.id = id;
        this.utterance = utterance;
        this.vanilla = vanilla;
        this.pack = pack;
        this.place = place;
        this.listeners = listeners;
        this.volume = (float) Math.max(0.0, Math.min(2.0, volume));
        this.whenDone = whenDone;
    }

    public Utterance utterance() {
        return utterance;
    }

    /** How many milliseconds the utterance has been playing (0 before the start). */
    public int elapsedMs() {
        long start = startNanos;
        return start < 0 ? 0 : (int) ((System.nanoTime() - start) / 1_000_000L);
    }

    /** The length of the utterance in milliseconds (including the lead of the sounds). */
    public int lengthMs() {
        int end = utterance.lengthMs();
        for (Cue cue : vanilla.isEmpty() ? pack : vanilla) {
            end = Math.max(end, cue.stops() ? cue.stopMs() : cue.atMs() + 300);
        }
        return end;
    }

    public boolean isPlaying() {
        return !done.get();
    }

    /** Stops the utterance (running sounds are cut; {@code whenDone} is called). */
    public void stop() {
        stopped.set(true);
    }

    /** Called when a new word starts: index of the word and the word itself (on the main thread). */
    public Speech onWord(BiConsumer<Integer, String> observer) {
        if (observer != null) {
            wordObservers.add(observer);
        }
        return this;
    }

    /** Called when a sound starts (on the main thread, bundled per tick: only the latest). */
    public Speech onSegment(java.util.function.Consumer<Segment> observer) {
        if (observer != null) {
            segmentObservers.add(observer);
        }
        return this;
    }

    /** The word with an index, from the text of the utterance. */
    String wordText(int index) {
        if (index < 0) {
            return "";
        }
        String[] words = utterance.text().split("\\s+");
        return index < words.length ? words[index] : "";
    }

    /** Whether a player listens. */
    boolean hears(Player player) {
        return listeners == null || listeners.test(player);
    }

    /** The id of the listener filter (for statistics). */
    UUID key() {
        return new UUID(id, 0);
    }
}
