# Áp dụng và khôi phục trong vùng tài nguyên

[Mục lục](../README.md) · [Kiểm tra đường dẫn](02-thiet-bi-va-duong-dan.md) · [Chẩn đoán](10-loi-thuong-gap.md)

Đây là quy trình có phạm vi rõ để dùng với một mẫu đã nghiên cứu. Repo không phát hành script root áp dụng hàng loạt cho mọi máy. Công cụ thay tệp phải đáp ứng các bước dưới đây; một trình quản lý tệp có nút “ghi đè” nhưng không kiểm tra owner/context hoặc cách ghi ZIP chưa đủ để coi là tương đương.

## 1. Xác nhận điểm bắt đầu

Máy đang mở khóa; biết PIN/mật khẩu dự phòng; mẫu gốc hiển thị bình thường. Đọc đúng style, đường đích, hash và phiên bản. Kiểm tra root ngay trước thao tác. Nếu đường không tồn tại, là symlink ngoài phạm vi dự kiến, metadata không khớp hoặc đang tải/cập nhật gói, dừng ghi và xác định lại.

Lưu ba vị trí riêng: **bản trước**, **bản làm việc**, **bản chuẩn bị áp dụng**. Không sửa trực tiếp bản trước. Ghi rõ bản trước là nguyên gốc hay một mod đang chạy được; “khôi phục gần nhất” không tự nghĩa là trở về Vivo gốc.

## 2. Sao lưu nội dung và ghi metadata

Với root file manager, sao chép đúng thư mục mẫu hoặc đúng các tệp sẽ sửa ra thư mục riêng trong Download, kiểm tra mở được và đối chiếu SHA-256. Không sao chép toàn bộ `/data` hoặc lấy dữ liệu ứng dụng không liên quan.

Nếu dùng shell, hai ví dụ dưới đây **tạo file backup trong Download**, không thay nguồn. Chọn tên đầu ra chưa tồn tại; `tar -cf` có thể ghi đè archive cùng tên. Mỗi lệnh là một lựa chọn theo mẫu, không cần chạy cả hai nếu chỉ sửa một loại:

```sh
su -c 'tar -cf /sdcard/Download/AOD-before-20261111-01.tar -C /data/bbkcore/screenclock screenclock'
su -c 'tar -cf /sdcard/Download/FP-Evolution-before-20261111-01.tar -C /data/bbkcore/bbkthemeres/novoland/103 "进化"'
```

Các đường này đã được xác nhận trên máy nghiên cứu. Chỉ dùng sau khi kiểm tra nguồn của chính bạn; các archive có thể chứa `key`, hãy giữ riêng, không đính kèm công khai. Nếu shell không truy cập được `/sdcard/Download`, không tự chuyển sang vị trí hệ thống khác.

Đọc lại danh sách archive và hash, ví dụ cho AOD:

```sh
tar -tf /sdcard/Download/AOD-before-20261111-01.tar
sha256sum /sdcard/Download/AOD-before-20261111-01.tar
```

Danh sách mở được chưa chứng minh đủ byte: giải nén vào thư mục làm việc riêng và so hash từng tệp cần khôi phục với nguồn trước sửa. Không thử giải nén backup đè trực tiếp vào đường đang dùng để “kiểm tra”.

**Tar hoặc bản sao trên bộ nhớ chia sẻ không tự bảo toàn SELinux context/xattr.** Ghi riêng đường tương đối, uid/gid dạng số, mode, SELinux context và hash bằng listing hiện tại. Có thể dùng `ls -lanZR` giới hạn ở thư mục mẫu; không đọc nội dung `key` ra log công khai.

Ở AOD từng thấy file 0600, thư mục 0700; lần sau lại có file/thư mục 0777. FP mẫu từng là uid/gid 10255, mode 0777, context `u:object_r:vivo_data_file:s0:c512,c768`. Đây là **quan sát lịch sử**, không phải lệnh đặt quyền chuẩn. Không áp `chmod -R 777`, `chown -R` hoặc copy context của máy khác để vượt lỗi kiểm tra.

## 3. Chuẩn bị bản mới ngoài nguồn đang chạy

Kiểm tra XML/JSON, đường tham chiếu, ảnh, số frame và archive. Đóng ZIP ở thư mục làm việc, mở lại, kiểm tra CRC và hash. Gói bên ngoài không được chứa script cần chạy bằng root. Ước lượng đủ chỗ cho backup, bản mới và tệp staging; đo dung lượng trống ở đúng filesystem đích.

Chuẩn bị bản kế hoạch trước khi ghi:

| Mục | Phải biết |
|---|---|
| Đích | Đúng một gói/nhánh tài nguyên đã xác minh |
| Tệp thay | Danh sách rõ ràng, không wildcard rộng |
| Trạng thái cũ/mới | Hash trước và hash dự kiến |
| Metadata | Owner, group, mode, SELinux label của từng tệp |
| Tệp thêm | Danh sách riêng, để khôi phục không để lại rác |
| Tệp khôi phục | Đọc được, hash khớp, biết cách đưa lại đúng metadata |

## 4. Thay tài nguyên FP: chú ý ZIP đang mở

Trên máy thử, chọn **Không có** bằng UI trước khi thay, sau đó chọn lại mẫu để thử. Giá trị style 100 từng tương ứng lựa chọn đó trên máy này; không ghi `settings put ... 100` để áp sang mọi máy.

