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
 * Ein laufendes Sprechstück: stoppen, nachsehen, wie weit es ist, und mithören, wann welches Wort beginnt (für Untertitel).
 * <p>
 * Die Zuhörer und der Ort werden einmal je Server-Tick im Hauptthread abgefragt; die Sprach-Uhr (eigener Thread, 5 ms) spielt die Klänge
 * aus dem letzten Stand. Beobachter und {@code whenDone} laufen im Hauptthread.
 */
public final class Speech {

    /** Ein Zuhörer-Stand: Spieler, Ort (Ohr oder Sprecher) und ob er das Paket hört. */
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

    /** Nächster Klang je Liste. */
    int vanillaIndex;
    int packIndex;
    /** Ausstehende Stopps (Klang, Zeit, Paket?), nach Zeit sortiert eingefügt. */
    final List<PendingStop> stops = new ArrayList<>();
    volatile List<Ear> ears = List.of();
    volatile long startNanos = -1;
    final AtomicBoolean done = new AtomicBoolean();
    final AtomicBoolean stopped = new AtomicBoolean();
    /** Das zuletzt gemeldete Wort (für die Beobachter). */
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

    /** Wie viele Millisekunden das Stück läuft (0 vor dem Start). */
    public int elapsedMs() {
        long start = startNanos;
        return start < 0 ? 0 : (int) ((System.nanoTime() - start) / 1_000_000L);
    }

    /** Die Länge des Stücks in Millisekunden (inklusive Vorlauf der Klänge). */
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

    /** Stoppt das Stück (laufende Klänge werden abgebrochen; {@code whenDone} wird aufgerufen). */
    public void stop() {
        stopped.set(true);
    }

    /** Ruft, wenn ein neues Wort beginnt: Nummer des Wortes und das Wort selbst (im Hauptthread). */
    public Speech onWord(BiConsumer<Integer, String> observer) {
        if (observer != null) {
            wordObservers.add(observer);
        }
        return this;
    }

    /** Ruft, wenn ein Laut beginnt (im Hauptthread, gebündelt je Tick: nur der jüngste). */
    public Speech onSegment(java.util.function.Consumer<Segment> observer) {
        if (observer != null) {
            segmentObservers.add(observer);
        }
        return this;
    }

    /** Das Wort mit einer Nummer, aus dem Text des Stücks. */
    String wordText(int index) {
        if (index < 0) {
            return "";
        }
        String[] words = utterance.text().split("\\s+");
        return index < words.length ? words[index] : "";
    }

    /** Ob ein Spieler zuhört. */
    boolean hears(Player player) {
        return listeners == null || listeners.test(player);
    }

    /** Die Kennung des Zuhörer-Filters (für die Statistik). */
    UUID key() {
        return new UUID(id, 0);
    }
}
