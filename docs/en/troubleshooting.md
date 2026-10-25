# Troubleshooting

*English · [Deutsch](../de/troubleshooting.md)* · [← Overview](../../README.md)

**Players hear villager grunts instead of real vowels.**
They have not loaded the resource pack, or the pack does not contain the VoiceAPI sounds yet. Check the log for
`VoiceAPI sounds found in the resource pack`. With NoteAPI the sounds are mixed in automatically after the download; the player has to
accept the pack.

**`Could not download the VoiceAPI sounds … HTTP 404 / ConnectException`**
The server cannot reach GitHub. VoiceAPI keeps running without the pack sounds. Place the file by hand or use a mirror —
see [Sound pack → offline](sound-pack.md#offline-mirrors-own-files).

**`wrong checksum`**
A proxy or mirror delivered another file. Nothing was replaced. Check `pack.download-url`.

**Singing is silent for some players.**
`sing.require-pack: true` — singing is only played to players with the pack, because the vanilla hum cannot hold a note. The integrating
plugin should require the pack (`VoiceApi.needsPack(utterance)`).

**Speech is silent for some players.**
`speech.require-pack: true` (default). Set it to `false` to let players without the pack hear the vanilla voice.

**Players can't hear it at all.**
The speech plays in the category from `speech.category` (default: Voice/Speech). Players may have that slider at 0.

**Villager singing: "voice model not set up".**
Run `python plugins/VoiceAPI/tools/rvc_setup.py --model villager.pth`; `VoiceApi.songVoiceMissing()` lists what is missing.

**`java -jar voiceapi.jar …` fails with `NoClassDefFoundError: com/google/gson/…`.**
Outside a server Gson is not there. Put it on the class path — see [Tools](tools.md).

**The server stopped during a song conversion.**
It continues on the next start (`rvc.resume: true`); the old version of the song stays playable until the new one is complete.
