# MVP scope and useful extensions

Status describes source code, separately from build/device verification. Android runtime has not been asserted merely because code exists.

| Feature | MVP / next step | Feasibility and safety |
|---|---|---|
| 1. Preview before apply | Implemented local frame player | No SU; lifecycle paused, bounded decode cache |
| 2. Favorites | Implemented Room field/filter | Local reversible metadata |
| 3. Search and kind filters | Implemented | Local library only |
| 4. Multiple PNG import | Implemented SAF | Natural frame order, bounded inputs |
| 5. GIF frame extraction | Implemented | Decoder budgets; preserve delays |
| 6. Automatic restore point | Implemented FP adapter | Snapshot verified before mutation |
| 7. Explicit snapshot export | Implemented engine SAF API; UI integration | Independent of future temp-root loss |
| 8. Device compatibility scanner | Implemented exact FP profile | Read-only hashes/context, unknown devices refused |
| 9. Per-app language | Implemented EN/VI/JA resources | Android applicationLocales, no system locale writes |
| 10. Pack metadata/schema/checksum | Implemented parser/export | Schema1, traversal/ZIP bomb/CRC/hash checks |
| 11. Local procedural Soft Pulse | Implemented | Original generated assets, no copyrighted bundle |
| 12. FPS/scale/rotation/offset/alpha editor | Implemented preview/export | Actual pixel export; unsupported native mixed timing rejected |
| 13. Sensor-artwork anchor calibration | Implemented artwork transform | Does not alter physical sensor region; hardware center not guessed |
| 14. Operation diagnostics export | Implemented sanitized history | No account IDs, source URIs, raw SU dumps |
| 15. Theme collections/profiles | Planned Room relations | Safe local grouping, no background auto-apply initially |
| 16. Comparison mode | Planned side-by-side same canvas | Safe, useful to expose scaling differences |
| 17. Frame deduplication/optimization | Planned | Preserve timeline; measure rather than assume power benefit |
| 18. PNG compression optimizer | Native PNG8 export implemented; tunable optimizer planned | Quantization changes colors/alpha, audit output |
| 19. Animation performance analyzer | Pixel/frame budgets implemented; runtime measurements planned | Distinguish estimates from measured GPU/FPS/power |
| 20. Device profile export/import | Export diagnostics implemented; profile import planned | Imported profile never grants arbitrary write paths |
| 21. Backup import | Planned signed/strict profile archive validation | Do not trust archive paths/metadata for restore |
| 22. AOD atomic activation | Research pending | Needs readback/rollback across full tree without killing services |
| 23. Clock/battery AOD editor | Research partly known; not implemented | Only verified attributes, no fictional controls |
| 24. Timeline and keyframes | Implemented source: scrub/trim/exclude/delay, transform keyframes and easing baked to PNG; not yet Android tested | Layers/particle editor remains unimplemented |
| 25. Community repository | Not implemented | Pack-only data; never download/execute scripts or manipulate entitlement |
| 26. Scheduling theme profiles | Deferred | Temp root and lockscreen lifecycle require careful explicit opt-in |
| 27. Additional presets | Nine original Canvas generators implemented in source, no third-party artwork | Rendering/build still needs Android verification |
| 28. Release signing/update continuity | Build config supplied | Owner-controlled keystore; no shared private signing key |

Three source UI styles are implemented: rounded MIUIX-inspired Compose components (no fake dependency), Material3 dynamic color, and translucent Glass compositing. True backdrop blur and advanced shared-element effects are not claimed. Reduced effects avoids decorative glass gradient.
