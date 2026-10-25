# Konzept: Soundpack seltener, kürzer und ohne Ruckeln laden (VoiceAPI + NoteAPI)

Stand: 25.10.2026 · Status: **freigegeben und umgesetzt** (nicht committet, Jars in `plugins/update/` des Testservers)

> **Umsetzung (Entscheidungen des Nutzers):** Packs nur beim Join, auf Paper/Purpur in der Configuration-Phase. Im Spiel wird nichts
> nachgeschoben, auch nicht auf Wunsch (`/music pack send` sagt nur „neu verbinden“). Standardmäßig Pflicht (`pack.force: true`), die
> Villager-Stimme immer. Getrennte, reproduzierbare Packs: Instrumente 15,4 MB, Stimme 5,9 MB, pro Song Originalton bzw. Villager-Gesang.
> Nur noch die Villager-Stimme, Basis-Pack mit 22,05 kHz (23,7 → 5,9 MB). Neu: pro Song „Instrumental mit Pack / als Notenblöcke“ auf der
> Song-Seite. Hat ein Song Villager-Gesang, singt der Villager immer über das VoiceAPI-Pack.
> **Client-Cache:** Der Server kann den Cache des Clients nicht abfragen. Das ist auch nicht nötig: Der Client prüft den SHA-1 selbst und
> lädt bekannte Packs ohne Download aus `downloads/<uuid>/<sha1>`. Senden muss der Server trotzdem bei jedem Join, weil der Client
> Server-Packs beim Trennen vergisst. Das eine Neuladen pro Join liegt jetzt hinter dem Verbindungsbildschirm.

Ziel: Ein Spieler soll das Ressourcenpaket (1) nicht bei jedem Join oder jeder Änderung neu herunterladen, (2) es soll schneller
laden und (3) im laufenden Spiel möglichst nie den bildschirmfüllenden Ladebalken mit kurzem Ruckeln auslösen.

---

## 1. Kurzfassung

- **Heruntergeladen wird schon heute selten.** Der Client speichert Packs nach UUID und SHA-1 (`downloads/<uuid>/<sha1>`). Bei
  gleichem Hash lädt er nichts neu. Neue Downloads gibt es nur, wenn der Hash sich ändert. Das passiert unnötig oft, vor allem beim
  Umschalten der Instrumente und durch nicht reproduzierbare ZIPs.
- **Das eigentliche Problem ist das Neuladen der Ressourcen.** Jedes angewendete Pack löst beim Client ein *vollständiges* Neuladen
  aller Ressourcen aus: Ladebalken, Sound-Engine-Neustart, Ruckeln. Das passiert bei **jedem Join** (Server-Packs verwirft der Client
  beim Trennen) und zusätzlich **mitten im Spiel** bei jeder Konvertierung und jedem `/music pack on|off`.
- **Empfehlung:**
  1. Packs während der **Configuration-Phase** des Logins senden (Paper/Purpur), nicht 2 s nach dem Join im Spiel. Dann fällt das
     Neuladen in den ohnehin vorhandenen Verbindungsbildschirm.
  2. **Im Spiel nichts nachschieben.** Neue Songs gelten ab dem nächsten Join oder auf Wunsch des Spielers.
  3. **Stabile Packs mit festen Hashes.** VoiceAPI-Basis und NoteAPI-Instrumente werden zwei getrennte Packs, alle ZIPs sind
     reproduzierbar (feste Zeitstempel, feste Reihenfolge).
  4. Größe senken: optionale Stimmen, eventuell Neukodierung. Dazu ein mehrsträngiger Webserver.

---

## 2. Ist-Analyse

### 2.1 Wer baut was?

```
VoiceAPI (plugins/VoiceAPI)
  Jar ──extractSounds()──► pack/VoiceAPI-Sounds-base.zip      23,7 MB, 2.405 Einträge (2.016 hum, 266 Vokale, 119 Konsonanten)
                              │ SongVoices.writePack() bei jedem Start + nach jeder Konvertierung/Löschung
                              ▼
                          VoiceAPI-Sounds.zip                 25,3 MB = Basis + 48 Gesangsabschnitte (2 Songs)
                              │ VoiceApi.onSoundPackChanged → NoteAPI PackService.refresh()
NoteAPI (plugins/NoteAPI)     ▼
  VoiceMerge.merge(..., songs=false) ──► pack/NoteAPI-Sounds.zip   39,5 MB, 3.622 Einträge (≈ 23,3 MB davon VoiceAPI)
  VoiceMerge.songPacks()             ──► pack/voices/<song>.zip    je ~0,8 MB  (Villager-Gesang pro Song)
  SongPacks.rebuild(songaudio)       ──► pack/songs/<song>.zip     je ~3,7 MB  (Originalaudio pro Song)
  voiceOnly() (wenn pack.instruments=false) ──► nur im Speicher, ~25 MB
```

