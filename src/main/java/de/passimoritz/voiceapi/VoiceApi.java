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
 * The entry point to VoiceAPI for plugins (NoteAPI, games):
 * <pre>{@code
 * if (VoiceApi.isAvailable()) {
 *     Utterance greeting = VoiceApi.speak("Welcome to the village!", Language.ENGLISH, Speaker.VILLAGER, Style.of(Style.Mood.HAPPY));
 *     Speech speech = VoiceApi.play(greeting, () -> villager.getLocation(), null, 1.0, () -> log("done"));
 *     speech.onWord((index, word) -> showSubtitle(word));
 * }
 * }</pre>
 * No commands: the plugin that integrates VoiceAPI offers it to its players. Text to speech ({@link #speak}, {@link #sing}) works without a
 * running server (also in the tool {@code java -jar voiceapi.jar}); only {@link #play} needs one.
 * <p>
 * <b>Resource pack:</b> players who loaded the NoteAPI resource pack with the VoiceAPI sounds hear real vowels and consonants, everyone else
 * villager and block sounds. VoiceAPI learns from NoteAPI who has the pack; another plugin can decide that itself with
 * {@link #packProbe(Predicate)}.
 */
public final class VoiceApi {

    /**
     * The version of the interface. 1: speak, sing, play, library, pack connection. 2: {@link #soundPack()} (bundled sounds).
     * 3: villager singing from the AI voice model ({@link #songVoice}, {@link #convertSong}, {@link #onSoundPackChanged}).
     */
    public static final int API_VERSION = 3;

    private static VoicePlayer player;
    private static VoiceLibrary library;
    private static PackBridge pack;
    private static java.nio.file.Path soundPack;

    private VoiceApi() {
    }

    /** Whether VoiceAPI is running on the server. */
    public static boolean isAvailable() {
        Plugin found = Bukkit.getPluginManager().getPlugin("VoiceAPI");
        return found != null && found.isEnabled();
    }

    // ── Text to speech (no server needed) ──

    /** Speaks a text: sounds, stress, intonation – ready to play or save. */
    public static Utterance speak(String text, Language language, Speaker speaker, Style style) {
        Language lang = language == null ? Language.guess(text) : language;
        Speaker voice = speaker == null ? Speaker.VILLAGER : speaker;
        Style how = style == null ? Style.DEFAULT : style;
        List<Word> words = lang.phonemizer().read(text);
        List<Segment> segments = Prosody.compile(words, how, voice);
        return new Utterance("", "", text, lang, voice.id(), how, segments);
    }

    /** Speaks a text with a guessed language, the villager voice and the normal style. */
    public static Utterance speak(String text) {
        return speak(text, null, Speaker.VILLAGER, Style.DEFAULT);
    }

    /**
     * Sings a text to a melody with own options: volume, dynamics, tying/overlapping (smooth instead of choppy), gliding on jumps, shortest
     * note ({@link de.passimoritz.voiceapi.sing.SingOptions}, also as text: {@code SingOptions.parse("legato=200; glide=2")}).
     */
    public static Utterance sing(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style,
                                 de.passimoritz.voiceapi.sing.SingOptions options) {
        Language lang = language == null ? Language.guess(text) : language;
        return Singer.sing(text, lang, speaker == null ? Speaker.VILLAGER : speaker, melody, style == null ? Style.DEFAULT : style, options);
    }

    /** Sings a text to a melody (see {@link Singer}; default options {@link de.passimoritz.voiceapi.sing.SingOptions#DEFAULT}). */
    public static Utterance sing(String text, Language language, Speaker speaker, List<MelodyNote> melody, Style style) {
        Language lang = language == null ? Language.guess(text) : language;
        return Singer.sing(text, lang, speaker == null ? Speaker.VILLAGER : speaker, melody, style == null ? Style.DEFAULT : style);
    }

    /** The words of a text with syllables and stress (to look up how a text is read). */
    public static List<Word> read(String text, Language language) {
        return (language == null ? Language.guess(text) : language).phonemizer().read(text);
    }

    /** The sound commands of an utterance – with vanilla sounds or with the sounds of the resource pack. */
    public static List<Cue> cues(Utterance utterance, boolean withPack) {
        return (withPack ? PackRenderer.INSTANCE : VanillaRenderer.INSTANCE).render(utterance);
    }

    // ── Playback (needs the server) ──

    /**
     * Plays an utterance.
     *
     * @param utterance the utterance
     * @param place     where it is spoken (queried once per server tick on the main thread), or {@code null}: at every listener themselves
     * @param listeners who listens (checked once per tick), or {@code null}: all players (in range if a place is set)
     * @param volume    volume factor (1 = normal)
     * @param whenDone  called on the main thread when the utterance has ended or was stopped (or {@code null})
     * @throws IllegalStateException if VoiceAPI is not enabled
     */
    public static synchronized Speech play(Utterance utterance, Supplier<Location> place, Predicate<Player> listeners, double volume, Runnable whenDone) {
        if (player == null) {
            throw new IllegalStateException("VoiceAPI is not enabled");
        }
        return player.play(utterance, place, listeners, volume, whenDone);
    }

    /** Speaks a text at a place (for all players in range). */
    public static Speech say(String text, Language language, Speaker speaker, Style style, Supplier<Location> place) {
        return play(speak(text, language, speaker, style), place, null, 1.0, null);
    }

    /** Speaks a text only for one player (at the player). */
    public static Speech tell(Player target, String text, Language language, Speaker speaker, Style style) {
        return play(speak(text, language, speaker, style), null, p -> p.getUniqueId().equals(target.getUniqueId()), 1.0, null);
    }

    /**
     * Whether an utterance needs the resource pack so players hear it properly (default: always – {@code sing.require-pack},
     * {@code speech.require-pack}; the vanilla sounds are only a fallback that can be switched off). The integrating plugin then enforces
     * the NoteAPI pack before it lets anyone speak or sing.
     */
    public static synchronized boolean needsPack(Utterance utterance) {
        if (player == null) {
            return true;
        }
        return utterance.sung() ? player.singRequiresPack() : player.speechRequiresPack();
    }

    /** Stops all running utterances. */
    public static synchronized void stopAll() {
        if (player != null) {
            player.stopAll();
        }
    }

    /** The player (counting, stopping, statistics). */
    public static synchronized VoicePlayer player() {
        if (player == null) {
            throw new IllegalStateException("VoiceAPI is not enabled");
        }
        return player;
    }

    /** The library of saved utterances ({@code plugins/VoiceAPI/voices/*.voice}). */
    public static synchronized VoiceLibrary library() {
        if (library == null) {
            throw new IllegalStateException("VoiceAPI is not enabled");
        }
        return library;
    }

    // ── Resource pack ──

    /**
     * Decides which players hear the sounds of the resource pack (instead of asking NoteAPI). {@code null} switches back to NoteAPI.
     */
    public static synchronized void packProbe(Predicate<Player> probe) {
        if (pack != null) {
            pack.probe(probe);
        }
    }

    /** Whether a resource pack with the VoiceAPI sounds is present (the NoteAPI pack file contains them). */
    public static synchronized boolean packAvailable() {
        return pack != null && pack.available();
    }

    /** Whether a player hears the sounds of the pack. */
    public static synchronized boolean hearsPack(Player target) {
        return pack != null && pack.hears(target);
    }

    /**
     * The finished sounds as part of a resource pack ({@code assets/voiceapi/…}, bundled with the plugin and placed at
     * {@code plugins/VoiceAPI/VoiceAPI-Sounds.zip} on start). NoteAPI mixes them into its pack by itself – nobody has to build anything.
     */
    public static synchronized java.util.Optional<java.nio.file.Path> soundPack() {
        return java.util.Optional.ofNullable(soundPack).filter(java.nio.file.Files::isRegularFile);
    }

    /** The format of the bundled sounds ({@link de.passimoritz.voiceapi.pack.VoicePack#FORMAT}). */
    public static int soundPackFormat() {
        return de.passimoritz.voiceapi.pack.VoicePack.FORMAT;
    }

    /** The pack connection (file, format, players). */
    public static synchronized PackBridge pack() {
        if (pack == null) {
            throw new IllegalStateException("VoiceAPI is not enabled");
        }
        return pack;
    }

    // ── Villager singing from the AI voice model (API 3) ──

    private static de.passimoritz.voiceapi.song.SongVoices songs;
    private static de.passimoritz.voiceapi.song.SongConverter converter;
    private static org.bukkit.plugin.Plugin owner;
    private static java.nio.file.Path basePack;
    private static final java.util.List<Runnable> PACK_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * The finished villager singing of a piece, if it was converted: real vocal track → AI voice model (RVC, CPU only) → short, crossfaded
     * sections in the sound pack. The integrating plugin plays {@link de.passimoritz.voiceapi.song.SongVoice#cues()} on its music clock
     * (players with the pack), so the singing stays in time; after a jump it continues with {@code cuesFrom(ms)}.
     */
    public static synchronized java.util.Optional<de.passimoritz.voiceapi.song.SongVoice> songVoice(String songId) {
        return songs == null ? java.util.Optional.empty() : songs.get(songId);
    }

    /** All converted singing voices. */
    public static synchronized java.util.List<de.passimoritz.voiceapi.song.SongVoice> songVoices() {
        return songs == null ? java.util.List.of() : songs.all();
    }

    /** What is missing for converting (empty = the voice model is set up; {@code tools/rvc_setup.py} sets it up). */
    public static synchronized java.util.List<String> songVoiceMissing() {
        return converter == null ? java.util.List.of("VoiceAPI is not enabled") : converter.missing();
    }

    /** Whether a song is being converted right now. */
    public static synchronized boolean songVoiceBusy() {
        return converter != null && converter.busy();
    }

    /**
     * Converts the vocal track of a piece into villager singing (on its own thread; on the CPU it takes about the song's length ×2–8).
     * Afterwards the sound pack is rewritten and {@link #onSoundPackChanged} is notified – the integrating plugin mixes it into its
     * resource pack again and sends it to the players.
     *
     * @param songId   id of the piece
     * @param vocals   the separated vocal track (WAV), e.g. {@code stems/vocals.wav} from the NoteAPI conversion
     * @param progress progress (on the main thread), or {@code null}
     * @return completed on the main thread with the singing, or with an error
     */
    public static synchronized java.util.concurrent.CompletableFuture<de.passimoritz.voiceapi.song.SongVoice> convertSong(String songId,
            java.nio.file.Path vocals, java.util.function.Consumer<String> progress) {
        java.util.concurrent.CompletableFuture<de.passimoritz.voiceapi.song.SongVoice> result = new java.util.concurrent.CompletableFuture<>();
        if (converter == null || songs == null || owner == null) {
            result.completeExceptionally(new IllegalStateException("VoiceAPI is not enabled"));
            return result;
        }
        String id = de.passimoritz.voiceapi.song.SongVoices.safe(songId);
        org.bukkit.plugin.Plugin plugin = owner;
        java.util.function.Consumer<String> onMain = progress == null ? null
                : text -> Scheduling.run(plugin, () -> progress.accept(text));
        converter.convert(id, vocals, songs.folder(id), onMain).whenComplete((folder, error) -> {
            de.passimoritz.voiceapi.song.SongVoice voice = null;
            Throwable failure = error;
            if (failure == null) {
                voice = songs.reload(id).orElse(null);
                if (voice == null) {
                    failure = new IllegalStateException("The singing could not be read after the conversion");
                } else {
                    failure = rebuildPack();
                }
            }
            de.passimoritz.voiceapi.song.SongVoice done = voice;
            Throwable fail = failure;
            Scheduling.run(plugin, () -> {
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

    /** Cancels the running conversion. */
    public static synchronized void cancelSongConversion() {
        if (converter != null) {
            converter.cancel();
        }
    }

    /** Deletes the singing of a piece (and removes it from the sound pack). @return whether there was one */
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

    /** Called (on the main thread) when the sound pack has changed – then mix it into the resource pack again and send it. */
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
                    owner.getLogger().log(java.util.logging.Level.WARNING, "Sound pack listener failed", e);
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
