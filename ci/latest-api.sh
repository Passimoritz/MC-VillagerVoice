#!/usr/bin/env bash
# Gibt die neueste veröffentlichte API-Version aus: ci/latest-api.sh paper|spigot
# (aus den Maven-Metadaten der Projekte – so prüft die CI ohne Pflege immer gegen den neuesten Stand)
set -euo pipefail
case "${1:-paper}" in
  paper)
    url="https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml" ;;
  spigot)
    url="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml" ;;
  *) echo "paper oder spigot" >&2; exit 2 ;;
esac
xml="$(curl -fsSL --retry 3 "$url")"
# <latest> ist die zuletzt veröffentlichte Version (auch Vorabversionen); bevorzugt die neueste ohne pre/rc/alpha-Kennung, sonst <latest>
stable="$(printf '%s' "$xml" | grep -oE '<version>[^<]+</version>' | sed -E 's#</?version>##g' | grep -viE 'pre|rc|alpha|experimental' | tail -n 1 || true)"
latest="$(printf '%s' "$xml" | grep -oE '<latest>[^<]+</latest>' | sed -E 's#</?latest>##g' | head -n 1)"
echo "${stable:-$latest}"