Ausgeliefert wird über den eingebauten `com.sun.net.httpserver` auf `pack.port` (25580). Die URL enthält den Hash
(`/NoteAPI-Sounds-<sha10>.zip`, `/NoteAPI-Song-…`, `/NoteAPI-Voice-…`).

### 2.2 Wie wird gesendet?

| Stelle | Verhalten |
|---|---|
| `ResourcePacks.send` | Ab 1.20.3 `addResourcePack(uuid, url, sha1, prompt, force)` per Reflection, sonst `setResourcePack` (genau ein Pack). |
| UUIDs | Basis: `nameUUID("NoteAPI-Sounds")` = `6469cd0f-…`; Song: `nameUUID("NoteAPI-Song:<id>")`; Gesang: `nameUUID("NoteAPI-Voice:<id>")`. Fest, gut. |
| `PackService.onJoin` | **40 Ticks (2 s) nach dem Join**, also im Spiel. Gesendet werden Basis, alle Song-Packs und alle Gesangs-Packs. |
| `send()` | Merkt sich pro Spieler `loaded`/`pending` (SHA-1). Dieselbe Version wird nicht doppelt gesendet. Beim Quit wird alles vergessen. Das ist richtig, denn auch der Client vergisst die Server-Packs. |
| `refresh()` | Nach jeder Konvertierung (`youtube.afterConvert`, `VoiceApi.onSoundPackChanged`) und bei `/music pack …`. Alle Spieler mit Pack bekommen **sofort** neue oder geänderte Packs. Das heißt: Neuladen mitten im Spiel. |
| Prompt | Ein Prompt-Text pro Pack. `pack.force=false`, der Spieler kann also ablehnen. |
| Ohne 1.20.3 oder mit `pack.url` | Alles in einem Pack (`SongAudioMerge` in die Basis). Jede neue Konvertierung ändert dann die ganzen 40 MB. |

### 2.3 Messwerte (Testserver `mein-server`, Purpur 26.3, Client Prism-Instanz „26.3“)

**Packgrößen** (24.10.2026):

| Pack | Größe | Einträge | Anmerkung |
|---|---:|---:|---|
| NoteAPI-Sounds.zip (Basis) | 39,5 MB | 3.622 | davon VoiceAPI ≈ 23,3 MB, `hum` allein 19,8 MB (2.016 Dateien) |
| 5 Song-Packs (Originalaudio) | 18,4 MB | je 137 | je 3,6–3,7 MB, reproduzierbar (Zeitstempel 1980) |
| 2 Gesangs-Packs | 1,6 MB | je 26 | reproduzierbar |
| **Pro Join angewendet** | **59,5 MB in 8 Packs** | | |
| VoiceAPI-Sounds.zip (Zwischendatei) | 25,3 MB | 2.453 | **bei jedem Start neu geschrieben** (Zeitstempel „jetzt“) |
| voice-only (Instrumente aus) | 24,5–25,3 MB | | **Hash ändert sich bei jedem Serverstart** (siehe 2.4) |

Alle ZIP-Einträge sind mit DEFLATE gepackt. Bei OGG bringt das < 10 %, kostet aber Rechenzeit beim Bauen.

**Sendehäufigkeit**, aus dem Client-Download-Protokoll `downloads/log.json`. Jeder Eintrag ist ein angewendetes Pack. Bei bekanntem
Hash nimmt der Client die Cache-Datei und frischt nur ihr Änderungsdatum auf.

| Tag | angewendete Packs | davon Basis | verschiedene Basis-Hashes = echte Basis-Downloads | Basis-Volumen angewendet |
|---|---:|---:|---:|---:|
| 18.10. | 149 | 17 | 8 | 630 MB |
| 21.10. | 67 | 7 | 2 | 230 MB |
| 22.10. | 131 | 4 | 2 | 165 MB |
| 23.10. | 70 | 2 | 1 | 79 MB |
| 24.10. | 35 | 12 | 4 (39,5 / 23,7 / 24,5 / 25,3 MB) | 429 MB |

