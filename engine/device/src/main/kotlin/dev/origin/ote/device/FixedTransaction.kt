package dev.origin.ote.device

import dev.origin.ote.root.RootManager

/** Generates only our fixed transaction program. Packs never supply shell text. */
internal object FixedTransaction {
 private fun q(s:String) = RootManager.quote(s)
 fun guard():String = GUARD
 private val GUARD = """
set -eu
set -C
PATH=/system/bin:/system/xbin
export PATH
DIR={{DIR}}
TARGET={{TARGET}}
DESC={{DESC}}
APK={{APK}}
LIB={{LIB}}
CTX={{CONTEXT}}
fail() { echo "STOP:${'$'}*" >&2; exit 1; }
hash_of() { h=${'$'}(sha256sum "${'$'}1") || return 1; printf '%s' "${'$'}{h%% *}"; }
hash_is() { [ -f "${'$'}1" ] && [ ! -L "${'$'}1" ] && [ "${'$'}(hash_of "${'$'}1")" = "${'$'}2" ]; }
meta() {
 [ -f "${'$'}1" ] && [ ! -L "${'$'}1" ] || return 1
 [ "${'$'}(stat -c '%u:%g:%a' "${'$'}1")" = 10255:10255:777 ] || return 1
 label=${'$'}(ls -Zd "${'$'}1") || return 1
 case " ${'$'}label " in *" ${'$'}CTX "*) return 0;; *) return 1;; esac
}
guard() {
 [ "${'$'}(getprop ro.product.model)" = V2453A ] && [ "${'$'}(getprop ro.build.version.sdk)" = 36 ] || fail device_mismatch
 for parent in /data /data/bbkcore /data/bbkcore/bbkthemeres /data/bbkcore/bbkthemeres/novoland /data/bbkcore/bbkthemeres/novoland/103 /data/bbkcore/bbkthemeres/novoland/103/进化 "${'$'}DIR"; do
  [ -d "${'$'}parent" ] && [ ! -L "${'$'}parent" ] || fail parent_mismatch
 done
 hash_is "${'$'}DESC" {{DESC_HASH}} && meta "${'$'}DESC" || fail description_mismatch
 hash_is "${'$'}APK" {{APK_HASH}} && hash_is "${'$'}LIB" {{LIB_HASH}} || fail engine_mismatch
 meta "${'$'}TARGET" || fail metadata_mismatch
}

# Call only when an operation needs a stable resource directory handle. FD8 is
# inherited by external tools; unsupported /proc/stat behavior fails closed.
anchor_resource() {
 original_dir_inode=${'$'}(stat -c '%d:%i' "${'$'}DIR") || fail directory_inode_unknown
 CDPATH= cd -P "${'$'}DIR" || fail directory_open_failed
 [ "${'$'}(stat -c '%d:%i' .)" = "${'$'}original_dir_inode" ] || fail directory_changed
 exec 8< .
 ROOT_FD=/proc/self/fd/8
 [ "${'$'}(stat -L -c '%d:%i' "${'$'}ROOT_FD")" = "${'$'}original_dir_inode" ] || fail directory_fd_unsupported
 TARGET=${'$'}ROOT_FD/animation.zip
}
anchor_ok() {
 [ -d "${'$'}DIR" ] && [ ! -L "${'$'}DIR" ] || return 1
 [ "${'$'}(stat -c '%d:%i' "${'$'}DIR")" = "${'$'}original_dir_inode" ] || return 1
 [ "${'$'}(stat -L -c '%d:%i' "${'$'}ROOT_FD")" = "${'$'}original_dir_inode" ]
}
guard
""".trimIndent()
  .replace("{{DIR}}",q(VerifiedProfile.DIR)).replace("{{TARGET}}",q(VerifiedProfile.TARGET))
  .replace("{{DESC}}",q(VerifiedProfile.DESCRIPTION)).replace("{{APK}}",q(VerifiedProfile.APK))
  .replace("{{LIB}}",q(VerifiedProfile.LIBRARY)).replace("{{CONTEXT}}",q(VerifiedProfile.CONTEXT))
  .replace("{{DESC_HASH}}",q(VerifiedProfile.DESCRIPTION_HASH)).replace("{{APK_HASH}}",q(VerifiedProfile.APK_HASH))
  .replace("{{LIB_HASH}}",q(VerifiedProfile.LIBRARY_HASH))

