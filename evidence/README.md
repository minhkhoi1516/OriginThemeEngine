# Dữ liệu đối chiếu đã lược bỏ nội dung riêng

[Mục lục](../README.md) · [Bảng phát hiện](../docs/11-bang-chung.md)

| Tệp | Nội dung |
|---|---|
| [provenance.json](provenance.json) | Tên, hash và vai trò các đầu vào nghiên cứu; binary giữ riêng |
| [aod-sample.json](aod-sample.json) | Số entry, cấu trúc ảnh, kết quả đối chiếu tar và ITZ |
| [aod-full-tree.txt](aod-full-tree.txt) | Cây đầy đủ mẫu ITZ và ZIP con |
| [fp-sample.json](fp-sample.json) | Listing gói Evolution và thuộc tính animation gốc |
| [fp-animation-tree.txt](fp-animation-tree.txt) | Danh sách trong animation.zip gốc |
| [fp-timing.json](fp-timing.json) | Vector đối chiếu phép chuyển thời gian native, chỉ phân tích tĩnh |
| [fp-marker-result.json](fp-marker-result.json) | Kết quả marker/khôi phục được tổng hợp từ log và phản hồi |
| [fp-wide-result.json](fp-wide-result.json) | Thông số bản wide và mức quan sát do chủ máy xác nhận |
| [video-20261108.json](video-20261108.json) | Hash, thông tin file và quan sát video mới; không chứa video |
| [code-map.md](code-map.md) | Nơi đối chiếu các kết luận trong bản mã đã phân tích |

Các file này là chứng cứ tóm tắt hoặc danh sách kỹ thuật, không phải gói cài. Một số hash tới từ audit đã lưu vì đầu vào gốc không còn tại đường upload ban đầu. Hash xác định byte của bản nghiên cứu; nó không chứng minh nguồn phát hành hay quyền sở hữu.

Raw APK, key, hình nhân vật và video cá nhân không được đưa lên nhánh tài liệu. Điều này giới hạn khả năng người đọc tái lập hoàn toàn nếu họ không có bản mẫu tương ứng; tài liệu không giấu giới hạn đó bằng cách gọi các bản tóm tắt là kiểm thử độc lập trên nhiều máy.
