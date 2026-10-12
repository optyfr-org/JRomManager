#!/usr/bin/env bash
# Regenerate dependency locks after bumping versions in gradle/libs.versions.toml.
#
# Flow (TOML is the single source of truth — edit it first by hand):
#   1. Sync TOML pins into the files that need literals (no catalog access):
#      root buildscript pins, buildSrc nebula pin, settings foojay pin,
#      dist/ver.properties. Sync FIRST so the relock below resolves the new pins.
#   2. Re-resolve + re-download everything (--refresh-dependencies) and rewrite
#      ALL locks (--write-locks): gradle.lockfile, buildscript-gradle.lockfile.
#      Covers all projects/configurations via scripts/resolve-all.init.gradle.kts.
#   3. Print values to mirror into the WebClient submodule + a summary diff.
#   Afterwards run scripts/update-verification-metadata.sh to re-record
#   checksums/signatures.
#
# Usage:
#   scripts/refresh-locks.sh [--dry-run] [--offline]
#     --dry-run   print what would change, write nothing
#     --offline   skip network refresh (locks rewrite from cache only)
#
# Requirements: bash, awk, JDK 25 toolchain (Gradle foojay auto-provisions it),
# network access to Maven Central / JitPack / plugin portal unless --offline.
# Override the Gradle binary with GRADLE_CMD=... if required.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOML="$ROOT_DIR/gradle/libs.versions.toml"
INIT="$ROOT_DIR/scripts/resolve-all.init.gradle.kts"
LOCKS=(gradle.lockfile buildscript-gradle.lockfile)
DRY_RUN=0
OFFLINE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --offline) OFFLINE=1; shift ;;
    -h|--help) sed -n '2,28p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
  esac
done

GRADLE_BIN="${GRADLE_CMD:-./gradlew}"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mXX\033[0m %s\n' "$*" >&2; exit 1; }

[[ -f "$TOML" ]] || die "catalog not found: $TOML"
[[ -f "$INIT" ]] || die "init script not found: $INIT"

# GNU vs BSD sed (macOS needs -i '').
if sed --version >/dev/null 2>&1; then SED=(sed -i -E); else SED=(sed -i '' -E); fi

# Print the version for a [versions] key in the TOML; dies if absent.
# Handles CRLF checkouts (strips trailing \r before matching).
toml_val() {
  awk -v key="$1" '
    { sub(/\r$/, "") }
    /^\[/ { in_versions = ($0 == "[versions]") }
    in_versions && $0 ~ "^" key "[[:space:]]*=" {
      if (match($0, /"[^"]*"/)) { print substr($0, RSTART + 1, RLENGTH - 2); found = 1; exit }
    }
    END { if (!found) exit 1 }
  ' "$TOML" || die "version key '$1' missing from $TOML"
}

# Sync one literal mirror: replace the quoted (or trailing) version following
# the LEAD anchor on matching lines of FILE with the TOML value for KEY.
# The value is expanded by the shell BEFORE sed sees it, so no placeholders
# can leak into the file.
mirror() {
  local file="$1" lead="$2" key="$3"
  local path="$ROOT_DIR/$file"
  local ver; ver="$(toml_val "$key")"
  [[ -f "$path" ]] || die "mirror target missing: $file"
  if ! grep -Eq "$lead" "$path"; then
    warn "$file: anchor not found, skipping ($lead)"
    return 0
  fi
  if [[ $DRY_RUN -eq 1 ]]; then
    log "(dry-run) would set $file [$key] -> $ver"
    return 0
  fi
  "${SED[@]}" "s|(${lead})[^\"']*|\1${ver//&/\\&}|g" "$path"
  log "synced $file [$key] -> $ver"
}

# ---------------------------------------------------------------------------
# 1. Sync literals from TOML (before relocking, so locks resolve new pins)
# ---------------------------------------------------------------------------
log "Syncing literal mirrors from $TOML..."
# root buildscript pins (libs catalog not visible in buildscript)
mirror "build.gradle.kts" 'force\("io\.airlift:aircompressor:' aircompressor
mirror "build.gradle.kts" 'bcpg-jdk18on:' bouncycastle
mirror "build.gradle.kts" 'bcprov-jdk18on:' bouncycastle
# buildSrc nebula pin (buildSrc cannot read the parent catalog)
mirror "buildSrc/build.gradle.kts" 'gradle-ospackage-plugin:' nebula-ospackage
# settings foojay pin (settings plugins block cannot read the catalog)
mirror "settings.gradle.kts" 'foojay-resolver-convention"\) version "' foojay-resolver
# distributed/binary pin consumed via ver.properties
mirror "dist/ver.properties" '^Commons-Daemon-Version=' commons-daemon-dist

# ---------------------------------------------------------------------------
# 2. Refresh + regenerate ALL locks
# ---------------------------------------------------------------------------
cd "$ROOT_DIR"
if [[ $OFFLINE -eq 1 ]]; then
  REFRESH=(--offline)
else
  REFRESH=(--refresh-dependencies)
fi
if [[ $DRY_RUN -eq 1 ]]; then
  log "(dry-run) would run: $GRADLE_BIN --no-daemon ${REFRESH[*]} --write-locks -I scripts/resolve-all.init.gradle.kts resolveAllForVerification"
else
  log "Refreshing dependencies and regenerating locks..."
  "$GRADLE_BIN" --no-daemon "${REFRESH[@]}" --write-locks -I "$INIT" resolveAllForVerification
fi

# ---------------------------------------------------------------------------
# 3. WebClient submodule reminder + summary diff
# ---------------------------------------------------------------------------
log "NOTE: WebClient is a separate submodule repo; mirror these TOML pins there:"
for k in sonarqube foojay-resolver gradle-download-task gwt-gradle gwt smartgwt lombok; do
  echo "  $k = $(toml_val "$k")"
done

log "Resulting changes:"
if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git -C "$ROOT_DIR" status --short -- gradle/libs.versions.toml "${LOCKS[@]}" \
    build.gradle.kts buildSrc/build.gradle.kts settings.gradle.kts dist/ver.properties || true
  echo
  git -C "$ROOT_DIR" diff --stat -- gradle/libs.versions.toml "${LOCKS[@]}" || true
else
  warn "not a git checkout; review changes manually"
fi

log "Done. Review the diff, then run scripts/update-verification-metadata.sh"
log "to regenerate dependency-verification metadata for the new versions."
