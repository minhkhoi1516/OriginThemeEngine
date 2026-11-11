# Kiểm tra thiết bị và tìm đúng đường dẫn

[Mục lục](../README.md) · [AOD](03-aod-co-che.md) · [Vân tay](05-fingerprint-co-che.md)

## Hồ sơ đã nghiên cứu

| Thành phần | Bản đã đối chiếu |
|---|---|
| Điện thoại | iQOO Z10 Turbo Pro; mã máy hiển thị V2453A |
| Hệ điều hành | OriginOS 6 China, Android 16 / SDK 36 |
| Build chủ máy cung cấp | PD2453_A_16.1.17.1.W10.VO00L1 |
| iTheme | `com.bbk.theme`, 20.5.8.1 |
| AOD | `com.vivo.nightpearl`, NightPearlUI 8.0.4.2 |
| Giao diện vân tay | `com.vivo.fingerprintui`, 3.1.36.118_beta |
| Tiến trình khai báo của FingerprintUI | `com.vivo.biometrics` |
| Quyền | Root tạm thời theo chủ máy; phải kiểm tra lại ở mỗi phiên |

Các hash của bản phân tích nằm trong [danh mục nguồn](../evidence/provenance.json). Đây là nhận dạng bản đã đọc, không phải chứng thư xác nhận APK chính hãng.

## Đọc thông tin trên điện thoại

Chạy từng lệnh trong aShell/Termux đã thiết lập phù hợp. Các lệnh trong mục này **chỉ đọc**. Chúng không cài theme hay sửa settings. Khi `su` hỏi cấp quyền, đó là yêu cầu của công cụ bạn đang dùng.

```sh
getprop ro.product.model
getprop ro.build.display.id
getprop ro.build.version.release
getprop ro.build.version.sdk
wm size
wm density
su -c 'id -u'
```

`id -u` phải trả `0` mới xác nhận tiến trình lệnh đó đang là root. Có ứng dụng SU hoặc chạy được shell qua Shizuku không tự chứng minh có quyền root. Root cũng không bỏ qua mọi giới hạn SELinux.

Đọc phiên bản package bằng các lệnh sau; trong đầu ra tìm `versionName` và `versionCode`:

```sh
dumpsys package com.bbk.theme
dumpsys package com.vivo.nightpearl
dumpsys package com.vivo.fingerprintui
```

Nếu không có package, dừng áp dụng hướng dẫn của bộ hiển thị đó. Không lấy tên gần giống để thay thế. Không cần gửi toàn bộ đầu ra công khai: chỉ ghi model/build, phiên bản và kết quả cần đối chiếu.

## AOD đang chọn

```sh
settings --user current get system screen_off_remind_style
su -c 'ls -lanZ /data/bbkcore/screenclock/screenclock'
su -c 'ls -lanZ /data/bbkcore/screenclock/screenclock/1080'
```

Trong thử nghiệm mẫu, style là `1638987`. Giá trị `490` đã xuất hiện ở trạng thái khác trước đó; không phải ID để “mở khóa” toàn bộ khả năng AOD. Thư mục `1080` từng rỗng khi mẫu khác đang được dùng, nên rỗng không tự chứng minh engine lỗi.

Một ITZ tải về đã được đối chiếu ở:

```text
/data/bbkcore/theme/.dwd/c/o/m/b/b/k/t/h/e/m/e/C/动态黑塔少女 揭露.itz
```

Log gốc có dấu `//` sau `theme`; đường trên chuẩn hóa dấu phân cách. Đây là **đường của mẫu cụ thể**, không phải thư mục áp dụng chung cho mọi tài nguyên. Nếu muốn tìm mẫu AOD khác, bắt đầu từ mục đã tải của iTheme và metadata của mẫu đó; không quét hoặc sửa toàn bộ `/data`.

## Hiệu ứng vân tay đang chọn

```sh
settings --user current get system udfp_unlock_effect_style
settings --user current get system udfp_unlock_res_effect_style
```

Khóa thứ hai trả về **JSON**, không phải chỉ một đường dẫn. Trong mẫu đã thử: `category=103`, `resId=2003000011`, `filePath=/data/bbkcore/bbkthemeres/novoland/103/进化/`. ID và đường phải phù hợp với mẫu đang chọn và thư mục thực còn tồn tại.

```sh
su -c 'ls -lanZ /data/bbkcore/bbkthemeres/novoland/103'
su -c 'ls -lanZR "/data/bbkcore/bbkthemeres/novoland/103/进化"'
su -c 'sha256sum "/data/bbkcore/bbkthemeres/novoland/103/进化/content/animation.zip"'
```

Lệnh cuối chỉ dành cho mẫu `进化`. Với mẫu khác, dùng đúng đường đã đọc được. Luôn đặt đường có khoảng trắng/tên Unicode trong dấu nháy; không chèn khoảng trắng giữa `/bbkcore/` và phần tiếp theo. Nếu metadata trả `null`, không tự ghi JSON hoặc đoán ID.

## Chuyển sang máy vivo/iQOO khác

| Đối tượng | Trạng thái |
|---|---|
| Máy/build nêu trên, đúng mẫu đã thử | Có cơ sở dùng làm điểm xuất phát; vẫn kiểm tra tình trạng hiện tại |
| Cùng model nhưng bản OTA/bộ hiển thị mới | Chưa kiểm chứng lại |
| Máy vivo/iQOO khác chạy OriginOS 6 China | Chưa thử; đọc cấu trúc và parser trước |
| Máy gập, màn hình phụ, người dùng phụ | Có nhánh mã riêng; tài liệu thao tác chưa xác nhận |
| Funtouch OS, OriginOS phiên bản khác | Ngoài phạm vi xác nhận |

Cần khớp **hệ điều hành + bộ hiển thị + cấu trúc tài nguyên + cách nạp lại**. Chỉ khớp đường dẫn chưa đủ. `wm size` cho thông tin màn hình; nó không trực tiếp cho biết kích thước vùng vẽ AOD hay tọa độ cảm biến mà một context của engine dùng.

Nếu khác cấu trúc, chỉ cần lưu danh sách tệp, cấu hình liên quan và phiên bản. Không cần dump cả phân vùng. [Mẫu ghi kết quả](../templates/ket-qua-thu-nghiem.md).
