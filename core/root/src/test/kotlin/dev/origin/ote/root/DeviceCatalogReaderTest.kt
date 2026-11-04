package dev.origin.ote.root

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeviceCatalogReaderTest {
    private fun row(path: String, kind: String = "AOD") =
        "ITEM\t$kind\t10\t" + Base64.getEncoder().encodeToString(path.toByteArray()) + "\n"
    private val roots = "ROOT\tAOD\tPRESENT\nROOT\tFINGERPRINT\tUNAVAILABLE\n"

    @Test fun acceptsUnicodeNamesAsMetadataNotWritePermission() {
        val path = DeviceCatalogReader.AOD_ROOT + "/动态黑塔少女 揭露.itz"
        val result = DeviceCatalogReader.parse(roots + row(path) + "END\t1\t0\n")
        assertEquals(path, result.entries.single().path)
        assertEquals(10L, result.entries.single().bytes)
    }
    @Test fun refusesTruncatedOutputAndWrongCount() {
        assertFailsWith<IllegalArgumentException> { DeviceCatalogReader.parse(roots) }
        assertFailsWith<IllegalArgumentException> { DeviceCatalogReader.parse(roots + "END\t1\t0\n") }
    }
    @Test fun refusesPathsOutsideObservedRootAndControlCharacters() {
        for (path in listOf("/system/test.itz", DeviceCatalogReader.AOD_ROOT + "/../test.itz",
            DeviceCatalogReader.AOD_ROOT + "/sub/test.itz", DeviceCatalogReader.AOD_ROOT + "/a\nb.itz")) {
            assertFailsWith<IllegalArgumentException> { DeviceCatalogReader.parse(roots + row(path) + "END\t1\t0\n") }
        }
    }
    @Test fun reportsLimitWithoutPretendingTheListIsComplete() {
        assertTrue(DeviceCatalogReader.parse(roots + "END\t0\t1\n").limited)
    }
    @Test fun refusesDuplicatePathsAndRowsAfterEnd() {
        val item = row(DeviceCatalogReader.AOD_ROOT + "/test.itz")
        assertFailsWith<IllegalArgumentException> { DeviceCatalogReader.parse(roots + item + item + "END\t2\t0\n") }
        assertFailsWith<IllegalArgumentException> { DeviceCatalogReader.parse(roots + "END\t0\t0\n" + item) }
    }
}
