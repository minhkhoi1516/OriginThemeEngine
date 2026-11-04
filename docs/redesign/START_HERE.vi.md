# OTE — bộ bàn giao thiết kế lại, 2026-11-04

**OTE là công cụ sáng tạo AOD và hiệu ứng vân tay, không phải trình quản lý root.** SukiSU chỉ là tham khảo cách tổ chức giao diện. Chủ máy đã nhắc lại ranh giới này ngày 2026-11-04.

Bản baseline đã build trên Codemagic, người dùng đã mở được trên V2453A. Nhánh bàn giao này thay luồng làm việc và bổ sung nghiên cứu; chưa có APK của nhánh mới được biên dịch hoặc thử trên máy. Không gọi đây là toàn bộ bản viết lại đã hoàn thiện.

## Đọc theo thứ tự

1. [Yêu cầu sản phẩm và hai không gian](PRODUCT.vi.md).
2. [APK SukiSU và lựa chọn thư viện](UI_RESEARCH.vi.md).
3. [Bằng chứng AOD/FP, giới hạn đã biết](DEVICE_RESEARCH.vi.md).
4. [Kiến trúc và quy trình áp dụng](ARCHITECTURE.vi.md).
5. [AI dùng chung cấu hình, luật riêng theo loại](AI_DESIGN.vi.md).
6. [Trạng thái mã, việc cần nối và tiêu chí nghiệm thu](IMPLEMENTATION.vi.md).
7. [Mở trên PC, kiểm thử, ký APK](PC_HANDOFF.vi.md).
8. [Prompt giao việc cho Codex PC/agent khác](../../prompts/PC_CONTINUATION.vi.md).

## Phần đã viết trong nhánh này

- Trang đầu chọn AOD hoặc FP; bốn mục chính: Sáng tạo, Áp dụng, Thiết bị, Cài đặt.
- Studio lọc đúng loại tài nguyên; nhập GIF/PNG trong Studio AOD giữ loại AOD; mở đúng mục từ preview.
- Trang áp dụng riêng, có preview, chọn tác phẩm và hướng dẫn mẫu FP đã nghiên cứu; vẫn dùng adapter kiểm tra/sao lưu cũ.
- Quét metadata ITZ và FP đã tải ở hai vị trí được ghi nhận, theo nút bấm. Không quét toàn bộ `/data`.
- Sửa kết quả kiểm tra root/compatibility và lịch sử bị báo thành công sai; dịch thông báo lỗi SU.
- Studio chia Timeline / Khung hình / Biến đổi.
- Báo cáo đọc APK, chứng cứ kỹ thuật, giao kèo AI và prompt tiếp tục.

**Còn thiếu:** editor AOD có đồng hồ thật/pin, lưu dự án nhiều lớp, lấy nội dung từ danh sách nguồn vào thư viện, adapter ghi AOD trong APK, tích hợp thư viện Miuix/Glasmio thật, API AI, kiểm thử áp dụng/khôi phục từ APK. Bản hiện tại vẫn dùng UI Compose Material với style nội bộ; không được đổi nhãn để giả thành tích hợp thư viện mới.

## Những điều không được mất khi chuyển máy

Giữ nguyên lịch sử git, mã giao dịch/rollback và kiểm thử. Giữ bản gốc tài nguyên ngoài repo công khai; đưa chúng vào thư mục private theo [input manifest](../../research/inputs.json). Không đưa key iTheme, ảnh tác giả, APK Vivo/SukiSU hay khóa ký/API lên GitHub. Không cần thu thập lại phân vùng điện thoại. Root tạm thời vẫn đủ cho các bước đọc/thử tài nguyên được giới hạn; không reboot để giải quyết mọi lỗi.
