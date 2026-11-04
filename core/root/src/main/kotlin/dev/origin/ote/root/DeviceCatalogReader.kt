package dev.origin.ote.root

import dev.origin.ote.model.ThemeKind
import java.util.Base64

data class DeviceSourceCandidate(val kind: ThemeKind, val path: String, val bytes: Long) {
    val title: String get() = if (kind == ThemeKind.AOD) path.substringAfterLast('/')
        else path.removeSuffix("/content/animation.zip").substringAfterLast('/')
}
data class DeviceCatalog(val entries: List<DeviceSourceCandidate>, val missing: Set<ThemeKind>, val limited: Boolean)

/** Lists observed locations only. Discovery never grants write eligibility or entitlement. */
class DeviceCatalogReader(private val root: RootManager) {
    suspend fun scan(): DeviceCatalog {
        val template = requireNotNull(javaClass.getResourceAsStream("/ote-catalog.sh"))
            .bufferedReader().use { it.readText() }
        val script = template.replace("@AOD_ROOT@", RootManager.quote(AOD_ROOT))
            .replace("@FP_ROOT@", RootManager.quote(FP_ROOT))
        val result = root.execute(script, 20_000)
        check(result.succeeded) { "catalog_read_failed" }
        return parse(result.stdout)
    }

    companion object {
        // Device evidence: research/evidence/itheme-original-hash.json, fp-evolution.vi.txt.
        const val AOD_ROOT = "/data/bbkcore/theme/.dwd/c/o/m/b/b/k/t/h/e/m/e/C"
        const val FP_ROOT = "/data/bbkcore/bbkthemeres/novoland/103"

        internal fun parse(text: String): DeviceCatalog {
            require(text.length <= 64 * 1024) { "catalog_output_invalid" }
            val lines = text.trimEnd('\n').split('\n')
            val entries = mutableListOf<DeviceSourceCandidate>()
            val roots = mutableMapOf<ThemeKind, String>()
            var limited = false
            lines.forEachIndexed { index, line ->
                val parts = line.split('\t')
                when (parts.firstOrNull()) {
                    "ROOT" -> {
                        require(parts.size == 3 && parts[2] in setOf("PRESENT", "UNAVAILABLE"))
                        val kind = ThemeKind.valueOf(parts[1]); require(roots.put(kind, parts[2]) == null)
                    }
                    "ITEM" -> {
                        require(parts.size == 4 && entries.size < 96)
                        val kind = ThemeKind.valueOf(parts[1]); require(roots[kind] == "PRESENT")
                        val size = parts[2].toLong(); require(size >= 0)
                        val raw = Base64.getDecoder().decode(parts[3])
                        val path = raw.toString(Charsets.UTF_8)
                        require(path.toByteArray(Charsets.UTF_8).contentEquals(raw) && path.length <= 360)
                        require(path.none { it.isISOControl() } && path.split('/').none { it == "." || it == ".." })
                        if (kind == ThemeKind.AOD) {
                            require(path.startsWith("$AOD_ROOT/") && path.endsWith(".itz"))
                            require('/' !in path.removePrefix("$AOD_ROOT/"))
                        } else {
                            require(path.startsWith("$FP_ROOT/") && path.endsWith("/content/animation.zip"))
                            val title = path.removePrefix("$FP_ROOT/").removeSuffix("/content/animation.zip")
                            require(title.isNotBlank() && '/' !in title)
                        }
                        require(entries.none { it.path == path })
                        entries += DeviceSourceCandidate(kind, path, size)
                    }
                    "END" -> {
                        require(index == lines.lastIndex && parts.size == 3 && parts[1].toInt() == entries.size)
                        require(parts[2] in setOf("0", "1")); limited = parts[2] == "1"
                    }
                    else -> error("catalog_output_invalid")
                }
            }
            require(lines.last().startsWith("END\t") && roots.size == ThemeKind.entries.size)
            return DeviceCatalog(entries, roots.filterValues { it == "UNAVAILABLE" }.keys, limited)
        }
    }
}
