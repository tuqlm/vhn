# GBA VN Studio (Android App)

Ung dung Android Viet hoa ROM Game Boy Advance (.gba) thiet ke moi hoan toan voi giao dien Dark Material 3 chia 3 tab (`ROM & Du an`, `Dich Offline`, `Trinh sua`):
- **Dich Offline sieu nhanh**: Tai goi ngon ngu Anh-Viet (~30MB) cua Google ML Kit 1 lan duy nhat, sau do dich hang nghin cau ngay tren dien thoai khong can mang, khong can API Key.
- **Kien truc loi viet moi (`com.gbalocalizer.vn.core`)**: Tach biet `GbaCodec`, `GbaScanner`, `GbaPatcher`, `VnDictionary`, `VnTranslator` giup loc sach chuoi nhi phan rac, bao ve ma `{FC:...}`, `{FD:...}`, `\p` va tu dong repoint sang vung trong.
- **Trinh sua tich hop RecyclerView**: Tim kiem, loc nhanh theo trang thai (`Da dich`, `Chua dich`, `Co bien {FD}`) va cham de chinh sua tung cau ngay trong app.

## Cach build file .APK tu dong bang GitHub Actions
1. Tao 1 repo moi tren https://github.com.
2. Tai toan bo thu muc/file cua du an len repo (bao gom `.github/workflows/build-apk.yml`).
3. Mo tab **Actions**, doi ~2 phut de Gradle bien dich, sau do tai artifact **GBA-Localizer-VN-APK** de lay `app-debug.apk`.