Sitzung am 24.10., Serverlog und Client-Log nebeneinander:

```
23:31:02  Join                    → 8 Packs (59,5 MB) angewendet          → 1× Neuladen
23:32:44  Join (nach Neustart)    → 8 Packs angewendet                     → 1× Neuladen
23:32:58  /music pack off         → neues Basis-Pack 750022… (25,3 MB) DOWNLOAD → Neuladen im Spiel
23:35:27  /music pack on          → Basis 8d8387… (39,5 MB) aus dem Cache  → Neuladen im Spiel
```

Das sind **4 Neuladevorgänge in 4 Minuten**. Zwei davon kamen im Spiel durch Umschalten. Mit jeder Konvertierung, die ein Spieler
online erlebt, kommt ein weiterer dazu. Der Client-Cache der Basis-UUID belegt 108 MB (4 Versionen), der ganze Cache 217 MB.

### 2.4 Gefundene Ursachen

1. **Neuladen bei jedem Join, und zwar im Spiel.** Der Client verwirft Server-Packs beim Trennen. Ein Neuladen pro Join lässt sich
   also nicht vermeiden. Heute kommt es 2 s *nach* dem Betreten der Welt. Das ergibt den bildschirmfüllenden Balken, und die Musik
   und Geräusche brechen ab.
2. **Neuladen mitten im Spiel** durch `refresh()`: nach jeder Konvertierung (neue Song- und Gesangs-Packs) und bei
   `/music pack on|off|voice|instruments`. Diese Befehle gelten serverweit, alle Online-Spieler laden neu.
3. **Instrumente aus = anderes Pack unter derselben UUID.** `voiceOnly()` ersetzt das 39,5-MB-Pack durch ein 25-MB-Pack. Zurück
   ersetzt es wieder. Das bedeutet jedes Mal Neuladen und beim ersten Mal einen Download.
4. **Nicht reproduzierbare ZIPs:** `SongVoices.writePack` setzt keinen Zeitstempel (`new ZipEntry` = aktuelle Zeit). Deshalb greift
   der `Files.mismatch`-Vergleich nie, und `VoiceAPI-Sounds.zip` bekommt bei jedem Start einen neuen Hash. `VoiceMerge.voiceOnly`
   und `merge` übernehmen diese Zeitstempel (`copy.setTime(entry.getTime())`). Folgen: Das voice-only-Pack hat **nach jedem
   Serverstart einen neuen Hash, also 25 MB neu für jeden**. Wird die Basis neu gemischt (Formatwechsel), bekommt auch sie einen
   neuen Hash, obwohl der Inhalt gleich ist.
5. **Basis = Instrumente + VoiceAPI in einem Pack.** Eine Änderung an einer Hälfte (neue Instrumente, neues VoiceAPI-Format)
   erzwingt 39,5 MB statt ~16 bzw. ~23 MB.
6. **Webserver ohne Executor.** `HttpServer.create(...)` ohne `setExecutor` bearbeitet die Anfragen nacheinander auf einem Thread.
   Joinen mehrere Spieler gleichzeitig, warten sie aufeinander. Dazu liegt `data` (39,5 MB) plus bis zu zwei alte Versionen im Heap.
7. **Kleinigkeit:** „Villager singing: 2 pack(s), new/changed: 2“ erscheint bei jedem Start, weil `packs` leer startet. Das ist nur
   eine Log-Meldung, es wird nichts zusätzlich gesendet.

Was schon gut ist: feste UUIDs, Hash in der URL, Song- und Gesangs-Packs als kleine eigene Packs (ab 1.20.3), keine Doppelsendung
derselben Version, `DISCARDED` wird nicht als Ablehnung gewertet, zwei alte Versionen bleiben ausgeliefert.

### 2.5 Hintergrund: Was der Client macht (1.20.3+ bis 26.x)

- Jeder `ResourcePackPush` (UUID, URL, SHA-1, required, prompt) landet im `ServerPackManager`. Gleiche UUID mit gleichem Hash wird
  nicht neu angewendet. Gleiche UUID mit neuem Hash ersetzt das alte Pack (`DISCARDED` für das alte).
