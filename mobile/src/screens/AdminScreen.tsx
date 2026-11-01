import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import AppBar from '../components/AppBar';
import { dataApi } from '../api';
import type { InstagramAdminStatus, InstagramChannel, InstagramProbe } from '../api/client';
import { useFeedStore } from '../store/useFeedStore';
import { colors, radius, shadow, space, type } from '../theme';

const formatDate = (iso?: string | null) => {
  if (!iso) return '—';
  const date = new Date(iso);
  const diffMinutes = Math.round((Date.now() - date.getTime()) / 60000);
  if (diffMinutes < 1) return 'دابا';
  if (diffMinutes < 60) return `قبل ${diffMinutes} دقيقة`;
  if (diffMinutes < 1440) return `قبل ${Math.round(diffMinutes / 60)} سوايع`;
  return date.toLocaleDateString('ar-MA');
};

const ChannelRow: React.FC<{ channel: InstagramChannel }> = ({ channel }) => (
  <View style={styles.channelRow}>
    <Ionicons
      name={channel.ok ? 'checkmark-circle' : 'close-circle-outline'}
      size={16}
      color={channel.ok ? colors.semantic.success : colors.text.tertiary}
    />
    <Text style={styles.channelName} numberOfLines={1}>
      {channel.name}
    </Text>
    <Text style={styles.channelValue}>
      {channel.ok ? `${channel.found} ريل` : (channel.error ?? 'ما خدمش')}
    </Text>
  </View>
);

/**
 * لوحة الأدمن — بلاصة وحدة فيها كلشي اللي محتاجو باش الريلز تبان:
 * كوكيز إنستغرام + الحالة + الجلب دابا. بلا ما تمس حتى ملف.
 */
