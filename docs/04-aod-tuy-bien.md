# Tùy biến AOD: GIF, bố cục, đồng hồ và pin

[Mục lục](../README.md) · [Cơ chế](03-aod-co-che.md) · [Áp dụng](08-ap-dung-va-khoi-phuc.md)

Hướng đã có kết quả là lấy bản sao của **mẫu pattern 4 đang chạy**, tạo ảnh riêng rồi thay tài nguyên đã bung. Đóng lại `.itz` chỉ tạo một archive; chưa bảo đảm iTheme nhận nhập hoặc đăng ký mẫu mới.

## Từ GIF tới chuỗi ảnh

1. Giải mã GIF thành các frame đầy đủ, có xử lý cách GIF xóa/giữ vùng ảnh trước đó. Không lấy riêng những vùng thay đổi nhỏ thành frame độc lập.
2. Giữ thông tin thời gian của GIF. Nếu muốn đổi nhịp, resample theo thời gian; tăng số frame bằng cách lặp ảnh không tạo thêm chuyển động thật.
3. Chọn nhánh tài nguyên mà máy đang dùng. Bản thử đầu nên giữ canvas, số frame và tên của mẫu; với mẫu này là 54 ảnh, `dynamic_bkg_anim_0.png` đến `_53.png` trong từng nhánh.
4. Fit ảnh giữ tỷ lệ vào canvas. Thu/phóng rồi thêm khoảng trống nếu muốn giữ trọn nội dung; crop chỉ khi chủ động chấp nhận mất mép.
5. Tạo `background.png` phù hợp với trạng thái kết thúc, tránh hoạt ảnh mới xong lại hiện nhân vật cũ.
6. Đọc từng ảnh xuất ra để xác nhận codec, kích thước và thứ tự. Xem toàn bộ lượt phát trên nền AOD dự kiến.
7. Với lần thử thay số frame, cập nhật cả `image` và `duration`, sao lưu và ghi danh sách tệp thêm/bớt. Không tự dọn các tệp khác bằng wildcard.

GIF gốc, file dự án chỉnh sửa và frame trung gian nên ở thư mục làm việc của bạn. Chỉ payload cuối cùng mới được đưa vào thư mục AOD.

## Ví dụ sửa một thuộc tính

Trong **bản sao** `1080/config.json` của mẫu, `backgroundWall.top` đang là chuỗi `"100"`. Đổi thành `"120"` là phép thử tăng margin trên 20 đơn vị bố trí trong nhánh đã đọc. Giữ nguyên các trường khác. Kiểm tra ảnh có chạm vùng đồng hồ hoặc bị clipping không; đây không phải lời hứa dịch đúng 20 pixel vật lý sau mọi biến đổi của view cha.

Mẫu dùng nhiều giá trị kiểu chuỗi (`"true"`, `"50"`, `"100"`). Giữ kiểu dữ liệu của mẫu khi chỉnh; đừng chuyển hàng loạt thành boolean/số chỉ vì JSON vẫn parse được.

| Mục muốn chỉnh | Trường/tệp có bằng chứng | Tình trạng |
|---|---|---|
| Ảnh nền/chuyển động | `hasBackgroundWall`, `backgroundWall.dynamic/image` | Mã + thử thay tài nguyên |
| Vị trí nền | `backgroundWall.left/top` | Có đường mã; thử riêng trên máy |
| Thời gian mỗi frame | `backgroundWall.duration` | Có đường mã dùng ms; mảng phải khớp frame |
| Ảnh sau lượt phát | `backgroundWall.lastImage`, mặc định `background.png` | Có đường mã |
| Đồng hồ số | `time_0.png`…`time_9.png`, `time_dot.png` | Có đường nạp bitmap |
| Vị trí từng chữ số | `digitClock.hour0/hour1/minute0/minute1/split.left/top/visible` | Có đường mã |
| Ngày, thứ, AM/PM | `hasTime`, `timeStyle`, các mục trong `time` | Có đường mã; kiểu hiển thị cần khớp mẫu |
| Cỡ chữ ngày/thứ | `textSize` tại nhánh được dùng | Gọi `setTextSize` với đơn vị dp ở đường đã đọc |
| Vị trí/ẩn hiện pin | `hasBattery`, `battery.visible/left/top/gravity` | Có đường mã; phần trăm do engine cập nhật |
| Trang trí tĩnh | `hasChartlet`, `chartlet.item[].src/left/top` | Có đường mã; chưa thử thêm tệp mới trên máy |

