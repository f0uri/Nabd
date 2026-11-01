import React, { useEffect } from 'react';
import {
  ActivityIndicator,
  AppState,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useUpdateStore } from '../store/useUpdateStore';
import { glass, radius, shadow, size, space, type } from '../theme';

/** A non-blocking update chip, available before login too. Never prints native diagnostics. */
export default function UpdateNotice() {
  const insets = useSafeAreaInsets();
  const {
    phase,
    visible,
    message,
    enabled,
    errorStep,
    noUpdateReason,
    check,
    install,
    dismiss,
  } = useUpdateStore();
  useEffect(() => {
    if (!enabled) return;
    const timer = setTimeout(() => {
      void check();
    }, 3000);
    let previous = AppState.currentState;
    const subscription = AppState.addEventListener('change', (next) => {
      if (next === 'active' && previous !== 'active') void check();
      previous = next;
    });
    return () => {
      clearTimeout(timer);
      subscription.remove();
    };
  }, [enabled, check]);
  if (!visible) return null;
  const busy = ['checking', 'downloading', 'applying'].includes(phase);
  const label =
    phase === 'available'
      ? 'تحديث التطبيق'
      : phase === 'downloading'
        ? 'جارٍ التحديث…'
        : phase === 'ready'
          ? message
            ? 'تعذّر التثبيت · أعد المحاولة'
            : 'إكمال التحديث'
          : phase === 'applying'
            ? 'جارٍ إعادة التشغيل…'
            : phase === 'checking'
              ? 'جارٍ البحث…'
              : phase === 'error'
                ? errorStep === 'download'
                  ? 'تعذّر التنزيل · أعد المحاولة'
                  : 'تعذّر البحث · أعد المحاولة'
                : !enabled
                  ? 'التحديث غير متاح لهذه النسخة'
                  : noUpdateReason === 'updatePreviouslyFailed'
                    ? 'تعذّر تشغيل التحديث السابق'
                    : 'لا يوجد تحديث متوافق';
  const action =
    phase === 'available'
      ? install
      : phase === 'ready'
        ? install
        : phase === 'error'
          ? errorStep === 'download'
            ? install
            : () => check(true)
          : null;
  return (
    <View
      nativeID="nabd-glass-one-tap-v1"
      pointerEvents="box-none"
      style={[
        styles.overlay,
        { bottom: insets.bottom + size.tabBar + space.lg },
      ]}
    >
      <View style={styles.chip}>
        <Pressable
          onPress={() => {
            if (action) void action();
          }}
          disabled={busy || !action}
          accessibilityRole={action ? 'button' : 'text'}
          accessibilityLabel={
            phase === 'available' || phase === 'ready'
              ? 'تحديث التطبيق وإعادة تشغيله تلقائياً'
              : label
          }
          accessibilityState={{ disabled: busy || !action, busy }}
          style={styles.action}
        >
          {busy ? (
            <ActivityIndicator size="small" color={glass.successText} />
          ) : (
            <Ionicons
              name={
                phase === 'error' || (phase === 'ready' && message)
                  ? 'alert-circle-outline'
                  : 'cloud-download-outline'
              }
              size={22}
              color={glass.successText}
            />
          )}
          <Text style={styles.label} accessibilityLiveRegion="polite">
            {label}
          </Text>
        </Pressable>
        {!busy && (
          <Pressable
            onPress={dismiss}
            accessibilityRole="button"
            accessibilityLabel="إخفاء تنبيه التحديث"
            style={styles.close}
          >
            <Ionicons name="close" size={19} color={glass.successText} />
          </Pressable>
        )}
      </View>
    </View>
  );
}
const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    left: space.md,
    right: space.md,
    alignItems: 'center',
    zIndex: 1000,
  },
  chip: {
    width: '100%',
    maxWidth: 320,
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radius.pill,
    backgroundColor: glass.successSurface,
    borderWidth: 1,
    borderColor: glass.successBorder,
    ...shadow.card,
  },
  action: {
    flex: 1,
    minHeight: 48,
    flexDirection: 'row',
    gap: space.sm,
    alignItems: 'center',
    justifyContent: 'center',
    padding: space.sm,
  },
  label: {
    ...type.bodyStrong,
    color: glass.successText,
    flexShrink: 1,
    textAlign: 'center',
  },
  close: {
    width: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
