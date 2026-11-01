# OTE pack format, version 1

An `.otepack` is a **single-volume ZIP of data**, not an installer. It contains
`manifest.json`, the PNG files named by `frames`, and an optional PNG thumbnail.
Directories that are parents of declared files are allowed. Undeclared files,
shell scripts, APKs, native libraries, encrypted entries, split ZIPs and ZIP64 are
rejected. Import never runs code or restores archive permissions/symlinks.

```text
example.otepack
├── manifest.json
├── thumbnail.png          # optional, declared in manifest
└── frames/
    ├── 000.png
    └── 001.png
```

```json
{
  "schemaVersion": 1,
  "id": "snow-example-1",
  "title": "Snow example",
  "author": "Your name",
  "version": "1.0",
  "kind": "FINGERPRINT",
  "canvas": { "width": 1080, "height": 1920, "anchorX": 540.0, "anchorY": 960.0 },
  "frames": [
    { "path": "frames/000.png", "durationMs": 33, "sha256": "<64 lowercase hexadecimal characters>" },
    { "path": "frames/001.png", "durationMs": 33, "sha256": "<64 lowercase hexadecimal characters>" }
  ],
  "compatibility": { "sdkMin": 33, "models": ["V2453A"] },
  "thumbnail": "thumbnail.png",
  "description": "Your own artwork"
}
```

The checksum placeholders in this example must be replaced by the actual SHA-256
of each file's bytes. `kind` is `FINGERPRINT` or `AOD`. `schemaVersion` must be
explicitly present and equal to `1`; unknown manifest fields are rejected.
Metadata defaults are defined by `ThemeManifest` in `core:model`. JSON object
keys must be unique, including keys spelled using Unicode escapes. Maximum JSON
nesting is 32. A companion structural schema is
[`otepack-v1.schema.json`](otepack-v1.schema.json); it does not replace runtime
file/hash/budget checks.

Coordinates belong to the **artwork canvas**. An anchor is not evidence of the
phone's sensor position. Both non-null coordinates must be finite and fall inside
the canvas. Device adapters must separately validate compatibility and map artwork
to the measured/calibrated device layout. The presence of `compatibility.profile`
does not grant trust or enable a device adapter on its own.

Per-frame durations are pack metadata. A target engine may support only one
uniform delay. Such an adapter must reject unsupported timing or explicitly
resample it; it must not pretend to have installed unequal frame durations.

## Validation and budgets

Default `PackLimits` are enforced using bounded reads and unsigned ZIP sizes:

| Quantity | Limit |
| --- | ---: |
| Compressed input archive | 128 MiB |
| Expanded declared files | 256 MiB |
| One PNG/file | 32 MiB |
| Manifest | 256 KiB |
| ZIP entries, including directories | 512 |
| Animation frames | 240 |
| Canvas side | 4096 pixels |
| Canvas area | 16,000,000 pixels |
| Canvas area × frame count | 240,000,000 pixels |
| Frame duration | 5–10,000 ms |
| Total timeline | 120,000 ms |
| Expansion ratio for entries over 64 KiB | 300:1 |

PNG files must be static, noninterlaced PNGs. Valid grayscale, RGB, indexed,
grayscale-alpha and RGBA PNG color types and their legal bit depths are accepted.
APNG chunks are rejected. Each frame's IHDR dimensions must match the declared
canvas. The parser verifies PNG chunk CRCs, ordering of critical chunks, palette
requirements, legal row filters, and the zlib output size implied by the PNG
dimensions. It does not allocate decoded bitmaps. Thumbnail dimensions obey the
same per-image bounds but need not equal the animation canvas. A thumbnail can
reference an existing frame; otherwise it is a separately declared PNG.

Each frame path is unique and has a `.png` suffix. Relative Unicode paths are
allowed, but empty components, `.`, `..`, leading `/`, backslashes, colons,
control characters and hidden path components are rejected. Paths are limited to
240 characters and 1024 UTF-8 bytes. Archive local and central headers must agree.
ZIP Unix symlink/special-file attributes are rejected; extraction creates only
ordinary new files, irrespective of archived attributes. ZIP CRCs are checked
against actual expanded bytes, rather than trusted as metadata.

## API and lifecycle

`PackCodec.importPack(input, destination)` requires a **nonexistent destination**
whose parent already exists in app-controlled storage. It copies the bounded
input to a staging ZIP inside the new directory, inspects ZIP metadata, parses the
manifest, extracts only declared assets, and validates their contents and hashes.
It returns `ImportedTheme` with the archive SHA-256. Streams supplied by the caller
remain caller-owned. On failure, only files/directories created by this invocation
are removed, without recursively walking unknown contents.

`readManifest(directory)` performs schema/metadata validation only, so library
refreshes do not repeatedly decompress every frame. It is **not** an integrity or
installation check. `validateDirectory(directory)` revalidates frame hashes and
PNG data and must be called before applying assets. It rejects linked assets.

`exportPack(directory, output)` runs fresh validation and exports only declared
files with normalized manifest JSON, stable order and fixed timestamps. Extra
local files, logs and native adapter payloads are not exported. The caller must
write the result to a different SAF destination, not over the source assets.

This module does not execute SU, decide installation paths, change system state,
verify Vivo ownership, or claim runtime compatibility. Those are separate adapter
and root-engine responsibilities. Integrity checks detect corruption and
inconsistency; SHA-256 inside an untrusted pack is **not a publisher signature**.

## Tests

`PackCodecTest` covers a real ZIP/PNG round trip and deterministic export, traversal,
undeclared scripts, unknown/missing schema, duplicate JSON keys, excessive nesting,
wrong hashes/dimensions, missing assets, timing/frame/pixel/byte budgets, duplicate
ZIP names, Unix symlink/encryption attributes, ZIP CRC corruption, malformed PNG
deflate/row filters/chunk CRCs, existing destinations and asset symlink replacement.
These are host JVM tests; they do not establish Android or device-adapter runtime
behavior.
