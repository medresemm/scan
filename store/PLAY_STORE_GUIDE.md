# Nibras Scan — Google Play hazırlıq siyahısı

## 0. Əlaqə məlumatları (artıq doldurulub)
- E-poçt: nibrascode@gmail.com
- Rəsmi sayt: https://nibrascode.com (Play Console → Store listing → "Sayt" sahəsinə bunu yaz)

## 1. Mütləq əvvəl dəyişdir
- `app/src/main/java/com/nibras/scan/util/AppConfig.kt` → `SUPPORT_EMAIL`
- `docs/privacy-policy.html` və `app/src/main/assets/privacy_policy_*.html` içində `nibrascode@gmail.com` → öz e-poçtun
- Developer adı ("Nibras Code") düzgündürmü yoxla.
- Məxfilik siyasətini hüquqi məsləhət kimi qəbul etmə; yayımlamazdan əvvəl özün oxu.

## 2. Məxfilik siyasəti üçün ictimai URL (Play Console tələb edir)
1. GitHub repo → Settings → Pages → Branch: `main`, folder: `/docs` → Save.
2. URL belə olacaq: `https://<istifadəçi>.github.io/<repo>/privacy-policy.html`
3. Play Console → App content → Privacy policy sahəsinə bu URL-i yaz.

## 3. İmza açarı (bir dəfə)
```
keytool -genkey -v -keystore release.jks -alias nibras -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks     # çıxışı kopyala
```
GitHub repo → Settings → Secrets and variables → Actions → yeni secret-lər:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (nibras), `KEY_PASSWORD`.
`release.jks` faylını və parolları ehtiyatda saxla — itirsən yeniləmə verə bilməzsən.
Workflow işləyəndə Artifacts-da `NibrasScan-release-aab` çıxacaq → Play Console-a bunu yüklə (APK yox, AAB).

## 3.1 Texniki tələblər (2026)
- targetSdk **36** (layihədə qoyulub; 31 Avqust 2026-dan yeni tətbiqlər üçün məcburi).
- AAB formatı, Play App Signing (Play Console-da qəbul et).
- 16 KB page size: Tesseract4Android 4.9.0 istifadə olunur. Play Console pre-launch report və ya Android Studio → APK Analyzer ilə yoxla; ML Kit xəbərdarlıq versə, `text-recognition` versiyasını yenilə.
- Yeni fərdi developer hesabları üçün Play qapalı test tələbi (testerlər + müddət) ola bilər — Console-da "Testing requirements"ə bax.

## 4. Store listing
**Ad (≤30):** Nibras Scan – PDF Scanner
**Qısa təsvir (≤80) EN:** Scan documents to PDF, straighten edges, apply filters, extract text (OCR).
**Qısa təsvir AZ:** Sənədləri PDF-ə skan et, filtr tətbiq et, mətni çıxar (OCR).
**Kateqoriya:** Productivity (Məhsuldarlıq)
**Qrafika:** `store/icon-512.png` (ikon), `store/feature-graphic-1024x500.png` (feature graphic).
**Screenshot:** özün çək (ən azı 2, telefon; ana səhifə, kamera/künc düzəltmə, redaktə, OCR).

### Tam təsvir (EN)
Nibras Scan turns your phone into a simple, private document scanner.

• Scan with the camera and straighten the page by dragging its four corners
• Filters: Original, Auto, Black & White, Grayscale
• Multi-page PDF: add, delete, rotate and reorder pages
• Text recognition (OCR): Latin scripts and Arabic, copy the text with one tap
• Share or open your PDFs in any app
• Private by design: everything stays on your device — no account, no ads, no tracking

### Tam təsvir (AZ)
Nibras Scan telefonunuzu sadə və məxfi sənəd skanerinə çevirir.

• Kamera ilə skan edin, 4 küncü sürüşdürərək səhifəni düzəldin
• Filtrlər: Original, Auto, Ağ-qara, Boz
• Çoxsəhifəli PDF: səhifə əlavə edin, silin, çevirin, sıranı dəyişin
• Mətn tanıma (OCR): Latın əlifbası və Ərəb dili, bir toxunuşla kopyalayın
• PDF-ləri istənilən tətbiqlə paylaşın və ya açın
• Məxfilik: hər şey cihazınızda qalır — hesab, reklam və izləmə yoxdur

## 5. Data safety (Play Console → App content → Data safety) — təklif olunan cavablar
- Does your app collect or share any of the required user data types? → **No** (developer heç bir məlumat toplamır/paylaşmır; emal cihazdadır)
- Is all of the user data encrypted in transit? → N/A (məlumat göndərilmir)
- Do you provide a way for users to request data deletion? → Tətbiqdə "Bütün məlumatları sil" + uninstall.
- Yoxla: https://developers.google.com/ml-kit/android-data-disclosure — ML Kit üçün Google-un tövsiyəsinə bax və cavabı ona uyğunlaşdır.

## 6. Digər App content bəyanları
- Ads: **No**
- App access: bütün funksiyalar login olmadan açıqdır
- Target audience: **18+ / 13+** (uşaqlara yönəlməyib)
- Content rating (IARC sorğusu): zorakılıq/cinsi/qumar və s. yoxdur → adətən "Everyone"
- Permissions: yalnız Camera (skan üçün əsas funksiya). Ayrıca "sensitive permission" deklarasiyası tələb olunmur.
- Government app / News / Health / Financial: **No**

## 7. Yayımdan əvvəl test
- Real cihazda: kamera icazəsi → skan → künc düzəlt → PDF → paylaş → OCR (Latın + Ərəb) → Ayarlar → məlumatları sil.
- Android 15/16 cihaz və ya emulyatorda edge-to-edge (status/naviqasiya paneli) düzgün görünürmü yoxla.
- Play Console → Internal testing-də yüklə, pre-launch report-a bax.
