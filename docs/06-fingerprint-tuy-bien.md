# Tùy biến hiệu ứng vân tay bằng chuỗi PNG

[Mục lục](../README.md) · [Cơ chế](05-fingerprint-co-che.md) · [Áp dụng](08-ap-dung-va-khoi-phuc.md)

Hướng đã thử là sửa `content/animation.zip` của mẫu tải hợp lệ, giữ đường dẫn gói mà hệ thống đang tham chiếu. Chuỗi PNG giúp thay hình mà không phải dựng lại atlas/mesh KTX2. Không có bước sửa APK hoặc native library.

## Bước 1 — Xác nhận nhánh PNG với ảnh gốc

Làm trên bản sao ZIP, không mở sửa trực tiếp ZIP đang được bộ hiển thị đọc. Với mẫu Evolution, giữ các PNG gốc và sửa **chỉ khai báo atlas** trong `animation.xml` để cấu hình không còn chọn atlas:

```xml
<?xml version="1.0" encoding="utf-8"?>
<animation type="18" name="evolution"
    texture="evolution/evolution_{n}.png"
    count="26" duration="32" height="600" width="600"/>
```

Đây là cấu trúc phép thử PNG-only của mẫu đã nghiên cứu. Có thể giữ nguyên các tệp atlas không còn được XML tham chiếu trong lần đầu, để giảm số thay đổi. Không xóa/đổi tên atlas tùy tiện rồi trông chờ mọi parser tự fallback.

Đóng ZIP sao cho `animation.xml` nằm ngay root, không bị lồng thành `my-effect/animation.xml`. Kiểm tra CRC, mở lại và kiểm tra đủ 26 ảnh. [Quy trình áp dụng](08-ap-dung-va-khoi-phuc.md) giải thích cách kiểm tra byte, nạp lại và quay về bản gốc.

Nếu chưa phân biệt được bản mới đang chạy, dùng vài frame có dấu nhỏ dễ nhận diện ngoài tâm. Không kết luận chỉ từ việc ZIP mới tồn tại hoặc preview nhìn giống bản gốc.

## Bước 2 — Chuẩn bị hình riêng

Chuỗi làm việc gợi ý:

```text
fp-project/                 ← thư mục riêng do bạn tạo
├── original/               ← bản tải về, giữ nguyên
├── source/                 ← file dự án và ảnh chất lượng cao
├── working/                ← cấu trúc ZIP đang sửa
│   ├── animation.xml
│   └── evolution/
│       └── evolution_1.png … evolution_N.png
├── output/                 ← animation.zip và bản preview
└── notes.txt               ← những gì đã đổi, hash, kết quả
```

1. Giữ bản gốc RGBA để chỉnh sửa, bảo toàn màu và độ trong suốt.
2. Xuất frame cùng canvas, đánh số liên tục theo XML. Không sắp thứ tự từ điển kiểu 1, 10, 11, 2.
3. Với backend đã nghiên cứu, đầu ra thử thành công là **PNG indexed 8-bit, color type 3, PLTE + tRNS, không interlace**. RGBA là nguồn làm việc tốt nhưng chưa phải bảo đảm mọi PNG RGBA trực tiếp đều tương thích.
4. Bộ chuyển palette phải giữ alpha bằng `tRNS`, không chỉ làm một màu hoàn toàn trong suốt. Kiểm tra gradient và viền trên nền đen lẫn nền sáng; palette 256 mục có thể làm mất chi tiết.
5. Đừng chạy công cụ “tối ưu PNG” rồi mặc định codec không đổi. Mở lại ảnh sau tối ưu, kiểm tra color type, palette và alpha.
6. Khi thay số frame, cập nhật `count`; khi đổi canvas, cập nhật `width/height` khớp ảnh. Đổi một nhóm mỗi lượt thử.

## Bước 3 — Kích thước và vị trí

Phân biệt kích thước canvas với **vùng có nét vẽ**. Canvas 1080 × 600 chứa một hình 80 × 80 ở giữa vẫn nhìn bé. Hãy xem bounding box các pixel có alpha trước khi tăng canvas hoặc tăng độ phân giải.

Để làm hình lớn và giữ đủ nội dung, scale toàn bộ bố cục giữ tỷ lệ, chừa biên cho hạt/ánh sáng và dùng canvas phù hợp. Không kéo giãn chiều ngang riêng, không cắt các frame giữa chừng cho vừa một khung cứng.

Nếu cần chỉnh điểm phát trong artwork, dịch lớp hình **bên trong frame**. Không suy ra hoặc sửa tọa độ cảm biến từ vị trí hình. Tâm canvas cũng không phải tọa độ vật lý chung cho mọi điện thoại; đối chiếu bằng một hiệu ứng thử nhỏ và UI gốc của máy.

Bản Angel đã thử có canvas **1080 × 600**, 27 frame, 33 ms/frame. Chủ máy báo không bị cắt. Điều này chưa chứng minh hỗ trợ 1260 × 2800 hoặc mọi kích thước lớn hơn. Số inch của màn hình không phải giới hạn phần mềm có thể dùng để suy ra canvas.

## Bước 4 — Tốc độ, mượt và kết thúc

- 50 ms ≈ 20 FPS danh nghĩa; 40 ms ≈ 25; 33 ms ≈ 30,3. FPS thực phải đo riêng.
- Muốn chuyển động mượt, nguồn phải có nhịp tốt. Giảm `duration` mà giữ ít frame sẽ làm hoạt ảnh chạy nhanh, chưa chắc mượt hơn.
- Root animation của mẫu dùng một khoảng frame. Chưa có cơ chế keyframe/easing/layer particle tùy ý được xác minh trong XML này: dựng chúng trước thành ảnh.
- Có thể giữ frame thêm một nhịp bằng cách lặp ảnh trong chuỗi, rồi cập nhật count. Đó là chỉnh timeline ngoại tuyến, không phải thêm key duration-per-frame chưa được chứng minh.
- Fade ở cuối giúp tránh ngắt hình. Bản Angel wide từng đặt frame cuối hoàn toàn trong suốt; không coi đây là yêu cầu bắt buộc cho mọi loại hiệu ứng.
- Công tắc “Tiếp tục phát hình động sau khi mở khóa” ảnh hưởng bối cảnh thử. Ghi trạng thái công tắc cùng kết quả.

## Bước 5 — Gói đầu ra

ZIP chứa XML và ảnh, không chứa script. Dùng cách đóng ZIP chuẩn, không mật khẩu; không dùng đường tuyệt đối, `..`, symlink hoặc entry trùng. Giữ `animation.xml` đúng tên/vị trí và mọi tham chiếu hợp lệ. Giữ `content/animation.zip` đúng tên ngoài gói khi áp dụng.

Tên bên trong có thể thay **chỉ khi** mọi tham chiếu được sửa nhất quán và đã kiểm tra parser; lần đầu nên giữ tên gốc. Tên gói/ID trong metadata bên ngoài là việc khác, không đổi để trang trí tên hiệu ứng.

Kiểm tra ảnh, số frame, XML, CRC và hash trước khi đưa vào máy. Sau khi chạy được mới [làm preview/thumbnail tương ứng](07-preview-va-thumbnail.md). Đừng dùng video preview đẹp để thay cho phép thử hoạt ảnh thật.
