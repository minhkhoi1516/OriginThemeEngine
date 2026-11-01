package dev.origin.ote.model

import kotlinx.serialization.Serializable

@Serializable enum class ThemeKind { FINGERPRINT, AOD }
@Serializable data class CanvasSpec(val width: Int, val height: Int, val anchorX: Float? = null, val anchorY: Float? = null)
@Serializable data class FrameSpec(val path: String, val durationMs: Int, val sha256: String)
@Serializable data class CompatibilitySpec(val sdkMin: Int = 33, val sdkMax: Int? = null, val models: List<String> = emptyList(), val profile: String? = null)
@Serializable data class ThemeManifest(val schemaVersion: Int = 1, val id: String, val title: String, val author: String = "", val version: String = "1.0", val kind: ThemeKind, val canvas: CanvasSpec, val frames: List<FrameSpec>, val compatibility: CompatibilitySpec = CompatibilitySpec(), val thumbnail: String? = null, val description: String = "")
data class ImportedTheme(val manifest: ThemeManifest, val directory: java.io.File, val packSha256: String)
data class ThemeItem(val id: String, val title: String, val author: String, val kind: ThemeKind, val directory: String, val favorite: Boolean, val createdAt: Long, val manifest: ThemeManifest)
data class OperationEntry(val id: Long = 0, val timestamp: Long, val action: String, val status: String, val detail: String)
enum class UiStyle { MIUIX, MATERIAL, GLASS }
enum class ColorMode { SYSTEM, LIGHT, DARK }
data class AppPreferences(val style: UiStyle = UiStyle.MIUIX, val colorMode: ColorMode = ColorMode.SYSTEM, val language: String = "system", val reducedMotion: Boolean = false, val dynamicColor: Boolean = true)
data class DeviceInfo(val model: String, val sdk: Int, val build: String, val manufacturer: String, val supportedProfile: String? = null)
data class RootState(val available: Boolean = false, val checked: Boolean = false, val manager: String = "Unknown", val detail: String = "")
data class CompatibilityResult(val supported: Boolean, val profileId: String? = null, val checks: List<String> = emptyList())
data class BackupInfo(val id: String, val path: String, val sha256: String, val timestamp: Long, val kind: ThemeKind)
