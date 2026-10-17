package de.passimoritz.voiceapi;

import de.passimoritz.voiceapi.lang.Language;
import de.passimoritz.voiceapi.lang.Word;
import de.passimoritz.voiceapi.pack.PackBridge;
import de.passimoritz.voiceapi.play.Speech;
import de.passimoritz.voiceapi.play.VoicePlayer;
import de.passimoritz.voiceapi.prosody.Prosody;
import de.passimoritz.voiceapi.render.Cue;
import de.passimoritz.voiceapi.render.PackRenderer;
import de.passimoritz.voiceapi.render.VanillaRenderer;
import de.passimoritz.voiceapi.sing.MelodyNote;
import de.passimoritz.voiceapi.sing.Singer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Der Zugang zur VoiceAPI für Plugins (NoteAPI, Spiele):
 * <pre>{@code
 * if (VoiceApi.isAvailable()) {
 *     Utterance greeting = VoiceApi.speak("Willkommen im Dorf!", Language.GERMAN, Speaker.VILLAGER, Style.of(Style.Mood.HAPPY));
 *     Speech speech = VoiceApi.play(greeting, () -> villager.getLocation(), null, 1.0, () -> log("fertig"));
 *     speech.onWord((index, word) -> showSubtitle(word));
 * }
 * }</pre>
 * Keine Befehle: Das Plugin, das die VoiceAPI einbindet, bietet sie seinen Spielern an. Text zu Sprache ({@link #speak}, {@link #sing}) geht
 * ohne laufenden Server (auch im Werkzeug {@code java -jar voiceapi.jar}); nur {@link #play} braucht ihn.
 * <p>
 * <b>Ressourcenpaket:</b> Spieler, die das NoteAPI-Ressourcenpaket mit den VoiceAPI-Lauten geladen haben, hören echte Vokale und Konsonanten,
 * alle anderen Dorfbewohner- und Block-Laute. Wer das Paket hat, erfährt VoiceAPI von der NoteAPI; ein anderes Plugin kann das mit
 * {@link #packProbe(Predicate)} selbst bestimmen.
 */
public final class VoiceApi {

    /** Die Version der Schnittstelle. 1: speak, sing, play, Bibliothek, Paket-Anbindung. */
    public static final int API_VERSION = 1;

    private static VoicePlayer player;
    private static VoiceLibrary library;
    private static PackBridge pack;

    private VoiceApi() {
    }

    /** Ob die VoiceAPI auf dem Server läuft. */
    public static boolean isAvailable() {
        Plugin found = Bukkit.getPluginManager().getPlugin("VoiceAPI");
        return found != null && found.isEnabled();
    }

    // ── Text zu Sprache (ohne Server) ──

    /** Spricht einen Text: Laute, Betonung, Satzmelodie – fertig zum Abspielen oder Speichern. */
    public static Utterance speak(String text, Language language, Speaker speaker, Style style) {
        Language lang = language == null ? Language.guess(text) : language;
        Speaker voice = speaker == null ? Speaker.VILLAGER : speaker;
        Style how = style == null ? Style.DEFAULT : style;
        List<Word> words = lang.phonemizer().read(text);
        List<Segment> segments = Prosody.compile(words, how, voice);
        return new Utterance("", "", text, lang, voice.id(), how, segments);
    }

    /** Spricht einen Text mit erratener Sprache, Dorfbewohner-Stimme und normalem Stil. */
    public static Utterance speak(String text) {
        return speak(text, null, Speaker.VILLAGER, Style.DEFAULT);
    }

    /** Singt einen Text auf eine Melodie (siehe {@link Singer}). */
    public static Utterance sing(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style) {
        Language lang = language == null ? Language.guess(text) : language;
        return Singer.sing(text, lang, speaker == null ? Speaker.VILLAGER : speaker, melody, style == null ? Style.DEFAULT : style);
    }

    /** Die Wörter eines Textes mit Silben und Betonung (zum Nachsehen, wie ein Text gelesen wird). */
    public static List<Word> read(String text, Language language) {
        return (language == null ? Language.guess(text) : language).phonemizer().read(text);
    }

    /** Die Klang-Befehle eines Stücks – mit Vanilla-Klängen oder mit den Lauten des Ressourcenpakets. */
    public static List<Cue> cues(Utterance utterance, boolean withPack) {
        return (withPack ? PackRenderer.INSTANCE : VanillaRenderer.INSTANCE).render(utterance);
    }

    // ── Abspielen (braucht den Server) ──

    /**
     * Spielt ein Stück.
     *
     * @param utterance das Stück
     * @param place     wo gesprochen wird (einmal je Server-Tick im Hauptthread abgefragt), oder {@code null}: bei jedem Zuhörer selbst
     * @param listeners wer zuhört (einmal je Tick geprüft), oder {@code null}: alle Spieler (in Reichweite, wenn ein Ort gesetzt ist)
     * @param volume    Lautstärke-Faktor (1 = normal)
     * @param whenDone  wird im Hauptthread aufgerufen, wenn das Stück zu Ende ist oder gestoppt wurde (oder {@code null})
     * @throws IllegalStateException wenn die VoiceAPI nicht eingeschaltet ist
     */
    public static synchronized Speech play(Utterance utterance, Supplier<Location> place, Predicate<Player> listeners, double volume, Runnable whenDone) {
        if (player == null) {
            throw new IllegalStateException("Die VoiceAPI ist nicht eingeschaltet");
        }
        return player.play(utterance, place, listeners, volume, whenDone);
    }

    /** Spricht einen Text an einem Ort (für alle Spieler in Reichweite). */
    public static Speech say(String text, Language language, Speaker speaker, Style style, Supplier<Location> place) {
        return play(speak(text, language, speaker, style), place, null, 1.0, null);
    }

    /** Spricht einen Text nur für einen Spieler (bei ihm selbst). */
    public static Speech tell(Player target, String text, Language language, Speaker speaker, Style style) {
        return play(speak(text, language, speaker, style), null, p -> p.getUniqueId().equals(target.getUniqueId()), 1.0, null);
    }

    /**
     * Ob ein Stück das Ressourcenpaket braucht, damit Spieler es richtig hören (Standard: immer – {@code sing.require-pack},
     * {@code speech.require-pack}; die Vanilla-Laute sind nur eine abschaltbare Notlösung). Das einbindende Plugin erzwingt dann das
     * NoteAPI-Paket, bevor es sprechen oder singen lässt.
     */
    public static synchronized boolean needsPack(Utterance utterance) {
        if (player == null) {
            return true;
        }
        return utterance.sung() ? player.singRequiresPack() : player.speechRequiresPack();
    }

    /** Stoppt alle laufenden Stücke. */
    public static synchronized void stopAll() {
        if (player != null) {
            player.stopAll();
        }
    }

    /** Der Abspieler (Zählen, Stoppen, Statistik). */
    public static synchronized VoicePlayer player() {
        if (player == null) {
            throw new IllegalStateException("Die VoiceAPI ist nicht eingeschaltet");
        }
        return player;
    }

    /** Die Bibliothek der gespeicherten Stücke ({@code plugins/VoiceAPI/voices/*.voice}). */
    public static synchronized VoiceLibrary library() {
        if (library == null) {
            throw new IllegalStateException("Die VoiceAPI ist nicht eingeschaltet");
        }
        return library;
    }

    // ── Ressourcenpaket ──

    /**
     * Legt fest, welche Spieler die Laute des Ressourcenpakets hören (statt der NoteAPI-Auskunft). {@code null} schaltet zurück auf die
     * NoteAPI.
     */
    public static synchronized void packProbe(Predicate<Player> probe) {
        if (pack != null) {
            pack.probe(probe);
        }
    }

    /** Ob ein Ressourcenpaket mit den VoiceAPI-Lauten vorliegt (die NoteAPI-Paketdatei enthält sie). */
    public static synchronized boolean packAvailable() {
        return pack != null && pack.available();
    }

    /** Ob ein Spieler die Laute des Pakets hört. */
    public static synchronized boolean hearsPack(Player target) {
        return pack != null && pack.hears(target);
    }

    /** Die Paket-Anbindung (Datei, Format, Spieler). */
    public static synchronized PackBridge pack() {
        if (pack == null) {
            throw new IllegalStateException("Die VoiceAPI ist nicht eingeschaltet");
        }
        return pack;
    }

    static synchronized void enable(VoicePlayer newPlayer, VoiceLibrary newLibrary, PackBridge newPack) {
        player = newPlayer;
        library = newLibrary;
        pack = newPack;
    }

    static synchronized void disable() {
        if (player != null) {
            player.shutdown();
        }
        player = null;
        library = null;
        pack = null;
    }
}
