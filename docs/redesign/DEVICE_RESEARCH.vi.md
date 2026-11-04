# Nghiên cứu nền tảng cho hai editor

## Mức bằng chứng

**Tệp / mã / host fixture / quan sát của chủ máy / thử từ APK OTE** phải được ghi riêng. Không gọi đường dẫn mẫu là chuẩn cho mọi vivo. Các thông tin dưới đây gắn với V2453A, Android16, OriginOS6 China và các APK đã gửi.

## AOD

Mẫu `动态黑塔少女 揭露.itz` có SHA-256 `b8aefa82bc08a25c829ee6a3d4d48816da386ff6fac1986b6632d913b73503b7`. Mẫu cụ thể là ZIP, chứa payload `screenclock/screenclock.zip`, manifest, metadata, preview và key; không suy mọi ITZ đều đọc như vậy. Payload có nhánh 1080/1440, config JSON, ảnh glyph và chuỗi frame; một số tên .png chứa JPEG, phải kiểm magic/decode chứ không chỉ đuôi.

- ID được quan sát:1638987, resId700076480. Query iTheme trả file trong `/data/bbkcore/theme/.dwd/c/o/m/b/b/k/t/h/e/m/e/C/`. Hash tải xuống trùng mẫu. [Bằng chứng query](../../research/evidence/itheme-clock-query.json), [hash](../../research/evidence/itheme-original-hash.json).
- Tài nguyên đang áp dụng được quan sát ở `/data/bbkcore/screenclock/screenclock`; tar của chủ máy có 135/135 file trùng payload. SELinux/owner/mode đã có thay đổi trong quá trình thử; không lấy mode600 từ tar làm luật bất biến, cũng không chmod777 hàng loạt để “sửa”.
- NightPearlUI8.0.4.2, SHA `623f473bb5ec28ab5ada0fa37696ac88cb98a9b677384fb97ed61d733bda4e51`. [Bảng tham số/callsite](../../research/evidence/aod-pattern4-controls.vi.txt) ghi code offset và PC.
- Chủ máy đã xác nhận mẫu chính thức chạy lớn hơn GIF tự tạo và bản Rintaro/Waguri thay ảnh hoạt động. Chưa có đo pin/FPS/viewport/OLED và chưa xác minh adapter AOD mới trong APK.

| Chức năng | Bằng chứng | Thiết kế |
| --- | --- | --- |
| Frame image/duration/lastImage/left/top | Đường đọc trong CustomOnlineStyleView, SequentialFrameView | Sửa qua typed patch; giữ số frame khớp duration |
| Chữ số clock | hour0/hour1/minute0/minute1/split.left/top/visible và bộ time_0..9/time_dot | Slot glyph ảnh, preview nhiều mốc giờ; không vẽ giờ chết vào GIF |
| Ngày/thứ/AM-PM | TextView với vị trí/visible, textSize gọi unit DIP | Editor theo profile; nội dung thật do engine cập nhật |
| Vị trí pin | left/top/gravity/visible | Cho chỉnh vị trí/hiện ẩn khi exporter đã có |
| Biểu tượng pin theo % | Drawable trong APK, chưa có src/icon ở bean đã đọc | Chưa hỗ trợ đổi icon thật; sticker pin phải ghi rõ là trang trí |
| Sticker tĩnh | chartlet.item.src/left/top | Có cơ sở tĩnh; thêm file cần transaction biết file mới |
| Sticker động, alpha tổng, background.width/height | Chưa có đường getter sử dụng tương ứng trong phân tích | Không bật nút như thể chắc chắn có tác dụng |
| Analog clock | Có nhánh mã, thiếu mẫu/pivot đầy đủ | Nghiên cứu bổ sung, không chỉ đổi clockStyle |

Ở nhánh không foldable, W<1440 chọn 1080; W>=1440 chọn1440. Layout adapter và giới hạn dùng điều kiện khác: limit=1200 khi W==1440, còn lại900; height=min(maxHeight,limit). Nếu context trảW1260 thì code dùng nhánh1080/height900. Đó là kết quả nhánh mã, chưa đo transform ở view cha. Không dùng màn hình1260×2800 hoặc6.78in để khẳng định AOD full screen. Width JSON và kích thước ảnh khác với viewport.

`repeatCount(1)` trong lớp đã đọc dừng sau một lượt; mode preview3 có đường giữ1000/fade500/chờ500 rồi phát lại, mode AOD2 không vào vòng này. duration33ms là fallback, không phải trầnFPS chung. Preview của OTE phải có nhãn mô phỏng; không tạo tham số loop/fade mới không có trong schema.

ITZ mới có metadata/checksum/key chưa đủ hiểu để bảo đảm iTheme nhận. Giữ bản raw bất biến, không sửa key/chữ ký/DB giao dịch. Tách “xuất bản sao để nghiên cứu” khỏi “gói nhập hợp lệ trên iTheme”.

## Fingerprint effect

Đã xác minh họ tài nguyên103, mẫu `进化`, ID2003000011. Payload đang dùng: `/data/bbkcore/bbkthemeres/novoland/103/进化/content/animation.zip`. [Báo cáo cấu trúc](../../research/evidence/fp-evolution.vi.txt) và `engine/device/VerifiedProfile.kt` chứa các pin hiện tại.

Gói gốc có animation.xml, dãy PNG và atlas KTX2/XML. Nhánh renderer có fallback PNG; thử marker caro và các gói Angel đã được chủ máy xác nhận hiển thị. Không suy từ “có PNG” rằng engine luôn chọn PNG; gói xuất mới cần cấu trúc đúng và xác minh marker/preview.

Bản wide Angel chủ máy báo không cắt:1080×600,27frame,33ms/frame. [Báo cáo quan sát](../../research/evidence/fp-wide-owner-result.json). Đây không phải giới hạn texture hay bằng chứng1260×2800 chạy ổn. Indexed PNG RGBA/palette encoder hiện trong `engine:png`; Android builder tạo type18 với texture frame_{n}, count, duration, width,height. Các giới hạn240frame/240Mpixel/tổng120s là **ngân sách OTE**, không phải trần Vivo đã đo.

Cảm biến do hệ thống định vị; anchor artwork của file không tự là tọa độ cảm biến. Không ghi config calibration, sensor service, TEE, Keystore/Gatekeeper/Weaver hoặc template sinh trắc học. Chỉ adapter tài nguyên hiển thị đã định danh.

## Đọc thư viện trên máy

Source mới có `DeviceCatalogReader` và script cố định `ote-catalog.sh`: scan `.itz` trực tiếp ở C và `103/*/content/animation.zip`, tối đa96entry, không đệ quy, không theo symlink theo kiểm canonical, output giới hạn. Đây là danh sách ứng viên chưa parse full. Mẫu khác layout/phiên bản nằm ngoài phạm vi; danh sách rỗng không chứng minh không có theme. Bất kỳ đọc đồng thời nào vẫn có race; kết quả không được dùng làm quyền ghi.

Bước tiếp theo trên PC: `SourceSnapshotReader` chép vào inode app-private đã tạo, chống symlink/path race, hạn dung lượng, hash nguồn trước/sau, kiểm identity; parse bản sao offline. Chỉ app cache mới được ghi; nguồn iTheme không đổi. Không lấy output root dạng text64KiB để truyền nguyên ZIP nhiềuMB. Chưa nối bước copy/import này trong nhánh bàn giao.

Muốn hỗ trợ mẫu mới phải bổ sung parser/profile bằng chứng; không biến phát hiện file thành wildcard cho đích ghi. Không sửa danh sách mua/tải/quyền sở hữu để “lấy quyền”.
