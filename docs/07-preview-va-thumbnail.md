# Preview, thumbnail và việc giữ tên tệp

[Mục lục](../README.md) · [Vân tay](05-fingerprint-co-che.md) · [Áp dụng](08-ap-dung-va-khoi-phuc.md)

## Có thể tùy biến nhiều hơn animation.zip

Trong gói FP Evolution đã kiểm tra, có ba nhóm hình riêng:

| Tệp | Vai trò tài nguyên | Mức xác minh |
|---|---|---|
| `content/animation.zip` | Payload hoạt ảnh mà loader FingerprintUI đọc | Listing + ZIP + đường mã + các bản thử |
| `preview/preview_0.mp4` | Video giới thiệu/xem trước của gói | Listing và metadata; chưa trace mọi màn hình tiêu thụ |
| `preview/preview_0_ff.jpg` | Ảnh tĩnh liên quan preview/first frame | Listing; tên/metadata phù hợp vai trò, chưa trace mọi UI |
| `thumb/thumb_0.jpg` | Ảnh thu nhỏ của gói | Listing; chưa trace mọi lớp cache/UI |

Với AOD mẫu, preview nằm ở lớp ngoài ITZ: `preview/preview_screenclock_0.jpg` và `preview/thumbnail_screenclock_0.gif`. Chúng không phải các frame `dynamic_bkg_anim_*` của payload.

Như vậy hướng tùy biến cả **hình thật + video giới thiệu + ảnh thẻ** có cơ sở cấu trúc. Chủ máy báo đã sửa preview được. Đây là mở rộng hợp lý của quy trình thay tài nguyên, nhưng từng tệp phải được kiểm tra theo bộ đọc của nó.

## Video được bổ sung ngày 11/11/2026

Video `VID_20261108_061622_590.mp4` dài khoảng 22,05 giây, bản gửi có khung 576 × 1280. Có màn hình lựa chọn hiệu ứng, khung preview, chuyển sang giao diện hình nền/đồng hồ, và hình động màu tím xuất hiện ở phần dưới, ngoài khung mô phỏng preview.

Video không quay thao tác sửa thư mục và không cung cấp hash trước/sau. Nó không tự xác định tên tệp gây ra từng hình, không chứng minh kích thước màn hình vật lý từ kích thước video, cũng không đo được giới hạn dung lượng hay ổn định dài hạn. [Biên bản quan sát](../evidence/video-20261108.json). Video cá nhân không được tải lên repo này.

## Cách sửa preview cho đúng

1. Mở **bản sao** thư mục của đúng hiệu ứng; đọc listing hiện tại để biết tên thật. Ví dụ tên `.jpg` trong mẫu trên không đảm bảo mọi gói đều dùng JPEG.
2. Sao lưu từng preview/thumbnail sắp đổi, kèm hash và metadata tệp. Backup chỉ `animation.zip` không khôi phục được preview bạn sửa sau đó.
3. Kiểm tra ảnh/video gốc bằng ứng dụng đọc thông tin media: codec, kích thước, tỷ lệ, thời lượng; nếu dùng máy tính, có thể dùng `ffprobe` trên bản sao.
4. Tạo nội dung mới theo tỷ lệ và định dạng của bản gốc cho lượt thử đầu. Ảnh `.jpg` phải thực sự là JPEG nếu bộ đọc đó đòi JPEG; đổi đuôi không phải chuyển đổi.
5. Giữ tên/vị trí, thay riêng một preview trước. Theo dõi đúng màn hình giới thiệu; không đồng thời đổi payload rồi đoán phần nào có tác dụng.
6. Nạp lại bằng luồng UI thông thường, so hash đích và xem ảnh hiển thị. Nếu vẫn hình cũ, có thể là cache hoặc UI dùng nguồn khác.
7. Khi một tệp đã được xác nhận, mới thử tệp còn lại và cập nhật bản ghi. Khôi phục từng tệp nếu UI không đọc được.

Chưa có thông tin codec của MP4 gốc đủ để quy định một lệnh encode chung. Cần giữ thông số đã kiểm tra trên máy của bạn trước, thay vì tuyên bố H.265/AV1 hay mọi MP4 đều chạy.

## Giữ tên giúp gì, và chưa đủ ở đâu?

Giữ tên giúp những tham chiếu cũ tiếp tục tìm thấy đúng vị trí. Nhưng tên chỉ là một điều kiện. File còn phải đúng định dạng, cấu trúc, quyền đọc, nhãn SELinux và đủ tài nguyên để giải mã; bộ hiển thị cũng phải nạp lại bản mới.

`/data/bbkcore/bbkthemeres/novoland/103/` là nhóm tài nguyên FP đã xác minh, **không phải vùng được bảo đảm sửa mọi thứ**. `key`, `description.xml`, metadata nhận dạng/xác thực và payload không có cùng vai trò với preview. Không mở rộng kết quả thay ảnh thành quyền thay dữ liệu tài khoản, đăng ký theme hoặc mọi file của nhóm 103.

Dung lượng mới có thể khác bản gốc; byte count cũ không tự là giới hạn. Nhưng file nén lớn hơn, ảnh có nhiều pixel hơn và số frame cao hơn là ba thay đổi khác nhau. Cần kiểm tra riêng, xem [hiệu năng](09-thiet-ke-va-hieu-nang.md).
