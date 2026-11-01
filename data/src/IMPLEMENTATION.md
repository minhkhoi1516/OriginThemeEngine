# Data module status

Implemented sources:

- Room library metadata, favorites and bounded operation history (last 200 records).
- DataStore preferences for style, system/light/dark, language, dynamic color and reduced motion.
- SAF import/export; user document URIs and filenames never determine private filesystem destinations.
- `.otepack` import/export through `PackCodec`; generated content roundtrips the same validator before a Room commit.
- PNG sequence import with natural filename order, a default 33 ms delay, consistent canvases and bounded pixel counts.
- GIF extraction through Glide's real `gifdecoder:4.16.0`, preserving decoded frame delays (zero delays default to 33 ms).
- Native fingerprint `animation.xml` plus numbered PNG frame extraction; atlas-only animations explicitly unsupported.
- Raw PNG ZIP import; scripts/videos in such archives are never run or imported as engine instructions.
- Verified pattern4 AOD `.itz` extraction from `screenclock/screenclock.zip`, using `1080/config.json`. JPEG-byte `.png` images become genuine PNGs. This imports animation assets for preview/editing, not clock/battery/layout/system application.
- Original procedural Soft Pulse content, generated locally as 16 transparent 256×256 PNG frames at 33 ms each.
- Failed/canceled imports clean only their newly created app-private UUID directories; no root or system writes exist in this module.

Limits match the pack codec: 240 frames, 4096 pixels per side, 16 million pixels per frame, 240 million summed frame pixels, 128 MiB input ZIP, 32 MiB converted frame, 256 MiB expanded assets, 120 s timeline. Input native archives additionally have a 2048-entry metadata cap. Unsupported split archives must be joined outside the app before import. Imported raw PNG/GIF sources use no inferred sensor coordinates; calibrate the artwork anchor in Studio.

Native fingerprint profile tag is `pd2453-fingerprint-v1`; AOD preview tag is `originos6-pattern4-preview`. These are OTE adapter identifiers, not claimed OEM metadata values.

`PrivateFilesTest` verifies traversal rejection, bounded copying, cancellation, symlink-safe cleanup and a known SHA-256. Those tests require a working Gradle/JDK setup; source creation itself is not proof they ran. Room, SAF/provider behavior, GIF decoding and bitmap conversion also require Android tests on a device/emulator; no such runtime validation is claimed by this module.

Public repository entrypoint is `importTheme(uri)`; the design-contract spelling `` `import`(uri) `` is an alias because `import` is a Kotlin keyword. Room provisioning is in `DataModule`; `PackCodec` is provided by app DI. There is no destructive Room migration fallback.
