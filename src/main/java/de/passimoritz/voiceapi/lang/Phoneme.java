package de.passimoritz.voiceapi.lang;

/**
 * The sounds VoiceAPI builds speech from – one shared inventory for German and English (IPA symbols).
 * <p>
 * Every sound knows what it is ({@link Kind}), whether it is voiced, how long it naturally lasts and – for vowels – how bright
 * ({@link #brightness()}: u = 0 … i = 1) and how open ({@link #openness()}: i/u = 0 … a = 1) the mouth is. From brightness and openness the
 * renderer picks the villager sound and pitch with which a player most likely recognises the vowel.
 */
public enum Phoneme {

    // ── Vowels ──────────────────────────────────────────── symbol, kind, voiced, brightness, openness, duration ms
    A("a", Kind.VOWEL, true, 0.45, 1.00, 95),
    AA("aː", Kind.VOWEL, true, 0.45, 1.00, 175),
    E("ɛ", Kind.VOWEL, true, 0.72, 0.60, 90),
    EE("eː", Kind.VOWEL, true, 0.85, 0.35, 165),
    AE("ɛː", Kind.VOWEL, true, 0.70, 0.65, 165),
    I("ɪ", Kind.VOWEL, true, 0.90, 0.20, 75),
    II("iː", Kind.VOWEL, true, 1.00, 0.05, 150),
    O("ɔ", Kind.VOWEL, true, 0.22, 0.60, 90),
    OO("oː", Kind.VOWEL, true, 0.18, 0.35, 165),
    U("ʊ", Kind.VOWEL, true, 0.08, 0.20, 75),
    UU("uː", Kind.VOWEL, true, 0.00, 0.05, 150),
    OE("œ", Kind.VOWEL, true, 0.55, 0.55, 90),
    OEE("øː", Kind.VOWEL, true, 0.60, 0.35, 160),
    UE("ʏ", Kind.VOWEL, true, 0.68, 0.20, 75),
    UEE("yː", Kind.VOWEL, true, 0.75, 0.05, 150),
    /** English a in "cat". */
    AEE("æ", Kind.VOWEL, true, 0.62, 0.85, 120),
    /** English u in "cup". */
    AH("ʌ", Kind.VOWEL, true, 0.40, 0.75, 100),
    /** English aw in "law", o in "thought". */
    AW("ɔː", Kind.VOWEL, true, 0.20, 0.70, 170),
    /** English er in "bird". */
    ERR("ɜː", Kind.VOWEL, true, 0.50, 0.45, 165),
    /** Schwa ("bitte", "about"). */
    SCHWA("ə", Kind.VOWEL, true, 0.45, 0.45, 60),
    /** Vocalic r ("Vater", "better"). */
    ER("ɐ", Kind.VOWEL, true, 0.42, 0.65, 70),

    // ── Diphthongs (glide from the first to the second vowel) ──
    AI("aɪ", Kind.DIPHTHONG, true, 0.45, 1.00, 180, A, I),
    AU("aʊ", Kind.DIPHTHONG, true, 0.45, 1.00, 180, A, U),
    OI("ɔʏ", Kind.DIPHTHONG, true, 0.22, 0.60, 180, O, UE),
    EI("eɪ", Kind.DIPHTHONG, true, 0.85, 0.35, 170, EE, I),
    OU("oʊ", Kind.DIPHTHONG, true, 0.18, 0.35, 170, OO, U),

    // ── Plosives ──
    P("p", Kind.PLOSIVE, false, 0.3, 0, 70),
    B("b", Kind.PLOSIVE, true, 0.3, 0, 60),
    T("t", Kind.PLOSIVE, false, 0.8, 0, 70),
    D("d", Kind.PLOSIVE, true, 0.8, 0, 55),
    K("k", Kind.PLOSIVE, false, 0.5, 0, 75),
    G("g", Kind.PLOSIVE, true, 0.5, 0, 60),

    // ── Fricatives ──
    F("f", Kind.FRICATIVE, false, 0.5, 0, 95),
    V("v", Kind.FRICATIVE, true, 0.5, 0, 70),
    S("s", Kind.FRICATIVE, false, 1.0, 0, 105),
    Z("z", Kind.FRICATIVE, true, 1.0, 0, 80),
    SH("ʃ", Kind.FRICATIVE, false, 0.7, 0, 110),
    ZH("ʒ", Kind.FRICATIVE, true, 0.7, 0, 85),
    TH("θ", Kind.FRICATIVE, false, 0.6, 0, 90),
    DH("ð", Kind.FRICATIVE, true, 0.6, 0, 60),
    H("h", Kind.FRICATIVE, false, 0.4, 0, 70),
    /** German "ach" sound. */
    X("x", Kind.FRICATIVE, false, 0.3, 0, 100),
    /** German "ich" sound. */
    XI("ç", Kind.FRICATIVE, false, 0.8, 0, 95),

