# Khi sửa rồi mà chưa hoạt động

[Mục lục](../README.md) · [Khôi phục](08-ap-dung-va-khoi-phuc.md)

Trước tiên, xác định lỗi ở **tệp**, **cách nạp** hay **hình đang xem**. Giữ bản trước, không sửa nhiều thứ cùng lúc.

| Hiện tượng | Kiểm tra tiếp | Tránh làm |
|---|---|---|
| Không có root / `su` thất bại | `su -c 'id -u'`, quyền của đúng ứng dụng terminal, temp root còn không | Kết luận chỉ từ việc có icon SU; tìm exploit mới |
| `Directory metadata differs: 1080` | Đọc owner/mode/context hiện tại, đối chiếu bản ghi và trạng thái mẫu | Sửa tất cả thư mục thành 0777 |
| `File metadata differs: manifest.xml` | Kiểm tra file cụ thể; nghiên cứu từng thấy 0600 rồi 0777 ở các thời điểm | Bỏ kiểm tra metadata chỉ vì hash giống |
| FP đổi PNG nhưng vẫn hình gốc | XML còn tham chiếu atlas không; hash ZIP sau chọn lại; cache | Xóa mọi file KTX2 của nhóm 103 |
| Preview mới nhưng lúc chạm vẫn cũ | Bạn mới thay MP4/thumbnail hay payload; nhánh atlas/PNG nào được dùng | Coi video preview là hoạt ảnh runtime |
| Payload mới nhưng ảnh thẻ còn cũ | Tệp thumb, nguồn URL/local và cache của màn hình đó | Xóa toàn bộ dữ liệu iTheme |
| Chọn lại mẫu làm mất mod | Hash đích có trở về gốc; iTheme có tải/bung lại không | Cho rằng resize chắc chắn không được hỗ trợ |
| XML đúng nhưng hoạt ảnh trống | `animation.xml` có ở root ZIP, đường PNG, count, codec/palette/alpha | Đổi ngẫu nhiên `type`, settings ID hoặc quyền hệ thống |
| Hiệu ứng nhìn rất nhỏ | Bounding box nét vẽ, scale artwork, vùng preview thu nhỏ | Tăng mỗi canvas trống lên toàn màn hình |
| Ảnh bị cắt | Viewport/parent của AOD; atlas mesh hoặc canvas của FP | Cho rằng kích thước vật lý màn hình là trần duy nhất |
| AOD preview lặp nhưng AOD thật dừng | Khác mode preview/AOD và ảnh kết thúc | Tự thêm `loopCount` chưa có parser |
| Không thấy log sau khi grep | Log đã trôi, mức log, backend hoặc tag khác; kiểm tra thời điểm | Coi log trống là thành công hoặc thất bại |
| Mất root sau reboot | Dùng UI đổi/tắt mẫu nếu được, xem phương án tải lại | Mong mọi file tự về gốc khi mất SU |

## Thu log tối thiểu

Sau một lượt thử, lệnh dưới chỉ đọc buffer log rồi lọc các thông báo đã thấy trong mã FP:

```sh
su -c 'logcat -d -v threadtime | grep -E "prepare animation .*use atlas|animation with background .*use atlas|itheme press animation package xml loaded|no atlas in animation" | tail -n 80'
```

Không có dòng nào là kết quả hợp lệ của phép lọc, chưa phải kết luận về renderer. Không bật debug property hệ thống, không xóa log và không kill dịch vụ sinh trắc học để ép xuất log. Nếu cần đọc rộng hơn, chỉ xem đoạn ngắn quanh thời điểm thử và che dữ liệu cá nhân trước khi chia sẻ.

## Hash đúng nhưng hình vẫn cũ

Hash chứng minh byte ở đường đó. Nó không chứng minh bộ hiển thị đang mở đúng đường, đã bỏ ZIP cũ khỏi cache hoặc đã đi tới frame mới. Đối chiếu settings → filePath → hash sau thao tác UI → dấu nhận diện dễ thấy. Với AOD, kiểm tra cả `background.png` vì đó có thể là hình đang đứng sau lượt phát.

## Preview chạy được nhưng khóa màn hình có vấn đề

Dừng thử bản lớn hơn, quay lại mẫu trước qua phương án đã chuẩn bị. Ghi chính xác bối cảnh: preview, chạm khi màn hình sáng, AOD khi màn hình tắt, sau mở khóa. Những bối cảnh này không nhất thiết cùng lifecycle. Không thử bằng cách thay tham số nhận diện vân tay hoặc phần cứng quét.

## Dữ liệu đủ để người khác giúp

Model/build, phiên bản iTheme/NightPearlUI/FingerprintUI, loại mẫu, danh sách tệp liên quan, XML/JSON đã bỏ thông tin riêng, hash payload trước/sau, thay đổi duy nhất của lượt thử và quan sát thực tế. Không cần gửi APK hoặc cả `/data` nếu một cấu hình và listing đã đủ. Dùng [biểu mẫu](../templates/ket-qua-thu-nghiem.md).
