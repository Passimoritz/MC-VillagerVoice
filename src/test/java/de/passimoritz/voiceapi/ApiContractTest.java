package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Language;
import de.passimoritz.voiceapi.sing.MelodyNote;
import de.passimoritz.voiceapi.sing.SingOptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What NoteAPI uses via reflection or against a slim voiceapi jar – must not change without telling NoteAPI.
 */
class ApiContractTest {

    @Test
    void singOptionsTextFormatIsStable() {
        assertEquals("volume=0.80; dynamics=0.35; legato=140; overlap=35; glide=3.0; glideMs=45; minNote=70", SingOptions.DEFAULT.describe());
        SingOptions parsed = SingOptions.parse("volume=0.7; dynamics=0.2; legato=200; overlap=40; glide=2; glideMs=50; minNote=60");
        assertEquals(new SingOptions(0.7, 0.2, 200, 40, 2.0, 50, 60), parsed);
        assertEquals(parsed, SingOptions.parse(parsed.describe()));
        // German keys and unknown keys
        assertEquals(0.5, SingOptions.parse("lautstaerke=0.5 foo=1 dynamik=x").volume(), 1e-9);
        assertEquals(SingOptions.DEFAULT, SingOptions.parse(""));
    }

    @Test
    void publicSignaturesUsedByNoteApiExist() throws Exception {
        Method sing = VoiceApi.class.getMethod("sing", String.class, Language.class, Speaker.class, List.class, Style.class, SingOptions.class);
        assertTrue(Modifier.isStatic(sing.getModifiers()));
        VoiceApi.class.getMethod("sing", String.class, Language.class, Speaker.class, List.class, Style.class);
        VoiceApi.class.getMethod("cues", Utterance.class, boolean.class);
        VoiceApi.class.getMethod("needsPack", Utterance.class);
        VoiceApi.class.getMethod("soundPack");
        VoiceApi.class.getMethod("onSoundPackChanged", Runnable.class);
        VoiceApi.class.getMethod("songVoice", String.class);
        VoiceApi.class.getMethod("convertSong", String.class, java.nio.file.Path.class, java.util.function.Consumer.class);
        VoiceApi.class.getMethod("convertSpeech", String.class, java.nio.file.Path.class, java.util.function.Consumer.class);
        assertTrue(VoiceApi.API_VERSION >= 4, "convertSpeech came with interface 4");
        VoiceApi.class.getMethod("songVoiceMissing");
        MelodyNote.class.getMethod("forWord", int.class);
        SingOptions.class.getMethod("parse", String.class);
        SingOptions.class.getMethod("describe");
        assertTrue(VoiceApi.API_VERSION >= 3);
    }

    @Test
    void singWithOptionsWorksWithoutServer() {
        List<MelodyNote> melody = List.of(new MelodyNote(0, 300, 60).forWord(0), new MelodyNote(300, 300, 64).forWord(1));
        Utterance sung = VoiceApi.sing("a a", Language.GERMAN, Speaker.VILLAGER, melody, Style.DEFAULT.withPitch(2), SingOptions.parse("legato=200"));
        assertTrue(sung.sung());
        assertTrue(VoiceApi.cues(sung, true).stream().allMatch(c -> c.sound().startsWith("voiceapi:hum.villager.")));
    }
}
