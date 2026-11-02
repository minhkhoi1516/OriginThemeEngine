# Build OTE bằng Codemagic trên điện thoại

GitHub Actions của chủ tài khoản đang bị disabled ở cấp tài khoản. Repo vẫn giữ
nguyên; Codemagic tải source bằng Git và chạy trên máy build riêng.
File `codemagic.yaml` đã định nghĩa workflow `ote-android-debug` / **OTE Android APK**.
Cấu hình này chưa có lượt chạy Codemagic được xác minh và chưa chứng minh APK đã build thành công.

## Thao tác một lần

1. Mở <https://codemagic.io/signup>, tạo tài khoản **Personal**. Có thể dùng email
   hoặc đăng nhập bằng GitHub hiện tại.
2. Vào **Applications → Add application → Other**.
3. Nhập `https://github.com/minhkhoi1516/OriginThemeEngine.git`, chọn **Public repository**.
   Repo hiện là public nên thao tác đọc source này không cần PAT hay khóa SSH.
4. Chọn loại **Android native** rồi **Finish: Add application**.
5. Chọn branch **main**, bấm **Check for configuration file** nếu trang yêu cầu.
6. Bấm **Start new build**, chọn **main** và workflow **OTE Android APK**, rồi chạy.
7. Mở lượt build để xem từng bước. Sau khi đạt, tải `app-debug.apk` trong **Artifacts**.
   Nếu thất bại, lấy log bước lỗi và `outcome.json`; có APK trong artifacts chưa có nghĩa
   mọi kiểm thử đã đạt. Không cần sửa mã trên điện thoại.

Tên nút có thể thay đổi theo UI của nhà cung cấp. Tài liệu chính thức:
[thêm repo](https://docs.codemagic.io/getting-started/adding-apps/),
[Android native](https://docs.codemagic.io/yaml-quick-start/building-a-native-android-app/).

## Chi phí và quyền truy cập

Theo [bảng giá](https://docs.codemagic.io/billing/pricing/) kiểm tra ngày 2026-11-02,
Personal có 500 phút/tháng miễn phí trên **macOS M2**. Team và các loại máy
Linux/Windows/M4 không hưởng hạn mức này. Workflow chọn `mac_mini_m2`, giới hạn
60 phút/lượt, chỉ chạy thủ công. Dừng ở gói miễn phí; không cần bật billing để thử
trong hạn mức hiện có. Kiểm tra hạn mức hiển thị trong tài khoản trước khi chạy.

Tạo/đăng nhập tài khoản và bấm build là thao tác của chủ tài khoản: môi trường
Codex hiện chưa có kết nối Codemagic để thực hiện thay. Không gửi OTP, mật khẩu,
PAT hoặc token Codemagic vào chat. Không cần chuyển repo hay tài khoản GitHub.

## Pipeline thực hiện

- JDK 17; Android SDK Platform 36; Build Tools 35.0.0 (AGP mặc định) và 36.0.0.
- Chạy kiểm tra cấu trúc/ngôn ngữ và bootstrap, sau đó biên dịch debug APK.
- Kiểm chữ ký APK, tạo SHA-256, chạy Kotlin unit tests và Android lint.
- Giữ log, test reports và `build/ci/outcome.json` khi lỗi.
- Không có thao tác SU/ADB, kết nối điện thoại, cài APK hay ghi dữ liệu thiết bị.

Shell fixture transaction/recovery dùng Linux `/proc/self/fd` và GNU tools.
Trên macOS chúng được ghi rõ **skipped**, không gán thành passed. Các kết quả đã
chạy trong Linux workspace nằm trong `verification/`; không thay thế test Android.
Kotlin unit tests vẫn chạy trên macOS. Instrumentation và test root thật cần máy
Android tương thích, không được tuyên bố đã đạt nhờ pipeline này.

SDK có thể đã được nhà cung cấp chuẩn bị và chấp nhận license. Nếu license SDK
chưa được chấp nhận, pipeline dừng và cần xử lý trên môi trường build; script
không tự trả lời đồng ý license.

## Khóa ký và cập nhật

APK đầu tiên dùng khóa debug tạm của máy CI. Khóa này có thể khác ở lượt sau,
khiến Android từ chối cập nhật đè. Trước khi dùng lâu dài cần ký các bản bàn giao
bằng cùng một khóa riêng do chủ dự án giữ, hoặc cấu hình signing secret riêng.
Pipeline giữ `apksigner.jar` công khai để hỗ trợ bước ký sau; không lưu khóa riêng
trong Git, artifacts hay cache.

Chưa thiết lập khóa ký ổn định và chưa có APK nào được bàn giao từ Codemagic.
Không gỡ app/xóa dữ liệu để xử lý lỗi chữ ký khi chưa xuất các snapshot OTE cần giữ.
