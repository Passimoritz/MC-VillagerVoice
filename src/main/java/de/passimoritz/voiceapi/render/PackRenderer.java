package de.passimoritz.voiceapi.render;

import de.passimoritz.voiceapi.Segment;
import de.passimoritz.voiceapi.Utterance;
import de.passimoritz.voiceapi.lang.Phoneme;
import de.passimoritz.voiceapi.pack.VoicePack;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprache aus den Lauten des VoiceAPI-Ressourcenpakets (im NoteAPI-Paket): echte Vokale und Konsonanten statt Dorfbewohner-Laute. Jeder
 * Vokal spielt vom Sample des nächstgelegenen Grundtons, höchstens zwei Halbtöne gestreckt; Diphthonge und Gleiten in zwei Stufen; am Ende
 * des Lauts wird das Sample gestoppt. Konsonanten sind fertige Samples; klingende (m, n, l …) in der Tonhöhe der Silbe.
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
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.isPause()) {
                continue;
            }
            Phoneme phoneme = segment.phoneme();
            float volume = segment.volume() * MASTER;
            if (utterance.sung()) {
                // Gesang: das echte Hmm der Stimme auf der Tonhöhe des Tons, keine Geräusche (wie die bekannten Dorfbewohner-Videos);
                // die Hmm-Samples sind über die Spitze normiert (leiser als die Vokale), darum ohne MASTER
                if (phoneme.isVocalic() || phoneme.isSonorant()) {
                    hum(cues, utterance.speaker(), segment.midPitch(), segment.startMs(), segment.endMs(), segment.volume(), i);
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
     * Ein gesungener Ton: das Hmm wird am Tonbeginn angestoßen und klingt von selbst aus (kein Stopp – der Ausklang liegt unter dem nächsten
     * Ton, wie in den bekannten Videos); hält der Ton länger als das Sample, wird es neu angesetzt.
     */
    private static void hum(List<Cue> cues, String speaker, double pitch, int from, int to, float volume, int index) {
        if (to <= from) {
            return;
        }
        int root = VoicePack.root(pitch);
        float rate = VoicePack.rate(pitch, root);
        int at = from;
        while (true) {
            // die Länge, die ungefähr mit dem Ton endet (klingt von selbst aus, Luft bis zum nächsten Ton); nie hart stoppen
            cues.add(new Cue(at, VoicePack.humSound(speaker, root, VoicePack.humLength(to - at)), rate, volume, -1, index));
            at += VoicePack.HUM_RETRIGGER_MS;
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