    // ── Affricates ──
    TS("ts", Kind.AFFRICATE, false, 1.0, 0, 120),
    PF("pf", Kind.AFFRICATE, false, 0.4, 0, 120),
    CH("tʃ", Kind.AFFRICATE, false, 0.7, 0, 120),
    DJ("dʒ", Kind.AFFRICATE, true, 0.7, 0, 100),

    // ── Nasals ──
    M("m", Kind.NASAL, true, 0.2, 0, 75),
    N("n", Kind.NASAL, true, 0.5, 0, 70),
    NG("ŋ", Kind.NASAL, true, 0.3, 0, 85),

    // ── Liquids and glides ──
    L("l", Kind.LIQUID, true, 0.6, 0, 65),
    R("r", Kind.LIQUID, true, 0.4, 0, 60),
    W("w", Kind.GLIDE, true, 0.1, 0, 60),
    Y("j", Kind.GLIDE, true, 1.0, 0, 55),

    /** Pause (between words, at punctuation). */
    SIL("_", Kind.PAUSE, false, 0, 0, 0);

    /** What kind of sound it is. */
    public enum Kind {
        VOWEL, DIPHTHONG, PLOSIVE, FRICATIVE, AFFRICATE, NASAL, LIQUID, GLIDE, PAUSE;

        /** Vowels and diphthongs carry the syllable. */
        public boolean isVocalic() {
            return this == VOWEL || this == DIPHTHONG;
        }

        /** Nasals, liquids and glides: voiced and resonant without being a vowel. */
        public boolean isSonorant() {
            return this == NASAL || this == LIQUID || this == GLIDE;
        }
    }

    private final String symbol;
    private final Kind kind;
    private final boolean voiced;
    private final double brightness;
    private final double openness;
    private final int baseMillis;
    private final Phoneme from;
    private final Phoneme to;

    Phoneme(String symbol, Kind kind, boolean voiced, double brightness, double openness, int baseMillis) {
        this(symbol, kind, voiced, brightness, openness, baseMillis, null, null);
    }

    Phoneme(String symbol, Kind kind, boolean voiced, double brightness, double openness, int baseMillis, Phoneme from, Phoneme to) {
        this.symbol = symbol;
        this.kind = kind;
        this.voiced = voiced;
        this.brightness = brightness;
        this.openness = openness;
        this.baseMillis = baseMillis;
        this.from = from;
        this.to = to;
    }

    /** The IPA symbol (as the sound is written in {@code .voice} files). */
    public String symbol() {
        return symbol;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isVoiced() {
        return voiced;
    }

    /** How bright the sound is (0 = u/dark … 1 = i/bright); for consonants the brightness of the noise. */
    public double brightness() {
        return brightness;
    }

    /** How open the mouth is (0 = closed … 1 = a). */
    public double openness() {
        return openness;
    }

    /** The natural duration in milliseconds at normal tempo. */
    public int baseMillis() {
        return baseMillis;
    }

    /** Whether the sound can carry a syllable. */
    public boolean isVocalic() {
        return kind.isVocalic();
    }

    public boolean isVowel() {
        return kind == Kind.VOWEL;
    }

    public boolean isDiphthong() {
        return kind == Kind.DIPHTHONG;
    }

    public boolean isConsonant() {
        return !kind.isVocalic() && kind != Kind.PAUSE;
    }

    public boolean isPause() {
        return kind == Kind.PAUSE;
    }

    /** A consonant that is only noise without voice (plosive, fricative, affricate without voice). */
    public boolean isNoise() {
        return !voiced && isConsonant();
    }

    /** Voiced consonants that sound like a short vowel (nasals, liquids, glides). */
    public boolean isSonorant() {
        return kind.isSonorant();
    }

    /** The vowel a diphthong starts with (for vowels the vowel itself, otherwise {@code null}). */
    public Phoneme start() {
        return from != null ? from : isVowel() ? this : null;
    }

    /** The vowel a diphthong glides to (for vowels the vowel itself, otherwise {@code null}). */
    public Phoneme end() {
        return to != null ? to : isVowel() ? this : null;
    }

    /** A long vowel (ː) or diphthong. */
    public boolean isLong() {
        return symbol.endsWith("ː") || isDiphthong();
    }

    /** The sound for an IPA symbol from a file, or {@code null}. */
    public static Phoneme bySymbol(String text) {
        for (Phoneme phoneme : values()) {
            if (phoneme.symbol.equals(text)) {
                return phoneme;
            }
        }
        return null;
    }
}
