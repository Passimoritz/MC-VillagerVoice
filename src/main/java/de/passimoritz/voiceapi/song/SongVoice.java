package de.passimoritz.voiceapi.song;

import de.passimoritz.voiceapi.render.Cue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Der Villager-Gesang eines Stücks, vom KI-Stimmmodell aus der echten Gesangsspur umgewandelt ({@code tools/villager_sing.py --chunks}) und als
 * kurze, überblendete Abschnitte im Lautpaket abgelegt ({@code voiceapi:song.<id>.<n>}).
 *
 * @param id        die Kennung des Stücks (wie in der NoteAPI)
 * @param chunkMs   Länge eines Abschnitts ohne Überlappung (ms)
 * @param overlapMs Überblendung an jeder Grenze (ms); Abschnitt {@code n} beginnt bei {@code n * chunkMs - overlapMs}
 * @param count     Anzahl der Abschnitte
 * @param silent    Abschnitte ohne Gesang (nicht im Paket, werden übersprungen)
 * @param lengthMs  Länge des Gesangs (ms)
 * @param model     das Stimmmodell, mit dem umgewandelt wurde
 */
public record SongVoice(String id, int chunkMs, int overlapMs, int count, Set<Integer> silent, int lengthMs, String model) {

    public SongVoice {
        silent = Set.copyOf(silent);
        chunkMs = Math.max(500, chunkMs);
        overlapMs = Math.max(0, Math.min(chunkMs / 4, overlapMs));
    }

    /** Der Klangname eines Abschnitts. */
    public String sound(int chunk) {
        return "voiceapi:" + key(chunk);
    }

    /** Der Schlüssel eines Abschnitts in {@code sounds.json}. */
    public String key(int chunk) {
        return "song." + id + "." + chunk;
    }

    /** Wann ein Abschnitt startet (ms ab Beginn des Stücks). */
    public int startMs(int chunk) {
        return Math.max(0, chunk * chunkMs - (chunk > 0 ? overlapMs : 0));
    }

    /**
     * Die Klang-Befehle des ganzen Gesangs (Geschwindigkeit 1, volle Lautstärke, ausklingen lassen) – zum Abspielen auf der Musik-Uhr des
     * einbindenden Plugins. Lautstärke regelt das Plugin.
     */
    public List<Cue> cues() {
        List<Cue> cues = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            if (!silent.contains(n)) {
                cues.add(new Cue(startMs(n), sound(n), 1.0f, 1.0f, -1, n));
            }
        }
        return cues;
    }

    /** Die Befehle ab einer Stelle (für Sprünge): der laufende Abschnitt wird ausgelassen, es geht mit dem nächsten weiter. */
    public List<Cue> cuesFrom(int fromMs) {
        return cues().stream().filter(cue -> cue.atMs() >= fromMs).toList();
    }
}
