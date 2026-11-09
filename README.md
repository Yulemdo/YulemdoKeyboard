# Yulemdo Keyboard

Android uchun maxsus klaviatura (custom IME) ilovasi.

## Imkoniyatlar
- Tizim bo'yicha default klaviatura sifatida o'rnatish (InputMethodService)
- O'zbek lotin alifbosi standart joylashuvi (oʻ, gʻ, sh, ch, ng harflari bilan)
- Ilova ichida drag-drop editor: tugmalarni o'zingiz xohlagan joyga torting va saqlang
- Saqlangan joylashuv avtomatik ravishda haqiqiy klaviaturada ishlatiladi

## Loyihani ochish
Android Studio (Koala yoki undan yangi) orqali ushbu papkani oching.

## Qo'lda build qilish
```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
`.github/workflows/android-build.yml` har bir push/PR'da debug APK'ni build qiladi
va uni Actions sahifasidagi "Artifacts" bo'limiga yuklaydi (`yulemdokeyboard-debug-apk`).

## Qurilmada ishga tushirish
1. Ilovani o'rnating va oching
2. "Klaviaturalar sozlamasini ochish" tugmasini bosib, YulemdoKeyboard'ni yoqing
3. "Klaviatura tanlash" orqali uni faol klaviatura qilib tanlang
4. Istasangiz "Klaviatura muharriri" orqali o'z joylashuvingizni tuzing va saqlang

## Keyingi qadamlar (v2 uchun takliflar)
- Raqam/belgilar (123) klaviaturasi
- Tugma kengligini editor'da o'zgartirish
- Bir nechta custom profil saqlash
- Light/Dark tema
