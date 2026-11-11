# Bằng chứng, kết quả và phần còn mở

[Mục lục](../README.md) · [Dữ liệu đối chiếu](../evidence/README.md)

Tài liệu này tổng hợp nhiều lượt nghiên cứu. Báo cáo sớm từng ghi “chưa có native”, “chưa thử PNG”; những bước sau đã bổ sung native và thử nghiệm. Không chép nguyên các mốc cũ thành trạng thái hiện tại. Mỗi kết luận dưới đây ghi rõ nó dựa vào tệp, mã hay phản hồi thiết bị.

## AOD

| Mã | Phát hiện | Nguồn / giới hạn |
|---|---|---|
| A01 | Mẫu ITZ là ZIP lồng ZIP; 135 tệp payload, pattern 4 | Audit container; [cây đầy đủ](../evidence/aod-full-tree.txt) và [số liệu](../evidence/aod-sample.json) |
| A02 | Thư mục đã bung trên máy khớp từng byte với payload mẫu | Tar do chủ máy cung cấp: 135/135 tệp trùng; không suy rằng metadata/xattr đều được tar giữ |
| A03 | Thay tài nguyên Waguri hiển thị được, vùng nhân vật lớn hơn GIF tự tạo | Phản hồi chủ máy; không có phép đo viewport/FPS/pin |
| A04 | ITZ đã tải vẫn là bản gốc sau nghiên cứu thay tài nguyên | Hash từ lệnh trên máy khớp mẫu; không suy trạng thái thư mục hiện tại từ hash ITZ |
| C01 | Nhánh GIF tự tạo, pattern 4, thời gian frame, vị trí chữ số/pin và giới hạn bố cục | NightPearlUI 8.0.4.2; [đường mã](../evidence/code-map.md); không đại diện mọi bản OriginOS |

## Vân tay

| Mã | Phát hiện | Nguồn / giới hạn |
|---|---|---|
| F01 | ResItem trỏ tới gói nhóm 103; có payload, preview và thumb riêng | Settings + listing từ máy, [cấu trúc](../evidence/fp-sample.json) |
| F02 | Evolution có 26 PNG và atlas XML/KTX2 | ZIP gốc, CRC; [cây](../evidence/fp-animation-tree.txt) |
| C02 | FingerprintUI đọc `filePath/content/animation.zip`, chọn atlas hoặc PNG | APK 3.1.36.118_beta; parser tải ngoài khác parser gói tích hợp |
| C03 | Native có nhánh frame/atlas và duration theo mili giây | Phân tích tĩnh libvkrenderer, [vector thời gian](../evidence/fp-timing.json); không phải chạy lại thư viện Android trên host |
| F03 | Trial control/marker áp dụng và khôi phục; chủ máy thấy dấu caro | Log/hashes và phản hồi; bề mặt thấy dấu chưa được chỉ rõ preview hay mở khóa |
| F04 | Angel wide 1080 × 600, 27 frame, 33 ms không bị cắt theo chủ máy | [Biên bản phản hồi](../evidence/fp-wide-result.json); không phải đo kích thước tối đa |
| F05 | Chủ máy báo sửa preview được; video có hiệu ứng ở khung preview và phần dưới giao diện | [Quan sát video mới](../evidence/video-20261108.json); không có diff tệp hoặc đo hiệu năng |

## Những điều chưa được xác minh

| Câu hỏi | Cách bổ sung đúng trọng tâm |
|---|---|
| Máy vivo/iQOO khác có cùng cơ chế? | Model/build, phiên bản parser, cấu trúc mẫu, thử một thay đổi và khôi phục trên máy đó |
| Màn hình preview cụ thể đọc file local hay nguồn khác? | Tên màn hình, listing, hash từng tệp trước/sau, thay một preview mỗi lần, kiểm tra cache |
| Độ phân giải/frame/dung lượng tối đa của FP? | Tăng từng biến từ mẫu đã chạy; ghi codec, số frame, RAM/lỗi và hành vi thật |
| Làm AOD vượt tiếp viewport pattern 4? | Đo vùng vẽ thật hoặc phân tích một pattern khác đang hoạt động; không chỉ tăng JSON width |
| Đổi icon pin AOD theo mức pin thật? | Cần tìm bộ đọc tài nguyên và nguồn cập nhật tương ứng; sticker chưa giải quyết được |
| Nhập icon vân tay đứng chờ tùy ý? | Cần nhánh nhận ảnh tùy ý và điều kiện bật; đường resource-name đã đọc chưa đủ |
| Tạo ITZ với ID mới và cài hợp lệ? | Cần quy trình nhập/phát hành được hỗ trợ và điều kiện xác thực; chưa có trong repo |
| Cache được làm mới chính xác lúc nào? | Trace lifecycle/reader và thao tác UI trên từng phiên bản; rename không giải quyết mọi cache |
| Pin, nhiệt, lưu ảnh lâu dài? | Đo có đối chứng và nhiều lần; chưa có số liệu đủ để khẳng định |

Không cần cung cấp lại các tệp đã phân tích chỉ để đọc tài liệu. Nếu nghiên cứu tiếp preview mới, dữ liệu hữu ích nhất là **listing của đúng gói và bảng tên/hash/kích thước trước–sau**, kèm màn hình nào thay đổi. Không cần toàn bộ phân vùng.

## Kiểm chứng tài liệu này

Danh mục [provenance](../evidence/provenance.json) ghi hash bản đầu vào. Các file JSON công khai là bản tóm tắt có chọn lọc, không thay thế raw APK/log chưa phát hành. Bản đồ đường mã ghi tên phương thức/offset để người có đúng bản riêng có thể kiểm tra lại; offset thay đổi khi phiên bản thay đổi và không phải vị trí để patch binary.

Không xuất hiện một cơ chế trong các nhánh đã rà không chứng minh nó không tồn tại ở mọi nơi. Ngược lại, chuỗi tên, một getter hoặc UI đẹp cũng chưa chứng minh tính năng đã dùng được.
