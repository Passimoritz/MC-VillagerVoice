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

    /**
     * Die Version der Schnittstelle. 1: speak, sing, play, Bibliothek, Paket-Anbindung. 2: {@link #soundPack()} (beigelegte Laute).
     * 3: Villager-Gesang aus dem KI-Stimmmodell ({@link #songVoice}, {@link #convertSong}, {@link #onSoundPackChanged}).
     */
    public static final int API_VERSION = 3;

    private static VoicePlayer player;
    private static VoiceLibrary library;
    private static PackBridge pack;
    private static java.nio.file.Path soundPack;

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

    /**
     * Singt einen Text auf eine Melodie mit eigenen Optionen: Lautstärke, Dynamik, Binden/Überlappen (smooth statt abgehackt), Gleiten bei
     * Sprüngen, kürzeste Note ({@link de.passimoritz.voiceapi.sing.SingOptions}, auch als Text: {@code SingOptions.parse("legato=200; glide=2")}).
     */
    public static Utterance sing(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style,
                                 de.passimoritz.voiceapi.sing.SingOptions options) {
        Language lang = language == null ? Language.guess(text) : language;
        return Singer.sing(text, lang, speaker == null ? Speaker.VILLAGER : speaker, melody, style == null ? Style.DEFAULT : style, options);
    }

    /** Singt einen Text auf eine Melodie (siehe {@link Singer}; Standard-Optionen {@link de.passimoritz.voiceapi.sing.SingOptions#DEFAULT}). */
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

    /**
     * Die fertigen Laute als Teil eines Ressourcenpakets ({@code assets/voiceapi/…}, dem Plugin beigelegt und beim Start nach
     * {@code plugins/VoiceAPI/VoiceAPI-Sounds.zip} gelegt). Die NoteAPI mischt sie von selbst in ihr Paket – niemand muss etwas bauen.
     */
    public static synchronized java.util.Optional<java.nio.file.Path> soundPack() {
        return java.util.Optional.ofNullable(soundPack).filter(java.nio.file.Files::isRegularFile);
    }

    /** Das Format der beigelegten Laute ({@link de.passimoritz.voiceapi.pack.VoicePack#FORMAT}). */
    public static int soundPackFormat() {
        return de.passimoritz.voiceapi.pack.VoicePack.FORMAT;
    }

    /** Die Paket-Anbindung (Datei, Format, Spieler). */
    public static synchronized PackBridge pack() {
        if (pack == null) {
            throw new IllegalStateException("Die VoiceAPI ist nicht eingeschaltet");
        }
        return pack;
    }

    // ── Villager-Gesang aus dem KI-Stimmmodell (API 3) ──

    private static de.passimoritz.voiceapi.song.SongVoices songs;
    private static de.passimoritz.voiceapi.song.SongConverter converter;
    private static org.bukkit.plugin.Plugin owner;
    private static java.nio.file.Path basePack;
    private static final java.util.List<Runnable> PACK_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Der fertige Villager-Gesang eines Stücks, wenn es umgewandelt wurde: echte Gesangsspur → KI-Stimmmodell (RVC, nur CPU) → kurze,
     * überblendete Abschnitte im Lautpaket. Das einbindende Plugin spielt {@link de.passimoritz.voiceapi.song.SongVoice#cues()} auf seiner
     * Musik-Uhr (Spieler mit Paket), so bleibt der Gesang im Takt; nach einem Sprung mit {@code cuesFrom(ms)} weiter.
     */
    public static synchronized java.util.Optional<de.passimoritz.voiceapi.song.SongVoice> songVoice(String songId) {
        return songs == null ? java.util.Optional.empty() : songs.get(songId);
    }

    /** Alle umgewandelten Gesänge. */
    public static synchronized java.util.List<de.passimoritz.voiceapi.song.SongVoice> songVoices() {
        return songs == null ? java.util.List.of() : songs.all();
    }

    /** Was fürs Umwandeln fehlt (leer = das Stimmmodell ist eingerichtet; {@code tools/rvc_setup.py} richtet es ein). */
    public static synchronized java.util.List<String> songVoiceMissing() {
        return converter == null ? java.util.List.of("VoiceAPI nicht eingeschaltet") : converter.missing();
    }

    /** Ob gerade ein Lied umgewandelt wird. */
    public static synchronized boolean songVoiceBusy() {
        return converter != null && converter.busy();
    }

    /**
     * Wandelt die Gesangsspur eines Stücks in den Villager-Gesang (in einem eigenen Thread; dauert auf der CPU etwa so lang wie das Lied ×2–8).
     * Danach wird das Lautpaket neu geschrieben und {@link #onSoundPackChanged} gemeldet – das einbindende Plugin mischt es neu in sein
     * Ressourcenpaket und schickt es den Spielern.
     *
     * @param songId   Kennung des Stücks
     * @param vocals   die getrennte Gesangsspur (WAV), z. B. {@code stems/vocals.wav} aus der NoteAPI-Umwandlung
     * @param progress Fortschritt (im Hauptthread), oder {@code null}
     * @return wird im Hauptthread mit dem Gesang erfüllt, oder mit einem Fehler
     */
    public static synchronized java.util.concurrent.CompletableFuture<de.passimoritz.voiceapi.song.SongVoice> convertSong(String songId,
            java.nio.file.Path vocals, java.util.function.Consumer<String> progress) {
        java.util.concurrent.CompletableFuture<de.passimoritz.voiceapi.song.SongVoice> result = new java.util.concurrent.CompletableFuture<>();
        if (converter == null || songs == null || owner == null) {
            result.completeExceptionally(new IllegalStateException("Die VoiceAPI ist nicht eingeschaltet"));
            return result;
        }
        String id = de.passimoritz.voiceapi.song.SongVoices.safe(songId);
        org.bukkit.plugin.Plugin plugin = owner;
        java.util.function.Consumer<String> onMain = progress == null ? null
                : text -> Bukkit.getScheduler().runTask(plugin, () -> progress.accept(text));
        converter.convert(id, vocals, songs.folder(id), onMain).whenComplete((folder, error) -> {
            de.passimoritz.voiceapi.song.SongVoice voice = null;
            Throwable failure = error;
            if (failure == null) {
                voice = songs.reload(id).orElse(null);
                if (voice == null) {
                    failure = new IllegalStateException("Der Gesang ließ sich nach der Umwandlung nicht lesen");
                } else {
                    failure = rebuildPack();
                }
            }
            de.passimoritz.voiceapi.song.SongVoice done = voice;
            Throwable fail = failure;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (fail != null) {
                    result.completeExceptionally(fail);
                } else {
                    result.complete(done);
                    firePackChanged();
                }
            });
        });
        return result;
    }

    /** Bricht die laufende Umwandlung ab. */
    public static synchronized void cancelSongConversion() {
        if (converter != null) {
            converter.cancel();
        }
    }

    /** Löscht den Gesang eines Stücks (und nimmt ihn aus dem Lautpaket). @return ob es einen gab */
    public static synchronized boolean deleteSongVoice(String songId) throws java.io.IOException {
        if (songs == null || !songs.delete(songId)) {
            return false;
        }
        Throwable failure = rebuildPack();
        if (failure instanceof java.io.IOException io) {
            throw io;
        }
        firePackChanged();
        return true;
    }

    /** Wird (im Hauptthread) gerufen, wenn sich das Lautpaket geändert hat – dann neu ins Ressourcenpaket mischen und senden. */
    public static void onSoundPackChanged(Runnable listener) {
        if (listener != null) {
            PACK_LISTENERS.add(listener);
        }
    }

    private static synchronized Throwable rebuildPack() {
        try {
            if (basePack != null && soundPack != null) {
                songs.writePack(basePack, soundPack);
            }
            return null;
        } catch (java.io.IOException e) {
            return e;
        }
    }

    private static void firePackChanged() {
        for (Runnable listener : PACK_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                if (owner != null) {
                    owner.getLogger().log(java.util.logging.Level.WARNING, "Paket-Beobachter", e);
                }
            }
        }
    }

    static synchronized void enableSongs(org.bukkit.plugin.Plugin plugin, de.passimoritz.voiceapi.song.SongVoices newSongs,
                                         de.passimoritz.voiceapi.song.SongConverter newConverter, java.nio.file.Path base, java.nio.file.Path pack) {
        owner = plugin;
        songs = newSongs;
        converter = newConverter;
        basePack = base;
        if (pack != null && java.nio.file.Files.isRegularFile(pack)) {
            soundPack = pack;
        }
    }

    static synchronized void enable(VoicePlayer newPlayer, VoiceLibrary newLibrary, PackBridge newPack, java.nio.file.Path newSoundPack) {
        player = newPlayer;
        library = newLibrary;
        pack = newPack;
        soundPack = newSoundPack;
    }

    static synchronized void disable() {
        if (player != null) {
            player.shutdown();
        }
        player = null;
        library = null;
        pack = null;
        soundPack = null;
        songs = null;
        converter = null;
        owner = null;
        basePack = null;
        PACK_LISTENERS.clear();
    }
}
