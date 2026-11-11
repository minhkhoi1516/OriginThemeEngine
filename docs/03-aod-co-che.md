# AOD: từ ITZ đến nội dung trên màn hình

[Mục lục](../README.md) · [Tiếp: tùy biến AOD](04-aod-tuy-bien.md)

## Mẫu đã kiểm tra

`动态黑塔少女 揭露.itz`, ID `1638987`, tài nguyên `700076480`, là ZIP trong lần kiểm tra này. CRC của ZIP ngoài và ZIP con đạt; không có entry trùng, mã hóa hoặc dữ liệu thừa cuối archive trong bản đã audit. Không suy rằng mọi tệp đuôi `.itz` đều cùng dạng.

```text
动态黑塔少女 揭露.itz
├── description.xml
├── key
├── preview/
│   ├── preview_screenclock_0.jpg
│   └── thumbnail_screenclock_0.gif
└── screenclock/
    └── screenclock.zip
        ├── manifest.xml
        ├── 1080/
        │   ├── config.json
        │   ├── background.png
        │   ├── dynamic_bkg_anim_0.png … dynamic_bkg_anim_53.png
        │   └── time_0.png … time_9.png, time_dot.png
        └── 1440/
            └── cùng nhóm tên như 1080
```

ZIP ngoài: **7 entry = 5 tệp + 2 thư mục**. ZIP con: **137 entry = 135 tệp + 2 thư mục**. Mỗi nhánh độ phân giải có 66 ảnh và một `config.json`; root có `manifest.xml`. [Cây đầy đủ](../evidence/aod-full-tree.txt).

| Thành phần | Vai trò đã xác định |
|---|---|
| `description.xml` | Mô tả, ID, phiên bản, thông tin công cụ, trường `md5result` |
| `key` | Có trong gói, 768 byte; chưa xác định đầy đủ cơ chế sử dụng/xác thực |
| `preview/*.jpg`, `*.gif` | Tài nguyên giới thiệu; GIF này không phải chuỗi frame mà nhánh pattern 4 đọc |
| `manifest.xml` | Mẫu khai báo `<aod background="true" pattern="4">` |
| `config.json` | Chọn nội dung, tọa độ và thời gian frame |
| `dynamic_bkg_anim_*` | 54 frame mỗi nhánh |
| `background.png` | Ảnh được dùng khi kết thúc theo giá trị `lastImage` mặc định trong bean |
| `time_*.png` | Bộ chữ số và dấu phân cách của đồng hồ số |

**Đuôi tệp không đủ để nhận dạng codec.** Trong mẫu, `1080/background.png` và các frame nền là JPEG 1080 × 744; nhánh 1440 tương ứng 1440 × 992. `time_0.png` là PNG RGBA 60 × 80. Cấu hình nền nhánh 1080 ghi height `750`, khác chiều cao ảnh 744. Đừng dùng giá trị JSON thay cho kiểm tra ảnh thật.

## iTheme và bộ hiển thị làm gì?

Mã NightPearlUI nối `manifest.xml` pattern 4 tới `CustomOnlineStyleView`; chuỗi ảnh được phát bởi `SequentialFrameView`. Cấu hình chọn danh sách frame, khoảng thời gian, chữ số, ngày tháng và pin.

Trên thiết bị, tar của thư mục `/data/bbkcore/screenclock/screenclock` chứa **135/135 tệp trùng byte** với payload ITZ. Như vậy đây là liên hệ đã kiểm tra giữa mẫu tải về và tài nguyên đã bung, không chỉ suy từ tên thư mục. Sau thử AOD riêng, hash của ITZ tải về vẫn bằng bản gốc; việc sửa thư mục đã bung không đồng nghĩa sửa kho ITZ.

Mã có luồng gặp `screenclock.zip` thì dọn/giải nén tài nguyên rồi xóa cache cấu hình. Chi tiết toàn bộ bàn giao từ iTheme tới luồng đó chưa được trace. Vì vậy không dùng thao tác chép một ZIP vào đích như một “API cài đặt” mặc định.

## Vì sao GIF tự tạo nhỏ hơn mẫu iTheme?

