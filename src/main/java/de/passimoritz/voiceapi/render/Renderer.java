package de.passimoritz.voiceapi.render;

import de.passimoritz.voiceapi.Compat;

import de.passimoritz.voiceapi.Utterance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an utterance into the sound commands for the game. Two renderers: {@link VanillaRenderer} (villager and block sounds, without a
 * resource pack) and {@link PackRenderer} (the sounds of the VoiceAPI pack in the NoteAPI resource pack).
 */
public interface Renderer {

    /** The name of the renderer (for display). */
    String name();

    /** The sound commands of an utterance, ordered by time, the first at 0 ms. */
    List<Cue> render(Utterance utterance);

    /** Overall volume vowels play at: leaves headroom so quieter sounds can be raised. */
    float MASTER = 0.6f;

    /**
     * Tidies a list: orders it by time, shifts everything so the first command is at 0 ms, and moves stops earlier if the same sound starts
     * again before the stop – in the game a stop hits all running sounds of that name, including the new one.
     */
    static List<Cue> finish(List<Cue> cues) {
        if (cues.isEmpty()) {
            return List.of();
        }
        List<Cue> sorted = new ArrayList<>(cues);
        sorted.sort(Comparator.comparingInt(Cue::atMs));
        int offset = -Math.min(0, Compat.first(sorted).atMs());
        List<Cue> shifted = new ArrayList<>(sorted.size());
        for (Cue cue : sorted) {
            shifted.add(offset == 0 ? cue : cue.shifted(offset));
        }
        // stops before the next start of the same sound
        Map<String, Integer> last = new HashMap<>();
        for (int i = shifted.size() - 1; i >= 0; i--) {
            Cue cue = shifted.get(i);
            Integer next = last.get(cue.sound());
            if (next != null && cue.stops() && cue.stopMs() >= next) {
                shifted.set(i, cue.stoppedAt(Math.max(cue.atMs(), next - 5)));
            }
            last.put(cue.sound(), cue.atMs());
        }
        return List.copyOf(shifted);
    }
}