Đây là bảng cho **mẫu/bộ hiển thị đã đọc**, không phải schema chính thức cho mọi AOD.

## Bo góc mà giữ trọn nhân vật

Bo góc bằng mask ngay trên ảnh xuất ra là hướng dễ kiểm soát; chưa có key `cornerRadius` đã xác minh trong cấu hình này. Nếu chủ thể chạm góc, thu nhỏ nó vào vùng an toàn trước khi thêm mask. Mask chỉ làm mềm góc nền; nó không được làm cụt tóc, tay hoặc hiệu ứng bạn muốn giữ.

Với nhánh xuất JPEG như nền gốc, compositing phần ngoài mask lên nền đen là cách tạo cảm giác bo góc trên AOD đen; JPEG không có alpha. Nếu đổi sang PNG có alpha, coi đó là **thử nghiệm codec riêng**: kiểm tra có nền khác phía sau và cách engine xử lý alpha. Không đổi đuôi JPEG sang PNG rồi cho rằng đã có trong suốt.

Giữ cùng mask, cùng điểm đặt và cùng kích thước ở mọi frame, kể cả ảnh kết thúc, để viền không nhấp nháy.

## Đổi đồng hồ theo phong cách riêng

Bạn có thể thiết kế bộ glyph 0–9 và dấu phân cách bằng bitmap, rồi điều chỉnh năm vị trí theo mẫu. Giữ vùng trong suốt nhất quán để chữ số không nhảy khi đổi giờ. Hãy kiểm tra `00:00`, `11:11`, `23:59`, định dạng 12/24 giờ và độ tương phản trên hoạt ảnh.

Thời gian thật vẫn do engine cập nhật. Đừng vẽ cố định `12:34` vào GIF rồi gọi đó là đồng hồ hoạt động. Nếu cần chữ ký nghệ thuật hoặc logo tĩnh, có thể đưa vào ảnh nền; nó không tự trở thành dữ liệu động.

Nhánh đồng hồ kim tồn tại trong mã nhưng còn cần cấu hình kim, tâm quay và tài nguyên phù hợp. Chưa có mẫu đã kiểm chứng để hướng dẫn chuyển hoàn chỉnh chỉ bằng `clockStyle`.

## Pin và trang trí

Có cơ sở đổi vị trí/ẩn hiện khối pin. Hình pin ở đường đã đọc được lấy từ drawable của APK; chưa có `battery.icon` hoặc `battery.src` đã xác minh để nạp icon tùy ý. `battery.textSize` có getter nhưng chưa tìm thấy direct caller. Một sticker hình pin chỉ là hình trang trí, không tự phản ánh pin thật.

Không thêm `loopCount`, `fadeDuration`, `cornerRadius`, `scale` hoặc khóa tương tự rồi công bố là hoạt động nếu chưa tìm đường đọc nó. Với fade, hạt, chuyển màu, bạn có thể dựng sẵn trong chuỗi ảnh mà không cần key mới. Tương tác theo pin/sạc/giờ lại cần đường dữ liệu runtime đã xác minh.

## Trước khi áp dụng

- JSON hợp lệ; tất cả tên ảnh được tham chiếu tồn tại đúng chữ hoa/thường.
- Số `image` bằng số `duration`; mọi thời gian là số nguyên dương sau parse.
- Frame không thiếu số; ảnh kết thúc phù hợp; không nhầm ảnh preview với ảnh phát.
- Giữ các trường không hiểu của mẫu; chỉ sửa nhóm đang thử.
- So sánh cả nhánh 1080 và 1440 nếu định chia sẻ: không nhân tọa độ mù quáng hoặc tuyên bố cả hai chạy khi mới thử một nhánh.
- Lưu bản trước và danh sách thay đổi, rồi theo [áp dụng/khôi phục](08-ap-dung-va-khoi-phuc.md).
