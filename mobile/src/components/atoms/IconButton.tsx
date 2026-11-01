import React from 'react';
import { Pressable, StyleSheet, Text, View, type StyleProp, type ViewStyle } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { colors, glass, size } from '../../theme';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

interface IconButtonProps {
  name: IoniconName;
  label: string;
  onPress?: () => void;
  badge?: number;
  variant?: 'soft' | 'plain';
  style?: StyleProp<ViewStyle>;
}

/** ATOM — IconButton (مساحة لمس 40×40 وhitSlop يوصلها لـ 44) */
export const IconButton: React.FC<IconButtonProps> = ({ name, label, onPress, badge, variant = 'soft', style }) => (
  <Pressable
    onPress={onPress}
    accessibilityRole="button"
    accessibilityLabel={label}
    hitSlop={4}
    style={({ pressed }) => [
      styles.base,
      variant === 'soft' ? styles.soft : styles.plain,
      pressed && styles.pressed,
      style,
    ]}
  >
    <Ionicons name={name} size={size.icon} color={colors.text.primary} />
    {!!badge && (
      <View style={styles.badge} accessibilityLabel={`${badge} جديد`}>
        <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
      </View>
    )}
  </Pressable>
);

const styles = StyleSheet.create({
  base: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  soft: { backgroundColor: glass.selected, borderWidth: 1, borderColor: glass.border },
  plain: { backgroundColor: glass.surface, borderWidth: 1, borderColor: glass.stroke },
  pressed: { opacity: 0.7, transform: [{ scale: 0.94 }] },
  badge: {
    position: 'absolute',
    top: -2,
    left: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.semantic.danger,
    borderWidth: 2,
    borderColor: colors.bg.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: '#FFFFFF', fontSize: 10, fontWeight: '700' },
});

export default IconButton;
