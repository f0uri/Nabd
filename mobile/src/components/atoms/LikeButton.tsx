import React, { useRef } from 'react';
import { Animated, Pressable, StyleSheet, Text } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, motion, size, space, type } from '../../theme';

interface LikeButtonProps {
  /** هل المستخدم الحالي متفاعل؟ */
  active: boolean;
  label?: string;
  onPress: () => void;
  /** ضغطة مطوّلة → منتقي التفاعلات */
  onLongPress?: () => void;
  disabled?: boolean;
}

/**
 * ATOM — LikeButton
 * عند اللمس: أنيميشن نبض فوري (لا ينتظر الشبكة) — جزء من الـ Optimistic UI.
 */
export const LikeButton: React.FC<LikeButtonProps> = ({ active, label = 'أعجبني', onPress, onLongPress, disabled }) => {
  const scale = useRef(new Animated.Value(1)).current;

  const handlePress = () => {
    Animated.sequence([
      Animated.spring(scale, { toValue: 1.3, useNativeDriver: true, damping: 8, stiffness: 260, mass: 0.5 }),
      Animated.spring(scale, { toValue: 1, useNativeDriver: true, damping: 10, stiffness: 220, mass: 0.5 }),
    ]).start();
    onPress();
  };

  return (
    <Pressable
      onPress={handlePress}
      onLongPress={onLongPress}
      delayLongPress={220}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ selected: active, disabled: !!disabled }}
      accessibilityLabel={active ? 'إلغاء الإعجاب' : 'أعجبني'}
      hitSlop={6}
      style={({ pressed }) => [styles.button, pressed && styles.pressed, disabled && styles.disabled]}
      android_ripple={{ color: colors.bg.canvas, borderless: false, radius: 60 }}
    >
      <Animated.View style={{ transform: [{ scale }] }}>
        <Ionicons
          name={active ? 'thumbs-up' : 'thumbs-up-outline'}
          size={size.iconSm + 2}
          color={active ? colors.reaction.like : colors.text.secondary}
        />
      </Animated.View>
      <Text style={[styles.label, active && styles.labelActive]}>{label}</Text>
    </Pressable>
  );
};

const styles = StyleSheet.create({
  button: {
    flex: 1,
    height: size.touchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.xs - 2,
    borderRadius: 8,
  },
  pressed: { opacity: 0.7, backgroundColor: colors.bg.canvas },
  disabled: { opacity: 0.4 },
  label: {
    ...type.bodySm,
    fontFamily: type.bodyStrong.fontFamily,
    color: colors.text.secondary,
  },
  labelActive: { color: colors.reaction.like },
});

export default LikeButton;
export { motion };
