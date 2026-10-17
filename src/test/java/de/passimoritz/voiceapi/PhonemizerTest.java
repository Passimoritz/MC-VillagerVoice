package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Language;
import de.passimoritz.voiceapi.lang.Phoneme;
import de.passimoritz.voiceapi.lang.Word;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhonemizerTest {

    private static String de(String word) {
        return Language.GERMAN.phonemizer().word(word).stream().map(Object::toString).reduce((a, b) -> a + "." + b).orElse("");
    }

    private static String en(String word) {
        return Language.ENGLISH.phonemizer().word(word).stream().map(Object::toString).reduce((a, b) -> a + "." + b).orElse("");
    }

    @Test
    void germanVowelLengthFollowsSpelling() {
        assertEquals("ˈhaː.bən", de("haben"));      // offene Silbe: lang
        assertEquals("ˈva.sɐ", de("wasser"));      // Doppelkonsonant: kurz, Murmel-r am Ende
        assertEquals("ˈʃtuːl", de("stuhl"));       // Dehnungs-h, st am Anfang = ʃt
        assertEquals("ˈboːt", de("boot"));         // Doppelvokal
        assertEquals("ˈliː.bə", de("liebe"));      // ie, Murmel-e
    }

    @Test
    void germanConsonantRules() {
        assertEquals("ˈʃuː.lə", de("schule"));
        assertEquals("ˈbuːx", de("buch"));          // ach-Laut nach u
        assertEquals("ˈmɪlç", de("milch"));         // ich-Laut nach Konsonant
        assertEquals("ˈkøː.nɪç", de("könig"));      // -ig = ɪç
        assertEquals("ˈveː.nɪç", de("wenig"));
        assertEquals("ˈtaːk", de("tag"));           // Auslautverhärtung
        assertEquals("ˈtsaɪt", de("zeit"));         // z = ts, ei = aɪ
        assertEquals("ˈhaʊs", de("haus"));
        assertEquals("ˈkvɛ.lə", de("quelle"));
    }

    @Test
    void germanStressAndPrefixes() {
        assertEquals(2, Language.GERMAN.phonemizer().word("verstehen").get(1).stress());
        assertEquals(2, Language.GERMAN.phonemizer().word("gefunden").get(1).stress());
        assertEquals(2, Language.GERMAN.phonemizer().word("studieren").get(1).stress());
        assertEquals(2, Language.GERMAN.phonemizer().word("kartoffeln").get(0).stress());
    }

    @Test
    void germanNumbersBecomeWords() {
        assertEquals("zweiundvierzig", Language.GERMAN.phonemizer().number(42));
        assertEquals("einhundertdrei", Language.GERMAN.phonemizer().number(103));
        assertEquals("zweitausendsiebzehn", Language.GERMAN.phonemizer().number(2017));
        assertEquals("eine million zweihunderttausend", Language.GERMAN.phonemizer().number(1_200_000));
    }

    @Test
    void englishLexiconAndRules() {
        assertEquals("ðə", en("the"));
        assertEquals("ˈwʌn", en("one"));
        assertEquals("ˈneɪm", en("name"));          // stummes e: langer Vokal
        assertEquals("ˈkæt", en("cat"));
        assertEquals("ˈʃɪp", en("ship"));
        assertEquals("ˈkɪŋ", en("king"));
        assertEquals("ˈstɔːrm", en("storm").replace("ɔːr", "ɔːr"));
        assertEquals("ˈθɪŋk", en("think"));
        assertEquals("ˈdʒʌmpt", en("jumped"));      // Stamm + -ed nach stimmlosem Laut = t
        assertEquals("ˈkæts", en("cats"));
        assertEquals("ˈdɔgz", en("dogs"));
    }

    @Test
    void englishNumbersBecomeWords() {
        assertEquals("forty two", Language.ENGLISH.phonemizer().number(42));
        assertEquals("one hundred three", Language.ENGLISH.phonemizer().number(103));
        assertEquals("one thousand two hundred", Language.ENGLISH.phonemizer().number(1200));
    }

    @Test
    void textsBecomeWordsWithBreaksEmphasisAndNumbers() {
        List<Word> words = Language.GERMAN.phonemizer().read("Hallo Welt! Hast du 2 Äpfel? Ja... *sehr* GUT.");
        assertEquals(List.of("Hallo", "Welt", "Hast", "du", "zwei", "Äpfel", "Ja", "sehr", "GUT"), words.stream().map(Word::text).toList());
        assertEquals(Word.Break.EXCLAMATION, words.get(1).after());
        assertEquals(Word.Break.QUESTION, words.get(5).after());
        assertEquals(Word.Break.ELLIPSIS, words.get(6).after());
        assertTrue(words.get(7).emphasis());
        assertTrue(words.get(8).emphasis());
        assertEquals(Word.Break.SENTENCE, words.getLast().after());
        assertFalse(words.get(0).emphasis());
    }

    @Test
    void everySyllableHasANucleusAndEveryWordAStress() {
        for (String text : new String[]{"Der schnelle braune Fuchs springt über den faulen Hund", "Kartoffeln wir zweiundvierzig Uhr hier Smaragde Qualität"}) {
            for (Word word : Language.GERMAN.phonemizer().read(text)) {
                assertTrue(word.syllables().stream().anyMatch(s -> s.stress() == 2), word.toString());
                word.syllables().forEach(s -> assertTrue(s.phonemes().stream().anyMatch(p -> p.isVocalic() || p.isSonorant()), word.toString()));
            }
        }
        for (Word word : Language.ENGLISH.phonemizer().read("The quick brown fox jumps over the lazy dog while strangers watch")) {
            // Funktionswörter (the) dürfen unbetont sein, alle anderen haben genau eine Hauptbetonung
            long stressed = word.syllables().stream().filter(s -> s.stress() == 2).count();
            assertTrue(stressed == 1 || stressed == 0 && word.syllables().size() == 1, word.toString());
        }
    }

    @Test
    void vocalicRStaysInItsSyllable() {
        assertEquals(1, Language.GERMAN.phonemizer().word("uhr").size());
        assertEquals(3, Language.GERMAN.phonemizer().word("kartoffeln").size());
        assertEquals(Phoneme.ER, Language.GERMAN.phonemizer().word("uhr").getFirst().phonemes().getLast());
    }

    @Test
    void languageIsGuessedFromTypicalWords() {
        assertEquals(Language.GERMAN, Language.guess("Hallo, wie geht es dir?"));
        assertEquals(Language.GERMAN, Language.guess("Schön dich zu sehen"));
        assertEquals(Language.ENGLISH, Language.guess("Hello, how are you today?"));
    }
}
