package de.passimoritz.voiceapi.song;

import de.passimoritz.voiceapi.render.Cue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The villager singing of a piece, converted by the AI voice model from the real vocal track ({@code tools/villager_sing.py --chunks}) and
 * stored as short, crossfaded sections in the sound pack ({@code voiceapi:song.<id>.<n>}).
 *
 * @param id        the id of the piece (as in NoteAPI)
 * @param chunkMs   length of a section without overlap (ms)
 * @param overlapMs crossfade at every boundary (ms); section {@code n} starts {@code overlapMs} before its boundary
 * @param count     number of sections
 * @param silent    sections without singing (not in the pack, skipped)
 * @param lengthMs  length of the singing (ms)
 * @param model     the voice model used for the conversion
 * @param starts    the boundary of every section (ms; cut at quiet moments, so a varying start delay of streamed sounds cannot be heard);
 *                  empty for songs converted before 1.7: then every {@code chunkMs}
 */
public record SongVoice(String id, int chunkMs, int overlapMs, int count, Set<Integer> silent, int lengthMs, String model, List<Integer> starts) {

    public SongVoice {
        silent = Set.copyOf(silent);
        chunkMs = Math.max(500, chunkMs);
        overlapMs = Math.max(0, Math.min(chunkMs / 4, overlapMs));
        starts = starts == null || starts.size() != count ? List.of() : List.copyOf(starts);
    }

    /** A song voice with fixed section lengths (converted before 1.7). */
    public SongVoice(String id, int chunkMs, int overlapMs, int count, Set<Integer> silent, int lengthMs, String model) {
        this(id, chunkMs, overlapMs, count, silent, lengthMs, model, List.of());
    }

    /** The sound name of a section. */
    public String sound(int chunk) {
        return "voiceapi:" + key(chunk);
    }

    /** The key of a section in {@code sounds.json}. */
    public String key(int chunk) {
        return "song." + id + "." + chunk;
    }

    /** When a section starts (ms from the beginning of the piece). */
    public int startMs(int chunk) {
        int boundary = starts.isEmpty() ? chunk * chunkMs : starts.get(chunk);
        return Math.max(0, boundary - (chunk > 0 ? overlapMs : 0));
    }

    /**
     * The sound commands of the whole singing (speed 1, full volume, let decay) – to be played on the music clock of the integrating plugin.
     * The plugin controls the volume.
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

    /** The commands from a position (for jumps): the running section is left out, playback continues with the next one. */
    public List<Cue> cuesFrom(int fromMs) {
        return cues().stream().filter(cue -> cue.atMs() >= fromMs).toList();
    }
}
