package de.passimoritz.voiceapi.render;

import de.passimoritz.voiceapi.Compat;

import de.passimoritz.voiceapi.Utterance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Macht aus einem Sprechstück die Klang-Befehle fürs Spiel. Zwei Renderer: {@link VanillaRenderer} (Dorfbewohner- und Block-Laute, ohne
 * Ressourcenpaket) und {@link PackRenderer} (die Laute des VoiceAPI-Pakets im NoteAPI-Ressourcenpaket).
 */
public interface Renderer {

    /** Der Name des Renderers (für Anzeigen). */
    String name();

    /** Die Klang-Befehle eines Stücks, nach der Zeit geordnet, der erste bei 0 ms. */
    List<Cue> render(Utterance utterance);

    /** Gesamt-Lautstärke, mit der Vokale spielen: lässt Luft, damit leisere Klänge angehoben werden können. */
    float MASTER = 0.6f;

    /**
     * Räumt eine Liste auf: ordnet nach der Zeit, verschiebt alles, damit der erste Befehl bei 0 ms liegt, und zieht Stopps vor, wenn derselbe
     * Klang vor dem Stopp erneut startet – ein Stopp trifft im Spiel alle laufenden Klänge dieses Namens, also auch den neuen.
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
        // Stopps vor dem nächsten Start desselben Klangs
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
