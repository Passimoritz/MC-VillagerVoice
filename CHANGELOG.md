# Changelog

## 1.0.0 (17.10.2026)

- **Sprache aus Dorfbewohner-Lauten**: Text (Deutsch, Englisch) → Laute, Silben, Betonung, Satzmelodie → Klang-Befehle fürs Spiel. Vokale
  aus dem gehaltenen „Hmm“ (gestartet, vorgezogen, gestoppt; Helligkeit über die Tonhöhe), Konsonanten aus Block- und Tiergeräuschen.
- **9 Stimmen** (Dorfbewohner, Händler, Hexe, Allay, Piglin, Zombie, Magier, Ziege, Katze), **10 Stimmungen**, Stil (Tempo, Tonlage, Umfang,
  Kraft), Hervorhebung (`GROSS`, `*so*`), Zahlen als Wörter.
- **Singen**: Silben auf Töne, Konsonanten vor dem Schlag, Melismen, Oktav-Faltung.
- **Ressourcenpaket**: `tools/build_voice_pack.py` baut Formant-Laute (19 Vokale × 14 Grundtöne, 7 klingende und 21 Geräusch-Konsonanten) und
  mischt sie in das NoteAPI-Paket; Spieler mit Paket hören echte Laute (Auskunft über die NoteAPI oder `packProbe`).
- **Sprach-Uhr** (5 ms), `Speech` mit `onWord`/`onSegment`/`stop`, Bibliothek `voices/*.voice`, Werkzeug `java -jar voiceapi.jar`.
- **Labor**: `voicelab.py` (Vanilla-Klänge laden, vermessen, rendern), `compare.py`, `sing_test.py` (echter Song vs. Dorfbewohner vs. Paket).
- Schnittstelle 1: `VoiceApi.speak / sing / play / say / tell / cues / library / packAvailable / hearsPack / packProbe`.
