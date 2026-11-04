# Nghiên cứu giao diện SukiSU / Miuix / Material / Glass

Ngày đối chiếu: 2026-11-04. APK chỉ được phân tích tĩnh, chưa cài/chạy; chưa có screenshot/video tham chiếu của APK này để khẳng định hình thức từng màn hình.

## APK người dùng cung cấp

File `SukiSU_v4.3.0-r1_41142-arm64-v8a-release-spoofed.apk`, 10,689,344 byte; SHA-256 `0c6c2db9fc591ff2441108e74e2def7fe5f785049be5b9c474312b6f381be5cf`. ZIP có 215 entry, kiểm CRC đạt. Manifest ghi package `xrzibv.uhthfe.adajzk`, versionCode 41142/versionName v4.3.0-r1, minSdk26/target37. Tên file không chứng minh nguồn ký hay các thay đổi “spoofed”; chưa xác thực signer.

Đã đọc binary manifest, resources.arsc và DEX (7,155 class, 31,201 method có định nghĩa). Tài nguyên có lựa chọn màu, blur, Liquid Glass, floating bottom bar và caption xem trước Miuix/Material 3 Expressive. DEX có `color_style`, `miuix_monet`, `enable_blur`, nhánh lưu bật thanh nổi, và dấu vết shader `LiquidGlassLens`. [Báo cáo có resource ID/callsite](../../research/evidence/sukisu-static.json).

Không suy ra “có Glasmio” từ chữ Liquid Glass. Không lấy kernel/module/shell/native loader của SukiSU vào OTE.

## Đối chiếu nguồn công khai

SukiSU-Ultra commit `b52705706f9d81be654a60af99929ceef0f955cd`:
- [Theme.kt](https://github.com/SukiSU-Ultra/SukiSU-Ultra/blob/b52705706f9d81be654a60af99929ceef0f955cd/manager/app/src/main/java/com/sukisu/ultra/ui/theme/Theme.kt) tách UiMode.Miuix và UiMode.Material.
- [FloatingBottomBar.kt](https://github.com/SukiSU-Ultra/SukiSU-Ultra/blob/b52705706f9d81be654a60af99929ceef0f955cd/manager/app/src/main/java/com/sukisu/ultra/ui/component/FloatingBottomBar.kt) dùng miuix-blur/Backdrop và thành phần liquid của dự án.
- [Version catalog](https://github.com/SukiSU-Ultra/SukiSU-Ultra/blob/b52705706f9d81be654a60af99929ceef0f955cd/manager/gradle/libs.versions.toml) ghi Miuix 0.9.4, Kotlin2.4.20, AGP9.4.1, Material3 1.5.0-alpha28.

Đây là nguồn tham khảo, chưa chứng minh khớp build APK đã gửi. Root repository có GPLv3; có file mang nguồn gốc/notice riêng. Nhánh OTE này không chép mã UI SukiSU. Nếu tái sử dụng sau này phải kiểm license từng file và nghĩa vụ phân phối, không đơn giản bỏ attribution.

## Lựa chọn và tương thích

| Nguồn | Đã đọc | Quyết định |
| --- | --- | --- |
| [Miuix](https://github.com/compose-miuix-ui/miuix/tree/e3cacf752139c5b9e709db271464d684720a2fc6) | README, module UI, versions; release0.9.4 | Dùng thư viện thật sau build spike; tách presentation khỏi nghiệp vụ |
| [Glasmio](https://github.com/NEORUAA/Glasmio/tree/700a611f9024c8f3bd9f0b8dd26d939a0dcbeb72) | README, root Gradle, glass Gradle | Ứng viên lớp Glass, không mặc định là thư viện SukiSU sử dụng |
| [Compose Material3](https://developer.android.com/develop/ui/compose/designsystems/material3) | Tài liệu Compose chính thức | Renderer nền ổn định; giữ AndroidX Compose, không ghép thư viện View theo tên tổ chức |
| [Material design](https://m3.material.io/) / [material-components](https://github.com/material-components) | Trang thiết kế và tổ chức | Quy chuẩn UI; repo material-components-android không thay cho Compose Material3 |

Miuix source HEAD dùng Kotlin2.4.21, AGP9.5.0, Compose Multiplatform1.12.2; tag0.9.4 dùng Kotlin2.4.20/AGP9.4.1/Compose1.12.0. Glasmio0.1.1 README yêu cầu JDK21, SDK37, NDK27.1.12297006, CMake3.22.1; root Gradle AGP9.4.1, Kotlin Compose2.4.21. Glass module còn có native CMake. Các con số là khai báo của upstream được đọc, chưa phải build thành công ở môi trường này.

OTE baseline: AGP8.10.1/Kotlin2.1.21/Gradle8.11.1/Java17/compile36. Vì vậy **không thêm dependency mới nhất rồi giữ nguyên toolchain và tuyên bố tích hợp xong**. Trên PC làm spike riêng: chốt tag, resolve artifact từ kho chính thức, kiểm metadata Kotlin/AAR/JDK/SDK, tạo màn hình nhỏ rồi đo frame pacing. Sau đó nâng đồng bộ toolchain/plugin/KSP/Hilt nếu cần. Không thay sourceCompatibility chỉ để im lỗi; không bỏ test.

Các coordinate có thật từ upstream:
- `top.yukonga.miuix.kmp:miuix-ui` và các module preference/blur/icons/nav; Android-specific variant được SukiSU khai báo ở version catalog.
- `io.github.neoruaa.glasmio:glasmio-scaffold:0.1.1` và `glasmio-preference:0.1.1` theo README. Chưa kiểm resolve Maven trong lượt này.
- `androidx.compose.material3:material3` qua BOM đang dùng của OTE.

Miuix và Glasmio root license Apache-2.0. Giữ notices của các thành phần dẫn xuất; Glasmio có third-party notices/native components. Không đưa SF Pro/SF Symbols từ demo vào OTE; dùng font hệ thống và icon có license phù hợp.

## Triển khai UI đích

Tạo module design-token chung và ba renderer. Các renderer nhận cùng screen state và callbacks, không sở hữu SU, DB hoặc shell. Preview, text field và canvas dùng hệ tọa độ rõ ràng; nâng font size/RTL vẫn phải thao tác được.

Glass chỉ ghi backdrop ở lớp nền cần thiết, không lồng capture chính lớp Glass. Giảm/tắt refraction khi timeline đang kéo hoặc preview toàn canvas; tắt khi reduced-transparency/reduced-motion được chọn; không yêu cầu GPU chạy liên tục khi idle. Nhãn “Glass” của baseline hiện chỉ là compositing trong suốt; không đánh đồng với Glasmio.

APK SukiSU là chứng cứ cấu trúc/lựa chọn, không đủ để dựng pixel-perfect. Khi kiểm UI trên PC có emulator hoặc điện thoại, chụp cùng kích thước/density/font, đo trước/sau từng renderer. Không cần đụng root để đo UI.
