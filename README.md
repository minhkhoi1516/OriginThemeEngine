# Origin Theme Engine — OTE 0.1 MVP

Mã nguồn Android Kotlin/Compose cho quản lý animation AOD và hiệu ứng vân tay OriginOS.
Đây là **bản đang phát triển**, chưa hoàn thành toàn bộ master prompt.

## Trạng thái bàn giao

- Đã viết giao diện Compose thật: Dashboard, thư viện AOD/FP, Studio, Root Center, Settings.
- Đã viết Room, DataStore, Hilt, Flow/Coroutines, Navigation Compose, SAF nhập/xuất.
- EN/VI/JA, đổi ngôn ngữ từng ứng dụng; style MIUIX-inspired, Material3, Glass compositing và giảm hiệu ứng.
- Nhập `.otepack`, PNG/ZIP frame, GIF, native FP ZIP; pattern4 ITZ trích animation để xem trước.
- Preview theo frame/duration; Studio FPS/scale/rotation/X/Y/alpha/căn tâm artwork và xuất pack PNG RGBA thật.
- Có chín preset tự sinh tại chỗ (tuyết, lông vũ, sakura, mắt cách điệu, manga, pha lê, pulse, aurora, bướm), không nhúng hình anime/asset theme có bản quyền.
- Studio có timeline chọn frame, trim/bỏ frame, delay từng frame, đổi canvas, căn artwork và keyframe/easing xuất PNG thật.
- RootManager chỉ xin SU khi bấm hành động, kiểm UID0 ở mỗi lần gọi; không tự exploit, mount hay giữ root.
- Adapter FP có profile chính xác, PNG8 encoder thật, snapshot/journal, staging/rename/postcheck/rollback.
- AOD apply/restore, layer/particle editor, factory restore khi không có snapshot gốc và community repository **chưa triển khai**. UI không báo giả thành công.

**Log Codemagic do chủ dự án cung cấp xác nhận đã build debug APK và kiểm chữ ký v2 thành công.** Bước kiểm thử tiếp theo thất bại khi JUnit khởi tạo `RootManagerTest`; đã sửa hàm test trả về sai kiểu, đang chờ lượt chạy mới. Chưa xác nhận toàn bộ unit tests/lint đạt và chưa test APK trên điện thoại. Xem [bằng chứng lượt build đầu](verification/codemagic-first-build.json) và [hướng dẫn Codemagic](docs/CODEMAGIC.md).

Xem [BUILD](docs/BUILD.md), [kiến trúc](docs/ARCHITECTURE.md), [định dạng pack](docs/PACK_FORMAT.md), [QA](docs/QA.md) và [tính năng/roadmap](docs/ROADMAP.md).

## Build

Cài **JDK17 đầy đủ**, Android SDK Platform36/BuildTools36 và Python3.9+; cấu hình `JAVA_HOME`, `ANDROID_HOME` hoặc `local.properties`.

```sh
./gradlew :core:root:test :engine:pack:test :data:testDebugUnitTest :engine:device:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Windows dùng `gradlew.bat :app:assembleDebug :app:lintDebug`; shell fixture transaction/recovery cần Linux hoặc WSL có `/proc/self/fd` và GNU tools. Build APK, Kotlin unit tests và kiểm tra cấu trúc/bootstrap có thể chạy trên macOS. Hai file này là bootstrap tùy chỉnh tải Gradle8.11.1 từ nguồn chính thức và kiểm SHA-256, không giả làm official wrapper JAR. Có thể mở bằng Android Studio sau khi cấu hình Gradle/SDK, hoặc sinh wrapper chính thức theo BUILD.md.

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`. Codemagic đã chạy đến bước tạo APK; kết quả kiểm thử/lint vẫn cần đạt trong lượt tiếp theo. GitHub Actions hiện bị khóa ở cấp tài khoản. Release cần keystore riêng qua biến môi trường; không kèm khóa ký sản xuất.

## Dùng MVP