- Download nach `downloads/<uuid>/<sha1>`. Ist die Datei mit passendem Hash da, gibt es keinen Netzverkehr. Der Client fragt den
  Server **nicht** per HTTP nach (kein ETag, kein If-Modified-Since). Die Cache-Entscheidung hängt nur am SHA-1.
- Nach dem Download folgt **ein vollständiges Neuladen** (Texturen, Modelle, Shader, Sounds). Gleichzeitig ankommende Pushes werden zu
  einem Neuladen gebündelt, später ankommende lösen ein weiteres aus. Die Sound-Engine wird dabei neu gestartet, laufende Sounds
  brechen ab.
- In der **Configuration-Phase** (vor dem Betreten der Welt, seit 1.20.2) zeigt der Client ohnehin einen Ladebildschirm. Ein
  Neuladen dort fällt kaum auf und erzeugt kein Ruckeln im Spiel.
- Vor 1.20.3: genau ein Server-Pack, `setResourcePack` ersetzt es, ein Entfernen gibt es nicht. Cache nach SHA-1 in
  `server-resource-packs/`.
- Den Prompt kann nur der Spieler abschalten (Serverliste → „Server-Ressourcenpakete: Aktiviert“).

---

## 3. Empfohlene Architektur

### 3.1 Pack-Aufteilung (ab 1.20.3)

| Pack | UUID (fest) | Inhalt | Hash ändert sich nur, wenn … | Größe heute → Ziel |
|---|---|---|---|---|
| **A NoteAPI-Instrumente** | `nameUUID("NoteAPI-Sounds")` (wie heute) | `assets/noteapi/…`, pack.mcmeta | `/music pack build` oder neues SoundBank-Format | ~16 MB |
| **B VoiceAPI-Basis** | `nameUUID("VoiceAPI-Base")` | `assets/voiceapi/…` ohne Songs | VoiceAPI-Format/Release (`VoicePack.FORMAT`, Release-SHA) | 23,7 MB → 6–13 MB (3.4) |
| **C Originalaudio je Song** | `NoteAPI-Song:<id>` (wie heute) | `noteapi_song` | Song neu konvertiert | je ~3,7 MB |
| **D Villager-Gesang je Song** | `NoteAPI-Voice:<id>` (wie heute) | `voiceapi/sounds/song/<id>` + eigene sounds.json | Gesang neu konvertiert | je ~0,8 MB |

- Instrumente aus: nur `removeResourcePack(A)` bzw. A nicht senden. **B bleibt, wie es ist.** Kein neues Pack, kein Download, kein
  zweiter Hash. Stimme aus entsprechend über B.
- Für das Pack B baut VoiceAPI die Datei selbst reproduzierbar. Mit dem Jar-Download-Branch kann es *identisch* mit dem
  Release-Asset sein (bekannter SHA-256, dazu SHA-1 für Minecraft). Damit hat Pack B auf allen Servern mit derselben VoiceAPI-Version
  denselben Hash. Optional kann es direkt von einer öffentlichen URL (GitHub-Release/CDN) kommen. Das entlastet den eigenen Upload.
- `sounds.json`-Zusammenführung: Minecraft führt `sounds.json` pro Namespace über alle Packs zusammen (ohne `"replace": true`).
  Die Gesangs-Packs D funktionieren so schon heute neben der Basis. Für B genügt es, dass NoteAPI kein `assets/voiceapi` mehr in A
  mischt.

### 3.2 Wann wird gesendet?

1. **Beim Login in der Configuration-Phase** (Paper/Purpur 1.21.7+: `AsyncPlayerConnectionConfigureEvent`). Dort A, B, alle C und
   D gemeinsam senden und auf die Antworten warten (mit Zeitlimit, z. B. 60 s). Erst dann betritt der Spieler die Welt.
   - Ergebnis: Das eine unvermeidbare Neuladen pro Join passiert im Verbindungsbildschirm. In der Welt gibt es keinen Ladebalken und
     kein Ruckeln, und die Musik startet auf vollständig geladenen Sounds.
   - Bekannte Packs (gleicher Hash) kommen aus dem Cache. Das dauert typisch 1–3 s Neuladezeit, ohne Netzverkehr.
   - `loaded`/`pending` von heute bleiben. Neu ist nur der Zeitpunkt, und `onJoin` sendet nichts mehr, wenn die Configuration-Phase
     das schon erledigt hat.
