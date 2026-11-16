#!/usr/bin/env bash
# Fase 1 — rclone-synkronisering mellem lokal mappe og gdrive:Projektstyring
#
# Brug:
#   ./scripts/sync.sh check        # verificer forbindelse (rclone lsd)
#   ./scripts/sync.sh push         # envejs: lokal -> Drive (standard)
#   ./scripts/sync.sh pull         # envejs: Drive -> lokal
#   ./scripts/sync.sh bisync       # tovejs (kør første gang med --resync)
#   DRY_RUN=1 ./scripts/sync.sh push   # vis hvad der ville ske
#
# Konfiguration via miljøvariabler (eller ret standardværdierne herunder):
LOCAL_DIR="${LOCAL_DIR:-$HOME/Projektstyring}"
REMOTE="${REMOTE:-gdrive:Projektstyring}"
LOG_DIR="${LOG_DIR:-$HOME/.local/state/projektstyring}"
MAX_RETRIES="${MAX_RETRIES:-5}"
RETRY_WAIT="${RETRY_WAIT:-30}"   # sekunder, fordobles ved hvert forsøg

set -uo pipefail
mkdir -p "$LOG_DIR" "$LOCAL_DIR"
LOG_FILE="$LOG_DIR/sync-$(date +%Y-%m-%d).log"
STATUS_FILE="$LOG_DIR/last_status.json"
LOCK_FILE="$LOG_DIR/sync.lock"

log() { printf '%s [%s] %s\n' "$(date '+%F %T')" "$1" "$2" | tee -a "$LOG_FILE"; }

write_status() {  # $1=ok|fail  $2=mode  $3=besked
  printf '{"status":"%s","mode":"%s","message":"%s","time":"%s"}\n' \
    "$1" "$2" "$3" "$(date -Iseconds)" > "$STATUS_FILE"
}

command -v rclone >/dev/null || { log ERROR "rclone er ikke installeret"; exit 127; }

# Undgå at to synkroniseringer kører samtidig
exec 9>"$LOCK_FILE"
if ! flock -n 9; then log WARN "En anden synkronisering kører allerede — springer over"; exit 0; fi

COMMON=(--log-file "$LOG_FILE" --log-level INFO --stats-one-line --stats 30s
        --retries 3 --low-level-retries 10 --exclude ".git/**" --exclude "*.tmp"
        --exclude "node_modules/**" --exclude "__pycache__/**")
[[ "${DRY_RUN:-0}" == "1" ]] && COMMON+=(--dry-run)

wait_for_network() {
  local i=0
  until rclone lsd "$REMOTE" >/dev/null 2>&1; do
    i=$((i+1))
    if (( i > MAX_RETRIES )); then return 1; fi
    local wait=$(( RETRY_WAIT * (2 ** (i-1)) ))
    log WARN "Ingen forbindelse til $REMOTE (forsøg $i/$MAX_RETRIES) — venter ${wait}s"
    sleep "$wait"
  done
}

run_with_retry() {  # $@ = rclone-kommando
  local i=1
  while (( i <= MAX_RETRIES )); do
    rclone "$@" "${COMMON[@]}"
    local code=$?
    if (( code == 0 )); then return 0; fi
    log WARN "rclone fejlede (exit $code, forsøg $i/$MAX_RETRIES)"
    wait_for_network || return "$code"
    i=$((i+1))
  done
  return 1
}

MODE="${1:-push}"; shift || true
log INFO "Start: mode=$MODE lokal=$LOCAL_DIR remote=$REMOTE"

case "$MODE" in
  check)
    if rclone lsd "$REMOTE"; then log INFO "Forbindelse OK"; write_status ok check "forbindelse OK"; exit 0
    else log ERROR "Kan ikke læse $REMOTE — kør 'rclone config reconnect gdrive:'"; write_status fail check "auth/netværk"; exit 1; fi ;;
  push)   wait_for_network || { write_status fail push "ingen forbindelse"; exit 1; }
          run_with_retry sync "$LOCAL_DIR" "$REMOTE" --backup-dir "${REMOTE}_backup/$(date +%F_%H%M)" "$@" ;;
  pull)   wait_for_network || { write_status fail pull "ingen forbindelse"; exit 1; }
          # --backup-dir: lokalt slettede/overskrevne filer kan gendannes
          run_with_retry sync "$REMOTE" "$LOCAL_DIR" --backup-dir "$LOG_DIR/local-trash/$(date +%F_%H%M)" "$@" ;;
  bisync) wait_for_network || { write_status fail bisync "ingen forbindelse"; exit 1; }
          run_with_retry bisync "$LOCAL_DIR" "$REMOTE" --check-access=false --resilient --recover "$@" ;;
  *) echo "Ukendt mode: $MODE (brug check|push|pull|bisync)"; exit 2 ;;
esac

if [[ $? -eq 0 ]]; then log INFO "Færdig: $MODE OK"; write_status ok "$MODE" "synkroniseret"
else log ERROR "Færdig: $MODE FEJLEDE efter $MAX_RETRIES forsøg"; write_status fail "$MODE" "fejlede"; exit 1; fi
