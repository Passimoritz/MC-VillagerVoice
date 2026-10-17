package de.passimoritz.voiceapi.lang;

/**
 * Die Laute, aus denen VoiceAPI Sprache baut – ein gemeinsamer Vorrat für Deutsch und Englisch (Zeichen nach IPA).
 * <p>
 * Jeder Laut weiß, was er ist ({@link Kind}), ob er stimmhaft ist, wie lang er von Natur aus klingt und – bei Vokalen – wie hell
 * ({@link #brightness()}: u = 0 … i = 1) und wie offen ({@link #openness()}: i/u = 0 … a = 1) der Mund ist. Aus Helligkeit und Offenheit wählt
 * der Renderer den Dorfbewohner-Laut und seine Tonhöhe, mit der ein Spieler den Vokal am ehesten erkennt.
 */
public enum Phoneme {

    // ── Vokale ──────────────────────────────────────────── Zeichen, Art, stimmhaft, Helligkeit, Offenheit, Dauer ms
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
    /** Englisches a in „cat“. */
    AEE("æ", Kind.VOWEL, true, 0.62, 0.85, 120),
    /** Englisches u in „cup“. */
    AH("ʌ", Kind.VOWEL, true, 0.40, 0.75, 100),
    /** Englisches aw in „law“, o in „thought“. */
    AW("ɔː", Kind.VOWEL, true, 0.20, 0.70, 170),
    /** Englisches er in „bird“. */
    ERR("ɜː", Kind.VOWEL, true, 0.50, 0.45, 165),
    /** Murmel-e („bitte“, „about“). */
    SCHWA("ə", Kind.VOWEL, true, 0.45, 0.45, 60),
    /** Vokalisches r („Vater“, „better“). */
    ER("ɐ", Kind.VOWEL, true, 0.42, 0.65, 70),

    // ── Diphthonge (gleiten vom ersten zum zweiten Vokal) ──
    AI("aɪ", Kind.DIPHTHONG, true, 0.45, 1.00, 180, A, I),
    AU("aʊ", Kind.DIPHTHONG, true, 0.45, 1.00, 180, A, U),
    OI("ɔʏ", Kind.DIPHTHONG, true, 0.22, 0.60, 180, O, UE),
    EI("eɪ", Kind.DIPHTHONG, true, 0.85, 0.35, 170, EE, I),
    OU("oʊ", Kind.DIPHTHONG, true, 0.18, 0.35, 170, OO, U),

    // ── Verschlusslaute ──
    P("p", Kind.PLOSIVE, false, 0.3, 0, 70),
    B("b", Kind.PLOSIVE, true, 0.3, 0, 60),
    T("t", Kind.PLOSIVE, false, 0.8, 0, 70),
    D("d", Kind.PLOSIVE, true, 0.8, 0, 55),
    K("k", Kind.PLOSIVE, false, 0.5, 0, 75),
    G("g", Kind.PLOSIVE, true, 0.5, 0, 60),

    // ── Reibelaute ──
    F("f", Kind.FRICATIVE, false, 0.5, 0, 95),
    V("v", Kind.FRICATIVE, true, 0.5, 0, 70),
    S("s", Kind.FRICATIVE, false, 1.0, 0, 105),
    Z("z", Kind.FRICATIVE, true, 1.0, 0, 80),
    SH("ʃ", Kind.FRICATIVE, false, 0.7, 0, 110),
    ZH("ʒ", Kind.FRICATIVE, true, 0.7, 0, 85),
    TH("θ", Kind.FRICATIVE, false, 0.6, 0, 90),
    DH("ð", Kind.FRICATIVE, true, 0.6, 0, 60),
    H("h", Kind.FRICATIVE, false, 0.4, 0, 70),
    /** ach-Laut. */
    X("x", Kind.FRICATIVE, false, 0.3, 0, 100),
    /** ich-Laut. */
    XI("ç", Kind.FRICATIVE, false, 0.8, 0, 95),

    // ── Affrikaten ──
    TS("ts", Kind.AFFRICATE, false, 1.0, 0, 120),
    PF("pf", Kind.AFFRICATE, false, 0.4, 0, 120),
    CH("tʃ", Kind.AFFRICATE, false, 0.7, 0, 120),
    DJ("dʒ", Kind.AFFRICATE, true, 0.7, 0, 100),

    // ── Nasale ──
    M("m", Kind.NASAL, true, 0.2, 0, 75),
    N("n", Kind.NASAL, true, 0.5, 0, 70),
    NG("ŋ", Kind.NASAL, true, 0.3, 0, 85),

    // ── Liquide und Gleitlaute ──
    L("l", Kind.LIQUID, true, 0.6, 0, 65),
    R("r", Kind.LIQUID, true, 0.4, 0, 60),
    W("w", Kind.GLIDE, true, 0.1, 0, 60),
    Y("j", Kind.GLIDE, true, 1.0, 0, 55),

    /** Pause (zwischen Wörtern, an Satzzeichen). */
    SIL("_", Kind.PAUSE, false, 0, 0, 0);

    /** Was für ein Laut es ist. */
    public enum Kind {
        VOWEL, DIPHTHONG, PLOSIVE, FRICATIVE, AFFRICATE, NASAL, LIQUID, GLIDE, PAUSE;

        /** Vokale und Diphthonge tragen die Silbe. */
        public boolean isVocalic() {
            return this == VOWEL || this == DIPHTHONG;
        }

        /** Nasale, Liquide und Gleitlaute: stimmhaft und klingend, ohne Vokal zu sein. */
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

    /** Das IPA-Zeichen (so steht der Laut in {@code .voice}-Dateien). */
    public String symbol() {
        return symbol;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isVoiced() {
        return voiced;
    }

    /** Wie hell der Laut klingt (0 = u/dunkel … 1 = i/hell); bei Konsonanten die Helligkeit des Geräuschs. */
    public double brightness() {
        return brightness;
    }

    /** Wie offen der Mund ist (0 = geschlossen … 1 = a). */
    public double openness() {
        return openness;
    }

    /** Die natürliche Dauer in Millisekunden bei normalem Tempo. */
    public int baseMillis() {
        return baseMillis;
    }

    /** Ob der Laut eine Silbe tragen kann. */
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

    /** Ein Konsonant, der ohne Stimme nur Geräusch ist (Verschluss, Reibung, Affrikate ohne Stimme). */
    public boolean isNoise() {
        return !voiced && isConsonant();
    }

    /** Stimmhafte Konsonanten, die wie ein kurzer Vokal klingen (Nasale, Liquide, Gleitlaute). */
    public boolean isSonorant() {
        return kind.isSonorant();
    }

    /** Der Vokal, mit dem ein Diphthong beginnt (bei Vokalen der Vokal selbst, sonst {@code null}). */
    public Phoneme start() {
        return from != null ? from : isVowel() ? this : null;
    }

    /** Der Vokal, zu dem ein Diphthong gleitet (bei Vokalen der Vokal selbst, sonst {@code null}). */
    public Phoneme end() {
        return to != null ? to : isVowel() ? this : null;
    }

    /** Ein langer Vokal (ː) oder Diphthong. */
    public boolean isLong() {
        return symbol.endsWith("ː") || isDiphthong();
    }

    /** Der Laut zu einem IPA-Zeichen aus einer Datei, oder {@code null}. */
    public static Phoneme bySymbol(String text) {
        for (Phoneme phoneme : values()) {
            if (phoneme.symbol.equals(text)) {
                return phoneme;
            }
        }
        return null;
    }
}