2. **Fallback** (Spigot, ältere Paper-Versionen ohne das Event): wie heute nach dem Join, aber alle Packs in *einem* Tick, damit der
   Client einmal neu lädt. Das ist heute schon so, solange `send()` alles zusammen schickt.
3. **Im Spiel standardmäßig nichts nachschieben** (`pack.live-update`):
   - `next-join` (Standard): Neue oder geänderte C/D-Packs bekommt der Spieler beim nächsten Betreten. Bis dahin hört er den neuen
     Song mit Noteblöcken bzw. ohne Villager-Gesang. Das ist mit `villagerSong`/`audioSong` schon so gelöst.
   - `ask`: Chat-Hinweis „Neuer Song verfügbar – [Jetzt laden]“. Der Spieler entscheidet, wann der Ladebalken kommt
     (z. B. `/music pack update`).
   - `immediately`: Verhalten wie heute.
   - Optional: beim Öffnen eines nicht geladenen Songs fragen „Song braucht ein kleines Pack (3,7 MB) – laden?“.
4. **`/music pack on|off|voice|instruments`** wirkt auf neue Joins sofort. Online-Spieler bekommen nur ein `removeResourcePack` bzw.
   ein Hinzufügen genau des betroffenen Packs, mit Hinweis statt stillem Neuladen. Langfristig sollte das eine Einstellung pro
   Spieler sein, nicht serverweit.

### 3.3 Stabile Hashes

- Alle ZIP-Schreiber (VoiceAPI `SongVoices.writePack`, NoteAPI `VoiceMerge.merge`/`voiceOnly`/`songPacks`, `SongAudioMerge`,
  `PackBuilder`) bekommen: **feste Zeit** (`setTime(0)` bzw. 1980-01-01), **sortierte Einträge**, **sortierte JSON-Schlüssel** und
  `STORED` für `.ogg` (spart CPU, Größe gleich). Gleicher Inhalt ergibt dann gleiches Byte-Bild und gleichen SHA-1 über Neustarts
  und Rechner hinweg.
- Den SHA-1 neben die Datei schreiben (`*.sha1`, wie heute bei den Song-Packs), damit beim Start nicht 40 MB gehasht werden müssen.
- Test: Das Pack zweimal bauen und die Bytes vergleichen. Für VoiceAPI gibt es so einen Test schon für die Release-Datei auf dem
  Jar-Branch.

### 3.4 Größe (Download kürzer)

| Hebel | Ersparnis | Aufwand/Risiko |
|---|---|---|
| A und B trennen | Instrument-Änderung: 16 statt 39,5 MB; VoiceAPI-Änderung: 23 statt 39,5 MB | gering |
| **Nur genutzte Stimmen** (`pack.voices: [villager, trader, …]`): `hum` sind 9 Stimmen × 224 Dateien ≈ 2,2 MB pro Stimme | nur Villager: B ≈ 6 MB statt 23,7 MB | mittel (Format-Marker muss die Stimmenliste kennen, fehlende Stimmen fallen auf den Villager zurück, das tut `humSound` schon) |
| Neukodierung 22 kHz / niedrigere Vorbis-Qualität | laut Auftrag ~45 %, laut Messung auf Branch `claude/magical-engelbart-7ebd6b` nur ~23 % (3–4 KB Vorbis-Header je Datei) | Klang prüfen (Hörprobe), `VoicePack.FORMAT` hochzählen, damit einmal neu laden für alle |
| Legato-Varianten (`…g`) nur für die Villager-Stimme | ~45 % des `hum`-Anteils der übrigen Stimmen | Klangverlust bei anderen Stimmen |
| DEFLATE → STORED für OGG | ~0 MB, schnelleres Bauen und Entpacken | keins |

Zum Vergleich: Bei 50 Mbit/s dauern 39,5 MB etwa 6–7 s, 59,5 MB etwa 10 s. Das gilt nur beim *ersten* Mal, danach kommt alles aus
dem Cache. Die Größe ist also vor allem für neue Spieler und für Formatwechsel wichtig. Gegen den Ladebalken im Spiel hilft sie wenig.

### 3.5 Auslieferung (HTTP)

- `server.setExecutor(Executors.newFixedThreadPool(4))` (oder virtuelle Threads), Dateien per `Files.copy` streamen statt
  `byte[]` im Heap. Alte Versionen als Dateien behalten (`pack/served/<sha>.zip`), nicht im Speicher.