1. Mở không cần SU. Tạo Soft Pulse hoặc nhập pack/GIF/PNG để xem và chỉnh trong Studio.
2. Dùng SAF xuất pack; nhập pack đã xuất để lưu bản đã chỉnh vào thư viện.
3. Root Center → kiểm tra root. Từ chối SU thì vẫn dùng thư viện/Studio; các thao tác ghi bị chặn.
4. Chỉ adapter V2453A/SDK36 có đúng hash FingerprintUI/native library/description và metadata đã nghiên cứu mới được thử ghi FP. Đổi OS/APK hoặc sai context sẽ bị từ chối.
5. Xuất snapshot qua SAF để giữ bên ngoài app. App tự tạo snapshot mới trước khi thay ZIP. Không cần thư mục backup Download cũ của các bản `.sh`.
6. Chọn **Không có** trong cài đặt hiệu ứng vân tay, xác nhận áp dụng trong app. Sau kết quả ghi/xác minh, chọn lại mục **进化 / Phát triển** qua giao diện hệ thống; xem preview trước, rồi mới thử mở khóa như các lượt nghiên cứu.
7. Khôi phục nghĩa là **snapshot trước đó**, có thể là Angel/Alya chứ không phải Vivo gốc. App không tạo giả gói gốc và không xóa/tải lại thay bạn.
8. Nếu timeout/mất root/giao dịch chưa rõ: không coi là thành công và không áp lại mù; đọc diagnostics/journal và dùng khôi phục. Root có thể mất sau reboot.

## Ranh giới ghi

Đích duy nhất của adapter ghi FP:

```text
/data/bbkcore/bbkthemeres/novoland/103/进化/content/animation.zip
```

Cùng staging/lock root-private trong cây tài nguyên đó và snapshot/journal trong dữ liệu riêng OTE. Mỗi nguồn có hash/metadata được kiểm lại. Gói nhập không có khả năng đưa script/lệnh vào engine.

Không ghi boot/system/vendor/vbmeta, APK/thư viện, settings/theme ownership/database giao dịch, đăng ký/xác thực vân tay hoặc phần cứng cảm biến. Không kill/reboot để ép cache. Có ghi `/data` như mô tả; không gọi đây là “không ghi phân vùng nào”.

Bản native pack và giao dịch Android mới của APK **chưa thử trên máy**. Các script nghiên cứu cũ đã chạy không tự chứng minh app mới đã ổn. Đọc nhãn thử nghiệm trong UI và checklist QA.

## Kiểm tra không cần Android SDK

```sh
python3 scripts/validate_project.py
python3 scripts/test_gradle_bootstrap.py
python3 scripts/test_device_transaction.py
python3 scripts/test_device_recovery.py
```

Những kiểm tra này xác minh cấu trúc/resources/bootstrap và giao dịch shell trong fixture. Android UID/SELinux/SDK/root được mô phỏng có ghi rõ; không thay thế build hoặc test điện thoại. Các báo cáo thực chạy được đóng trong `verification/` khi bàn giao.

## Cấu trúc

```text
app/                 Compose UI, ViewModel, DI, EN/VI/JA
core/model/          Domain models
core/root/           SU process/runtime
engine/pack/         Versioned secure pack parser/exporter
engine/device/       Native PNG8 + verified profile/resource transaction
data/                Room, DataStore, SAF, GIF/PNG/ITZ converters
docs/                Architecture, schema, QA, roadmap, build
scripts/             Bootstrap and host checks
```

Các module rút gọn từ cấu trúc đề xuất để MVP dễ build, không đặt toàn bộ code vào một file. Chức năng chưa làm và các giới hạn không được che bằng dữ liệu mock.

## Mã thông báo thường gặp

- `select_none_first`: chọn Không có trong giao diện hiệu ứng hệ thống rồi thử lại.
- `fp_uniform_timing_required`: frame có delay khác nhau; xuất lại từ Studio với FPS cố định.
- `fp_anchor_recenter_required`: tâm artwork chưa ở giữa; dùng căn tâm/offset trong Studio, không đổi dữ liệu cảm biến.
- `mv_no_target_directory_unsupported` / `directory_fd_unsupported`: runtime chưa đáp ứng giao dịch đã siết; app từ chối ghi, cần bổ sung kiểm chứng/cơ chế tương thích.
- `pending_transaction_restore_first`: giao dịch trước chưa rõ kết quả; dùng kiểm tra/khôi phục, không áp lại mù.
- `foreign_lock` / `stage_file_unknown`: có dữ liệu không đủ bằng chứng thuộc giao dịch; app không tự xóa.
- `aod_write_not_supported_mvp`: chỉ nhập/xem trước AOD trong MVP này.

ZIP bộ thử `.sh` cũ không phải `.otepack`. Khi nhập hiệu ứng native, chọn đúng `png-angel-wide.zip` hoặc `png-alya.zip` nằm trong bộ thử, không chọn ZIP chứa cả script và nhiều bản backup.
