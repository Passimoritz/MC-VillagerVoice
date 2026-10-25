# Development

*English · [Deutsch](../de/development.md)* · [← Overview](../../README.md)

## Build and test

```bash
mvn verify                                                                     # Spigot API 1.17.1, Java 17 target
mvn -P paper-latest -Dbukkit.version=$(bash ci/latest-api.sh paper) verify    # against the newest Paper API
mvn package -P bundled-pack                                                    # big jar with the sounds inside
python ci/smoke.py --project paper --version latest --jar target/voiceapi-*.jar   # start a real server with the jar
```

The jar lands in `target/voiceapi-<version>.jar` (≈ 0.2 MB).

## Project layout

| Path | Contents |
|---|---|
| `src/main/java/de/passimoritz/voiceapi/` | `VoiceApi` (the interface), `VoiceApiPlugin`, `Speaker`, `Style`, `Utterance`, `VoiceLibrary` |
| `…/lang` | phonemizers (German, English), syllables, stress |
| `…/prosody` | durations, melody, loudness |
| `…/render` | vanilla and pack renderer, `Cue` |
| `…/play` | speech clock, `Speech` |
| `…/sing` | `MelodyNote`, `SingOptions`, `Singer` |
| `…/song` | AI villager singing: conversion, sections, pack writing |
| `…/pack` | resource-pack bridge, sound names, `SoundDownload` |
| `…/tool` | command-line tool |
| `src/main/resources/pack/VoiceAPI-Sounds.zip` | the sounds (in the repository, **not** in the normal jar; release asset `voice-pack`) |
| `tools/` | Python tools (packed into the jar) |
| `ci/` | smoke test, newest API versions, voice-model test |
| `docs/` | this documentation; `docs/images` diagrams, `docs/audio` samples |

## CI

| Workflow | When | What |
|---|---|---|
| *Java CI with Maven* | every push/PR, every Monday | build + tests (Spigot 1.17.1), the same against the newest Paper and Spigot API, real servers: Paper 1.17.1, 1.20.4, latest, Folia latest, Spigot latest (BuildTools). A tag `v<version>` creates a release with the jar |
| *Voice model (CPU, Linux + Windows Server)* | weekly and when the tools change | sets up the voice-model environment from scratch on runners without a GPU and converts a melody like the plugin |

## Release

1. Set `<revision>` in `pom.xml`, add a section to `CHANGELOG.md`.
2. If the sounds changed: update `SoundDownload.SHA256` and upload the asset ([Sound pack → maintainers](sound-pack.md#for-maintainers-publishing-new-sounds)).
3. Tag `v<version>` and push the tag — CI builds and publishes the release.

## Documentation

- Diagrams: `python tools/doc_diagrams.py` (English and German SVG, light and dark).
- In-game speech samples: `python tools/doc_samples.py --jar target/voiceapi-<version>.jar --gson <gson.jar>`.
- Villager speeches: `python tools/doc_speeches.py`.
- Measured pictures: `python tools/readme_images.py --orig … --video … --ours …`.
