# Trạng thái và backlog triển khai

## Đã thay source trong nhánh bàn giao

| Phần | File | Kiểm chứng hiện tại |
| --- | --- | --- |
| Điều hướng hai workspace, bốn tab | OteApp.kt, WorkspaceScreens.kt | Source/resources kiểm tra; chưa compile nhánh này |
| Studio theo loại, mở đúng tác phẩm | StudioScreen.kt | Code path đã đổi; cần test navigation/process death |
| Trang review FP/AOD riêng | ApplyReviewScreen trong WorkspaceScreens.kt | Có hướng dẫn/điều kiện; ghi thật vẫn DeviceEngine hiện có |
| Quét metadata nguồn trên máy | DeviceCatalogReader.kt, DeviceCatalogService.kt, ote-catalog.sh | 7 host fixture chạy đạt; 5 Kotlin tests viết, chưa chạy |
| Root status/history | RootReadinessChecker.kt, SuLaunchFailure.kt, RootUi.kt | Thêm 9 test trước đó, chờ Kotlin run; không tự cho SU thành công |
| Research + handoff + prompts | docs/redesign, research, prompts | Tài liệu/hash/link được kiểm tra offline |

Nhánh chưa có native Miuix/Glasmio, AI HTTP, full source-copy/import, native AOD editor/export/transaction. Không có API fake trả success. App nguồn hiện tại vẫn giữ chức năng frame library/preview/Studio/export/experimentalFP đã có.

## Ưu tiên trên PC

P0 — Build đúng branch với toolchain baseline, chạy unit/lint; giữ APK baseline để so sánh. Sửa compile mới nếu có, không disable tests. Chốt signing key của chủ máy trước cập nhật lâu dài. Quét read-only trên V2453A, xác nhận không sửa nguồn.

P1 — Chép SourceSnapshot an toàn + parser giữ rawITZ. Vẽ lại AODDocument theo pattern4 (đồng hồ số, ngày, vị trí pin, static decoration); bộ kiểm dữ liệu theo capability; preview viewport; export round-trip không đổi byte ngoài whitelist. Tạo draft DB/migration/undo/autosave và kiểm process death.

P1 — Tách Device page thành compatibility/source/snapshots; FP ApplyPlan chứa exact base target, generated preview/hash và snapshot. Thử source hiện tại ở chế độ chỉ đọc rồi mới fixture/apply trên máy với chủ máy chọn thao tác. Không tự đổi hash allowlist để ép nhận mẫu mới.

P2 — Integration spike Miuix thật trước; Material renderer vẫn hoạt động; Glass spike sau ở budget GPU chấp nhận được. Kết quả dependency/build/benchmark quyết định tag; không nâng đồng loạt mà không có rollback git. Triển khai snapshot multi-file AOD, thử trường hợp root mất/partialwrite trước khi mở nút.

P2 — Timeline multilayer/particle, rounded mask fit toàn ảnh (không cắt nhân vật), anchor calibration, frame optimizer. Xuất nativeFP tương đương control; test bản hiển thị lớn riêng trên máy, không suy từ resolution.

P3 — Proposal schema, local compiler và fuzz tests rồi mới key storage/provider client/AI UI. Thêm network permission có chủ đích, cho biết dữ liệu gửi và quota; không thay điều kiện root/Apply.

## Nghiệm thu bắt buộc

- AOD source/FP source không trộn khi import, mở preview, edit, export và Apply.
- Không root vẫn mở thư viện/Studio được; chỉ hành động đọc/ghi thiết bị xin SU.
- Thiếu SU, denied, UID không0, mất quyền giữa chừng không tạo lịch sử VERIFIED.
- Bấm review không ghi; chọn tab/đổi theme không kích hoạt scan/apply.
- Unknown/giả mạo pack path/checksum/symlink/ZIPbomb bị chặn.
- Source đổi sau plan, mode/context khác, unknown native profile đều từ chối.
- Restore kiểm snapshot thật; không dùng “xóa rồi tải lại” thay cho backup bắt buộc của ứng dụng.
- Chỉnh clock AOD vẫn lấy giờ hệ thống thật; icon pin chưa hỗ trợ phải ghi rõ.
- QuotaAI hết vẫn sửa tay được; key không có trong backup/log; output không gọi shell.
- Locale VI/JA/EN, cỡ chữ lớn, dark/light, edge-to-edge, insets và quay lại giữ document.
- Dữ liệu kiểm UI không được coi là test thao tác root hay pin/OLED lâu dài.