export const AdminScreen: React.FC = () => {
  const showToast = useFeedStore((store) => store.showToast);

  const [status, setStatus] = useState<InstagramAdminStatus | null>(null);
  const [cookieInput, setCookieInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [probe, setProbe] = useState<InstagramProbe | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const next = await dataApi.instagramSessionStatus();
      setStatus(next);
    } catch (error) {
      const code = (error as { code?: string })?.code;
      showToast(code === 'FORBIDDEN' ? 'هاد الشاشة غير للأدمن' : 'ما قدرناش نقراو الحالة', 'error');
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => {
    load();
  }, [load]);

  const saveCookies = async () => {
    if (!cookieInput.trim()) {
      showToast('لصق الكوكيز أولاً', 'error');
      return;
    }
    setSaving(true);
    try {
      const result = await dataApi.saveInstagramSession(cookieInput);
      setProbe(result);
      setCookieInput('');
      await load();
      showToast(
        result.valid ? `الجلسة خدامة ✓ (${result.totalCodes} ريل)` : 'الكوكيز تسجلو ولكن الجلسة ما خدماش',
        result.valid ? 'success' : 'error',
      );
    } catch (error) {
      const code = (error as { code?: string })?.code ?? '';
      showToast(
        code === 'INVALID_INSTAGRAM_COOKIE' ? 'الكوكيز ناقصين ولا ماشي صحيحين' : 'ما قدرناش نسجلو الكوكيز',
        'error',
      );
    } finally {
      setSaving(false);
    }
  };

  const testSession = async () => {
    setTesting(true);
    try {
      const result = await dataApi.testInstagramSession();
      setProbe(result);
      showToast(result.valid ? 'الجلسة خدامة ✓' : 'الجلسة ما كترجعش ريلز', result.valid ? 'success' : 'error');
      await load();
    } catch {
      showToast('ما قدرناش نختبرو الجلسة', 'error');
    } finally {
      setTesting(false);
    }
  };

  const clearSession = () => {
    Alert.alert('حذف جلسة إنستغرام', 'غادي نحيدو الكوكيز — الريلز الجداد ما غاديش يوصلو.', [
      { text: 'إلغاء', style: 'cancel' },
      {
        text: 'حذف',
        style: 'destructive',
        onPress: async () => {
          try {
            await dataApi.clearInstagramSession();
            setProbe(null);
            await load();
            showToast('تحيدت الجلسة', 'success');
          } catch {
            showToast('ما قدرناش نحيدو الجلسة', 'error');
          }
        },
      },
    ]);
  };

  const importNow = async () => {
    setTesting(true);
    try {
      await dataApi.runReelsIngest();
      await load();
      showToast('جرينا دورة جلب دابا', 'success');
    } catch {
      showToast('ما قدرناش نشغلو الجلب', 'error');
    } finally {
      setTesting(false);
    }
  };

  const pasteFromClipboard = async () => {
    const text = await Clipboard.getStringAsync();
    if (text) setCookieInput(text);
  };

  return (
    <View style={styles.flex}>
      <AppBar />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <View style={styles.headRow}>
          <View style={styles.headIcon}>
            <Ionicons name="shield-checkmark" size={18} color={colors.brand.primary} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>لوحة الأدمن</Text>
            <Text style={styles.subtitle}>كلشي اللي محتاجو باش ريلز إنستغرام تبان — من هنا، بلا ملفات</Text>
          </View>
        </View>

        {/* ================= الحالة ================= */}
        <View style={styles.card}>
          <View style={styles.cardHead}>
            <Text style={styles.cardTitle}>الحالة</Text>
            {loading ? <ActivityIndicator size="small" color={colors.brand.primary} /> : null}
          </View>

          <View style={styles.statusRow}>
            <View
              style={[
                styles.dot,
                { backgroundColor: status?.configured ? colors.semantic.success : colors.text.tertiary },
              ]}
            />
            <Text style={styles.statusText}>
              {status?.configured ? `جلسة إنستغرام مربوطة (${status.fingerprint})` : 'ما كايناش جلسة — الريلز كتجي من القنوات العامة'}
            </Text>
          </View>

          <View style={styles.statsGrid}>
            <View style={styles.stat}>
              <Text style={styles.statValue}>{status?.reelsCount ?? '—'}</Text>
              <Text style={styles.statLabel}>ريلز</Text>
            </View>
            <View style={styles.stat}>
              <Text style={styles.statValue}>{status?.queueImported ?? '—'}</Text>
              <Text style={styles.statLabel}>مستورد</Text>
            </View>
            <View style={styles.stat}>
              <Text style={styles.statValue}>{status?.intervalMinutes ?? '—'}</Text>
              <Text style={styles.statLabel}>دقيقة/دورة</Text>
            </View>
            <View style={styles.stat}>
              <Text style={styles.statValue}>{status?.queuePending ?? '—'}</Text>
              <Text style={styles.statLabel}>فالطابور</Text>
            </View>
          </View>

          <Text style={styles.metaLine}>آخر دورة: {formatDate(status?.lastRunAt)}</Text>
          {!!status?.lastError && <Text style={styles.errorLine}>آخر خطأ: {status.lastError}</Text>}
        </View>

        {/* ================= الكوكيز ================= */}
        <View style={styles.card}>
          <View style={styles.cardHead}>
            <Text style={styles.cardTitle}>كوكيز إنستغرام</Text>
            <Pressable onPress={pasteFromClipboard} hitSlop={8} style={styles.pasteBtn} accessibilityRole="button">
              <Ionicons name="clipboard-outline" size={14} color={colors.brand.primary} />
              <Text style={styles.pasteText}>لصق</Text>
            </Pressable>
          </View>

          <Text style={styles.helpText}>
            من المتصفح: حلّ instagram.com ← F12 ← Application ← Cookies ← https://www.instagram.com ← خُد
            <Text style={styles.mono}> sessionid</Text> و
            <Text style={styles.mono}> csrftoken</Text> و
            <Text style={styles.mono}> ds_user_id</Text>، ولصقهم هنا كيف ما كانو.
          </Text>

          <TextInput
            value={cookieInput}
            onChangeText={setCookieInput}
            placeholder="sessionid=…; csrftoken=…; ds_user_id=…"
            placeholderTextColor={colors.text.tertiary}
            style={styles.input}
            multiline
            autoCapitalize="none"
            autoCorrect={false}
            accessibilityLabel="كوكيز إنستغرام"
          />

          <View style={styles.actionsRow}>
            <Pressable
              onPress={saveCookies}
              disabled={saving}
              style={[styles.primaryBtn, saving && { opacity: 0.6 }]}
              accessibilityRole="button"
            >
              {saving ? (
                <ActivityIndicator size="small" color={colors.text.inverse} />
              ) : (
                <>
                  <Ionicons name="save-outline" size={16} color={colors.text.inverse} />
                  <Text style={styles.primaryText}>حفظ واختبار</Text>
                </>
              )}
            </Pressable>

            <Pressable
              onPress={testSession}
              disabled={testing || !status?.configured}
              style={[styles.ghostBtn, (!status?.configured || testing) && { opacity: 0.5 }]}
              accessibilityRole="button"
            >
              {testing ? (
                <ActivityIndicator size="small" color={colors.brand.primary} />
              ) : (
                <Text style={styles.ghostText}>اختبار</Text>
              )}
            </Pressable>

            {status?.configured && (
              <Pressable onPress={clearSession} style={styles.dangerBtn} accessibilityRole="button">
                <Text style={styles.dangerText}>حذف</Text>
              </Pressable>
            )}
          </View>

          <Text style={styles.warnText}>
            ⚠️ استعمل حساب جديد ماشي حسابك الأساسي · الكوكيز كيبقاو غير فالخادم وما كيوصلوش للتطبيق.
          </Text>
        </View>

        {/* ================= نتيجة الاختبار ================= */}
        {!!probe && (
          <View style={styles.card}>
            <View style={styles.cardHead}>
              <Text style={styles.cardTitle}>نتيجة الاختبار</Text>
              <View
                style={[
                  styles.pill,
                  { backgroundColor: probe.valid ? colors.brand.soft : '#FDECEA' },
                ]}
              >
                <Text style={[styles.pillText, { color: probe.valid ? colors.brand.primary : colors.semantic.danger }]}>
                  {probe.valid ? `${probe.totalCodes} ريل` : 'ما خدمتش'}
                </Text>
              </View>
            </View>
            {probe.channels.map((channel) => (
              <ChannelRow key={channel.name} channel={channel} />
            ))}
            <Text style={styles.metaLine}>آخر فحص: {formatDate(probe.checkedAt)}</Text>
          </View>
        )}

        {/* ================= الجلب ================= */}
        <View style={styles.card}>
          <Text style={styles.cardTitle}>الجلب التلقائي</Text>
          <Text style={styles.helpText}>
            الخادم كيدير دورة كل {status?.intervalMinutes ?? 45} دقيقة وكيجيب ريلز بوحدو. إلا بغيتي دابا، دوس هنا.
          </Text>
          <Pressable onPress={importNow} disabled={testing} style={[styles.ghostBtn, { alignSelf: 'flex-start' }]}>
            <Ionicons name="refresh" size={15} color={colors.brand.primary} />
            <Text style={styles.ghostText}>جلب الريلز دابا</Text>
          </Pressable>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  content: { padding: space.md, gap: space.sm, paddingBottom: space.xxxl },
  headRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs, marginBottom: space.xs },
  headIcon: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: colors.brand.soft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { ...type.title, color: colors.text.primary },
  subtitle: { ...type.caption, color: colors.text.secondary },

  card: {
    backgroundColor: colors.bg.surface,
    borderRadius: radius.lg,
    padding: space.md,
    gap: space.xs,
    ...shadow.card,
  },
  cardHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardTitle: { ...type.bodyStrong, color: colors.text.primary },

  statusRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 8, height: 8, borderRadius: 4 },
  statusText: { ...type.bodySm, color: colors.text.secondary, flex: 1 },

  statsGrid: { flexDirection: 'row', gap: space.xs, marginTop: space.xs },
  stat: {
    flex: 1,
    backgroundColor: colors.bg.canvas,
    borderRadius: radius.md,
    paddingVertical: space.xs,
    alignItems: 'center',
  },
  statValue: { ...type.bodyStrong, color: colors.text.primary },
  statLabel: { ...type.caption, color: colors.text.tertiary, fontSize: 10 },

  metaLine: { ...type.caption, color: colors.text.tertiary },
  errorLine: { ...type.caption, color: colors.semantic.danger },

  helpText: { ...type.caption, color: colors.text.secondary, lineHeight: 19 },
  mono: { color: colors.text.primary, fontWeight: '700' },
  input: {
    minHeight: 76,
    borderRadius: radius.md,
    backgroundColor: colors.bg.canvas,
    padding: space.sm,
    ...type.caption,
    color: colors.text.primary,
    textAlignVertical: 'top',
  },
  pasteBtn: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  pasteText: { ...type.caption, color: colors.brand.primary, fontWeight: '700' },

  actionsRow: { flexDirection: 'row', gap: space.xs, marginTop: space.xs, alignItems: 'center' },
  primaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    height: 40,
    paddingHorizontal: space.md,
    borderRadius: radius.md,
    backgroundColor: colors.brand.primary,
  },
  primaryText: { ...type.bodyStrong, color: colors.text.inverse },
  ghostBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    height: 40,
    paddingHorizontal: space.sm,
    borderRadius: radius.md,
    backgroundColor: colors.brand.soft,
  },
  ghostText: { ...type.bodyStrong, color: colors.brand.primary },
  dangerBtn: {
    height: 40,
    paddingHorizontal: space.sm,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FDECEA',
  },
  dangerText: { ...type.bodyStrong, color: colors.semantic.danger },

  warnText: { ...type.caption, color: colors.text.tertiary, marginTop: space.xs, lineHeight: 17 },

  channelRow: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 3 },
  channelName: { ...type.caption, color: colors.text.primary, flex: 1 },
  channelValue: { ...type.caption, color: colors.text.tertiary },

  pill: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: radius.pill },
  pillText: { ...type.caption, fontWeight: '700' },
});

export default AdminScreen;