- `Content-Length` (gibt es schon). `Cache-Control: public, max-age=31536000, immutable` und `ETag: "<sha1>"` schaden nicht und helfen
  einem vorgeschalteten Proxy/CDN. Der Minecraft-Client selbst nutzt sie nicht.
- Optional pro Pack eine externe URL (`pack.url` für A, `pack.download-url`-Mirror für B), *ohne* dass dann wie heute der
  Song-Pack-Modus abgeschaltet wird.
- `HEAD`-Anfragen beantworten und `Range` ignorieren (der Client lädt ganz).

### 3.6 Ältere Clients und Server-Software

| Fall | Verhalten |
|---|---|
| Server Paper/Purpur ≥ 1.21.7 (Testserver: Purpur 26.3) | volle Lösung: Configuration-Phase + mehrere Packs |
| Server Paper/Spigot 1.20.3 – 1.21.6 | mehrere Packs, Senden nach dem Join (Fallback 3.2.2); Neuladen im Spiel bleibt beim Join |
| Spigot (kein Configuration-Event) | wie oben. Alternative: Pack A/B über `server.properties` (`resource-pack`, `resource-pack-id`, `resource-pack-sha1`), das sendet Vanilla in der Configuration-Phase, aber nur **ein** Pack und mit statischem Hash |
| Server < 1.20.3 | ein zusammengeführtes Pack (heutiger Weg). Neue Songs ändern alles → `next-join` als Standard ist hier besonders wichtig |
| ältere Clients über ViaVersion/ViaBackwards | Via übersetzt mehrere Packs für Clients < 1.20.3 nicht sauber (meist gilt nur das letzte). **Offene Frage 6.3** |
| Bedrock (Geyser) | Java-Packs werden nicht übertragen. Fallback Vanilla-Klänge (wie heute ohne Pack) |

---

## 4. Alternativen

| Option | Vorteile | Nachteile | Bewertung |
|---|---|---|---|
| **A: Nur stabile Hashes + nichts im Spiel nachschieben** (ohne Configuration-Phase) | klein, auf allen Servern gleich, beseitigt Downloads nach Neustart/Toggle und die Ladebalken im Spiel nach Konvertierungen | ein Neuladen 2 s nach jedem Join bleibt im Spiel | erster Schritt, nötig für alles Weitere |
| **B: Configuration-Phase (Paper-Event)** | Ladebalken und Ruckeln im Spiel ganz weg | Paper-spezifisch (Reflection oder eigenes Modul, NoteAPI baut gegen die älteste API), Login dauert bei neuem Spieler einmal länger | **empfohlen** zusätzlich zu A |
| C: `server.properties`-Pack für die Basis | Vanilla, Configuration-Phase, auch Spigot | ein einziges Pack, Hash muss von Hand gepflegt werden, Plugin-Änderungen am Pack brauchen einen Neustart | Notlösung für Spigot |
| D: Ein großes Pack (alles zusammen) | einfach, auch < 1.20.3 | jede Änderung = alles neu (40–60 MB) | nur Fallback |
| E: Song-Packs erst beim Abspielen laden | kleinster Join | Neuladen genau beim Songstart (schlechtester Moment), Musik bricht ab | abgelehnt |
| F: Alle Songs in einem „Songs“-Pack | weniger Pushes | jede Konvertierung lädt alle Songs neu | abgelehnt, die Einzelpacks sind besser |
| G: Eigener HTTP-Server mit ETag/CDN | entlastet Upload, viele Spieler gleichzeitig | Client nutzt ETag nicht, Nutzen nur für Bandbreite; privates Repo liefert anonym 404 | optional (3.5) |
| H: Größe senken (Stimmen, Neukodierung) | erster Download kürzer | Klang prüfen, Formatwechsel = einmal neu laden | sinnvoll, unabhängig |
| I: Client-Mod (z. B. Sounds ohne Neuladen nachladen) | gar kein Ladebalken | Spieler müssen eine Mod installieren | nicht im Rahmen |

---

## 5. Migrationsschritte

Jeder Schritt lässt sich einzeln ausliefern und testen.

1. **Reproduzierbare ZIPs** (VoiceAPI `SongVoices.writePack`, NoteAPI `VoiceMerge`, `PackBuilder`): feste Zeit, Sortierung, STORED
   für OGG, `.sha1`-Sidecar. *Test:* zweimal bauen, Bytes gleich. Nach Serverneustart im Client-`log.json` kein neuer Hash.
