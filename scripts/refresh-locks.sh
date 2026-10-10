#!/usr/bin/env bash
# Regenerate dependency locks while refreshing all dependencies everywhere.
#
# What it does:
#   1. Re-resolves and re-downloads every dependency (--refresh-dependencies),
#      covering all projects and configurations via scripts/resolve-all.init.gradle.
#   2. Regenerates ALL dependency locks (--write-locks): gradle.lockfile and
#      buildscript-gradle.lockfile.
#   3. Propagates pins from gradle/libs.versions.toml into the files that need
#      literals (root buildscript pins, dist/ver.properties) and prints the
#      values to mirror into the WebClient submodule.
#   4. Prints a summary diff for review.
#
# Version bumps are driven by editing gradle/libs.versions.toml first, then
# running this script to propagate + relock. Afterwards run
# scripts/update-verification-metadata.sh to re-record checksums/signatures.
#
# Usage:
#   scripts/refresh-locks.sh [--dry-run] [--offline]
#     --dry-run   print what would run, change nothing
#     --offline   skip network refresh (locks rewrite from cache only)
#
# Requirements: bash, JDK 25 toolchain (Gradle foojay auto-provisions it),
# network access to Maven Central / JitPack / plugin portal unless --offline.
# No Python needed. Override the Gradle binary with GRADLE_CMD=... if required.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOML="$ROOT_DIR/gradle/libs.versions.toml"
INIT="$ROOT_DIR/scripts/resolve-all.init.gradle"
LOCKS=("gradle.lockfile" "buildscript-gradle.lockfile")
DRY_RUN=0
OFFLINE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --offline) OFFLINE=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
  esac
done

GRADLE_BIN="${GRADLE_CMD:-./gradlew}"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mXX\033[0m %s\n' "$*" >&2; exit 1; }

[[ -f "$TOML" ]] || die "catalog not found: $TOML"
[[ -f "$INIT" ]] || die "init script not found: $INIT"

# ---------------------------------------------------------------------------
# 1. Snapshot state for the final diff
# ---------------------------------------------------------------------------
SNAP_DIR="$(mktemp -d)"
trap 'rm -rf "$SNAP_DIR"' EXIT
cp "$TOML" "$SNAP_DIR/libs.versions.toml"
for f in "${LOCKS[@]}"; do
  [[ -f "$ROOT_DIR/$f" ]] && cp "$ROOT_DIR/$f" "$SNAP_DIR/" || true
done

# ---------------------------------------------------------------------------
# 2. Refresh + regenerate ALL locks
# ---------------------------------------------------------------------------
GRADLE_ARGS=(--no-daemon)
if [[ $OFFLINE -eq 1 ]]; then
  GRADLE_ARGS+=(--offline)
else
  GRADLE_ARGS+=(--refresh-dependencies)
fi

cd "$ROOT_DIR"
if [[ $DRY_RUN -eq 1 ]]; then
  log "(dry-run) would run: $GRADLE_BIN ${GRADLE_ARGS[*]} --write-locks -I scripts/resolve-all.init.gradle resolveAllForVerification"
else
  log "Refreshing dependencies and regenerating locks..."
  "$GRADLE_BIN" "${GRADLE_ARGS[@]}" --write-locks -I "$INIT" resolveAllForVerification
fi

# ---------------------------------------------------------------------------
# 3. Propagate TOML pins into files that need literals (no catalog access)
# ---------------------------------------------------------------------------
declare -A V
parse_toml_versions() {
  local section="" line t k v
  while IFS= read -r line || [[ -n "$line" ]]; do
    t="$(printf '%s' "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    case "$t" in
      '['*']') section="$t" ;;
      \#*|"") ;;
      *=*)
        if [[ "$section" == "[versions]" ]]; then
          k="${t%%=*}"; v="${t#*=}"
          k="$(printf '%s' "$k" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
          v="$(printf '%s' "$v" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^"//' -e 's/"$//')"
          V["$k"]="$v"
        fi
        ;;
    esac
  done < "$TOML"
}

patch_file() {
  local file="$1" pattern="$2" replacement="$3"
  local path="$ROOT_DIR/$file"
  if ! grep -Eq "$pattern" "$path"; then
    warn "$file: pattern not found, skipping: $pattern"
    return 0
  fi
  sed -i -E "s|${pattern}|${replacement}|g" "$path"
  echo "patched $file"
}

if [[ $DRY_RUN -eq 1 ]]; then
  log "(dry-run) skipping literal propagation."
else
  log "Propagating pins into literal-version files..."
  parse_toml_versions
  for k in aircompressor bouncycastle commons-daemon-dist sonarqube foojay-resolver \
           gradle-download-task gwt-gradle gwt smartgwt lombok; do
    [[ -n "${V[$k]:-}" ]] || die "version key '$k' missing from $TOML"
  done
  # root buildscript pins (libs catalog not visible in buildscript)
  patch_file "build.gradle" \
    "force 'io\.airlift:aircompressor:[^']*'" \
    "force 'io.airlift:aircompressor:${V[aircompressor]}'"
  patch_file "build.gradle" \
    "using module\('org\.bouncycastle:bcpg-jdk18on:[^']*'\)" \
    "using module('org.bouncycastle:bcpg-jdk18on:${V[bouncycastle]}')"
  patch_file "build.gradle" \
    "using module\('org\.bouncycastle:bcprov-jdk18on:[^']*'\)" \
    "using module('org.bouncycastle:bcprov-jdk18on:${V[bouncycastle]}')"
  # distributed/binary version pins consumed via ver.properties
  patch_file "dist/ver.properties" \
    "^Commons-Daemon-Version=.*$" \
    "Commons-Daemon-Version=${V[commons-daemon-dist]}"
  echo "NOTE: WebClient is a separate submodule repo; mirror these TOML pins there:"
  for k in sonarqube foojay-resolver gradle-download-task gwt-gradle gwt smartgwt lombok; do
    echo "  $k = ${V[$k]}"
  done
fi

# ---------------------------------------------------------------------------
# 4. Summary diff
# ---------------------------------------------------------------------------
log "Resulting changes (TOML + locks + literals):"
if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git -C "$ROOT_DIR" status --short -- gradle/libs.versions.toml "${LOCKS[@]}" build.gradle dist/ver.properties || true
  echo
  git -C "$ROOT_DIR" diff --stat -- gradle/libs.versions.toml "${LOCKS[@]}" || true
else
  diff -u "$SNAP_DIR/libs.versions.toml" "$TOML" || true
fi

log "Done. Review the diff, then run scripts/update-verification-metadata.sh"
log "to regenerate dependency-verification metadata for the new versions."
