# Moon Zoom — By GODLY (bản nền: Giai đoạn 1–4 một phần)

> CHƯA được biên dịch hay chạy thử trên máy thật (môi trường tạo code không có Android SDK/Gradle/mạng).
> Hãy build bằng GitHub Actions và báo lỗi nếu có để sửa.

## Đã có (code thật, không giả lập)
- Camera thật (CameraX): xem trước, chụp, đổi cam, flash, quyền camera, lưu `Pictures/MoonZoom` (Android 10+).
- Chạm lấy nét + đo sáng, thanh bù sáng EV. Zoom 1X→đúng 100X (chụm, núm xoay, nút nhanh).
- NIGHT: chụp 6 khung exposure tự động -> căn chỉnh -> chồng (giảm nhiễu) -> kéo tông -> làm nét nhẹ.
- MILKY WAY: đếm ngược 15 giây có thu ảnh thật. Nếu máy hỗ trợ MANUAL_SENSOR: phơi sáng tay
  (tối đa 4s/khung, ISO tối đa 3200, lấy nét vô cực nếu hỗ trợ), tối đa 12 khung, rồi căn chỉnh + chồng.
  Nếu không hỗ trợ: chồng khung exposure tự động và app ghi rõ đó KHÔNG phải phơi sáng dài.
- Vòng tiến trình Liquid Glass: % lấy từ các bước xử lý thật (đọc/căn chỉnh từng khung, phân tích, tông màu, làm nét, xuất).

## Giới hạn đã biết
- Ảnh NIGHT/MILKY xử lý ở độ phân giải giảm (cạnh dài ≤ 2600px) để khỏi hết RAM.
- Chưa có: trừ dark-frame, phát hiện chuyển động, phát hiện sao, RAW, chế độ PRO, AI PHOTO / MoonAIEnhancer,
  cài đặt, watermark, logo, thư viện trong app, Android 8–9.
- Căn chỉnh chỉ tịnh tiến (đủ cho chân máy/rung tay nhẹ), không bù xoay sao qua phơi sáng dài.
- Phơi sáng tay áp qua Camera2 interop; một số máy có thể từ chối hoặc kẹp giá trị — chưa thử máy thật.
- Test đơn vị mới chỉ có cho giới hạn zoom; chưa có test cho bộ chồng khung.

## Build APK
Đẩy lên GitHub rồi chạy Actions → Build APK → tải artifact `MoonZoom-debug-apk`.
