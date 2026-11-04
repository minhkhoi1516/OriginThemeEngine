# Read-only metadata discovery. Roots are substituted by OTE-owned code only.
# Limits here are discovery budgets, not Vivo engine limits.
set -eu
AOD_ROOT=@AOD_ROOT@
FP_ROOT=@FP_ROOT@
COUNT=0
LIMITED=0
fail() { printf '%s\n' CATALOG_READ_FAILED >&2; exit 1; }
root_ok() {
  [ ! -L "$1" ] || fail
  if [ ! -e "$1" ]; then return 1; fi
  [ -d "$1" ] && [ -r "$1" ] && [ -x "$1" ] || fail
  [ "$(readlink -f "$1")" = "$1" ] || fail
}
emit() {
  [ "$COUNT" -lt 96 ] || { LIMITED=1; return; }
  [ ! -L "$2" ] && [ -f "$2" ] || return 0
  [ "$(readlink -f "$2")" = "$2" ] || return 0
  [ -r "$2" ] || fail
  [ "${#2}" -le 360 ] || { LIMITED=1; return; }
  SIZE=$(stat -c '%s' "$2") || fail
  case "$SIZE" in ''|*[!0-9]*) fail;; esac
  NAME=$(printf '%s' "$2" | base64 | tr -d '\n') || fail
  printf 'ITEM\t%s\t%s\t%s\n' "$1" "$SIZE" "$NAME"
  COUNT=$((COUNT + 1))
}
if root_ok "$AOD_ROOT"; then
  printf 'ROOT\tAOD\tPRESENT\n'
  for FILE in "$AOD_ROOT"/*.itz; do emit AOD "$FILE"; done
else printf 'ROOT\tAOD\tUNAVAILABLE\n'; fi
if root_ok "$FP_ROOT"; then
  printf 'ROOT\tFINGERPRINT\tPRESENT\n'
  for FILE in "$FP_ROOT"/*/content/animation.zip; do emit FINGERPRINT "$FILE"; done
else printf 'ROOT\tFINGERPRINT\tUNAVAILABLE\n'; fi
printf 'END\t%s\t%s\n' "$COUNT" "$LIMITED"
