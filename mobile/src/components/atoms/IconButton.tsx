import React from 'react';
import { Pressable, StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, control, size } from '../../theme';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

interface IconButtonProps {
  name: IoniconName;
  label: string;
  onPress?: () => void;
  badge?: number;
  variant?: 'soft' | 'plain' | 'primary';
  style?: StyleProp<ViewStyle>;
}

/** ATOM — IconButton (مساحة لمس 44×44 مع حدود مرئية) */
export const IconButton: React.FC<IconButtonProps> = ({ name, label, onPress, badge, variant = 'soft', style }) => (
  <Pressable
    onPress={onPress}
    accessibilityRole="button"
    accessibilityLabel={label}
    hitSlop={4}
    style={({ pressed }) => [
      styles.base,
      variant === 'primary' ? styles.primary : variant === 'soft' ? styles.soft : styles.plain,
      pressed && styles.pressed,
      style,
    ]}
  >
    <Ionicons name={name} size={size.icon} color={variant === 'primary' ? colors.text.inverse : control.icon} />
    {!!badge && (
      <View style={styles.badge} accessibilityLabel={`${badge} جديد`}>
        <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
      </View>
    )}
  </Pressable>
);

const styles = StyleSheet.create({
  base: {
    width: size.touchTarget,
    height: size.touchTarget,
    borderRadius: size.touchTarget / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  soft: { backgroundColor: control.selected, borderWidth: 1, borderColor: control.border },
  plain: { backgroundColor: control.surface, borderWidth: 1, borderColor: control.border },
  primary: { backgroundColor: colors.brand.primary, borderWidth: 1, borderColor: colors.brand.dark },
  pressed: { opacity: 0.9, transform: [{ scale: 0.97 }] },
  badge: {
    position: 'absolute',
    top: -2,
    left: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.brand.primary,
    borderWidth: 2,
    borderColor: colors.bg.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: '#FFFFFF', fontSize: 10, fontWeight: '700' },
});

export default IconButton;