 fun replace(id:String, before:String, wanted:String, payload:String, undo:String, requiredKb:Long, applying:Boolean):String {
  require(id.matches(Regex("[a-f0-9]{32}")))
  require(listOf(before,wanted).all { it.matches(Regex("[a-f0-9]{64}")) })
  require(requiredKb > 0)
  return guard()+"\n"+TRANSACTION
   .replace("{{ID}}",q(id)).replace("{{BEFORE}}",q(before)).replace("{{WANTED}}",q(wanted))
   .replace("{{PAYLOAD}}",q(payload)).replace("{{UNDO}}",q(undo)).replace("{{REQUIRED_KB}}",requiredKb.toString())
   .replace("{{APPLYING}}",if(applying) "1" else "0")
 }
 private val TRANSACTION = """
ID={{ID}}
BEFORE={{BEFORE}}
WANTED={{WANTED}}
PAYLOAD={{PAYLOAD}}
UNDO={{UNDO}}
APPLYING={{APPLYING}}
# Fail before creating anything if this shell/runtime cannot provide the anchors
# and non-directory-follow rename that the transaction requires.
for tool in mv stat mkdir cat chown chmod chcon rm rmdir df awk; do
 command -v "${'$'}tool" >/dev/null 2>&1 || fail runtime_tool_missing
done
mv_help=${'$'}(mv --help 2>&1) || fail mv_help_unavailable
case "${'$'}mv_help" in *'-T'*) ;; *) fail mv_no_target_directory_unsupported;; esac
anchor_resource
LOCK_ENTRY=${'$'}ROOT_FD/.ote-lock
STAGE_ENTRY=${'$'}ROOT_FD/.ote-stage-${'$'}ID
LOCK=/proc/self/fd/7
STAGE=/proc/self/fd/9
private_entry() {
 [ -d "${'$'}1" ] && [ ! -L "${'$'}1" ] && [ "${'$'}(stat -c '%u:%g:%a' "${'$'}1")" = 0:0:700 ]
}
private_fd() {
 [ -d "${'$'}1" ] && [ "${'$'}(stat -L -c '%u:%g:%a' "${'$'}1")" = 0:0:700 ] && [ "${'$'}(stat -L -c '%d:%i' "${'$'}1")" = "${'$'}2" ]
}
entry_matches() {
 private_entry "${'$'}1" && [ "${'$'}(stat -c '%d:%i' "${'$'}1")" = "${'$'}2" ]
}
anchor_ok || fail directory_changed
guard
hash_is "${'$'}PAYLOAD" "${'$'}WANTED" && hash_is "${'$'}UNDO" "${'$'}BEFORE" || fail payload_mismatch
hash_is "${'$'}TARGET" "${'$'}BEFORE" || fail source_changed
if [ "${'$'}APPLYING" = 1 ]; then
 [ "${'$'}(settings --user current get system udfp_unlock_effect_style)" = 100 ] || fail select_none_first
fi
free_kb=${'$'}(df -Pk "${'$'}ROOT_FD" | awk 'NR==2 {print ${'$'}4}') || fail space_unknown
case "${'$'}free_kb" in ''|*[!0-9]*) fail space_unknown;; esac
[ "${'$'}free_kb" -ge {{REQUIRED_KB}} ] || fail insufficient_space
umask 077
mkdir -m 700 "${'$'}LOCK_ENTRY" || fail transaction_busy
LOCK_CREATED=1
LOCK_OPENED=0
LOCK_INODE=''
STAGE_CREATED=0
STAGE_OPENED=0
STAGE_INODE=''
TOKEN_OWNED=0
PID_OWNED=0
NEXT_OWNED=0
UNDO_OWNED=0
PUBLISHED=0
SUCCESS=0
# Unknown entries are never removed, even if a created private directory was
# replaced by another root-owned directory before its descriptor was opened.
owned_stage_entries() {
 for entry in "${'$'}STAGE"/* "${'$'}STAGE"/.[!.]* "${'$'}STAGE"/..?*; do
  [ -e "${'$'}entry" ] || [ -L "${'$'}entry" ] || continue
  leaf=${'$'}{entry##*/}
  case "${'$'}leaf" in
   next.zip) [ "${'$'}NEXT_OWNED" = 1 ] || return 1;;
   undo.zip) [ "${'$'}UNDO_OWNED" = 1 ] || return 1;;
   *) return 1;;
  esac
  [ -f "${'$'}entry" ] && [ ! -L "${'$'}entry" ] || return 1
 done
}
owned_lock_entries() {
 for entry in "${'$'}LOCK"/* "${'$'}LOCK"/.[!.]* "${'$'}LOCK"/..?*; do
  [ -e "${'$'}entry" ] || [ -L "${'$'}entry" ] || continue
  leaf=${'$'}{entry##*/}
  case "${'$'}leaf" in
   token) [ "${'$'}TOKEN_OWNED" = 1 ] || return 1;;
   pid) [ "${'$'}PID_OWNED" = 1 ] || return 1;;
   *) return 1;;
  esac
  [ -f "${'$'}entry" ] && [ ! -L "${'$'}entry" ] || return 1
 done
}
clean() {
 anchor_ok || return 1
 if [ "${'$'}STAGE_CREATED" = 1 ]; then
  [ "${'$'}STAGE_OPENED" = 1 ] && private_fd "${'$'}STAGE" "${'$'}STAGE_INODE" && entry_matches "${'$'}STAGE_ENTRY" "${'$'}STAGE_INODE" || return 1
  owned_stage_entries || return 1
  for leaf in next.zip undo.zip; do
   if [ -e "${'$'}STAGE/${'$'}leaf" ] || [ -L "${'$'}STAGE/${'$'}leaf" ]; then
    rm -f "${'$'}STAGE/${'$'}leaf" || return 1
   fi
  done
  entry_matches "${'$'}STAGE_ENTRY" "${'$'}STAGE_INODE" || return 1
  rmdir "${'$'}STAGE_ENTRY" || return 1
  STAGE_CREATED=0
  exec 9<&-
  STAGE_OPENED=0
 fi
 if [ "${'$'}LOCK_CREATED" = 1 ]; then
  [ "${'$'}LOCK_OPENED" = 1 ] && private_fd "${'$'}LOCK" "${'$'}LOCK_INODE" && entry_matches "${'$'}LOCK_ENTRY" "${'$'}LOCK_INODE" || return 1
  owned_lock_entries || return 1
  for leaf in token pid; do
   if [ -e "${'$'}LOCK/${'$'}leaf" ] || [ -L "${'$'}LOCK/${'$'}leaf" ]; then
    rm -f "${'$'}LOCK/${'$'}leaf" || return 1
   fi
  done
  entry_matches "${'$'}LOCK_ENTRY" "${'$'}LOCK_INODE" || return 1
  rmdir "${'$'}LOCK_ENTRY" || return 1
  LOCK_CREATED=0
  exec 7<&-
  LOCK_OPENED=0
 fi
}
finish() {
 result=${'$'}?
 trap - 0 1 2 15
 if [ "${'$'}PUBLISHED" = 1 ] && [ "${'$'}SUCCESS" != 1 ]; then
  if hash_is "${'$'}TARGET" "${'$'}BEFORE" && meta "${'$'}TARGET"; then :
  elif [ "${'$'}STAGE_OPENED" = 1 ] && private_fd "${'$'}STAGE" "${'$'}STAGE_INODE" && hash_is "${'$'}TARGET" "${'$'}WANTED" && hash_is "${'$'}STAGE/undo.zip" "${'$'}BEFORE" && meta "${'$'}STAGE/undo.zip"; then
   mv -f -T "${'$'}STAGE/undo.zip" "${'$'}TARGET" && hash_is "${'$'}TARGET" "${'$'}BEFORE" && meta "${'$'}TARGET" || { echo STOP:rollback_uncertain >&2; exit 1; }
  else echo STOP:rollback_refused >&2; exit 1
  fi
 fi
 clean || { echo STOP:cleanup_uncertain >&2; exit 1; }
 exit "${'$'}result"
}
trap finish 0
trap 'exit 130' 1 2 15
LOCK_INODE=${'$'}(stat -c '%d:%i' "${'$'}LOCK_ENTRY") || fail lock_inode_unknown
private_entry "${'$'}LOCK_ENTRY" || fail lock_metadata
exec 7< "${'$'}LOCK_ENTRY"
LOCK_OPENED=1
private_fd "${'$'}LOCK" "${'$'}LOCK_INODE" && entry_matches "${'$'}LOCK_ENTRY" "${'$'}LOCK_INODE" || fail lock_entry_changed
[ ! -e "${'$'}LOCK/token" ] && [ ! -L "${'$'}LOCK/token" ] || fail unknown_lock_leaf
TOKEN_OWNED=1
printf '%s' "${'$'}ID" > "${'$'}LOCK/token" || fail lock_token_failed
[ ! -e "${'$'}LOCK/pid" ] && [ ! -L "${'$'}LOCK/pid" ] || fail unknown_lock_leaf
PID_OWNED=1
printf '%s' "${'$'}${'$'}" > "${'$'}LOCK/pid" || fail lock_pid_failed
mkdir -m 700 "${'$'}STAGE_ENTRY" || fail stage_exists
STAGE_CREATED=1
STAGE_INODE=${'$'}(stat -c '%d:%i' "${'$'}STAGE_ENTRY") || fail stage_inode_unknown
private_entry "${'$'}STAGE_ENTRY" || fail stage_metadata
exec 9< "${'$'}STAGE_ENTRY"
STAGE_OPENED=1
private_fd "${'$'}STAGE" "${'$'}STAGE_INODE" && entry_matches "${'$'}STAGE_ENTRY" "${'$'}STAGE_INODE" || fail stage_entry_changed
source_inode=${'$'}(stat -c '%d:%i' "${'$'}TARGET") || fail inode_unknown
prepare() {
 cat "${'$'}1" > "${'$'}2" || return 1
 chown 10255:10255 "${'$'}2" && chmod 777 "${'$'}2" && chcon "${'$'}CTX" "${'$'}2" || return 1
 hash_is "${'$'}2" "${'$'}3" && meta "${'$'}2"
}
[ ! -e "${'$'}STAGE/next.zip" ] && [ ! -L "${'$'}STAGE/next.zip" ] || fail unknown_stage_leaf
NEXT_OWNED=1
prepare "${'$'}PAYLOAD" "${'$'}STAGE/next.zip" "${'$'}WANTED" || fail prepare_failed
[ ! -e "${'$'}STAGE/undo.zip" ] && [ ! -L "${'$'}STAGE/undo.zip" ] || fail unknown_stage_leaf
UNDO_OWNED=1
prepare "${'$'}UNDO" "${'$'}STAGE/undo.zip" "${'$'}BEFORE" || fail undo_failed
guard
anchor_ok && entry_matches "${'$'}LOCK_ENTRY" "${'$'}LOCK_INODE" && entry_matches "${'$'}STAGE_ENTRY" "${'$'}STAGE_INODE" || fail anchor_entry_changed
[ "${'$'}(stat -c '%d:%i' "${'$'}TARGET")" = "${'$'}source_inode" ] && hash_is "${'$'}TARGET" "${'$'}BEFORE" || fail source_changed
if [ "${'$'}APPLYING" = 1 ]; then
 [ "${'$'}(settings --user current get system udfp_unlock_effect_style)" = 100 ] || fail select_none_first
fi
PUBLISHED=1
mv -f -T "${'$'}STAGE/next.zip" "${'$'}TARGET" || fail rename_failed
guard
anchor_ok && entry_matches "${'$'}LOCK_ENTRY" "${'$'}LOCK_INODE" && entry_matches "${'$'}STAGE_ENTRY" "${'$'}STAGE_INODE" || fail anchor_entry_changed
hash_is "${'$'}TARGET" "${'$'}WANTED" || fail postcheck_failed
SUCCESS=1
clean || fail cleanup_uncertain
trap - 0 1 2 15
printf '%s\n' OTE_COMMIT_OK
""".trimIndent()
}
