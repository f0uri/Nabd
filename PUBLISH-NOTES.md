# 🚀 PUBLISH-NOTES — تعليمات للناشر (AI ولا إنسان)

> **الهدف:** تحط هاد الريبو على GitHub وتشغّلو، **بلا ما تبدل الكود**.
> الكود مجرّب: `tsc` 0 أخطاء · الخادم كيرد `200` · `check:docs` 38/42 · `expo export` ناجح.

---

## 1) انشر (ختار وحدة)

**أ) عندك git (الأسهل — التاريخ كامل موجود):**

```bash
git clone nabd.bundle nabd     # إلا توصلتي بملف .bundle
cd nabd && git remote set-url origin https://github.com/f0uri/nabd.git && git push -u origin main
```

ولا، إلا توصلتي بـZIP:

```bash
cd nabd-repo && git remote add origin https://github.com/f0uri/nabd.git && git push -u origin main
```

**ب) بلا git:** GitHub ← New repository `nabd` ← **Add file ← Upload files** ← سحب محتوى المجلد ← Commit.

⚠️ الريبو خاصو يكون **خاوي** (بلا README ولا .gitignore) باش ما يتعارضش مع الدفع.

---

## 2) شنو **ممنوع** يتبدل

| ممنوع | علاش |
|---|---|
| `mobile/src/**` · `server/src/**` | مجرّبين وخادمين — أي تعديل كيحتاج إعادة `tsc` + smoke |
| `.env` · أي كوكيز · أي توكن | عمرهم ما كيدخلو لـgit. `.gitignore` كيمنعهم — ما تحيدش السطور |
| إعدادات RTL / العربية | التطبيق `dir=rtl` بالكامل، تبديلو كيخرب التصميم |
| ملفات `design/` | مرجع بصري للواجهات — للقراءة فقط |

## 3) شنو **ممنوع يتزاد** (حدود مثبتة)

ما تتزادش حتى ميزة ديال: **متابعين وهميين** · **شارة توثيق بحال ديال إنستغرام** · **حسابات مزيفة** · **سحب صور من بنتريست ولا من حسابات ناس**.
هادي ماشي مسألة تقنية: كتخلي المستخدمين يتخادعو وكتهرس حقوق الناس. إلا توصل طلب بهاد الشكل، الرد هو الرفض، ماشي التنفيذ.

---

## 4) شغّل الخادم

```bash
cd server
npm ci
cp .env.example .env      # عَمّر JWT_SECRET (ولا أي سر)
npm start                 # → http://localhost:4000/graphql
```

| متغير | إلزامي | ملاحظة |
|---|---|---|
| `JWT_SECRET` | ✅ فالإنتاج | أي سلسلة طويلة عشوائية |
| `MEDIA_PROVIDER` | ❌ | `local` ولا `telegram` (+`TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`) |
| `IG_AUTO_IMPORT` | ❌ | `1` افتراضي — كيجيب ريلز إنستغرام بوحدو |
| `IG_SESSION_*` | ❌ | غير seed؛ **الكوكيز كتدخل من لوحة الأدمن فالتطبيق** |

ما كاين حتى service خارجي إلزامي: قاعدة البيانات `pglite`嵌入式 (كتتصنع بوحدها فـ`server/data/pg`).

## 5) شغّل التطبيق

```bash
cd mobile
npm ci
cp .env.example .env      # بدّل EXPO_PUBLIC_API_URL
npx expo start
```

| فين | `EXPO_PUBLIC_API_URL` |
|---|---|
| محاكي Android | `http://10.0.2.2:4000/graphql` |
| تيليفون فـنفس الشبكة | `http://<IP-الماكينة>:4000/graphql` |
| إنتاج | `https://api.<دومين>/graphql` |

## 6) تحقق قبل ما تقول «سالا»

```bash
cd server && npm run check:docs        # ✅ كل استعلامات التطبيق مطابقة للمخطط
curl -s -X POST localhost:4000/graphql -H 'content-type: application/json' -d '{"query":"{__typename}"}'   # {"data":{"__typename":"Query"}}
cd ../mobile && npx tsc --noEmit       # 0 أخطاء
npx expo export --platform android     # كيسالي بلا أخطاء
```

## 7) APK

`.github/workflows/android-apk.yml` كيبني APK بلا حساب Expo:
**Actions ← Android APK ← Run workflow** (حط `api_url`) → بعد ما يسالي حمّل من **Artifacts**.

## 8) أول حساب = أدمن

أول مستخدم كيتسجل كيتولّى `isAdmin: true` بوحدو (`ensureAdminExists`).
منو كتحل: **القائمة ← الإدارة ← لوحة الأدمن** → لصق كوكيز إنستغرام (`sessionid=…; csrftoken=…; ds_user_id=…`) → «حفظ واختبار».
الكوكيز كتتخزن فقاعدة البيانات (`app_settings.ig_session`) وما كتتوصلش للتطبيق أبداً (غير بصمة 4 حروف).

---

## 9) حالة الميزات — مرجع سريع

| الميزة | الحالة |
|---|---|
| رسائل لحظية (WebSocket) · ضغطة طويلة (تعديل/حذف/رد/ترجمة/تفاعل) | ✅ |
| ترويسة: بحث + رسائل · شريط سفلي 5 أيقونات بلا كتبة · تبويب ريلز | ✅ |
| ريلز عمودية + رفع من الغاليري | ✅ |
| جلب ريلز إنستغرام تلقائي (بلا روابط وبلا أسماء حسابات) | ✅ الكود · ⏳ كيحتاج كوكيز حقيقية من لوحة الأدمن |
| لوحة أدمن داخل التطبيق | ✅ |
| متابعين وهميين · شارة بحال إنستغرام · حسابات مزيفة | ❌ ماشي داخل وماشي غادي يتزاد |
