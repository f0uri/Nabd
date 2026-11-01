# 📤 ن push التطبيق لـGitHub (باش يتزادو المساهمات وبناء APK)

التطبيق واجد ومجرّب. هادي هي الخطوات باش يطلع لـGitHub ويبقى كيتزاد فيه بلا ما تخسر شي حاجة.

---

## 1) خلق الريبو

من GitHub: **New repository** → سمّيه `nabd` ولا `social-blue` → **Private** ولا Public (على مسؤوليتك) →
**ما تخليش** GitHub يزيد README/.gitignore/LICENSE (حنا عندنا) → **Create**.

## 2) ادفع الكود (دقيقة وحدة)

```bash
cd social-blue

git remote add origin https://github.com/<USERNAME>/nabd.git
git branch -M main
git push -u origin main
```

إلا كنتي كتستعمل SSH بدل HTTPS:

```bash
git remote add origin git@github.com:<USERNAME>/nabd.git
git push -u origin main
```

> الـrepo واجد ومcommitّ من قبل (`git log` كيبان ليك). ما محتاجش تدير `git init` من جديد.

## 3) الأسرار — ماشي فالـgit

`.gitignore` كيمنع `.env` · `server/uploads/` · `server/data/pg/` (قاعدة البيانات) · `node_modules` · `dist`.
الأسرار كتدوز **فـSettings ← Secrets and variables ← Actions** إلا كان عندك CI كيحتاجهم:

| Secret | فين كيتستعمل |
|---|---|
| `TELEGRAM_BOT_TOKEN` · `TELEGRAM_CHAT_ID` | تخزين الوسائط (اختياري — بلاها كيتخزن محلياً) |
| `JWT_SECRET` | الخادم فالإنتاج |
| `IG_ACCESS_TOKEN` · `IG_BUSINESS_ACCOUNT_ID` | قناة إنستغرام الرسمية (اختياري) |

> ⚠️ كوكيز إنستغرام **ما كتمشيش** لـGitHub ولا لأي سر: كتدخل من لوحة الأدمن فالتطبيق وكتتخزن فقاعدة البيانات.

## 4) بناء APK أوتوماتيكياً (بلا حساب Expo)

الريبو فيه workflow واجد: **`.github/workflows/android-apk.yml`**

- كيبني `app-debug.apk` (موقّع بمفتاح debug، كيتثبّت على أي تيليفون Android).
- كيتشغّل: **Actions ← Android APK ← Run workflow**، ولا بوحدو مع كل push على `main` كيمس `mobile/`.
- منين يسالي: **Actions ← آخر run ← Artifacts ← `nabd-debug-apk`** → تحمّل الـAPK.

قبل ما تشغّلو، حدّد عنوان الخادم فـinput `api_url`:

| فين كتجرب | القيمة |
|---|---|
| محاكي Android | `http://10.0.2.2:4000/graphql` |
| تيليفون فـنفس الشبكة | `http://<IP-الماكينة>:4000/graphql` |
| إنتاج | `https://api.دومين-ديالك/graphql` |

باش تبني نسخة موقّعة للنشر (Play Store) زيد keystore كـBase64 فـSecrets وبدّل `assembleDebug` بـ`assembleRelease`، ولا استعمل `eas build -p android --profile production`.

## 5) CI (فحص أوتوماتيكي)

`.github/workflows/ci.yml` كيدير فكل push:

| الخطوة | شنو كيتحقق |
|---|---|
| فحص الصيغة | `node --check` على كل ملفات الخادم |
| مطابقة المخطط | `npm run check:docs` — كل استعلامات التطبيق مقابل GraphQL schema |
| تشغيل حقيقي | كيشغّل الخادم ويضرب `{__typename}` على `:4000` |
| smoke | `npm run smoke:flow` (مستخدم → منشور → إعجاب → تعليق…) |
| التطبيق | `tsc --noEmit` + `expo export --platform android` |

## 6) بعد الدفع — وصف ومواضيع الريبو

**About** (نسخ/لصق): `نبض — شبكة اجتماعية عربية: React Native + GraphQL + PostgreSQL + WebSocket، ريلز عمودية مع جلب تلقائي من إنستغرام، ولوحة أدمن داخل التطبيق.`

**Topics:** `react-native` `expo` `graphql` `postgresql` `websocket` `arabic` `rtl` `reels` `apollo`

---

## حالة الريبو دابا

| | |
|---|---|
| **الفرع** | `main` |
| **عدد الملفات** | كل الكود + الوثائق + التصاميم (`design/`) |
| **اللي مستثنى** | `.env` · الوسائط المرفوعة · قاعدة البيانات · `node_modules` |
| **اللي داخل** | الخادم كامل · التطبيق كامل · 8 سكريبتات اختبار · README · workflows |

من بعد الدفع، قول ليا وشنو بغيتي تزيد — كنخدم على الفرع ومن بعد كندير commit جديد فحال ما بغيتي.
