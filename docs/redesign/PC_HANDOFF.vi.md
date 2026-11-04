# Tiếp tục trên máy tính

Có thể mở repo trong Android Studio, Codex PC hoặc một coding agent khác. Công cụ chỉ hỗ trợ viết/chạy build; cần JDK/SDK/Gradle và dependency access thật. Không cần GitHub Actions. Codemagic manual vẫn là lựa chọn hiện có.

## Lấy source

Clone `https://github.com/minhkhoi1516/OriginThemeEngine.git`, checkout nhánh `redesign/aod-fp-workspaces`. Baseline main `e6d6cbbbcbae6288d9dc7ef7647767d63e850d90` có build được chủ máy báo thành công; nhánh redesign mới chưa được compile. Không dùng screenshot để suy commit APK đang cài.

Cài JDK17 đầy đủ, Android SDK Platform36/Build-Tools35.0.0 hoặc36.0.0 theo build, platform-tools và Python3.9+. Cấu hình JAVA_HOME/ANDROID_HOME; xác nhận license qua sdkmanager/Android Studio. Không commit local.properties. Wrapper hiện là bootstrap Python đã pin SHA Gradle8.11.1, không phải official wrapper JAR.

```sh
python3 scripts/validate_project.py
python3 scripts/validate_handoff.py
python3 scripts/test_gradle_bootstrap.py
./gradlew :app:assembleDebug :core:root:test :engine:pack:test :engine:png:test :data:testDebugUnitTest :engine:device:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug --continue --stacktrace
```

Windows dùng `gradlew.bat` với cùng danh sách task. Linux/WSL thêm:

```sh
python3 scripts/test_catalog_discovery.py
python3 scripts/test_device_transaction.py
python3 scripts/test_device_recovery.py
```

Mac build Android/Kotlin được; shell fixture ở trên dùng GNU stat/readlink và `/proc` cho transaction, cần Linux/WSL hoặc container phù hợp. Không tuyên bố skipped là passed. Instrumentation/exporter cần device/emulator qua `:app:connectedDebugAndroidTest`; kiểm root/apply cần máy thật đã cho phép. Không tự cấp root cho emulator hoặc flash điện thoại để hoàn thành test.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Giữ full log/test XML/lint. Muốn dùng Miuix/Glasmio bản mới, làm toolchain spike riêng theo UI_RESEARCH trước; các lệnh baseline này không khẳng định compile upstream mới.

## Ký và cập nhật

Build Codemagic trước dùng Android Debug certificate sinh trên worker. Khóa riêng của lần đó chưa được cung cấp/lưu bền; APK và apksigner.jar không thể khôi phục private key. Giữ applicationId mà đổi key sẽ không cập nhật đè được theo cơ chế Android bình thường.

Trên PC tạo và giữ keystore do chủ dự án sở hữu, lưu ngoài git; release config nhận OTE_KEYSTORE, OTE_STORE_PASSWORD, OTE_KEY_ALIAS, OTE_KEY_PASSWORD qua môi trường cục bộ. Không đưa mật khẩu vào prompt/commit/command history. Không tự xoay signing key mỗi build.

Trước khi yêu cầu gỡ bản cũ phải xuất thư viện/snapshot và kiểm khả năng khôi phục; backup import của OTE hiện chưa có. Nếu không giữ được key cũ, lập phương án migration hoặc dùng applicationId thử nghiệm riêng; không bảo người dùng xóa dữ liệu mù. Build key mới không làm bản cũ tự tin tưởng bản mới.

## Nguyên liệu nghiên cứu

`research/inputs.json` ghi tên/hash/scope các file đã có; không yêu cầu chủ máy trích lại partition. Giữ APK Vivo/SukiSU, ITZ gốc, animation gốc và log cần thiết ngoài repo công khai, đặt vào `private-research/` bị gitignore. `research/tools/` chỉ phục vụ phân tích offline, không cài APK hay chạy asset/script trong APK.

Báo cáo trong repo đủ bắt đầu thiết kế/parser và đọc code hiện tại. Để mở rộng reverse engineering cần private originals tại máy PC; chưa tự chuyển toàn bộ attachment lên GitHub. Chủ máy có thể chuyển lại chính file đã sở hữu qua kênh riêng khi chuyển môi trường, không tạo lệnh root mới để lấy lại nếu file vẫn có.

## Điểm tiếp tục

Đọc prompt `prompts/PC_CONTINUATION.vi.md`, báo repo/branch/hash đang mở. Build baseline/current branch, sửa lỗi xác thực trước. Mỗi thay đổi cần nêu “đã viết”, “đã chạy host/JVM”, “đã compile”, “đã thử trên máy” riêng. Không bỏ giới hạn an toàn để làm nút Apply sáng lên. Không triển khai Root Manager.
