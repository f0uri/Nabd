import React, { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { isTelegramDirect, telegramConfigured } from './config';
import { telegramApi } from './native';

/** The owner explicitly enabled direct mode. Startup reads only; initialization
 * remains a separate, confirmed action. Provider limitations are documented. */
export default function TelegramGate({ children }: React.PropsWithChildren) {
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(isTelegramDirect && telegramConfigured);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState('');
  const [confirming, setConfirming] = useState(false);
  const mounted = useRef(true);
  const pending = useRef(false);
  async function connect(initialize = false) {
    if (pending.current || !telegramConfigured) return;
    pending.current = true;
    setBusy(true);
    setError('');
    try {
      if (initialize) await telegramApi.initialize();
      await telegramApi.status();
      if (mounted.current) {
        setMissing(false);
        setReady(true);
      }
    } catch (failure) {
      if (!mounted.current) return;
      const absent = (failure as Error)?.message === 'TELEGRAM_VAULT_MISSING';
      setMissing(absent);
      setConfirming(false);
      setError(
        absent
          ? ''
          : initialize
            ? 'تعذّر إنشاء البيانات. لم يتم تأكيد الحفظ.'
            : 'تعذّر الاتصال. تحقق من الإنترنت وأعد المحاولة.',
      );
    } finally {
      pending.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  useEffect(() => {
    mounted.current = true;
    if (isTelegramDirect && telegramConfigured) void connect();
    return () => {
      mounted.current = false;
    };
  }, []);
  if (!isTelegramDirect || ready) return <>{children}</>;
  return (
    <SafeAreaView style={styles.page}>
      <View style={styles.content}>
        {busy ? (
          <ActivityIndicator
            size="large"
            color="#1877F2"
            accessibilityLabel="جارٍ الاتصال"
          />
        ) : (
          <>
            {!telegramConfigured ? (
              <Text style={styles.error}>
                تعذّر إعداد الاتصال. يرجى مراجعة إعدادات الخدمة.
              </Text>
            ) : missing ? (
              <>
                <Text style={styles.title}>إعداد البيانات</Text>
                <Text style={styles.body}>
                  {confirming
                    ? 'سيتم إنشاء مساحة فارغة جديدة. هذا الإجراء لا يستعيد أي حسابات سابقة.'
                    : 'لم يتم العثور على مساحة بيانات جاهزة.'}
                </Text>
                <Pressable
                  onPress={() =>
                    confirming ? void connect(true) : setConfirming(true)
                  }
                  style={styles.button}
                  accessibilityRole="button"
                >
                  <Text style={styles.buttonText}>
                    {confirming ? 'تأكيد الإنشاء' : 'إنشاء مساحة جديدة'}
                  </Text>
                </Pressable>
                {confirming && (
                  <Pressable
                    onPress={() => setConfirming(false)}
                    style={styles.button}
                    accessibilityRole="button"
                  >
                    <Text style={styles.buttonText}>إلغاء</Text>
                  </Pressable>
                )}
              </>
            ) : null}
            {!!error && (
              <Text style={styles.error} accessibilityRole="alert">
                {error}
              </Text>
            )}
            {telegramConfigured && (
              <Pressable
                onPress={() => {
                  void connect();
                }}
                style={styles.button}
                accessibilityRole="button"
              >
                <Text style={styles.buttonText}>إعادة المحاولة</Text>
              </Pressable>
            )}
          </>
        )}
      </View>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: '#F0F2F5', justifyContent: 'center' },
  content: {
    padding: 24,
    gap: 18,
    width: '100%',
    maxWidth: 520,
    alignSelf: 'center',
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    textAlign: 'center',
    color: '#152238',
  },
  body: { fontSize: 16, lineHeight: 26, textAlign: 'center', color: '#152238' },
  error: {
    fontSize: 15,
    lineHeight: 24,
    textAlign: 'center',
    color: '#9B2C19',
  },
  button: {
    minHeight: 48,
    justifyContent: 'center',
    padding: 15,
    borderRadius: 12,
    backgroundColor: '#1877F2',
  },
  buttonText: {
    textAlign: 'center',
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
