package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Language;
import de.passimoritz.voiceapi.lang.Phoneme;
import de.passimoritz.voiceapi.lang.Word;
import de.passimoritz.voiceapi.prosody.Prosody;
import de.passimoritz.voiceapi.render.Cue;
import de.passimoritz.voiceapi.render.PackRenderer;
import de.passimoritz.voiceapi.render.VanillaRenderer;
import de.passimoritz.voiceapi.sing.MelodyNote;
import de.passimoritz.voiceapi.sing.Singer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechTest {

    private static Utterance speak(String text, Language language, Style style) {
        List<Word> words = language.phonemizer().read(text);
        return new Utterance("", "", text, language, Speaker.VILLAGER.id(), style, Prosody.compile(words, style, Speaker.VILLAGER));
    }

    @Test
    void segmentsAreOrderedAndDoNotOverlap() {
        Utterance utterance = speak("Willkommen im Dorf! Hast du Hunger?", Language.GERMAN, Style.DEFAULT);
        assertFalse(utterance.isEmpty());
        int end = 0;
        for (Segment segment : utterance.segments()) {
            assertTrue(segment.startMs() >= end, "überlappt: " + segment);
            end = segment.endMs();
        }
        assertEquals(6, utterance.words());
        assertTrue(utterance.lengthMs() > 1500 && utterance.lengthMs() < 4000, "Länge " + utterance.lengthMs());
    }

    @Test
    void questionsRiseAndStatementsFall() {
        Utterance question = speak("Hast du Hunger?", Language.GERMAN, Style.DEFAULT);
        Utterance statement = speak("Du hast Hunger.", Language.GERMAN, Style.DEFAULT);
        Segment lastQ = question.segments().stream().filter(s -> s.phoneme().isVocalic()).reduce((a, b) -> b).orElseThrow();
        Segment lastS = statement.segments().stream().filter(s -> s.phoneme().isVocalic()).reduce((a, b) -> b).orElseThrow();
        assertTrue(lastQ.pitchEnd() > lastQ.pitch(), "Frage steigt: " + lastQ);
        assertTrue(lastS.pitchEnd() < lastS.pitch(), "Aussage fällt: " + lastS);
    }

    @Test
    void stressedSyllablesAreLongerAndLouder() {
        Utterance utterance = speak("Kartoffeln", Language.GERMAN, Style.DEFAULT);
        List<Segment> vowels = utterance.segments().stream().filter(s -> s.phoneme().isVocalic()).toList();
        Segment stressed = vowels.stream().filter(Segment::stressed).findFirst().orElseThrow();
        Segment unstressed = vowels.stream().filter(s -> !s.stressed()).findFirst().orElseThrow();
        assertTrue(stressed.volume() > unstressed.volume());
    }

    @Test
    void styleChangesRateAndPitch() {
        Utterance normal = speak("Die Nacht wird kalt.", Language.GERMAN, Style.DEFAULT);
        Utterance fast = speak("Die Nacht wird kalt.", Language.GERMAN, Style.DEFAULT.withRate(1.5));
        Utterance high = speak("Die Nacht wird kalt.", Language.GERMAN, Style.DEFAULT.withPitch(5));
        assertTrue(fast.lengthMs() < normal.lengthMs() * 0.8);
        assertEquals(normal.segments().get(1).pitch() + 5, high.segments().get(1).pitch(), 0.01);
        Utterance robot = speak("Die Nacht wird kalt.", Language.GERMAN, Style.of(Style.Mood.ROBOT));
        robot.segments().stream().filter(s -> s.phoneme().isVocalic()).forEach(s -> assertEquals(s.pitch(), s.pitchEnd(), 0.01));
    }

    @Test
    void fileRoundTripKeepsEverything() {
        Utterance utterance = speak("Hello there, traveler! Do you want to trade?", Language.ENGLISH, Style.of(Style.Mood.HAPPY).withRate(1.2));
        Utterance again = UtteranceFile.read("gruss", UtteranceFile.write(utterance.named("gruss", "Gruß")));
        assertEquals(utterance.segments().size(), again.segments().size());
        for (int i = 0; i < utterance.segments().size(); i++) {
            Segment a = utterance.segments().get(i);
            Segment b = again.segments().get(i);
            assertEquals(a.startMs(), b.startMs());
            assertEquals(a.durMs(), b.durMs());
            assertEquals(a.phoneme(), b.phoneme());
            assertEquals(a.pitch(), b.pitch(), 0.051);
            assertEquals(a.pitchEnd(), b.pitchEnd(), 0.051);
            assertEquals(a.volume(), b.volume(), 0.0051);
            assertEquals(a.word(), b.word());
            assertEquals(a.stressed(), b.stressed());
        }
        assertEquals(Language.ENGLISH, again.language());
        assertEquals(Style.Mood.HAPPY, again.style().mood());
        assertEquals(1.2, again.style().rate(), 1e-9);
        assertEquals("Gruß", again.title());
        assertEquals(utterance.text(), again.text());
        assertEquals(utterance.words(), again.words());
    }

    @Test
    void vanillaCuesStartAtZeroAndStopBeforeTheSameSoundRestarts() {
        Utterance utterance = speak("Mama und Papa nehmen neun Nüsse.", Language.GERMAN, Style.DEFAULT);
        List<Cue> cues = VanillaRenderer.INSTANCE.render(utterance);
        assertFalse(cues.isEmpty());
        assertEquals(0, cues.getFirst().atMs());
        for (int i = 0; i < cues.size(); i++) {
            Cue cue = cues.get(i);
            assertTrue(cue.pitch() >= 0.5f && cue.pitch() <= 2.0f);
            assertTrue(cue.volume() > 0f && cue.volume() <= 1f);
            for (int j = i + 1; j < cues.size(); j++) {
                Cue later = cues.get(j);
                if (later.sound().equals(cue.sound()) && later.atMs() > cue.atMs() && cue.stops()) {
                    assertTrue(cue.stopMs() < later.atMs(), "Stopp würde den nächsten Klang treffen: " + cue + " vs " + later);
                }
            }
        }
        assertTrue(cues.stream().anyMatch(c -> c.sound().equals("entity.villager.no")));
        assertTrue(cues.stream().anyMatch(c -> c.sound().equals("block.lava.pop")), "p = Lava-Blubb");
    }

    @Test
    void everyPhonemeHasAVanillaAndAPackSound() {
        for (Phoneme phoneme : Phoneme.values()) {
            if (phoneme.isPause()) {
                continue;
            }
            Utterance one = new Utterance("", "", "x", Language.GERMAN, "villager", Style.DEFAULT,
                    List.of(new Segment(0, 150, phoneme, 43f, 0.8f, 0, true)));
            assertFalse(VanillaRenderer.INSTANCE.render(one).isEmpty(), "vanilla: " + phoneme);
            assertFalse(PackRenderer.INSTANCE.render(one).isEmpty(), "pack: " + phoneme);
            for (Cue cue : PackRenderer.INSTANCE.render(one)) {
                assertTrue(cue.sound().startsWith("voiceapi:"), cue.sound());
            }
        }
    }

    @Test
    void allVanillaSoundsExistInMinecraft() throws Exception {
        // die Liste der Klang-Ereignisse von Minecraft 26.3 (aus sounds.json der Assets)
        java.util.Set<String> known = new java.util.HashSet<>();
        try (var in = getClass().getResourceAsStream("/vanilla-sounds.txt")) {
            assertNotNull(in, "src/test/resources/vanilla-sounds.txt fehlt");
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                known.add(line.strip());
            }
        }
        for (String sound : VanillaRenderer.sounds()) {
            assertTrue(known.contains(sound), "kein Vanilla-Klang: " + sound);
        }
    }

    @Test
    void singingPutsVowelsOnTheNotesAndFoldsIntoTheRange() {
        List<MelodyNote> melody = List.of(new MelodyNote(0, 400, 64), new MelodyNote(400, 400, 67), new MelodyNote(800, 800, 71), new MelodyNote(1600, 400, 60));
        Utterance sung = Singer.sing("Hallo Welt", Language.GERMAN, Speaker.VILLAGER, melody, Style.DEFAULT);
        List<Segment> vowels = sung.segments().stream().filter(s -> s.phoneme().isVocalic()).toList();
        assertEquals(4, vowels.size(), sung.segments().toString());
        assertEquals(0, vowels.get(0).startMs());
        assertEquals(400, vowels.get(1).startMs());
        for (Segment vowel : vowels) {
            assertTrue(vowel.pitch() >= 40 && vowel.pitch() <= 70, vowel.toString());
        }
        // eine Oktavlage für alle, um die Gesangslage (55,6) herum: Mitte 67 → eine Oktave tiefer (64 → 52); h vor dem ersten Ton bleibt bei 0
        assertEquals(52, vowels.get(0).pitch(), 0.01);
        assertTrue(sung.segments().getFirst().startMs() >= 0);
        assertEquals(Phoneme.H, sung.segments().getFirst().phoneme());
    }

    @Test
    void fewerNotesThanSyllablesShareNotes() {
        List<MelodyNote> melody = List.of(new MelodyNote(0, 600, 60), new MelodyNote(600, 600, 62));
        Utterance sung = Singer.sing("Hunger und Smaragde", Language.GERMAN, Speaker.VILLAGER, melody, Style.DEFAULT);
        long vowels = sung.segments().stream().filter(s -> s.phoneme().isVocalic()).count();
        assertEquals(6, vowels);
        assertTrue(sung.lengthMs() <= 1200 + 5);
    }

    @Test
    void notesTaggedWithWordsStayWithTheirWord() {
        // "Hallo" bekommt zwei Töne, "Welt" einen – egal wie die Silben sonst verteilt würden
        List<MelodyNote> melody = List.of(new MelodyNote(0, 300, 60, 0.8f, 0), new MelodyNote(300, 300, 62, 0.8f, 0), new MelodyNote(1000, 500, 64, 0.8f, 1));
        Utterance sung = Singer.sing("Hallo Welt", Language.GERMAN, Speaker.VILLAGER, melody, Style.DEFAULT);
        List<Segment> vowels = sung.segments().stream().filter(s -> s.phoneme().isVocalic()).toList();
        assertEquals(3, vowels.size());
        assertEquals(0, vowels.get(0).word());
        assertEquals(0, vowels.get(1).word());
        assertEquals(1, vowels.get(2).word());
        assertEquals(1000, vowels.get(2).startMs());
    }

    @Test
    void speakersStayInTheirRange() {
        for (Speaker speaker : Speaker.values()) {
            assertEquals(1.0f, speaker.rateFor(speaker.naturalPitch()), 1e-6);
            assertEquals(2.0f, speaker.rateFor(speaker.naturalPitch() + 12), 1e-6);
            assertEquals(0.5f, speaker.rateFor(speaker.naturalPitch() - 12), 1e-6);
            assertEquals(2.0f, speaker.rateFor(speaker.naturalPitch() + 30), 1e-6);
            assertTrue(speaker.fit(100) <= speaker.highest() && speaker.fit(100) >= speaker.lowest());
            assertEquals(speaker, Speaker.find(speaker.id()).orElseThrow());
        }
    }

    @Test
    void styleTextRoundTrips() {
        Style style = Style.parse("rate=1.3; pitch=-2; range=0.5; energy=1.2; mood=traurig");
        assertEquals(1.3, style.rate(), 1e-9);
        assertEquals(-2, style.pitch(), 1e-9);
        assertEquals(Style.Mood.SAD, style.mood());
        assertEquals(style, Style.parse(style.describe()));
        assertEquals(Style.Mood.HAPPY, Style.parse("happy").mood());
    }
}
