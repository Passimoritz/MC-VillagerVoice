package de.passimoritz.voiceapi.lang;

import de.passimoritz.voiceapi.Compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.passimoritz.voiceapi.lang.Phoneme.*;

/**
 * German: spelling to sounds following the rules of German orthography (vowel length from the lengthening h, double vowels, following
 * consonants; sch, ch as "ach" or "ich" sound, st/sp at the start, final devoicing, schwa in final syllables, vocalic r), plus a small
 * dictionary for the common exceptions (short vowel before one consonant: "mit", "das"; loan words with v: "Vase"). Stress: first syllable,
 * after the unstressed prefixes (ge-, be-, ver-, er-, ent-, zer-) the second; the endings -ieren, -ion, -tät, -ei carry the stress.
 */
public final class GermanPhonemizer implements Phonemizer {

    public static final GermanPhonemizer INSTANCE = new GermanPhonemizer();

    /** Allowed onsets with two or three consonants (as IPA strings). */
    private static final Set<String> ONSETS = Set.of(
            "pl", "pr", "bl", "br", "tr", "dr", "kl", "kr", "kn", "kv", "gl", "gr", "gn", "fl", "fr", "vr", "ʃl", "ʃm", "ʃn", "ʃp", "ʃt", "ʃr",
            "ʃv", "ʃpr", "ʃtr", "ʃpl", "tsv", "pfl", "pfr", "ps", "pn", "tv", "sk", "sl", "sp", "st", "str", "spr", "skl", "skr");

    /** Words the rules would get wrong: spelling → transcription. */
    private static final Map<String, List<Syllable>> LEXICON = new HashMap<>();