FingerprintUI có lớp cache nguồn ZIP và bộ đọc có thể giữ file descriptor. Vì vậy không dùng ghi đè từng byte (`cat` vào ZIP đích hoặc mở ZIP đích chỉnh trực tiếp) trong khi nó còn được đọc.

Trình thay tệp phù hợp cần làm theo thứ tự:

1. Lấy khóa thao tác riêng của nó để tránh hai lần sửa đồng thời; vẫn phải tránh iTheme đang cập nhật mẫu.
2. Kiểm tra lại hash/metadata của đích, không chỉ dùng kết quả từ vài phút trước.
3. Chuẩn bị **toàn bộ ZIP mới** thành tệp staging cạnh đích, cùng filesystem. Đây đã là thao tác ghi vào `/data`, dù chưa thay file đang dùng.
4. Đặt owner/group/mode/context của staging theo dữ liệu đích đã xác minh; so lại, không giả định `cp -p` giữ mọi nhãn.
5. Kiểm tra hash/CRC staging; đối chiếu lần cuối đích vẫn là phiên bản đã cho phép thay.
6. Thay bằng rename trong cùng filesystem. Rename làm việc chuyển tên nguyên khối; không tự xóa cache, không bảo đảm tiến trình đã mở ZIP cũ chuyển sang bản mới.
7. So hash/metadata đích mới. Nếu sai, phục hồi từ backup hợp lệ bằng cùng quy trình có kiểm tra.
8. Chọn lại hiệu ứng qua UI để kiểm tra. So hash sau lựa chọn: iTheme có thể giải nén lại hoặc tải lại, làm mất bản sửa.

Các trial FP trước đây đã có kiểm tra, staging, rename và khôi phục trên máy nghiên cứu. Điều đó không biến mọi file manager thành một trình thay tệp có cùng bảo đảm, cũng không tạo giao dịch nguyên khối cho nhiều tệp cùng lúc. Tính bền vững khi mất điện giữa chừng chưa được kiểm chứng.

## 5. Thay AOD và preview

AOD đã bung gồm nhiều ảnh/config. Nếu thay nửa chừng, bộ hiển thị có thể thấy cấu hình mới nhưng frame cũ, hoặc ngược lại. Tạm ngừng AOD qua lựa chọn UI đang có trước khi thay; chuẩn bị đủ toàn bộ tập thay đổi trước. Tắt AOD không chứng minh mọi file handle đã đóng, nên công cụ vẫn cần kiểm tra và ghi có kiểm soát.

Với lần thử đầu, chỉ thay nội dung của bộ tên sẵn có. Đổi config hoặc thêm file là bước riêng. Khôi phục phải trả lại mọi file đã thay và xử lý **đúng các file mới do lần thử tạo ra**, không xóa hàng loạt thư mục.

Đừng mặc định chọn lại mẫu trong iTheme chỉ có nghĩa “reload”: nó có thể bung lại dữ liệu gốc. Kiểm tra hash sau thao tác UI. Nếu bản mod bị thay bằng bản gốc, đó là vấn đề vòng áp dụng, chưa phải bằng chứng cấu hình mod bị engine từ chối.

Preview/thumbnail cũng cần giữ metadata và kiểm tra cache, nhưng mỗi ảnh/video là tệp riêng, không nhất thiết do cùng bộ đọc với animation. Áp dụng từng nhóm và ghi rõ UI nào đã đổi.

## 6. Thử thực tế

Xem preview khi máy còn mở khóa, sau đó mới tắt **màn hình** để kiểm tra AOD hoặc thử mở khóa một lượt. Tắt màn hình khác tắt nguồn/khởi động lại; reboot có thể làm mất temp root. Không kill/force-stop tiến trình biometrics hoặc tắt SELinux để ép reload.

Ghi kết quả riêng cho: preview, AOD thật, hiệu ứng chạm, sau mở khóa, hash đích, lỗi hiển thị. Hình hoạt động một lượt chưa xác nhận ổn định lâu dài. [Biểu mẫu thử](../templates/ket-qua-thu-nghiem.md).

## 7. Khôi phục

- Khi còn root: dùng backup đã kiểm hash, đưa lại đúng tệp và metadata, kiểm tra byte rồi dùng luồng UI đã thử để nạp lại. Kiểm tra hình thật, không chỉ dòng “restore thành công”.
- Nếu mất root: dùng lựa chọn giao diện sẵn có để đổi/tắt hiệu ứng nếu còn truy cập được. Dữ liệu sửa có thể vẫn còn; không hứa nó tự khôi phục khi mất SU.
- Xóa **đúng mẫu** bằng UI rồi tải lại đã được chủ máy báo có hiệu quả với hiệu ứng tải từ Vivo. Đây là phương án bổ sung, phụ thuộc còn mạng, tài khoản/quyền tải và mẫu còn trên server; không bảo đảm khi UI không mở được.
- Không có đường khôi phục hoạt động thì không tăng kích thước/đổi nhiều tệp tiếp. Không dùng reboot như bảo đảm cứu lỗi trong môi trường temp root.

Không có lệnh flash, remount hệ thống, đổi dữ liệu sinh trắc học hoặc tắt kiểm tra bảo mật trong quy trình này. Nếu công cụ yêu cầu các thao tác đó để thay ảnh, nó đã vượt phạm vi nghiên cứu hiện tại.
