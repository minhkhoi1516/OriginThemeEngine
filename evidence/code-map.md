# Bản đồ các đường mã đã đối chiếu

[Bằng chứng](README.md). Các tên ngắn có thể đã bị obfuscate. Offset DEX/ELF dưới đây chỉ dùng để tìm lại trong **đúng hash ở provenance**, không phải hướng dẫn patch hoặc địa chỉ ASLR.

## NightPearlUI 8.0.4.2

| Kết luận | Điểm đối chiếu đã lưu trong nghiên cứu |
|---|---|
| iTheme pattern 4 dùng CustomOnlineStyleView | `ScreenOffClockView.B`, code_off `0x565624`; `StyleSettingsActivity.N0` |
| Layout, vị trí và giới hạn maxHeight | `CustomOnlineStyleView.m`, `0x539c04`; PC `0032–0063` chọn layout; `07ca–0801` giới hạn chiều cao |
| Thư mục 1080/1440 | `a6.v.E`, `0x1b3d50` |
| Chiều ngắn display và nhánh máy gập | `a6.z0.k`, `0x1c25dc`; constructor `r4.h` |
| Mốc chiều cao | `a6.l.b`, `0x1ab5a8` |
| Đo view hoạt ảnh | `SequentialFrameView.e`, `0x56e50c`; `onMeasure`, `0x56eb10` |
| Frame timing, số lượt | `SequentialFrameView.onDraw`, `0x56e708`; `setInvalidateIntervalFromArray`, `0x56ef48`; Handler `SequentialFrameView$d` |
| Preview/AOD khác mode | `ScreenOffClockView.B` đặt mode 2; `StyleSettingsActivity.N0` đặt mode 3 |
| Ảnh kết thúc, fade/restart theo mode | `CustomOnlineStyleView.n`, `0x53b948`; `k`, `0x539b94`; listener `c`, `0x539704`; handler `a`, `0x5395f8` |
| Cỡ chữ ngày/thứ dùng dp trong nhánh đã đọc | `CustomOnlineStyleView.m`, ví dụ PC `05e4–05f1` |
| Khung GIF tự tạo | `chartlet_style`, `chartlet_layout`, `EditableImageView`, `ChartletClockView.P/f/z` |
| Nhập GIF qua URI | `a6.f0.p`; nhánh URI kiểm 20 MiB, không gộp sang nhánh File |
| Đường tài nguyên/giải nén | `a6.v.m/B/D/E`, `a6.l.g`; listing và tar bổ sung xác nhận đường trên máy |

Phân tích 122 getter và direct reference cho thấy có đường đọc nhiều vị trí/time/visibility; một số getter width/height/alpha không có direct caller trong chỉ mục. Không loại trừ reflection, field access hoặc mã ngoài APK.

## FingerprintUI 3.1.36.118_beta

| Kết luận | Điểm đối chiếu |
|---|---|
| Settings → ResItem → content/animation.zip | `h2.j.S0`, `0x1645fc` |
| Đọc XML gói tải ngoài | `a2.v.i`, `0x8c8fc`; `a2.b0.p`, `0x8d97c` |
| Ghép hai mảng atlas | `a2.b0.j`, `0x8d410` |
| Chọn atlas hoặc PNG | `h2.z0.J`, `0x16f9a0`; nhánh tương tự `h2.y0.O/y` |
| PNG texture/count và tên frame | `a2.b0.n`, dữ liệu do `a2.b0.p` tạo |
| Parser khác cho gói tích hợp | `a2.b0.r`; không chuyển mọi attribute sang parser tải ngoài |
| Cache/mở ZIP | `a2.o.y`, `0x8bf98`; `a2.r.a`, `0x8c234`; `a2.r.b`, `0x8c3d0` |
| Sự kiện đổi mẫu và nạp xong | `h2.j.k0/e0` |
| Icon chờ tra resource có sẵn | `q1.d.a/c/h`; không chứng minh mở PNG tùy ý từ /sdcard |

## libvkrenderer.so, AArch64

| Đường | Địa chỉ virtual trong ELF / dấu hiệu |
|---|---|
| JNI nativeSetAnimation | `0x99db0`, chữ ký `(JJI[Ljava/lang/String;J)V` |
| JNI nativeSetAtlasAnimation | `0x99fcc`, chữ ký `(JJI[Ljava/lang/String;[Ljava/lang/String;J)V` |
| Nhánh frame | `0xfdc80` → `0xdde4c` → `0xdfbf4` → `0xbc968` → `0xa8e08` |
| Nhánh atlas | `0xfdd78` → `0xdde4c` → `0xe0024` → `0xaa8bc` → `0xa8e08` |
| Duration lưu ở offset object 0x90 | Constructor `0xa8e08`, callback `0xa9cc0` |
| Đồng hồ sang ms | `0xa182c`, steady_clock, phép chia nanosecond cho 1.000.000; vector ở fp-timing.json |
| Một nhánh PNG palette | Checker `0x110820`, reader `0x112294`, caller `0x10fbb0` |

Tên constructor/factory native được đặt trong quá trình phân tích từ RTTI/vtable/luồng gọi; không khẳng định mọi symbol gốc còn nguyên. Không thực thi/dlopen native Android trên host để đưa ra các kết luận này.

## iTheme 20.5.8.1

`ExternalResourceConstants.Fingerprint` có RES_TYPE 103 và hai tên settings FP. `ResApplyManager.applyFingerprint`, code_off `0x8b7df8`, đi qua helper `ja.putInt/putString` tới Settings.System. `ClockDatabaseHelper` có bảng clock lưu uid/resId/filename/state và các trường khác. Truy vấn bản mẫu và hash file đã được chủ máy cung cấp; các giá trị state/downloadState không được diễn giải thành quyền mua/xác thực.

Việc tìm thấy phương thức nội bộ không tạo thành API nhập/apply công khai đã được hỗ trợ. Repo không cung cấp lệnh ghi provider/database hoặc phát intent đoán tên.