    static {
        String entries = """
                mit=ˈmɪt das=ˈdas dass=ˈdas es=ˈɛs an=ˈan in=ˈɪn im=ˈɪm am=ˈam um=ˈʊm ab=ˈap ob=ˈɔp von=ˈfɔn zum=ˈtsʊm zur=ˈtsuːɐ hat=ˈhat
                man=ˈman bin=ˈbɪn bis=ˈbɪs was=ˈvas des=ˈdɛs dem=ˈdeːm den=ˈdeːn der=ˈdeːɐ die=ˈdiː hin=ˈhɪn weg=ˈvɛk wen=ˈveːn wem=ˈveːm
                ist=ˈɪst hast=ˈhast bist=ˈbɪst ich=ˈɪç dich=ˈdɪç mich=ˈmɪç sich=ˈzɪç nicht=ˈnɪçt nichts=ˈnɪçts doch=ˈdɔx noch=ˈnɔx
                nach=ˈnaːx auch=ˈaʊx ja=ˈjaː nein=ˈnaɪn du=ˈduː zu=ˈtsuː so=ˈzoː wo=ˈvoː da=ˈdaː je=ˈjeː wie=ˈviː nie=ˈniː
                er=ˈeːɐ wir=ˈviːɐ ihr=ˈiːɐ mir=ˈmiːɐ dir=ˈdiːɐ vor=ˈfoːɐ für=ˈfyːɐ nur=ˈnuːɐ sehr=ˈzeːɐ hier=ˈhiːɐ
                ein=ˈaɪn eine=ˈaɪ.nə einen=ˈaɪ.nən einem=ˈaɪ.nəm einer=ˈaɪ.nɐ eines=ˈaɪ.nəs kein=ˈkaɪn keine=ˈkaɪ.nə mein=ˈmaɪn dein=ˈdaɪn sein=ˈzaɪn
                sie=ˈziː wer=ˈveːɐ und=ˈʊnt oder=ˈoː.dɐ aber=ˈaː.bɐ wenn=ˈvɛn dann=ˈdan denn=ˈdɛn als=ˈals
                hallo=ˈha.lo hey=ˈhɛɪ tschüss=ˈtʃʏs tschau=ˈtʃaʊ okay=o.ˈkeː ok=o.ˈkeː bitte=ˈbɪ.tə danke=ˈdaŋ.kə willkommen=vɪl.ˈkɔ.mən
                gut=ˈguːt gute=ˈguː.tə guten=ˈguː.tən morgen=ˈmɔr.gən abend=ˈaː.bənt tag=ˈtaːk nacht=ˈnaxt heute=ˈhɔʏ.tə jetzt=ˈjɛtst
                spiel=ˈʃpiːl spieler=ˈʃpiː.lɐ spielen=ˈʃpiː.lən spielt=ˈʃpiːlt team=ˈtiːm punkte=ˈpʊŋk.tə punkt=ˈpʊŋkt sieg=ˈziːk
                vase=ˈvaː.zə video=ˈviː.de.o videos=ˈviː.de.os vulkan=vʊl.ˈkaːn klavier=kla.ˈviːɐ vitamin=vi.ta.ˈmiːn november=no.ˈvɛm.bɐ
                nerv=ˈnɛrf nerven=ˈnɛr.fən level=ˈlɛ.vəl visum=ˈviː.zʊm villa=ˈvɪ.la vokal=vo.ˈkaːl evangelium=e.vaŋ.ˈgeː.li.ʊm
                computer=kɔm.ˈpjuː.tɐ internet=ˈɪn.tɐ.nɛt server=ˈzɜː.vɐ chat=ˈtʃɛt chef=ˈʃɛf china=ˈçiː.na chance=ˈʃãs
                orange=o.ˈrãʒ garage=ga.ˈraː.ʒə etage=e.ˈtaː.ʒə genie=ʒe.ˈniː journal=ʒʊr.ˈnaːl jeans=ˈdʒiːns job=ˈdʒɔp
                minecraft=ˈmaɪn.kraft creeper=ˈkriː.pɐ dorfbewohner=ˈdɔrf.bə.voː.nɐ dorf=ˈdɔrf
                habe=ˈhaː.bə haben=ˈhaː.bən hatte=ˈha.tə war=ˈvaːɐ waren=ˈvaː.rən wird=ˈvɪrt werden=ˈveːɐ.dən wurde=ˈvʊr.də
                kann=ˈkan können=ˈkœ.nən muss=ˈmʊs müssen=ˈmʏ.sən soll=ˈzɔl will=ˈvɪl darf=ˈdarf mag=ˈmaːk
                leben=ˈleː.bən liebe=ˈliː.bə welt=ˈvɛlt rot=ˈroːt tot=ˈtoːt brot=ˈbroːt mond=ˈmoːnt pferd=ˈpfeːɐt erde=ˈeːɐ.də
                schon=ˈʃoːn schön=ˈʃøːn groß=ˈgroːs los=ˈloːs bus=ˈbʊs plus=ˈplʊs
                """;
        for (String entry : entries.strip().split("\\s+")) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                LEXICON.put(entry.substring(0, eq), Phonemizer.transcription(entry.substring(eq + 1)));
            }
        }
    }

    private GermanPhonemizer() {
    }

    @Override
    public String decimalPoint() {
        return "Komma";
    }

    @Override
    public List<Syllable> word(String word) {
        List<Syllable> known = LEXICON.get(word);
        if (known != null) {
            return known;
        }
        Walk walk = new Walk(word);
        List<Phoneme> phonemes = walk.run();
        if (phonemes.isEmpty()) {
            return List.of();
        }
        List<List<Phoneme>> parts = Phonemizer.syllabify(phonemes, ONSETS);
        return Phonemizer.stress(parts, stressedSyllable(word, parts));
    }

    /** Which syllable carries the primary stress. */
    private static int stressedSyllable(String word, List<List<Phoneme>> parts) {
        if (parts.size() < 2) {
            return 0;
        }
        if (word.endsWith("ieren") || word.endsWith("iert")) {
            return Math.max(0, parts.size() - (word.endsWith("ieren") ? 2 : 1));
        }
        if (word.endsWith("ion") || word.endsWith("tät") || word.endsWith("ei") || word.endsWith("ent") && word.length() > 5
                || word.endsWith("ur") && word.length() > 4 || word.endsWith("ell") || word.endsWith("iv")) {
            return parts.size() - 1;
        }
        int stressed = 0;
        for (String prefix : new String[]{"ge", "be", "ver", "zer", "er", "ent", "emp", "miss"}) {
            if (word.startsWith(prefix) && word.length() > prefix.length() + 2 && parts.size() > 1) {
                // the prefix is exactly the first syllable
                stressed = 1;
                break;
            }
        }
        return stressed;
    }

    /** Walks through a word letter by letter. */
    private static final class Walk {
        private final String w;
        private final List<Phoneme> out = new ArrayList<>();
        private int i;
        private final int vowelCount;
        private int vowelsSeen;

        Walk(String word) {
            this.w = word;
            int count = 0;
            for (int k = 0; k < word.length(); k++) {
                if (isVowelLetter(word.charAt(k)) && !(k > 0 && isVowelLetter(word.charAt(k - 1)) && !isDiphthongStart(word, k - 1))) {
                    count++;
                }
            }
            vowelCount = Math.max(1, count);
        }

        private static boolean isVowelLetter(char c) {
            return "aeiouäöüy".indexOf(c) >= 0;
        }

        private static boolean isDiphthongStart(String word, int k) {
            return k + 1 < word.length() && !(isVowelLetter(word.charAt(k)) && isVowelLetter(word.charAt(k + 1)));
        }

        private boolean at(String text) {
            return w.startsWith(text, i);
        }

        private char ch(int offset) {
            int k = i + offset;
            return k >= 0 && k < w.length() ? w.charAt(k) : '\0';
        }

        private void add(Phoneme... phonemes) {
            for (Phoneme phoneme : phonemes) {
                out.add(phoneme);
            }
        }

        List<Phoneme> run() {
            boolean wordStart = true;
            int afterPrefix = prefixLength();
            while (i < w.length()) {
                char c = w.charAt(i);
                boolean start = wordStart || i == afterPrefix;
                wordStart = false;
                if (isVowelLetter(c)) {
                    vowel();
                    continue;
                }
                consonant(start);
            }
            return out;
        }

        /** The length of an unstressed prefix (ge-, be-, ver- …) after which s, st, sp sound like at the start of a word again. */
        private int prefixLength() {
            for (String prefix : new String[]{"ge", "be", "ver", "zer", "er", "ent", "emp", "ab", "an", "auf", "aus", "ein", "vor", "zu", "um", "mit", "nach", "über", "unter"}) {
                if (w.startsWith(prefix) && w.length() > prefix.length() + 2) {
                    return prefix.length();
                }
            }
            return -1;
        }

        // ── Vowels ──

        private void vowel() {
            vowelsSeen++;
            boolean lastSyllable = vowelsSeen == vowelCount;
            char c = ch(0);
            char n = ch(1);
            // diphthongs
            if (c == 'e' && (n == 'i' || n == 'y') || c == 'a' && (n == 'i' || n == 'y')) {
                add(AI);
                i += 2;
                return;
            }
            if (c == 'a' && n == 'u') {
                add(AU);
                i += 2;
                return;
            }
            if ((c == 'e' || c == 'ä') && n == 'u') {
                add(OI);
                i += 2;
                return;
            }
            if (c == 'i' && n == 'g' && i + 2 == w.length() && vowelCount > 1) {
                // -ig at the end of a word: ɪç (König, wenig)
                add(I, XI);
                i += 2;
                return;
            }
            if (c == 'i' && n == 'e') {
                // ie = long i; "ie" at the end with a following vowel ("Familie") = i + ə
                add(II);
                i += 2;
                if (ch(0) == 'h') {
                    i++;
                }
                return;
            }
            // lengthening h: vowel + h + (consonant or end), not h + vowel
            boolean dehnung = n == 'h' && !isVowelLetter(ch(2)) && ch(2) != '\0' || n == 'h' && ch(2) == '\0';
            boolean doubled = n == c && (c == 'a' || c == 'e' || c == 'o');
            int consonantsAfter = consonantsAfter(dehnung ? 2 : 1);
            boolean endsWord = i + (dehnung ? 2 : 1) + consonantsAfter >= w.length();
            boolean longVowel;
            if (dehnung || doubled) {
                longVowel = true;
            } else if (consonantsAfter == 0) {
                longVowel = true;
            } else if (consonantsAfter == 1) {
                // open syllable (consonant + vowel) = long; at the end of a word mostly long ("Tag", "gut"); exceptions in the dictionary
                longVowel = !(endsWord && vowelCount > 1);
            } else {
                longVowel = false;
            }
            // final syllables with e: schwa
            if (c == 'e' && !doubled && !dehnung && lastSyllable && vowelCount > 1 && !w.endsWith("ee")) {
                String rest = w.substring(i + 1);
                if (rest.isEmpty() || rest.matches("[nlmst]{1,2}") || rest.equals("r") || rest.equals("rn") || rest.equals("rs") || rest.equals("rt")) {
                    i++;
                    if (rest.startsWith("r")) {
                        add(ER);
                        i++;
                    } else {
                        add(SCHWA);
                    }
                    return;
                }
            }
            // unstressed prefixes: ge-, be- with schwa
            if (c == 'e' && i == 1 && (w.startsWith("ge") || w.startsWith("be")) && vowelCount > 1 && !isVowelLetter(ch(1))) {
                add(SCHWA);
                i++;
                return;
            }
            Phoneme chosen = switch (c) {
                case 'a' -> longVowel ? AA : A;
                case 'e' -> longVowel ? EE : E;
                case 'i' -> longVowel ? II : I;
                case 'o' -> longVowel ? OO : O;
                case 'u' -> longVowel ? UU : U;
                case 'ä' -> longVowel ? AE : E;
                case 'ö' -> longVowel ? OEE : OE;
                case 'ü', 'y' -> longVowel ? UEE : UE;
                default -> A;
            };
            add(chosen);
            i += doubled ? 2 : 1;
            if (dehnung) {
                i++;
            }
            // vocalic r after a long vowel at the end of a syllable ("Uhr", "Tor", "wir"): r → ɐ
            if (ch(0) == 'r' && longVowel && !isVowelLetter(ch(1))) {
                add(ER);
                i++;
            }
        }

        /** How many consonant letters follow the position until the next vowel (ck, sch, ch, ß count as one sound). */
        private int consonantsAfter(int offset) {
            int k = i + offset;
            int count = 0;
            while (k < w.length() && !isVowelLetter(w.charAt(k))) {
                if (w.startsWith("sch", k)) {
                    k += 3;
                } else if (w.startsWith("ch", k) || w.startsWith("ck", k) || w.startsWith("ph", k) || w.startsWith("th", k) || w.startsWith("qu", k)) {
                    k += 2;
                } else if (w.charAt(k) == 'h' && k > i && count == 0 && offset == 1) {
                    // a silent h after a vowel does not count
                    k++;
                    continue;
                } else {
                    k++;
                }
                count++;
            }
            return count;
        }

        // ── Consonants ──

        private void consonant(boolean start) {
            char c = ch(0);
            char n = ch(1);
            boolean beforeVowel = isVowelLetter(n);
            boolean wordEnd = i + 1 >= w.length();
            // multi-letter spellings first
            if (at("tsch")) {
                add(CH);
                i += 4;
                return;
            }
            if (at("sch")) {
                add(SH);
                i += 3;
                return;
            }
            if (at("chs") && !at("chst")) {
                add(K, S);
                i += 3;
                return;
            }
            if (at("ch")) {
                Phoneme before = out.isEmpty() ? null : Compat.last(out);
                if (i == 0) {
                    add(isVowelLetter(n) ? XI : K);
                } else if (before == A || before == AA || before == O || before == OO || before == U || before == UU || before == AU) {
                    add(X);
                } else {
                    add(XI);
                }
                i += 2;
                return;
            }
            if (at("ck")) {
                add(K);
                i += 2;
                return;
            }
            if (at("ph")) {
                add(F);
                i += 2;
                return;
            }
            if (at("th")) {
                add(T);
                i += 2;
                return;
            }
            if (at("dt")) {
                add(T);
                i += 2;
                return;
            }
            if (at("qu")) {
                add(K, V);
                i += 2;
                return;
            }
            if (at("pf")) {
                add(PF);
                i += 2;
                return;
            }
            if (at("tz")) {
                add(TS);
                i += 2;
                return;
            }
            if (at("tion") && i > 0) {
                add(TS, Y, OO, N);
                i += 4;
                vowelsSeen++;
                return;
            }
            if (at("ng")) {
                add(NG);
                i += 2;
                return;
            }
            if (at("nk")) {
                add(NG, K);
                i += 2;
                return;
            }
            if (at("ig") && i + 2 >= w.length()) {
                add(I, XI);
                i += 2;
                vowelsSeen++;
                return;
            }
            // double consonants: one sound (ss stays voiceless)
            boolean doubledS = false;
            if (n == c && "bdfgklmnprst".indexOf(c) >= 0) {
                doubledS = c == 's';
                i++;
                n = ch(1);
                beforeVowel = isVowelLetter(n);
                wordEnd = i + 1 >= w.length();
            }
            boolean devoice = wordEnd || !beforeVowel && !"lrnmhjw".contains(String.valueOf(n)) && n != '\0';
            switch (c) {
                case 'b' -> add(devoice ? P : B);
                case 'd' -> add(devoice ? T : D);
                case 'g' -> add(devoice ? K : G);
                case 'p' -> add(P);
                case 't' -> add(T);
                case 'k', 'q' -> add(K);
                case 'c' -> add(n == 'e' || n == 'i' ? TS : K);
                case 'f', 'v' -> add(F);
                case 'w' -> add(V);
                case 'ß' -> add(S);
                case 's' -> {
                    if (start && (n == 'p' || n == 't')) {
                        add(SH);
                    } else if (!doubledS && beforeVowel && (start || out.isEmpty() || Compat.last(out).isVocalic() || Compat.last(out).isSonorant())) {
                        add(Z);
                    } else {
                        add(S);
                    }
                }
                case 'z' -> add(TS);
                case 'x' -> add(K, S);
                case 'j' -> add(Y);
                case 'y' -> add(Y);
                case 'h' -> {
                    // h before a vowel at the start of a syllable is heard, after a vowel it is silent (lengthening) – also before a vowel in joints ("gehen": silent)
                    if (beforeVowel && (out.isEmpty() || !Compat.last(out).isVocalic())) {
                        add(H);
                    }
                }
                case 'm' -> add(M);
                case 'n' -> add(N);
                case 'l' -> add(L);
                case 'r' -> {
                    // r after a vowel at the end of a syllable/word: vocalic (ɐ), otherwise a tongue r
                    if (!beforeVowel && !out.isEmpty() && Compat.last(out).isVocalic() && Compat.last(out) != ER) {
                        add(ER);
                    } else {
                        add(R);
                    }
                }
                default -> {
                    // skip foreign characters
                }
            }
            i++;
        }
    }

    // ── Numbers ──

    private static final String[] ONES = {"null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf",
            "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn"};
    private static final String[] TENS = {"", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig"};

    @Override
    public String number(long value) {
        if (value < 0) {
            return "minus " + number(-value);
        }
        if (value < 20) {
            return ONES[(int) value];
        }
        if (value < 100) {
            int tens = (int) (value / 10);
            int ones = (int) (value % 10);
            return ones == 0 ? TENS[tens] : (ones == 1 ? "ein" : ONES[ones]) + "und" + TENS[tens];
        }
        if (value < 1000) {
            int hundreds = (int) (value / 100);
            long rest = value % 100;
            return (hundreds == 1 ? "ein" : ONES[hundreds]) + "hundert" + (rest == 0 ? "" : number(rest));
        }
        if (value < 1_000_000) {
            long thousands = value / 1000;
            long rest = value % 1000;
            return (thousands == 1 ? "ein" : number(thousands)) + "tausend" + (rest == 0 ? "" : number(rest));
        }
        if (value < 1_000_000_000L) {
            long millions = value / 1_000_000;
            long rest = value % 1_000_000;
            return (millions == 1 ? "eine million" : number(millions) + " millionen") + (rest == 0 ? "" : " " + number(rest));
        }
        long billions = value / 1_000_000_000L;
        long rest = value % 1_000_000_000L;
        return (billions == 1 ? "eine milliarde" : number(billions) + " milliarden") + (rest == 0 ? "" : " " + number(rest));
    }
}
