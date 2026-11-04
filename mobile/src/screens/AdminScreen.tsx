import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as Clipboard from 'expo-clipboard';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system';
import AppBar from '../components/AppBar';
import { dataApi } from '../api';
import type { InstagramAdminStatus, InstagramChannel, InstagramProbe } from '../api/client';
import { useFeedStore } from '../store/useFeedStore';
import { colors, radius, shadow, space, type } from '../theme';

const adminErrorCode = (error: unknown) => {
  const value = error as { code?: string; message?: string };
  return value?.code === 'GRAPHQL_ERROR' ? value.message : value?.code;
};

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
  const [importedFileName, setImportedFileName] = useState<string | null>(null);
  const [readingFile, setReadingFile] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [probe, setProbe] = useState<InstagramProbe | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const next = await dataApi.instagramSessionStatus();
      setStatus(next);
    } catch (error) {
      const code = adminErrorCode(error);
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
      setImportedFileName(null);
      await load();
      showToast(
        result.valid ? `تم التحقق من الحساب ✓ (${result.totalCodes} ريل متاح)` : 'تم الحفظ، لكن تعذّر التحقق من الجلسة',
        result.valid ? 'success' : 'error',
      );
    } catch (error) {
      const code = adminErrorCode(error) ?? '';
      showToast(
        code === 'INVALID_INSTAGRAM_COOKIE' ? 'صيغة الملف غير مدعومة أو الكوكيز غير صالحة'
          : code === 'INSTAGRAM_SESSION_EXPIRED' ? 'الجلسة منتهية؛ استورد جلسة جديدة'
          : code === 'INSTAGRAM_SESSION_KEY_REQUIRED' ? 'يجب ضبط مفتاح تشفير الجلسة في الخادم أولاً'
          : 'ما قدرناش نسجلو الجلسة',
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
      showToast(result.valid ? 'تم التحقق من الحساب ✓' : 'تعذّر التحقق؛ راجع نتيجة الاختبار', result.valid ? 'success' : 'error');
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
    if (text) { setCookieInput(text); setImportedFileName(null); }
  };

  const importSessionFile = async () => {
    setReadingFile(true);
    let cachedUri: string | undefined;
    try {
      const result = await DocumentPicker.getDocumentAsync({ type: '*/*', copyToCacheDirectory: true, multiple: false });
      if (result.canceled) return;
      const asset = result.assets[0];
      if (Platform.OS !== 'web') cachedUri = asset.uri;
      if (!/\.(json|txt|cookies|session)$/i.test(asset.name)) throw new Error('FORMAT');
      const info = Platform.OS === 'web' ? null : await FileSystem.getInfoAsync(asset.uri);
      const size = asset.file?.size ?? asset.size ?? (info?.exists ? info.size : undefined);
      if (size === undefined || size > 64 * 1024) throw new Error('SIZE');
      const content = Platform.OS === 'web'
        ? await asset.file!.text()
        : await FileSystem.readAsStringAsync(asset.uri);
      if (!content.trim() || content.length > 64 * 1024) throw new Error('FORMAT');
      setCookieInput(content);
      setImportedFileName(asset.name);
      setProbe(null);
      showToast('الملف جاهز؛ اضغط حفظ واختبار لإرساله إلى الخادم', 'success');
    } catch (error) {
      showToast((error as Error).message === 'SIZE' ? 'الحد الأقصى للملف 64 كيلوبايت' : 'اختر ملف جلسة JSON أو cookies.txt صالحًا', 'error');
    } finally {
      // Delete only our document-picker cache copy, never the original file.
      if (cachedUri && FileSystem.cacheDirectory && cachedUri.startsWith(FileSystem.cacheDirectory)) {
        await FileSystem.deleteAsync(cachedUri, { idempotent: true }).catch(() => {});
      }
      setReadingFile(false);
    }
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
            <Text style={styles.subtitle}>ربط جلسة Instagram واستيراد الريلز المتاحة للحساب</Text>
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
              {status?.configured ? `جلسة إنستغرام مربوطة (${status.fingerprint})` : 'لا توجد جلسة محفوظة'}
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
            <Text style={styles.cardTitle}>ملف جلسة Instagram</Text>
            <Pressable onPress={pasteFromClipboard} disabled={saving || readingFile || testing} hitSlop={8} style={styles.pasteBtn} accessibilityRole="button">
              <Ionicons name="clipboard-outline" size={14} color={colors.brand.primary} />
              <Text style={styles.pasteText}>لصق</Text>
            </Pressable>
          </View>

          <Text style={styles.helpText}>
            استورد ملف JSON أو cookies.txt لحساب تملكه. ندعم تصدير كوكيز المتصفح،
            وملفات تحتوي sessionid أو authorization_data. الحد الأقصى 64 كيلوبايت.
          </Text>
          <Pressable onPress={importSessionFile} disabled={saving || readingFile || testing}
            style={styles.ghostBtn} accessibilityRole="button" accessibilityLabel="استيراد ملف جلسة Instagram">
            {readingFile ? <ActivityIndicator size="small" color={colors.brand.primary} /> : <>
              <Ionicons name="document-attach-outline" size={18} color={colors.brand.primary} />
              <Text style={styles.ghostText}>اختيار ملف الجلسة</Text>
            </>}
          </Pressable>
          {importedFileName ? <View style={styles.fileRow}>
            <Text style={[styles.helpText, { flex: 1 }]} numberOfLines={1}>ملف جاهز: {importedFileName}</Text>
            <Pressable accessibilityRole="button" onPress={() => { setCookieInput(''); setImportedFileName(null); }} disabled={saving || readingFile || testing}>
              <Text style={styles.dangerText}>إزالة</Text>
            </Pressable>
          </View> : <>
          <TextInput
            value={cookieInput}
            onChangeText={setCookieInput}
            editable={!saving && !readingFile && !testing}
            placeholder="sessionid=…; csrftoken=…; ds_user_id=…"
            placeholderTextColor={colors.text.tertiary}
            style={styles.input}
            multiline
            autoCapitalize="none"
            autoCorrect={false}
            accessibilityLabel="كوكيز إنستغرام"
          />
          </>}

          <View style={styles.actionsRow}>
            <Pressable
              onPress={saveCookies}
              disabled={saving || readingFile || testing}
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
              disabled={saving || readingFile || testing || !status?.configured}
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
              <Pressable onPress={clearSession} disabled={saving || testing || readingFile} style={styles.dangerBtn} accessibilityRole="button">
                <Text style={styles.dangerText}>حذف</Text>
              </Pressable>
            )}
          </View>

          <Text style={styles.warnText}>
            ⚠️ الجلسة تمنح الوصول إلى حسابك. تُرسل للخادم عبر اتصالك ولا تُعاد للمستخدمين، وتُحفظ مشفّرة. استعمل HTTPS ولا تشارك الملف. الربط غير رسمي وقد يطلب Instagram إعادة تسجيل الدخول.
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
                  { backgroundColor: probe.valid ? colors.brand.soft : colors.statusSurface.danger },
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
          <Pressable onPress={importNow} disabled={saving || readingFile || testing} style={[styles.ghostBtn, { alignSelf: 'flex-start' }]}>
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
  fileRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  pasteBtn: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  pasteText: { ...type.caption, color: colors.brand.dark, fontWeight: '700' },

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
  ghostText: { ...type.bodyStrong, color: colors.brand.dark },
  dangerBtn: {
    height: 40,
    paddingHorizontal: space.sm,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.statusSurface.danger,
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
