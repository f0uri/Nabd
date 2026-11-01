import React, { useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { isTelegramDirect, telegramConfigured } from './config';
import { telegramApi } from './native';
import { authErrorMessage } from '../store/useAuthStore';

/** Children (including authentication hydration) never mount before consent and
 * a verified vault. Consent is intentionally requested again on each cold start. */
export default function TelegramGate({ children }: React.PropsWithChildren) {
  const [accepted, setAccepted] = useState(false);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState('');
  if (!isTelegramDirect) return <>{children}</>;
  if (ready) return <>{children}</>;
  async function connect(initialize = false) {
    setBusy(true);
    setError('');
    try {
      if (initialize) await telegramApi.initialize();
      await telegramApi.status();
      setReady(true);
    } catch (failure) {
      setMissing((failure as Error).message === 'TELEGRAM_VAULT_MISSING');
      setError(authErrorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <SafeAreaView style={styles.page}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>تلغرام مباشر — وضع مؤقت</Text>
        <Text style={styles.body}>
          التطبيق غادي يتصل مباشرة بـ Telegram Bot API، بلا Render ولا خادم
          وسيط. الحسابات القديمة كتبقى منفصلة وما كتتنقلش تلقائياً.
        </Text>
        <Text style={styles.body}>
          هاد الطريقة ماشي قاعدة بيانات آمنة: مفتاح البوت قابل للاستخراج من
          التطبيق، وأي مشارك يقدر يقرا أو يبدل نسخة الحسابات كاملة، وينتحل
          حسابات الآخرين. حتى الصور والمنشورات كتكون فالنسخة المشتركة. ما
          تستعملش كلمة سر كتستعملها فبلاصة أخرى، ولا معلومات حساسة.
        </Text>
        <Text style={styles.body}>
          مناسب غير لتجربة صغيرة بأشخاص موثوقين. الحفظ بين جهازين ماشي ذري وقد
          يضيع تغيير متزامن. النسخ القديمة كتبقى فالمحادثة. الحد 2 ميغابايت
          للنسخة و128 كيلوبايت للصورة.
        </Text>
        <Text style={styles.body}>
          المتاح: الحساب، الصورة، اليوزر، البحث، الأصدقاء، المنشورات العامة
          والتعليقات والتفاعلات. الدردشة الخاصة والفيديو واستيراد إنستغرام
          والإشعارات ما متوفرينش هنا.
        </Text>
        {!telegramConfigured ? (
          <Text style={styles.error}>
            هاد النسخة ما مهيّآش: خاص بوت ومحادثة خاصة مخصصين لنبض فإعدادات
            البناء. ما تسيفطش المفاتيح فالدردشة.
          </Text>
        ) : (
          <>
            <Pressable
              disabled={busy}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: accepted }}
              onPress={() => setAccepted(!accepted)}
              style={styles.button}
            >
              <Text style={styles.buttonText}>
                {accepted ? '✓ ' : '□ '}فهمت المخاطر وباغي نكمل التجربة
              </Text>
            </Pressable>
            <Pressable
              disabled={!accepted || busy}
              onPress={() => connect()}
              style={[styles.button, (!accepted || busy) && styles.disabled]}
            >
              <Text style={styles.buttonText}>
                الاتصال وقراءة النسخة المثبتة
              </Text>
            </Pressable>
            {missing && accepted && (
              <>
                <Text style={styles.error}>
                  ما كاين حتى ملف مثبت. غير إلا كانت هادي محادثة جديدة مخصصة
                  لنبض، تقدر تبدأ نسخة خاوية. ما تديرش هاد الخطوة لاسترجاع
                  حسابات ضاعت: ثبّت النسخة الصحيحة يدوياً أولاً.
                </Text>
                <Pressable
                  disabled={busy}
                  onPress={() => connect(true)}
                  style={styles.button}
                >
                  <Text style={styles.buttonText}>
                    أؤكد: إنشاء نسخة جديدة فهاد المحادثة الخاوية
                  </Text>
                </Pressable>
              </>
            )}
          </>
        )}
        {busy && <ActivityIndicator />}
        {!!error && <Text style={styles.error}>{error}</Text>}
      </ScrollView>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: '#F0F2F5' },
  content: { padding: 24, gap: 18 },
  title: {
    fontSize: 24,
    fontWeight: '700',
    textAlign: 'right',
    color: '#152238',
  },
  body: { fontSize: 16, lineHeight: 27, textAlign: 'right', color: '#152238' },
  error: { fontSize: 15, lineHeight: 24, textAlign: 'right', color: '#9B2C19' },
  button: { padding: 15, borderRadius: 12, backgroundColor: '#1877F2' },
  buttonText: {
    textAlign: 'center',
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  disabled: { opacity: 0.45 },
});