2. **`pack.live-update: next-join`** in NoteAPI: `refresh()` baut neu, sendet aber Online-Spielern nichts mehr bzw. nur einen
   Hinweis. `/music pack update` zum Selbstladen. *Test:* Song konvertieren, während man online ist. Kein Ladebalken, nach Rejoin ist
   der Song da.
3. **Webserver**: Thread-Pool, Streaming aus Dateien, Cache-Header. *Test:* zwei Clients gleichzeitig joinen.
4. **Pack B abtrennen**: NoteAPI mischt VoiceAPI-Sounds nicht mehr in A (`VoiceMerge.merge` nur noch für < 1.20.3), sendet B mit
   eigener UUID. `/music pack instruments off` entfernt nur A. VoiceAPI liefert B reproduzierbar, ideal byte-gleich mit dem
   Release-Asset. *Test:* Instrumente aus/an, kein Download, nur das Entfernen bzw. Hinzufügen von A.
5. **Configuration-Phase**: `AsyncPlayerConnectionConfigureEvent` per Reflection (oder optionales Paper-Modul), Packs senden und
   auf `SUCCESSFULLY_LOADED`/`DECLINED`/`FAILED` warten (Zeitlimit), `loaded` füllen. `onJoin` sendet nur noch, wenn nichts geladen
   wurde. *Test:* Join, kein Ladebalken in der Welt; Spigot-Fallback testen.
6. **Größe**: `pack.voices`-Liste und/oder Neukodierung mit Hörprobe, `VoicePack.FORMAT` + 1.
7. **Doku/Changelog** beider Plugins, Hinweis für Spieler: „Server-Ressourcenpakete: Aktiviert“ spart den Prompt.

Reihenfolge der Freigabe: Schritte 1 + 2 bringen am schnellsten spürbar weniger Ladebalken. Schritt 5 beseitigt den letzten
sichtbaren Ladebalken, Schritt 4 + 6 sparen Downloads.

---

## 6. Offene Fragen

1. **Neue Songs im Spiel:** Lieber `next-join` (still) oder `ask` (Hinweis mit Klick) als Standard?
2. **`/music pack on|off`** serverweit lassen oder zu einer Einstellung pro Spieler machen?
3. **Ältere Clients** über ViaVersion: Gibt es die auf dem Server, und welche Versionen sollen unterstützt werden?
4. **Welche Stimmen** braucht der Server wirklich (nur Villager oder alle 9)? Das bestimmt, ob Pack B 6 oder 23 MB groß ist.
5. **Hosting von Pack B:** Repo `MC-VillagerVoice` öffentlich machen (der Jar-Branch geht davon aus, heute liefert das private
   Repo anonym 404) oder weiter nur über den eigenen Webserver?
6. **Neukodierung:** Ist eine Hörprobe mit 22 kHz / geringerer Qualität gewünscht? Die Ersparnis ist widersprüchlich gemessen
   (~23 % vs. ~45 %).
7. **`pack.force`:** Soll das Pack Pflicht werden? Sonst können Spieler ablehnen und hören Noteblöcke.
8. **Paper-Abhängigkeit:** Die Configuration-Phase nur per Reflection nutzen (Plugin bleibt Spigot-kompatibel) oder ein separates
   Paper-Modul bauen?

---

## Anhang: Messmethode

- Packinhalte: Python `zipfile` über die Dateien in `plugins/NoteAPI/pack`, `plugins/VoiceAPI` (Einträge, Roh-/ZIP-Größe,
  Kompressionsart, Zeitstempel).
- Sendehäufigkeit: `PrismLauncher/instances/26.3/minecraft/downloads/log.json` (466 Einträge, 15.–24.10.), gruppiert nach Tag und
  UUID. Die UUIDs wurden aus `UUID.nameUUIDFromBytes` nachgerechnet (Basis `6469cd0f-…`, Song/Voice je Song). Echte Downloads =
  verschiedene SHA-1 je UUID; Cache-Treffer erkennt man daran, dass der Client nur das Änderungsdatum der vorhandenen Datei auffrischt.
- Abgleich mit `logs/2026-10-24-19.log.gz` des Servers (Join, `/music pack off|on`, „Resource pack new“).
