# Fehlerbehebung

*[English](../en/troubleshooting.md) · Deutsch* · [← Übersicht](../../README.de.md)

**Spieler hören Villager-Grunzen statt echter Vokale.**
Sie haben das Ressourcenpaket nicht geladen, oder das Paket enthält die VoiceAPI-Laute noch nicht. Im Log nach
`VoiceAPI sounds found in the resource pack` suchen. Mit der NoteAPI werden die Laute nach dem Download automatisch eingemischt; der
Spieler muss das Paket annehmen.

**`Could not download the VoiceAPI sounds … HTTP 404 / ConnectException`**
Der Server erreicht GitHub nicht. VoiceAPI läuft ohne die Paket-Laute weiter. Datei von Hand ablegen oder einen Spiegel nutzen —
siehe [Lautpaket → offline](sound-pack.md#offline-spiegel-eigene-dateien).

**`wrong checksum`**
Ein Proxy oder Spiegel hat eine andere Datei geliefert. Nichts wurde ersetzt. `pack.download-url` prüfen.

**Gesang ist für manche Spieler stumm.**
`sing.require-pack: true` — Gesang hören nur Spieler mit Paket, weil das Vanilla-Summen keinen Ton halten kann. Das einbindende Plugin
sollte das Paket verlangen (`VoiceApi.needsPack(utterance)`).

**Sprache ist für manche Spieler stumm.**
`speech.require-pack: true` (Standard). Auf `false` setzen, damit Spieler ohne Paket die Vanilla-Stimme hören.

**Spieler hören gar nichts.**
Die Sprache läuft in der Kategorie aus `speech.category` (Standard: Stimme/Sprache). Vielleicht steht der Regler bei 0.

**Villager-Gesang: „voice model not set up“.**
`python plugins/VoiceAPI/tools/rvc_setup.py --model villager.pth` ausführen; `VoiceApi.songVoiceMissing()` listet, was fehlt.

**`java -jar voiceapi.jar …` scheitert mit `NoClassDefFoundError: com/google/gson/…`.**
Außerhalb eines Servers fehlt Gson. In den Klassenpfad legen — siehe [Werkzeuge](tools.md).

**Der Server wurde während einer Liedumwandlung gestoppt.**
Sie läuft beim nächsten Start weiter (`rvc.resume: true`); die alte Version des Lieds bleibt spielbar, bis die neue fertig ist.
