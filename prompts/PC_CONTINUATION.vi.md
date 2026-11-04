# Tiếp nhận Origin Theme Engine trên PC

Bạn tiếp tục dự án OTE từ nhánh `redesign/aod-fp-workspaces`, không khởi tạo lại dự án trắng. Đọc docs/redesign/START_HERE.vi.md, MVP_STATUS.json, research/inputs.json và code thực trước khi kết luận. Kiểm tra branch/head/git status và giữ các thay đổi của người dùng.

Ý định chủ dự án: một ứng dụng sáng tạo gồm hai workspace AOD và hiệu ứng vân tay (FP; trước đó có gọi FG), một bảng áp dụng chung và trang Thiết bị cho tương thích/nguồn/khôi phục. SukiSU chỉ là mẫu UI. KHÔNG biến OTE thành Root Manager; không quản lý quyền SU của app khác, module, kernel, flash hoặc quyền sinh trắc học.

Thiết bị chính V2453A/iQOO Z10 Turbo Pro/OriginOS6 China/Android16/PD2453_A_16.1.17.1.W10, temp root BakaSU/ReSukiSU có thể mất khi reboot. Không ghi boot/system/vendor/vbmeta/blockdevices, không sửa APK/lib hệ thống, enrollment/sensor calibration/KeyMint/Weaver/Gatekeeper, settings ownership/billing/DB iTheme. Root chỉ là transport cho read-only source và giao dịch tài nguyên đã xác minh trong dữ liệu theme.

Ưu tiên:
1. Build source hiện tại bằng JDK17/SDK36 và Gradle8.11.1; chạy tất cả unit/lint phù hợp. Không lặp lại lỗi JUnit coroutine trả exception hay ImageIO trong Android module đã sửa; PNG tests nằm engine:png.
2. Kiểm luồng AOD/FP tách biệt, review không ghi, mấtroot không thành công, source catalog read-only. Hoàn thiện persistence/autosave/undo và copy SourceSnapshot có giới hạn/hash/race checks.
3. Xây AodDocument giữ raw config/unknownfields + typed supported edits từ pattern4 research. Clock glyph/time/battery position có bằng chứng; custom live battery icon chưa có. Không biến1260×2800 thành viewport AOD mặc định. Preview mode khác real AOD. Full ITZ export/import xác thực chưa chứng minh.
4. FP chỉ dùng đúng profile đã nghiên cứu; unknown template là unsupported cho ghi. Bảo toàn snapshot/journal/rollback. Không coi scan file hoặc state3 database là quyền áp dụng/bản quyền.
5. Tích hợp Miuix/Material/Glasmio sau dependency/toolchain spike có log. Dùng nguồn thật và pin version; không gọi gradient hiện tại là Glasmio. Các upstream có thể cần JDK21/SDK37/NDK, khác baseline; nâng riêng và giữ khả năng revert. Không copy SukiSU GPL/UI assets vô điều kiện.
6. AI là optional, nhập key một lần cho provider do người dùng chọn; luật theo document.kind. Chốt schema + validator + local scene compiler trước networking. Không hardcode key/free model giả; không AI-to-root; không đánh đồng LLM text với image generator. Xem AI_DESIGN và hai prompt luật. Nếu cấu hình provider thật cần secret, dùng cơ chế nhập an toàn, không yêu cầu dán key vào hội thoại.

Mục tiêu là triển khai thật theo acceptance checklist, không một loạt scaffold giả. Tính năng còn thiếu phải unavailable có lý do. Một loạt source checks không phải APK build. Screenshot của bản cũ chỉ chứng minh mở UI/preset; các thử .sh trước không chứng minh giao dịch từ APK. Nếu thiếu chứng cứ, tìm đúng tệp trong private-research và input manifest trước; chỉ hỏi thêm phần thực sự thiếu, không xin dump toàn partition.

Bàn giao mỗi bước: commit tập trung, test report, source/design update, APK khi build được, giới hạn chính xác. Đừng publish signing keys/API keys/APKs proprietary hoặc nguyên liệu tác giả lên repo công khai. Repo này có GitHub Actions bị khóa cấp tài khoản; build local hoặc Codemagic manual, không tạo account né khóa.
