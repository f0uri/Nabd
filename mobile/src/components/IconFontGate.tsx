import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import * as Font from 'expo-font';
import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import { colors, radius, size, space, type } from '../theme';

const ICON_FONTS = { ...Ionicons.font, ...MaterialCommunityIcons.font };

/** Load both shipped icon families before mounting any screen (including reactions/reels).
 * No new native dependency, remote font, timeout race, or account/storage changes.
 */
export default function IconFontGate({
  children,
}: {
  children: React.ReactNode;
}) {
  const [phase, setPhase] = useState<'loading' | 'ready' | 'error'>('loading');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let mounted = true;
    Promise.resolve()
      .then(() => Font.loadAsync(ICON_FONTS))
      .then(() => {
        if (mounted) setPhase('ready');
      })
      .catch(() => {
        if (mounted) setPhase('error');
      });
    return () => {
      mounted = false;
    };
  }, [attempt]);

  if (phase === 'ready')
    return (
      <View nativeID="nabd-icons-ready-v2" style={styles.page}>
        {children}
      </View>
    );
  return (
    <View style={[styles.page, styles.center]}>
      {phase === 'loading' ? (
        <>
          <ActivityIndicator
            color={colors.brand.primary}
            accessibilityLabel="تجهيز الواجهة"
          />
          <Text style={styles.hint} accessibilityLiveRegion="polite">
            جارٍ تجهيز الواجهة…
          </Text>
        </>
      ) : (
        <>
          <Text style={styles.hint} accessibilityLiveRegion="polite">
            تعذّر تحميل الأيقونات
          </Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="إعادة تحميل الأيقونات"
            onPress={() => {
              setPhase('loading');
              setAttempt((value) => value + 1);
            }}
            style={styles.retry}
          >
            <Text style={styles.retryText}>إعادة المحاولة</Text>
          </Pressable>
        </>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  page: { flex: 1, backgroundColor: colors.bg.canvas },
  center: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.sm,
    padding: space.lg,
  },
  hint: { ...type.body, color: colors.text.secondary, textAlign: 'center' },
  retry: {
    minHeight: size.touchTarget,
    paddingHorizontal: space.lg,
    paddingVertical: space.sm,
    borderRadius: radius.pill,
    backgroundColor: colors.brand.primary,
  },
  retryText: { ...type.bodyStrong, color: colors.text.inverse },
});
