import React, { useEffect } from 'react';
import { ActivityIndicator, AppState, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useUpdateStore } from '../store/useUpdateStore';
import { colors, radius, space, type } from '../theme';

/** Available before login too; independent of the application API and Instagram session. */
export default function UpdateNotice() {
  const { phase, visible, message, enabled, errorStep, check, download, apply, dismiss } = useUpdateStore();
  useEffect(() => {
    if (!enabled) return;
    const timer = setTimeout(() => { void check(); }, 3000);
    let previous = AppState.currentState;
    const subscription = AppState.addEventListener('change', (next) => {
      if (next === 'active' && previous !== 'active') void check();
      previous = next;
    });
    return () => { clearTimeout(timer); subscription.remove(); };
  }, [enabled, check]);
  const busy = ['checking', 'downloading', 'applying'].includes(phase);
  const title = phase === 'available' ? 'تحديث جديد لنبض'
    : phase === 'downloading' ? 'جارٍ تنزيل التحديث'
    : phase === 'ready' ? 'التحديث جاهز'
    : phase === 'applying' ? 'جارٍ إعادة التشغيل'
    : phase === 'checking' ? 'جارٍ البحث عن تحديث'
    : phase === 'error' ? 'تعذّر إكمال التحديث' : 'تحديثات نبض';
  const description = message ?? (phase === 'available'
    ? 'يوجد تحديث متوافق مع نسختك. يمكنك تنزيله هنا دون إعادة تثبيت التطبيق.'
    : phase === 'ready' ? 'اكتمل التنزيل. احفظ أي منشور أو رسالة تكتبها قبل إعادة التشغيل. سيُغلق التطبيق ويُفتح على التحديث.'
    : phase === 'downloading' ? 'يرجى الانتظار وعدم إغلاق التطبيق حتى يكتمل التنزيل.'
    : phase === 'applying' ? 'سيعود التطبيق للعمل بعد لحظات.' : 'نتحقق من وجود تحديث متوافق مع جهازك.');
  const action = phase === 'available' ? download : phase === 'ready' ? apply
    : phase === 'error' ? errorStep === 'download' ? download : () => check(true) : null;
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={dismiss} statusBarTranslucent>
      <View style={styles.backdrop}>
        <View style={styles.card} accessibilityViewIsModal>
          <View style={styles.icon}><Ionicons name={phase === 'ready' ? 'checkmark-circle-outline' : 'cloud-download-outline'} size={32} color={colors.brand.primary} /></View>
          <Text style={styles.title} accessibilityRole="header">{title}</Text>
          <Text style={styles.description} accessibilityLiveRegion="polite">{description}</Text>
          {busy && <ActivityIndicator color={colors.brand.primary} style={styles.spinner} accessibilityLabel={title} />}
          {action && !busy && <Pressable onPress={() => { void action(); }} accessibilityRole="button" style={styles.primary}>
            <Text style={styles.primaryText}>{phase === 'ready' ? 'إعادة التشغيل والتحديث' : phase === 'error' ? 'إعادة المحاولة' : 'تنزيل التحديث'}</Text>
          </Pressable>}
          {!busy && <Pressable onPress={dismiss} accessibilityRole="button" style={styles.secondary}>
            <Text style={styles.secondaryText}>{action ? 'لاحقًا' : 'إغلاق'}</Text>
          </Pressable>}
        </View>
      </View>
    </Modal>
  );
}
const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: colors.bg.overlay, justifyContent: 'center', padding: space.xl },
  card: { width: '100%', maxWidth: 440, alignSelf: 'center', backgroundColor: colors.bg.surface, borderRadius: radius.lg, padding: space.xl, gap: space.sm },
  icon: { backgroundColor: colors.brand.soft, width: 60, height: 60, borderRadius: 30, alignItems: 'center', justifyContent: 'center', alignSelf: 'center' },
  title: { ...type.headline, color: colors.text.primary, textAlign: 'center' },
  description: { ...type.body, color: colors.text.secondary, textAlign: 'center' },
  spinner: { marginVertical: space.sm },
  primary: { minHeight: 48, backgroundColor: colors.brand.primary, borderRadius: radius.sm, justifyContent: 'center', alignItems: 'center', padding: space.xs },
  primaryText: { ...type.bodyStrong, color: colors.text.inverse, textAlign: 'center' },
  secondary: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  secondaryText: { ...type.bodyStrong, color: colors.text.secondary },
});
