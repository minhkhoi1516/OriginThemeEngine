# AI tùy chọn — cấu hình một lần, luật theo AOD/FP

Đây là **đặc tả**, chưa có HTTP/API client hoặc API key trong APK bàn giao. Lượt này không gọi model, không tạo key và không phát sinh phí. Chức năng chỉnh sửa thủ công phải dùng được khi AI tắt hoặc quota hết.

## Cấu hình

Một khu vực Nhà cung cấp AI trong Cài đặt: provider, endpoint đã xác minh, model và key do người dùng nhập trên máy. Key lưu mã hóa trong storage riêng, encryption key từ Android Keystore; không plain DataStore, BuildConfig, log, git, screenshot, export hay cloud backup. Key bị mất/thu hồi vẫn được sửa/xóa trong UI.

“Một lần” nghĩa là nhập một key của nhà cung cấp được chọn, dùng cho cả hai workspace. Một key không tự dùng được ở tất cả hãng. Thay provider cần key thích hợp; không thử gửi key lần lượt đến các endpoint để đoán provider. Provider adapter xác minh capabilities/model qua tài liệu/API được cho phép; model text không được quảng cáo là tạo ảnh/video.

Ưu tiên provider có lựa chọn miễn phí hợp lệ. [Gemini rate limits](https://aistudio.google.com/docs/rate-limits), [Groq limits](https://console.groq.com/docs/rate-limits) và [OpenRouter free variants](https://openrouter.ai/docs/guides/routing/model-variants/free) có giới hạn theo model/tài khoản. Chưa chọn model/provider sản xuất và chưa kiểm tài khoản của chủ máy; không cam kết free không giới hạn. Không xoay tài khoản/key để tránh quota. Hiển thị lỗi401/403/429/timeouts rõ ràng, backoff hữu hạn và không fallback sang trả phí nếu chưa được chọn.

[Android Keystore](https://developer.android.com/privacy-and-security/keystore) bảo vệ khóa mã hóa, không phải bảo đảm tuyệt đối trên thiết bị root. Không lưu nguyên secret API làm “Keystore entry” tùy tiện; dùng encrypted blob và key alias riêng, thử migration/key invalidation.

## Điều phối theo workspace

Nút AI nằm trong editor đang mở. Mode lấy từ document.kind, không để prompt tự đổi từ AOD sang FP. App dựng input từ:
- yêu cầu người dùng;
- snapshot thông số khả năng đã xác minh, không raw system paths;
- danh sách asset ID riêng app và thông tin size/alpha;
- project revision và budget xuất;
- tập thao tác cho phép của adapter.

Prompt routing: `AOD_SCENE_V1` hoặc `FP_SCENE_V1`. AI chỉ trả scene/diff đề xuất có schema; app kiểm lại bằng code. JSON không phải code. Không shell, Kotlin, JS, expressions để eval, URI để tải tùy ý, root target path hoặc sửa entitlement. Tên field “safety=true” do AI gửi không cấp quyền.

## Hai loại luật

AOD: chỉ đề xuất layout/ảnh/glyph/static decoration và frame timing nằm trong capability. Không bịa battery.src, loopCount, fadeDuration hoặc giả canvas màn hình là viewport AOD. Đồng hồ/pin thật phải là semantic layer của engine được hỗ trợ; không bake giờ/%pin ngẫu nhiên rồi gọi là live.

FP: đề xuất vector chuyển động/particles có giới hạn, scale/alpha/rotation và canvas artwork. Tâm cảm biến lấy profile/user calibration; AI không tự đoán theo màn hình6.78in. Native exporter hiện cần timing đồng đều; đề xuất delay khác nhau phải resample có kiểm total duration hoặc bị từ chối. Giới hạn pixel/RAM là budget OTE, không phải promise hỗ trợ phần cứng.

AI text có thể tạo kế hoạch và tham số procedural renderer; không đương nhiên tạo hình anime chất lượng cao. Tạo ảnh là capability/provider riêng với ngân sách/rate limit/quyền gửi input riêng. Không tự upload toàn APK/ITZ/log hoặc ảnh cá nhân. Người dùng thấy chính xác phần dữ liệu gửi trước lần đầu sử dụng.

## Pipeline bắt buộc

Yêu cầu → context tối thiểu → model → JSON parse nghiêm → schema version/kind/revision → asset references trong allowlist → capability/budget → local render preview → diff/undoable draft → export validate → người dùng chọn Apply riêng.

Bất kỳ response bị cắt, field lạ, NaN/Infinity, asset lạ, canvas vượt budget, quá nhiều layer/frame hoặc capability chưa biết đều bị từ chối. Không tự sửa âm thầm response thành “thành công”. Chấp nhận scene không tự thay đổi resource trên thiết bị.

## Interface đề xuất cho PC

`AiProvider.describeCapabilities()`, `propose(DesignRequest): ProposalText`; `ProposalValidator.validate(text, capabilitySnapshot, projectRevision): ValidatedProposal`; `SceneCompiler.compile(proposal, assets): LocalPreview`. Đây là giao kèo thiết kế, chưa có implementation network. `ValidatedProposal` không expose root/target path. Đơn vị trace là request ID ngẫu nhiên, status/model/latency/token counts khi provider trả; không log key/request body/raw filenames.

Prompt chuyên biệt ở `prompts/AOD_SCENE_RULES.md` và `prompts/FP_SCENE_RULES.md` phải được dùng cùng validator; chỉ prompt không đủ chặn output sai. Chốt schema/validator trước khi thêm INTERNET permission, endpoint selector hoặc màn hình nhập key hoạt động.
