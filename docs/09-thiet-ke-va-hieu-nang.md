# Làm hoạt ảnh đẹp, mượt và vừa khả năng bộ hiển thị

[Mục lục](../README.md) · [AOD](04-aod-tuy-bien.md) · [FP](06-fingerprint-tuy-bien.md)

Phần này là khuyến nghị thiết kế và cách đo, không phải thông số giới hạn chính thức của Vivo.

## AOD từ GIF có phong cách riêng

Giữ bảng màu, nét vẽ và nhịp chuyển động của nguồn. Chọn một điểm nhấn chính: nhân vật hoặc đồng hồ. Đặt đồng hồ/ngày/pin vào khoảng trống thay vì để chúng đè lên mặt, tay hoặc chuyển động quan trọng. Hạn chế glow trắng, hạt dày và nhiều kiểu hiệu ứng cùng lúc nếu nguồn không có ngôn ngữ hình ảnh đó.

Một lượt phát có thể gồm xuất hiện nhẹ, chuyển động chính, rồi ổn định ở ảnh kết thúc. Pattern 4 đã nghiên cứu có đường chuyển sang `background.png`, nên ảnh kết thúc cần được thiết kế như một bố cục AOD hoàn chỉnh. Đừng dựa hoàn toàn vào vòng lặp của preview để che một điểm nối xấu.

Bo góc nên áp vào nền hoặc một khung đã chừa mép, giữ trọn nội dung quan trọng. Dùng cùng mask cho cả chuỗi. Hiệu ứng fade, hạt, dịch chuyển có thể dựng trước thành frame; không cần phát minh khóa JSON mới.

## FP: nhìn rõ trong thời gian ngắn

Hiệu ứng mở khóa thường được nhìn trong một khoảng ngắn. Tập trung vào một động tác chính có hướng và điểm kết thúc rõ: bung cánh, lan vòng, tan tuyết. Độ lớn của phần có nét vẽ quan trọng hơn con số canvas.

Giữ điểm neo ổn định giữa frame; biến đổi scale/rotation quanh cùng điểm để hình không rung. Xem trên nền sáng lẫn tối để phát hiện viền đen do alpha hoặc màu bị mất khi chuyển palette. Đừng che quá lâu các thông tin khóa màn hình.

## Bốn kích thước phải tách riêng

| Đại lượng | Cho biết điều gì? |
|---|---|
| Dung lượng ZIP/ITZ trên đĩa | Chi phí lưu trữ/tải; không phải toàn bộ bộ nhớ khi chạy |
| Canvas mỗi frame | Số pixel cần xử lý, kể cả vùng trong suốt |
| Vùng có nét vẽ | Phần bạn thật sự nhìn thấy; giúp giải thích “canvas to nhưng hình bé” |
| Vùng hiển thị của engine | Phần có thể được bố trí/vẽ, chịu clip/scale và parent view |

Ví dụ 1260 × 2800 × 4 byte = **14.112.000 byte, khoảng 13,46 MiB** cho một bitmap RGBA8 chưa tính overhead. Nếu giữ đồng thời 30 bitmap như vậy sẽ khoảng **403,75 MiB**. Đây là phép ước lượng theo giả định RGBA8, không phải khẳng định engine luôn giải mã hoặc giữ đủ 30 frame. Texture nén, cache, palette và buffering có thể làm khác.

Nén ZIP tốt không tự giảm số pixel khi ảnh được giải mã. Canvas toàn màn hình trong suốt phần lớn vẫn có thể tốn tài nguyên. Dung lượng lớn hơn bản gốc không tự làm gói sai, nhưng không có bằng chứng cho dung lượng hoặc độ phân giải vô hạn.

## Đo thay vì đoán

Làm đối chứng giữa mẫu gốc, bản thay ảnh cùng kích thước, rồi bản lớn hơn. Giữ độ sáng, các công tắc AOD/FP, thời gian chờ và điều kiện sử dụng tương tự. Mỗi lượt ghi:

- Kích thước canvas, số frame, codec, byte của gói và khoảng frame cấu hình.
- Có cắt mép, nháy, sai màu, bỏ frame hoặc khựng lúc mở lần đầu không.
- Preview khác AOD thật/mở khóa thật ở đâu; công tắc phát sau mở khóa bật hay tắt.
- Lỗi trong log và khả năng khôi phục sau lượt thử.

Video quay màn hình có thể bỏ sót AOD/tốc độ thật hoặc làm tăng tải. Dùng quay ngoài nếu cần đối chiếu chuyển động thật; video 30/60 FPS vẫn không tự đo được lịch vẽ chính xác. Không gọi giá trị `1000/duration` là FPS đo được.

Theo dõi pin bằng các lượt đủ dài và lặp lại trong điều kiện gần nhau; vài phần trăm pin khác nhau trong một lượt ngắn chưa chứng minh tác động của mod. Hiện chưa có bộ số liệu pin hoặc nhiệt dài hạn cho các mẫu trong repo.

## OLED và thời gian hiển thị

Ưu tiên nền đen, độ sáng vừa phải và tránh vùng sáng lớn cố định quá lâu. Hình chuyển động không tự loại bỏ nguy cơ lưu ảnh; frame cuối hoặc đồng hồ vẫn có thể đứng yên. Giữ các cơ chế tiết kiệm điện/bảo vệ màn hình sẵn có, không chỉnh dịch vụ hay thời gian hệ thống chỉ để hoạt ảnh chạy mãi.

Chưa xác minh đầy đủ cơ chế dịch điểm ảnh/chống lưu ảnh cho mọi nhánh OriginOS 6. Repo không tuyên bố một mẫu an toàn OLED lâu dài chỉ vì đã chạy vài lần.
