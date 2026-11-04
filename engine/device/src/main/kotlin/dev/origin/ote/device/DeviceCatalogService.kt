package dev.origin.ote.device

import android.os.Build
import dev.origin.ote.root.DeviceCatalog
import dev.origin.ote.root.DeviceCatalogReader
import dev.origin.ote.root.RootManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCatalogService @Inject constructor(private val root: RootManager) {
    suspend fun scan(): DeviceCatalog {
        check(Build.MODEL == VerifiedProfile.MODEL && Build.VERSION.SDK_INT == VerifiedProfile.SDK) {
            "catalog_device_not_profiled"
        }
        // RootManager verifies UID0 for this explicit read; no automatic launch scan.
        return DeviceCatalogReader(root).scan()
    }
}
