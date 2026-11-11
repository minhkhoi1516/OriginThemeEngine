# Chuyển từ dự án ứng dụng sang kho nghiên cứu

[Mục lục](../README.md)

Ngày 11/11/2026, theo yêu cầu chủ dự án, nhánh mặc định `main` được thay bằng tài liệu về tài nguyên AOD và hiệu ứng vân tay OriginOS 6 China. Tên repo `OriginThemeEngine` được giữ để các liên kết cũ còn tìm được dự án.

Mã Android/Kotlin, Gradle, cấu hình GitHub Actions/Codemagic, kế hoạch UI và hướng dẫn build APK không còn là nội dung của `main`. Không có yêu cầu bật Actions hoặc build APK để sử dụng bộ tài liệu hiện tại.

## Nguồn ứng dụng được giữ ở đâu?

| Bản lưu | Nhánh | Commit nguồn |
|---|---|---|
| App ở main trước khi chuyển tài liệu | [archive/android-app-main-20261111](https://github.com/minhkhoi1516/OriginThemeEngine/tree/archive/android-app-main-20261111) | `e6d6cbbbcbae6288d9dc7ef7647767d63e850d90` |
| Bản viết lại workspace AOD/FP trước đó | [archive/android-app-redesign-20261111](https://github.com/minhkhoi1516/OriginThemeEngine/tree/archive/android-app-redesign-20261111) | `7b648909c35bfcbf28151eede974825a094a3583` |

Nhánh `redesign/aod-fp-workspaces` cũ cũng được giữ. Đây là nguồn lịch sử; tài liệu trong nhánh archive có thể mô tả hướng phát triển đã dừng hoặc mức kiểm thử cũ. Không dùng README archive làm hướng dẫn hiện tại.

Việc chuyển đổi sử dụng commit nối tiếp lịch sử main, không force-push xóa lịch sử. Thành công build/chạy giao diện app trước đây không được dùng làm bằng chứng rằng mọi thao tác áp dụng/khôi phục qua APK đã được thử trên máy.

## Nội dung chủ động giữ lại

Kết quả đọc ITZ, cơ chế AOD pattern 4, đường tải FP, cấu trúc atlas/PNG, các trial tài nguyên và kinh nghiệm metadata/khôi phục được viết lại thành hướng dẫn. Phần giao diện SukiSU, quản lý root framework, API key AI và APK manager không thuộc mục tiêu hiện tại.
