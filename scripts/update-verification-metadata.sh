#!/usr/bin/env bash
# Generate / update Gradle dependency-verification metadata.
#
# Produces gradle/verification-metadata.xml with SHA-256/SHA-512 checksums for
# every resolved artifact plus PGP signature entries where artifacts are signed
# (--write-verification-metadata sha256,sha512,pgp --export-keys).
#
# NOTE on X.509/Sigstore: Gradle dependency verification only consumes PGP
# (.asc) signatures. Artifacts signed via Sigstore/X.509 are NOT verifiable by
# Gradle and are covered by their sha256/sha512 checksums instead.
#
# Also maintains gradle/verification-keyring.keys (armored, written by Gradle)
# and per-key exports under gradle/verification-keys/<fingerprint>.asc for
# audit/offline re-import (needs the `gpg` CLI; skipped with a warning if absent).
#
# Modes:
#   scripts/update-verification-metadata.sh [--dry-run] [--offline]
#       [--clean] [--keyring PATH]
#     --dry-run   print what would run, change nothing
#     --offline   no network; skip regeneration (key exports still refresh from disk)
#     --clean     delete existing metadata + keyring first (full bootstrap)
#     --keyring   extra armored public key(s) to add to verification-keyring.keys
#                 (repeatable)
#
# Requirements: bash, JDK 25 toolchain, network unless --offline. No Python needed.
# Override the Gradle binary with GRADLE_CMD=... if required.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
META="$ROOT_DIR/gradle/verification-metadata.xml"
KEYRING="$ROOT_DIR/gradle/verification-keyring.keys"
KEYS_DIR="$ROOT_DIR/gradle/verification-keys"
INIT="$ROOT_DIR/scripts/resolve-all.init.gradle"
DRY_RUN=0
OFFLINE=0
CLEAN=0
EXTRA_KEYRINGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --offline) OFFLINE=1; shift ;;
    --clean|--from-scratch) CLEAN=1; shift ;;
    --keyring)
      [[ $# -ge 2 ]] || { echo "--keyring needs a path" >&2; exit 2; }
      EXTRA_KEYRINGS+=("$2"); shift 2 ;;
    --keyring=*) EXTRA_KEYRINGS+=("${1#--keyring=}"); shift ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
  esac
done

GRADLE_BIN="${GRADLE_CMD:-./gradlew}"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mXX\033[0m %s\n' "$*" >&2; exit 1; }

[[ -f "$ROOT_DIR/gradle/libs.versions.toml" ]] || die "catalog not found — run scripts/refresh-locks.sh first"
[[ -f "$INIT" ]] || die "init script not found: $INIT"

HAVE_GPG=0
if command -v gpg >/dev/null 2>&1; then
  HAVE_GPG=1
else
  warn "gpg not found on PATH — per-key exports under gradle/verification-keys/ will be skipped"
fi

count_matches() { grep -c "$1" "$2" 2>/dev/null || true; }

# gpg helpers (temp homedir so the user's keyring is never touched)
new_tmhome() { TMP_HOME="$(mktemp -d)"; chmod 700 "$TMP_HOME"; }
list_fprs() { # $1 = armored key file; prints primary fingerprints, one per line
  local tmphome="$1" keyfile="$2"
  gpg --batch --homedir "$tmphome" --import "$keyfile" >/dev/null 2>&1 || return 1
  gpg --batch --homedir "$tmphome" --list-keys --with-colons 2>/dev/null \
    | awk -F: '$1=="pub"{want=1} $1=="fpr" && want==1{print $10; want=0}'
}

# ---------------------------------------------------------------------------
# 0. Optional clean + pre-seed minimal configuration (preserved incrementally)
# ---------------------------------------------------------------------------
if [[ $CLEAN -eq 1 && $DRY_RUN -eq 0 ]]; then
  log "Removing existing metadata + keyring for a full bootstrap..."
  rm -f "$META" "$KEYRING"
fi

if [[ ! -f "$META" ]]; then
  if [[ $DRY_RUN -eq 1 ]]; then
    log "(dry-run) would pre-seed $META with verify-metadata/signatures + armored keyring config"
  else
    log "Pre-seeding minimal verification configuration..."
    cat > "$META" <<'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
   <configuration>
      <verify-metadata>true</verify-metadata>
      <verify-signatures>true</verify-signatures>
      <keyring-format>armored</keyring-format>
      <trusted-artifacts>
         <trust file=".*-javadoc[.]jar" regex="true" reason="IDEs fetch javadoc automatically"/>
         <trust file=".*-sources[.]jar" regex="true" reason="IDEs fetch sources automatically"/>
      </trusted-artifacts>
   </configuration>
   <components/>
</verification-metadata>
XMLEOF
  fi
fi

# ---------------------------------------------------------------------------
# 1. Bootstrap / update verification-metadata.xml + export keyring
# ---------------------------------------------------------------------------
GRADLE_ARGS=(--no-daemon)
if [[ $OFFLINE -eq 1 ]]; then
  GRADLE_ARGS+=(--offline)
else
  GRADLE_ARGS+=(--refresh-dependencies)
fi

cd "$ROOT_DIR"
if [[ $OFFLINE -eq 1 ]]; then
  warn "--offline: skipping metadata regeneration (network needed for checksums/signatures)"
elif [[ $DRY_RUN -eq 1 ]]; then
  log "(dry-run) would run: $GRADLE_BIN ${GRADLE_ARGS[*]} --write-verification-metadata sha256,sha512,pgp --export-keys -I scripts/resolve-all.init.gradle resolveAllForVerification"
else
  log "Generating verification metadata (sha256,sha512,pgp) + exporting keyring..."
  "$GRADLE_BIN" "${GRADLE_ARGS[@]}" --write-verification-metadata "sha256,sha512,pgp" \
    --export-keys -I "$INIT" resolveAllForVerification
fi

# ---------------------------------------------------------------------------
# 2. Merge extra keyrings (documented flow: keys live in verification-keyring.keys)
# ---------------------------------------------------------------------------
if [[ ${#EXTRA_KEYRINGS[@]} -gt 0 ]]; then
  for kr in "${EXTRA_KEYRINGS[@]}"; do
    [[ -f "$kr" ]] || die "--keyring file not found: $kr"
    grep -q "BEGIN PGP PUBLIC KEY BLOCK" "$kr" || die "--keyring is not an armored public key file: $kr"
  done
  if [[ $DRY_RUN -eq 1 ]]; then
    log "(dry-run) would merge ${#EXTRA_KEYRINGS[@]} extra keyring(s) into gradle/verification-keyring.keys"
  elif [[ $HAVE_GPG -eq 1 ]]; then
    log "Merging extra keyring(s) (deduplicated by fingerprint)..."
    new_tmhome; tmphome="$TMP_HOME"; trap 'rm -rf "$TMP_HOME"' EXIT
    [[ -f "$KEYRING" ]] && gpg --batch --homedir "$tmphome" --import "$KEYRING" >/dev/null 2>&1 || true
    for kr in "${EXTRA_KEYRINGS[@]}"; do
      gpg --batch --homedir "$tmphome" --import "$kr" >/dev/null 2>&1 || warn "could not import $kr"
    done
    gpg --batch --homedir "$tmphome" --armor --export > "$KEYRING"
    rm -rf "$tmphome"; trap - EXIT
    echo "updated $KEYRING"
  else
    log "Appending extra keyring(s) without dedupe (no gpg available)..."
    for kr in "${EXTRA_KEYRINGS[@]}"; do cat "$kr" >> "$KEYRING"; done
  fi
fi

# ---------------------------------------------------------------------------
# 3. Per-key exports for audit / offline re-import
# ---------------------------------------------------------------------------
if [[ $DRY_RUN -eq 1 ]]; then
  log "(dry-run) would export per-key files to $KEYS_DIR/"
elif [[ $HAVE_GPG -eq 0 ]]; then
  warn "skipping per-key export (no gpg)"
elif [[ ! -f "$KEYRING" ]]; then
  warn "skipping per-key export (no $KEYRING yet — run without --offline first)"
else
  log "Exporting per-key files..."
  mkdir -p "$KEYS_DIR"
  new_tmhome; tmphome="$TMP_HOME"
  mapfile -t FPRS < <(list_fprs "$tmphome" "$KEYRING")
  for fpr in "${FPRS[@]}"; do
    gpg --batch --homedir "$tmphome" --armor --export "$fpr" > "$KEYS_DIR/$fpr.asc" 2>/dev/null \
      || warn "could not export $fpr"
  done
  # prune stale per-key files (keys removed from the keyring)
  for existing in "$KEYS_DIR"/*.asc; do
    [[ -e "$existing" ]] || continue
    base="$(basename "$existing" .asc)"
    keep=0
    for fpr in "${FPRS[@]}"; do [[ "$fpr" == "$base" ]] && { keep=1; break; }; done
    [[ $keep -eq 0 ]] && rm -f "$existing" && echo "pruned stale key file $(basename "$existing")"
  done
  rm -rf "$tmphome"
  echo "exported ${#FPRS[@]} key file(s) to $KEYS_DIR/"
fi

# ---------------------------------------------------------------------------
# 4. Summary
# ---------------------------------------------------------------------------
if [[ -f "$META" ]]; then
  log "Verification metadata: $META"
  echo "  verified components : $(count_matches '<component[ >]' "$META")"
  echo "  trusted pgp keys    : $(count_matches '<trusted-key[ >]' "$META")"
  echo "  ignored keys        : $(count_matches '<ignored-key[ >]' "$META")"
  echo "  trusted artifacts   : $(count_matches '<trust[ >]' "$META")"
  echo "  x509 entries        : $(count_matches '<certificate[ >]' "$META") (Gradle does not verify X.509; checksums cover those artifacts)"
  [[ -f "$KEYRING" ]] && echo "  keyring             : $KEYRING"
else
  [[ $DRY_RUN -eq 1 ]] || die "verification metadata was not generated ($META). Re-run without --offline."
fi

if [[ $DRY_RUN -eq 0 ]]; then
  cat <<'EOF'

Next steps:
  1. Review: git diff gradle/verification-metadata.xml
     For each entry with origin="Generated by Gradle", verify the checksum
     against an independent source, then mark it origin="Verified".
  2. Commit: gradle/verification-metadata.xml + gradle/verification-keyring.keys
     + gradle/verification-keys/ (locks + TOML from refresh-locks.sh too).
  3. Enforce in CI: add --dependency-verification=strict to the Gradle
     invocation in .github/workflows so tampered artifacts fail the build.
EOF
fi
