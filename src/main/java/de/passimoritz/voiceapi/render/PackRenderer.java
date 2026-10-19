package de.passimoritz.voiceapi.render;

import de.passimoritz.voiceapi.Segment;
import de.passimoritz.voiceapi.Utterance;
import de.passimoritz.voiceapi.lang.Phoneme;
import de.passimoritz.voiceapi.pack.VoicePack;

import java.util.ArrayList;
import java.util.List;

/**
 * Speech from the sounds of the VoiceAPI resource pack (in the NoteAPI pack): real vowels and consonants instead of villager sounds. Every
 * vowel plays from the sample of the nearest root, stretched by at most two semitones; diphthongs and glides in two steps; at the end of the
 * sound the sample is stopped. Consonants are finished samples; voiced ones (m, n, l …) at the pitch of the syllable.
 */
public final class PackRenderer implements Renderer {

    public static final PackRenderer INSTANCE = new PackRenderer();

    private static final int GLIDE_MIN_MS = 110;

    private PackRenderer() {
    }

    @Override
    public String name() {
        return "pack";
    }

    @Override
    public List<Cue> render(Utterance utterance) {
        List<Cue> cues = new ArrayList<>(utterance.segments().size() * 2);
        List<Segment> segments = utterance.segments();
        int lastVoicedEnd = -1;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.isPause()) {
                continue;
            }
            Phoneme phoneme = segment.phoneme();
            float volume = segment.volume() * MASTER;
            if (utterance.sung()) {
                // singing: the real hum of the voice at the pitch of the note, no noises (like the well-known villager videos);
                // the hum samples have their own loudness normalisation, hence no MASTER. If the note follows the previous one directly,
                // it is tied (soft onset without a new attack).
                if (phoneme.isVocalic() || phoneme.isSonorant()) {
                    // tied: follows directly or overlaps (Singer.smooth starts tied notes earlier)
                    boolean legato = lastVoicedEnd > 0 && segment.startMs() - lastVoicedEnd <= VoicePack.LEGATO_GAP_MS;
                    hum(cues, utterance.speaker(), segment.midPitch(), segment.startMs(), segment.endMs(), segment.volume(), i, legato);
                    lastVoicedEnd = segment.endMs();
                }
                continue;
            }
            if (phoneme.isVocalic()) {
                Phoneme first = phoneme.start() != null ? phoneme.start() : phoneme;
                Phoneme second = phoneme.end() != null ? phoneme.end() : phoneme;
                boolean glide = segment.durMs() >= GLIDE_MIN_MS && (phoneme.isDiphthong() || Math.abs(segment.pitchEnd() - segment.pitch()) >= 1.0);
                if (!glide) {
                    tone(cues, VoicePack.vowelKey(first), true, segment.midPitch(), segment.startMs(), segment.endMs(), volume, i);
                } else {
                    int middle = segment.startMs() + segment.durMs() * (phoneme.isDiphthong() ? 3 : 2) / 5;
                    tone(cues, VoicePack.vowelKey(first), true, segment.pitch(), segment.startMs(), middle, volume, i);
                    tone(cues, VoicePack.vowelKey(second), true, segment.pitchEnd(), middle, segment.endMs(), volume * 0.9f, i);
                }
            } else if (phoneme.isSonorant()) {
                tone(cues, VoicePack.consonantKey(phoneme), false, segment.midPitch(), segment.startMs(), segment.endMs(), volume * 0.85f, i);
            } else if (phoneme.isConsonant()) {
                String key = VoicePack.consonantKey(phoneme);
                if (key != null) {
                    cues.add(new Cue(segment.startMs(), VoicePack.noiseSound(key), 1.0f, volume, segment.endMs() + 40, i));
                }
            }
        }
        return Renderer.finish(cues);
    }

    /**
     * A sung note: the hum is triggered at the start of the note and decays by itself (no stop – the decay lies under the next note, like in
     * the well-known videos); if the note lasts longer than the sample, it is retriggered.
     */
    private static void hum(List<Cue> cues, String speaker, double pitch, int from, int to, float volume, int index, boolean legato) {
        if (to <= from) {
            return;
        }
        int root = VoicePack.root(pitch);
        float rate = VoicePack.rate(pitch, root);
        int at = from;
        boolean soft = legato;
        while (true) {
            // the length that ends about with the note (decays by itself, air until the next note); never stop hard;
            // continuations of a long note are always tied
            cues.add(new Cue(at, VoicePack.humSound(speaker, root, VoicePack.humLength(to - at), soft), rate, volume, -1, index));
            at += VoicePack.HUM_RETRIGGER_MS;
            soft = true;
            if (at + 120 >= to) {
                break;
            }
        }
    }

    private static void tone(List<Cue> cues, String key, boolean vowel, double pitch, int from, int to, float volume, int index) {
        if (key == null || to <= from) {
            return;
        }
        int root = VoicePack.root(pitch);
        float rate = VoicePack.rate(pitch, root);
        String sound = vowel ? VoicePack.vowelSound(key, root) : VoicePack.sonorantSound(key, root);
        int usable = (int) ((vowel ? VoicePack.VOWEL_MS : VoicePack.SONORANT_MS) / rate) - 20;
        int at = from;
        while (at < to) {
            int stop = Math.min(to, at + usable);
            cues.add(new Cue(at, sound, rate, volume, stop, index));
            if (stop >= to) {
                break;
            }
            at = stop - 20;
        }
    }
}
