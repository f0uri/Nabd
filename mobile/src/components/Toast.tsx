import React, { useEffect, useRef } from 'react';
import { Animated, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import useDockInset from './useDockInset';
import { useFeedStore } from '../store/useFeedStore';
import { colors, motion, radius, shadow, space, type } from '../theme';

const TONE: Record<string, { icon: 'alert-circle' | 'information-circle' | 'checkmark-circle'; color: string }> = {
  error: { icon: 'alert-circle', color: '#FF6B6B' },
  info: { icon: 'information-circle', color: '#4DA3FF' },
  success: { icon: 'checkmark-circle', color: '#5CD67E' },
};

/** Toast صغير أعلى الـ tab bar — بديل للنوافذ المنبثقة المزعجة */
export const Toast: React.FC = () => {
  const toast = useFeedStore((s) => s.toast);
  const dismiss = useFeedStore((s) => s.dismissToast);
  const dockInset = useDockInset();
  const translate = useRef(new Animated.Value(20)).current;
  const opacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!toast) return;
    Animated.parallel([
      Animated.timing(opacity, { toValue: 1, duration: motion.normal, useNativeDriver: true }),
      Animated.timing(translate, { toValue: 0, duration: motion.normal, useNativeDriver: true }),
    ]).start();

    const timer = setTimeout(() => {
      Animated.parallel([
        Animated.timing(opacity, { toValue: 0, duration: motion.normal, useNativeDriver: true }),
        Animated.timing(translate, { toValue: 20, duration: motion.normal, useNativeDriver: true }),
      ]).start(() => dismiss());
    }, 3400);

    return () => clearTimeout(timer);
  }, [toast, opacity, translate, dismiss]);

  if (!toast) return null;
  const tone = TONE[toast.tone] ?? TONE.info;

  return (
    <Animated.View
      pointerEvents="box-none"
      style={[
        styles.wrap,
        { bottom: dockInset + space.xs, opacity, transform: [{ translateY: translate }] },
      ]}
    >
      <Pressable onPress={dismiss} style={styles.toast} accessibilityRole="alert">
        <Ionicons name={tone.icon} size={18} color={tone.color} />
        <Text style={styles.text}>{toast.message}</Text>
      </Pressable>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: space.md, right: space.md },
  toast: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    backgroundColor: '#1C1E21',
    borderRadius: radius.md,
    paddingHorizontal: space.sm + 2,
    paddingVertical: space.sm,
    ...shadow.raised,
  },
  text: { ...type.bodySm, color: colors.text.inverse, flex: 1 },
});

export default Toast;
