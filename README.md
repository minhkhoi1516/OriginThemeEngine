# Origin Theme Engine — Sổ tay AOD & hiệu ứng vân tay

Tài liệu tiếng Việt về cách tài nguyên AOD và hiệu ứng vân tay tải từ iTheme hoạt động trên **OriginOS 6 China**, và cách tùy biến chúng bằng quyền root hiện có.

Từ ngày **11/11/2026**, nhánh `main` chuyển thành kho tài liệu nghiên cứu. Dự án hiện không phát triển hay cung cấp APK quản lý. Mã ứng dụng trước đây được giữ ở [nhánh lưu trữ](docs/12-lich-su-du-an.md).

Mục tiêu là để bạn hiểu **đang sửa tệp gì, thành phần nào đọc nó, thay đổi đó xuất hiện ở đâu, và quay về bản trước bằng cách nào**. Bạn có thể đọc và thao tác chuẩn bị trên điện thoại; không cần máy tính để bắt đầu.

## Chọn việc bạn muốn làm

| Tôi muốn… | Bắt đầu ở đây |
|---|---|
| Hiểu tổng thể, chưa biết ITZ/atlas là gì | [Bắt đầu](docs/01-bat-dau.md) |
| Kiểm tra máy vivo/iQOO của mình có cùng cơ chế không | [Tương thích và lệnh chỉ đọc](docs/02-thiet-bi-va-duong-dan.md) |
| Hiểu gói AOD `.itz` | [Cấu trúc và bộ hiển thị AOD](docs/03-aod-co-che.md) |
| Làm AOD từ GIF, bo góc, đổi chữ số hoặc vị trí pin | [Tùy biến AOD](docs/04-aod-tuy-bien.md) |
| Hiểu `animation.zip`, PNG và KTX2 | [Cấu trúc và bộ hiển thị vân tay](docs/05-fingerprint-co-che.md) |
| Tạo hiệu ứng vân tay mới, đổi kích thước và tốc độ | [Tùy biến hiệu ứng](docs/06-fingerprint-tuy-bien.md) |
| Đổi video preview và ảnh thumbnail | [Preview, thumbnail và tên tệp](docs/07-preview-va-thumbnail.md) |
| Đưa bản sửa vào máy hoặc khôi phục | [Áp dụng và khôi phục](docs/08-ap-dung-va-khoi-phuc.md) |
| Làm hoạt ảnh đẹp, mượt và kiểm tra dung lượng | [Thiết kế và hiệu năng](docs/09-thiet-ke-va-hieu-nang.md) |
| Sửa rồi nhưng chưa thấy thay đổi | [Chẩn đoán](docs/10-loi-thuong-gap.md) |
| Kiểm tra nguồn của một kết luận | [Bằng chứng và câu hỏi còn mở](docs/11-bang-chung.md) |

## Đã biết đến đâu?

| Nội dung | Mức bằng chứng hiện có |
|---|---|
| AOD mẫu là ZIP chứa một ZIP tài nguyên; có cấu hình và chuỗi ảnh | Kiểm tra trực tiếp tệp |
| Tài nguyên AOD đã bung có thể thay bằng hoạt ảnh riêng | Chủ máy báo bản Waguri chạy được; đối chiếu gói và dữ liệu gốc |
| Đồng hồ số, ngày tháng, vị trí pin có đường xử lý trong mã | Đã phân tích NightPearlUI; chưa thử toàn bộ tùy chọn trên máy |
| FP tải ngoài có nhánh atlas và nhánh PNG rời | Đã phân tích Java/native; thử PNG có dấu nhận diện được chủ máy nhìn thấy |
| Bản FP 1080 × 600, 27 frame chạy được | Chủ máy xác nhận; chưa có phép đo FPS, pin hoặc giới hạn tối đa |
| Preview/thumbnail là những tài nguyên riêng trong gói FP | Listing từ máy xác nhận tên tệp; chủ máy báo đã tùy biến được |
| Mọi máy vivo/iQOO OriginOS 6 đều tương thích | **Chưa xác minh**; cần kiểm tra theo từng máy/build |
| Có thể sửa mọi tệp trong `103`, dung lượng tăng bao nhiêu cũng được | **Không phải kết luận của nghiên cứu** |

Thiết bị đã có thử nghiệm là **iQOO Z10 Turbo Pro**, Android 16, OriginOS 6 China, build `PD2453_A_16.1.17.1.W10.VO00L1`. Tài liệu hướng đến các máy vivo/iQOO cùng hệ, nhưng chỉ chuyển giao **cách kiểm tra**, không mặc định chuyển giao mọi đường dẫn, quyền tệp hay giới hạn.

Các thay đổi được nghiên cứu nằm trong dữ liệu tài nguyên `/data/bbkcore/...`. Chúng có ghi vào vùng dữ liệu `/data`; không nên gọi là “không ghi phân vùng nào”. Quy trình ở đây không yêu cầu flash hoặc sửa `boot`, `system`, `vendor`, `vbmeta`, APK hệ thống hay dữ liệu xác thực vân tay. Tệp hình lỗi vẫn có thể làm bộ hiển thị lỗi; đọc [quy trình khôi phục](docs/08-ap-dung-va-khoi-phuc.md) trước khi áp dụng.

Repo công khai bản mô tả, bảng đối chiếu và dấu vân tay SHA-256 của mẫu. Không đính kèm APK hệ thống, khóa của gói, theme thương mại, tài nguyên nhân vật hoặc video cá nhân. [Cách đóng góp kết quả](CONTRIBUTING.md).
