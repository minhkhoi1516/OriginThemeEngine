package dev.origin.ote.device

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.origin.ote.data.ThemeRepository
import dev.origin.ote.model.*
import dev.origin.ote.pack.PackCodec
import dev.origin.ote.root.RootManager
import dev.origin.ote.root.RootResult
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable internal data class TransactionRecord(
 val id:String, val beforeHash:String, val payloadHash:String, val timestamp:Long,
 val phase:String = "pending", val profile:String = VerifiedProfile.ID, val operation:String = "apply",
)

/** Only the verified resource profile is writable. This class never edits APKs or settings. */
@Singleton class DeviceEngine @Inject constructor(
 @ApplicationContext private val context:Context,
 private val root:RootManager,
 private val repository:ThemeRepository,
 private val codec:PackCodec,
) {
 private val mutex = Mutex()
 private val json = Json { encodeDefaults = true }
 private val stateDirectory get() = File(context.filesDir,"device-state").apply { mkdirs() }
 private val journalFile get() = File(stateDirectory,"active.json")
 private val backupPointer get() = File(stateDirectory,"latest-backup.json")
 private fun q(s:String) = RootManager.quote(s)
 private fun folder(id:String):File {
  require(id.matches(Regex("[a-f0-9]{32}"))) { "journal_id_invalid" }
  return File(stateDirectory,id).also { require(it.canonicalFile.parentFile == stateDirectory.canonicalFile) }
 }
 fun deviceInfo():DeviceInfo = DeviceInfo(Build.MODEL,Build.VERSION.SDK_INT,Build.DISPLAY,Build.MANUFACTURER,
  if(Build.MODEL == VerifiedProfile.MODEL && Build.VERSION.SDK_INT == VerifiedProfile.SDK) VerifiedProfile.ID else null)
 suspend fun compatibility():CompatibilityResult = withContext(Dispatchers.IO) {
  if(deviceInfo().supportedProfile == null) return@withContext CompatibilityResult(false,checks=listOf("device_not_profiled"))
  val r=root.execute(FixedTransaction.guard()+"\nprintf '%s\\n' OTE_PROFILE_OK")
  CompatibilityResult(r.succeeded && r.stdout.lineSequence().any { it=="OTE_PROFILE_OK" },
   VerifiedProfile.ID,listOf(if(r.succeeded) "profile_hashes_and_metadata_verified" else detail(r)))
 }
 suspend fun currentStatus():String = withContext(Dispatchers.IO) {
  val r=root.execute(FixedTransaction.guard()+"\nprintf 'FP_STYLE='; settings --user current get system udfp_unlock_effect_style\nprintf 'FP_HASH='; hash_of "+q(VerifiedProfile.TARGET)+"\nprintf '\\nAOD_STYLE='; settings --user current get system screen_off_remind_style\n")
  checked(r); r.stdout.trim()+"\nAOD_WRITE=unsupported_in_mvp\nJOURNAL="+(read(journalFile)?.phase?:"none")
 }
 suspend fun backup():String = withContext(Dispatchers.IO) { mutex.withLock {
  val hash=inspectHash(); requireAllowed(hash)
  val id=newId(); val dir=folder(id); check(dir.mkdir()) { "backup_directory_failed" }
  capture(dir,hash)
  val record=TransactionRecord(id,hash,hash,System.currentTimeMillis(),"backup",operation="backup")
  write(File(dir,"record.json"),record); write(backupPointer,record)
  repository.log("backup","verified", "fingerprint_snapshot_verified")
  "backup_verified:"+id
 } }
 suspend fun apply(id:String):String = withContext(Dispatchers.IO) { mutex.withLock {
  val item=repository.get(id) ?: error("theme_missing")
  check(item.kind==ThemeKind.FINGERPRINT) { "aod_write_not_supported_mvp" }
  val manifest=codec.validateDirectory(repository.directory(id))
  check(manifest.compatibility.sdkMin <= Build.VERSION.SDK_INT &&
   (manifest.compatibility.sdkMax==null || Build.VERSION.SDK_INT<=manifest.compatibility.sdkMax!!)) { "pack_sdk_incompatible" }
  check(manifest.compatibility.models.isEmpty() || Build.MODEL in manifest.compatibility.models) { "pack_model_incompatible" }
  check(manifest.compatibility.profile==null || manifest.compatibility.profile==VerifiedProfile.ID) { "pack_profile_incompatible" }
  val existing=read(journalFile)
  check(existing?.phase !in setOf("pending","uncertain")) { "pending_transaction_restore_first" }
  val before=inspectHash(); requireAllowed(before)
  val style=root.execute("settings --user current get system udfp_unlock_effect_style"); checked(style)
  check(style.stdout.trim()=="100") { "select_none_first" }
  val tx=newId();val dir=folder(tx);check(dir.mkdir()) { "transaction_directory_failed" }
  val payload=File(dir,"payload.zip")
  val wanted=NativeFpBuilder().build(repository.directory(id),manifest,payload)
  check(sha(payload)==wanted) { "generated_payload_changed" }
  if(before==wanted) return@withLock "already_applied"
  capture(dir,before)
  var record=TransactionRecord(tx,before,wanted,System.currentTimeMillis())
  write(File(dir,"record.json"),record);write(journalFile,record);write(backupPointer,record)
  val required=(payload.length()+File(dir,"before.zip").length()+1024*1024)/1024+1
  val result=root.execute(FixedTransaction.replace(tx,before,wanted,payload.absolutePath,File(dir,"before.zip").absolutePath,required,true),60_000)
  if(!result.succeeded || !result.stdout.lineSequence().any { it=="OTE_COMMIT_OK" }) {
   record=record.copy(phase="uncertain");write(journalFile,record)
   repository.log("apply","uncertain",detail(result));error(detail(result))
  }
  check(inspectHash()==wanted) { "postcheck_uncertain" }
  record=record.copy(phase="applied");write(journalFile,record);write(File(dir,"record.json"),record)
  repository.log("apply","verified","fingerprint_asset_verified_ui_reload_required")
  "fingerprint_applied_select_effect_in_settings"
 } }
 suspend fun restore():String = withContext(Dispatchers.IO) { mutex.withLock {
  val active=read(journalFile)
  val record=if(active?.phase in setOf("pending","uncertain")) active else read(backupPointer) ?: active?.takeIf { it.phase!="restored" }
  check(record != null) { "no_local_backup" }
  validateRecord(record)
  recoverStale(record)
  val before=inspectHash();requireAllowed(before)
  val restoringPendingRestore=record.operation=="restore"
  val goalHash=if(restoringPendingRestore) record.payloadHash else record.beforeHash
  val snapshot=File(folder(record.id),if(restoringPendingRestore) "payload.zip" else "before.zip")
  check(snapshot.isFile && sha(snapshot)==goalHash) { "backup_corrupted" }
  if(before==goalHash) {
   write(journalFile,record.copy(phase="restored"));return@withLock "already_restored"
  }
  // Create a NEW capture of current bytes for rollback if this restore fails.
  val tx=newId();val dir=folder(tx);check(dir.mkdir()) { "restore_directory_failed" }
  capture(dir,before)
  val pending=TransactionRecord(tx,before,goalHash,System.currentTimeMillis(),"pending",operation="restore")
  File(dir,"payload.zip").apply { snapshot.copyTo(this,overwrite=false) }
  write(File(dir,"record.json"),pending);write(journalFile,pending)
  val required=(snapshot.length()+File(dir,"before.zip").length()+1024*1024)/1024+1
  val result=root.execute(FixedTransaction.replace(tx,before,goalHash,File(dir,"payload.zip").absolutePath,File(dir,"before.zip").absolutePath,required,false),60_000)
  if(!result.succeeded || !result.stdout.lineSequence().any { it=="OTE_COMMIT_OK" }) {
   write(journalFile,pending.copy(phase="uncertain"));repository.log("restore","uncertain",detail(result));error(detail(result))
  }
  check(inspectHash()==goalHash) { "restore_postcheck_uncertain" }
  write(journalFile,pending.copy(phase="restored"))
  repository.log("restore","verified","previous_fingerprint_snapshot_restored")
  "previous_snapshot_restored_select_effect_in_settings"
 } }
 suspend fun exportLatestBackup(uri:Uri) = withContext(Dispatchers.IO) { mutex.withLock {
  val record=read(backupPointer) ?: error("no_local_backup");validateRecord(record)
  val snapshot=File(folder(record.id),"before.zip");check(sha(snapshot)==record.beforeHash) { "backup_corrupted" }
  val stream=context.contentResolver.openOutputStream(uri,"wt") ?: error("output_unavailable")
  ZipOutputStream(stream).use { z ->
   z.putNextEntry(ZipEntry("snapshot.json"));z.write(json.encodeToString(record).toByteArray());z.closeEntry()
   z.putNextEntry(ZipEntry("animation.zip"));snapshot.inputStream().use { it.copyTo(z) };z.closeEntry()
  }
  repository.log("backup_export","completed","snapshot_exported_via_saf")
 } }
 private suspend fun inspectHash():String {
  val r=root.execute(FixedTransaction.guard()+"\nhash_of "+q(VerifiedProfile.TARGET));checked(r)
  return r.stdout.trim().also { check(it.matches(Regex("[a-f0-9]{64}"))) { "invalid_hash_response" } }
 }
 private fun requireAllowed(hash:String) {
  if(hash in VerifiedProfile.knownHashes) return
  val r=read(journalFile) ?: error("unknown_target_refused")
  validateRecord(r)
  check(hash==r.beforeHash || hash==r.payloadHash) { "unknown_target_refused" }
 }
 private suspend fun capture(dir:File,before:String) {
  check(StatFs(context.filesDir.absolutePath).availableBytes > 64L*1024*1024) { "backup_storage_low" }
  val snapshot=File(dir,"before.zip");check(snapshot.createNewFile()) { "backup_exists" }
  check(snapshot.setReadable(false,false) && snapshot.setWritable(false,false) && snapshot.setReadable(true,true) && snapshot.setWritable(true,true)) { "backup_permission_failed" }
  val fileStat=android.system.Os.stat(snapshot.absolutePath)
  val inode=fileStat.st_dev.toString()+":"+fileStat.st_ino
  val appUid=android.os.Process.myUid()

  // Precreated app-owned private inode: root only writes its bytes, never chmods app data.
  val cmd=FixedTransaction.guard()+"\n[ \"$(hash_of "+q(VerifiedProfile.TARGET)+")\" = "+q(before)+" ] || fail source_changed\n[ -f "+q(snapshot.absolutePath)+" ] && [ ! -L "+q(snapshot.absolutePath)+" ] && [ \"$(stat -c '%d:%i' "+q(snapshot.absolutePath)+")\" = "+q(inode)+" ] && [ \"$(stat -c '%u:%a' "+q(snapshot.absolutePath)+")\" = "+q(appUid.toString()+":600")+" ] || fail snapshot_inode_changed\ncat "+q(VerifiedProfile.TARGET)+" >| "+q(snapshot.absolutePath)+"\nguard\n[ \"$(hash_of "+q(VerifiedProfile.TARGET)+")\" = "+q(before)+" ] || fail source_changed\n"
  val r=root.execute(cmd);checked(r)
  check(sha(snapshot)==before) { "backup_verification_failed" }
 }
 private fun validateRecord(r:TransactionRecord) {
  check(r.operation in setOf("apply","restore","backup") && r.profile==VerifiedProfile.ID && r.beforeHash.matches(Regex("[a-f0-9]{64}")) && r.payloadHash.matches(Regex("[a-f0-9]{64}"))) { "journal_invalid" }
  val dir=folder(r.id);val snapshot=File(dir,"before.zip")
  check(snapshot.canonicalFile.parentFile==dir.canonicalFile && snapshot.isFile && sha(snapshot)==r.beforeHash) { "backup_corrupted" }
  if(r.phase!="backup" && r.payloadHash!=r.beforeHash) check(File(dir,"payload.zip").let { it.isFile && sha(it)==r.payloadHash }) { "journal_payload_corrupted" }
 }
 private suspend fun recoverStale(r:TransactionRecord) {
  validateRecord(r)
  val script=FixedTransaction.guard()+"\n"+RECOVER
   .replace("{{ID}}",q(r.id)).replace("{{BEFORE}}",q(r.beforeHash)).replace("{{AFTER}}",q(r.payloadHash))
  checked(root.execute(script))
 }
 private fun read(file:File):TransactionRecord? {
  val atomic=AtomicFile(file)
  if(!atomic.baseFile.exists() && !File(file.path+".bak").exists()) return null
  return atomic.openRead().use { json.decodeFromString<TransactionRecord>(it.bufferedReader().readText().also { text -> check(text.length<8192) { "journal_oversize" } }) }
 }
 private fun write(file:File,r:TransactionRecord) {
  val atomic=AtomicFile(file);val out=atomic.startWrite()
  try { out.write(json.encodeToString(r).toByteArray());atomic.finishWrite(out) } catch(t:Throwable) { atomic.failWrite(out);throw t }
 }
 private fun checked(r:RootResult) { check(r.succeeded) { detail(r) } }
 private fun detail(r:RootResult):String = if(r.timedOut) "root_timeout_operation_uncertain" else r.stderr.lineSequence().lastOrNull { it.startsWith("STOP:") }?.take(160) ?: "root_command_failed_${r.exitCode}"
 private fun newId()=UUID.randomUUID().toString().replace("-","")
 private fun sha(f:File):String = f.inputStream().use { input -> val d=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)};d.digest().joinToString(""){"%02x".format(it)} }
 private val RECOVER = """
ID={{ID}}
BEFORE={{BEFORE}}
AFTER={{AFTER}}
anchor_resource
anchor_ok || fail recovery_directory_changed
LOCK_ENTRY=${'$'}ROOT_FD/.ote-lock
STAGE_ENTRY=${'$'}ROOT_FD/.ote-stage-${'$'}ID
if [ -e "${'$'}LOCK_ENTRY" ] || [ -L "${'$'}LOCK_ENTRY" ]; then
 [ -d "${'$'}LOCK_ENTRY" ] && [ ! -L "${'$'}LOCK_ENTRY" ] && [ "${'$'}(stat -c '%u:%g:%a' "${'$'}LOCK_ENTRY")" = 0:0:700 ] || fail lock_metadata
 lock_inode=${'$'}(stat -c '%d:%i' "${'$'}LOCK_ENTRY") || fail lock_metadata
 exec 7< "${'$'}LOCK_ENTRY"
 LOCK=/proc/self/fd/7
 [ "${'$'}(stat -L -c '%d:%i' "${'$'}LOCK")" = "${'$'}lock_inode" ] && [ "${'$'}(stat -L -c '%u:%g:%a' "${'$'}LOCK")" = 0:0:700 ] || fail lock_fd_changed
 [ -f "${'$'}LOCK/token" ] && [ ! -L "${'$'}LOCK/token" ] && [ "${'$'}(stat -c '%u:%a' "${'$'}LOCK/token")" = 0:600 ] && [ "${'$'}(cat "${'$'}LOCK/token")" = "${'$'}ID" ] || fail foreign_lock
 [ -f "${'$'}LOCK/pid" ] && [ ! -L "${'$'}LOCK/pid" ] && [ "${'$'}(stat -c '%u:%a' "${'$'}LOCK/pid")" = 0:600 ] || fail lock_pid_invalid
 pid=${'$'}(cat "${'$'}LOCK/pid")
 case "${'$'}pid" in ''|*[!0-9]*) fail lock_pid_invalid;; esac
 if kill -0 "${'$'}pid" 2>/dev/null; then fail transaction_still_running; fi
 current=${'$'}(hash_of "${'$'}TARGET")
 [ "${'$'}current" = "${'$'}BEFORE" ] || [ "${'$'}current" = "${'$'}AFTER" ] || fail recovery_unknown_target
 if [ -e "${'$'}STAGE_ENTRY" ] || [ -L "${'$'}STAGE_ENTRY" ]; then
  [ -d "${'$'}STAGE_ENTRY" ] && [ ! -L "${'$'}STAGE_ENTRY" ] && [ "${'$'}(stat -c '%u:%g:%a' "${'$'}STAGE_ENTRY")" = 0:0:700 ] || fail stage_metadata
  stage_inode=${'$'}(stat -c '%d:%i' "${'$'}STAGE_ENTRY") || fail stage_metadata
  exec 9< "${'$'}STAGE_ENTRY"
  STAGE=/proc/self/fd/9
  [ "${'$'}(stat -L -c '%d:%i' "${'$'}STAGE")" = "${'$'}stage_inode" ] && [ "${'$'}(stat -L -c '%u:%g:%a' "${'$'}STAGE")" = 0:0:700 ] || fail stage_fd_changed
  for leaf in next.zip undo.zip; do
   if [ -e "${'$'}STAGE/${'$'}leaf" ] || [ -L "${'$'}STAGE/${'$'}leaf" ]; then
    meta "${'$'}STAGE/${'$'}leaf" || fail stage_file_metadata
    h=${'$'}(hash_of "${'$'}STAGE/${'$'}leaf")
    [ "${'$'}h" = "${'$'}BEFORE" ] || [ "${'$'}h" = "${'$'}AFTER" ] || fail stage_file_unknown
    rm -f "${'$'}STAGE/${'$'}leaf" || fail stage_cleanup_failed
   fi
  done
  anchor_ok && [ ! -L "${'$'}STAGE_ENTRY" ] && [ "${'$'}(stat -c '%d:%i' "${'$'}STAGE_ENTRY")" = "${'$'}stage_inode" ] || fail stage_entry_changed
  rmdir "${'$'}STAGE_ENTRY" || fail stage_not_empty
 fi
 anchor_ok && [ ! -L "${'$'}LOCK_ENTRY" ] && [ "${'$'}(stat -c '%d:%i' "${'$'}LOCK_ENTRY")" = "${'$'}lock_inode" ] || fail lock_entry_changed
 rm -f "${'$'}LOCK/token" "${'$'}LOCK/pid" || fail lock_cleanup_failed
 rmdir "${'$'}LOCK_ENTRY" || fail lock_not_empty
fi
""".trimIndent()
}