Cùng NightPearlUI nhưng có hai nhánh view khác nhau:

| Nhánh | Bằng chứng trong bản đã phân tích |
|---|---|
| Ảnh/GIF tự tạo | `ChartletClockView` / `EditableImageView`; layout thường có crop 152 × 120 dp |
| AOD mẫu pattern 4 | `CustomOnlineStyleView` / `SequentialFrameView`; tài nguyên và layout riêng |

Ở một nhánh chartlet, ảnh 190 × 150 dp bị scale 0,8, cho 152 × 120 dp. Layout biến thể lớn hơn cũng tồn tại nhưng cần điều kiện máy/style; không đề nghị ép flag hoặc ID. Như vậy giới hạn nhỏ có bằng chứng ở cả editor/view và bố cục hiển thị, không phải chỉ do file GIF có ít pixel.

Bản thay tài nguyên theo mẫu online đã được chủ máy báo lớn hơn GIF tự tạo và chạy được. Đây là hướng có kết quả. Nó không chứng minh đã bỏ mọi giới hạn trong AOD engine.

## Giới hạn vùng vẽ của pattern 4

Ở nhánh máy không gập đã đọc, gọi `W` là chiều ngắn do helper dùng `Display.getRealMetrics` trả về:

| Bước | Quy tắc trong mã |
|---|---|
| Chọn thư mục | W < 1440 → `1080`; W ≥ 1440 → `1440` |
| Chọn layout | Đúng 1080/1440 dùng layout chuẩn; các W khác dùng adapter rộng 1080/1440 px tương ứng |
| Chiều cao vùng nội dung | `min(int(maxHeight), 1200 nếu W == 1440, còn lại 900)` |

Nếu context trả W = 1260, nhánh này dùng tài nguyên `1080`, adapter rộng 1080 px, trần chiều cao 900 px. Đó là kết quả đọc mã, **chưa là phép đo vùng sáng thực** trên thiết bị; còn ảnh hưởng view cha và clipping. Màn hình 1260 × 2800 không tự tạo canvas AOD 1260 × 2800.

Các getter `width`, `height`, `alpha` ở ConfigBean và `backgroundWall.width/height` chưa tìm được direct caller trong chỉ mục đã rà. Điều này không loại trừ reflection hay đường ngoài APK, nhưng chưa đủ để coi đổi chúng là cách resize đã được hỗ trợ.

## Hoạt ảnh và ảnh kết thúc

Mẫu có 54 khoảng `50` ms: tốc độ danh nghĩa 20 frame/giây, tổng danh nghĩa 2.700 ms. Mảng `duration` cần khớp mảng `image`. Thời gian thực phụ thuộc tải ảnh và lịch vẽ; chưa đo FPS.

Đường khởi tạo đặt repeat count bằng 1, nghĩa là một lượt trong lớp này. Hết lượt, view nạp `lastImage`; bean mặc định là `background.png` khi mẫu không khai báo key.

Preview truyền mode 3, AOD thật truyền mode 2. Vòng kết thúc giữ 1.000 ms → fade 500 ms → chờ 500 ms rồi phát lại chỉ nằm ở nhánh mode 0/3 đã đọc. Vì vậy xem trước lặp không chứng minh AOD thật lặp mãi. Chưa có key JSON đã xác minh để tùy chỉnh những khoảng này.

Giới hạn 20 MiB đã thấy ở một đường nhập GIF qua URI, và đoạn hẹn giờ 4.000 ms ở nhánh chartlet, **không phải trần chung của mọi ITZ**.

## Checksum và chữ ký

CRC ZIP phát hiện lỗi archive; SHA-256 giúp so sánh byte. Chúng không chứng minh được phép cài. `md5result`, `key`, `sysromver=13.0` và phiên bản công cụ có thật trong mẫu, nhưng chưa đủ giải thích toàn bộ cơ chế xác thực hoặc giới hạn ROM. Không tự đổi ID, làm giả trạng thái mua/tải hay cập nhật `key` để tuyên bố gói mới hợp lệ.

Nguồn: [A01–A04, C01 trong bảng bằng chứng](11-bang-chung.md), [đối chiếu các đường mã](../evidence/code-map.md).
