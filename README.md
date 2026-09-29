# Nibras Scan (Android, Kotlin)

Sənəd skaneri: kamera → künc düzəltmə → filtr → çoxsəhifəli PDF → OCR.

## APK necə çıxarılır (GitHub Actions)
1. Bu qovluğun içindəkiləri yeni GitHub repo-ya yükləyin (`.github` qovluğu daxil).
2. Repo → Actions → "Build APK" → Run workflow (və ya push edin).
3. Bitəndə "Artifacts" bölməsindən `NibrasScan-debug-apk` yükləyin.

Workflow Ərəb/İngilis OCR dil fayllarını (`ara`, `eng`) avtomatik endirir.

## Android Studio ilə
Açın → Gradle sync → Run. OCR üçün `app/src/main/assets/tessdata/`
içinə `ara.traineddata` və `eng.traineddata` qoyun
(https://github.com/tesseract-ocr/tessdata_fast).

## Google Play
Bax: `store/PLAY_STORE_GUIDE.md` (açar yaratma, AAB, məxfilik siyasəti URL-i, listing mətnləri, Data safety cavabları).
Məxfilik siyasəti: `docs/privacy-policy.html` (GitHub Pages ilə yayımla).
