#!/usr/bin/env bash
# Prints the newest published API version: ci/latest-api.sh paper|spigot
# (from the projects' Maven metadata – so CI always checks against the newest state without maintenance)
set -euo pipefail
case "${1:-paper}" in
  paper)
    url="https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml" ;;
  spigot)
    url="https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml" ;;
  *) echo "paper or spigot" >&2; exit 2 ;;
esac
xml="$(curl -fsSL --retry 3 "$url")"
# <latest> is the last published version (pre-releases too); prefer the newest without a pre/rc/alpha tag, otherwise <latest>
stable="$(printf '%s' "$xml" | grep -oE '<version>[^<]+</version>' | sed -E 's#</?version>##g' | grep -viE 'pre|rc|alpha|experimental' | tail -n 1 || true)"
latest="$(printf '%s' "$xml" | grep -oE '<latest>[^<]+</latest>' | sed -E 's#</?latest>##g' | head -n 1)"
echo "${stable:-$latest}"
