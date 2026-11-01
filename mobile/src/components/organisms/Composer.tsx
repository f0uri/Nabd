import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../atoms/Avatar';
import { CURRENT_USER } from '../../api/mockApi';
import { useAuthStore } from '../../store/useAuthStore';
import { colors, radius, size, space, type } from '../../theme';

const ACTIONS = [
  { key: 'photo', label: 'صورة', icon: 'image-outline', color: '#E9710F' },
  { key: 'video', label: 'فيديو', icon: 'videocam-outline', color: '#31A24C' },
  { key: 'feeling', label: 'شعور', icon: 'happy-outline', color: '#F7B125' },
] as const;

/** ORGANISM — Composer (صندوق «فيمَ تفكر») — كيخاطب المستخدم باسمه الحقيقي */
export const Composer: React.FC<{ onPress?: () => void }> = ({ onPress }) => {
  const user = useAuthStore((s) => s.user) ?? CURRENT_USER;
  const firstName = user.name?.trim().split(/\s+/)[0] ?? '';

  return (
  <View style={styles.card}>
    <View style={styles.top}>
      <Avatar initial={user.initial} avatarUrl={user.avatarUrl} size={size.avatarMd} />
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel="اكتب منشوراً جديداً"
        style={({ pressed }) => [styles.fakeInput, pressed && { opacity: 0.7 }]}
      >
        <Text style={styles.placeholder}>{firstName ? `فيمَ تفكر، ${firstName}؟` : 'فيمَ تفكر؟'}</Text>
      </Pressable>
    </View>

    <View style={styles.divider} />

    <View style={styles.actions}>
      {ACTIONS.map((action) => (
        <Pressable
          key={action.key}
          onPress={onPress}
          accessibilityRole="button"
          accessibilityLabel={action.label}
          style={({ pressed }) => [styles.action, pressed && { backgroundColor: colors.bg.canvas }]}
        >
          <Ionicons name={action.icon} size={size.iconSm} color={action.color} />
          <Text style={styles.actionLabel}>{action.label}</Text>
        </Pressable>
      ))}
    </View>
  </View>
  );
};

const styles = StyleSheet.create({
  card: { backgroundColor: colors.bg.surface, marginBottom: space.xs, paddingTop: space.sm },
  top: { flexDirection: 'row', alignItems: 'center', gap: space.xs, paddingHorizontal: space.md },
  fakeInput: {
    flex: 1,
    height: size.avatarMd,
    borderRadius: radius.pill,
    backgroundColor: colors.bg.canvas,
    justifyContent: 'center',
    paddingHorizontal: space.md,
  },
  placeholder: { ...type.body, color: colors.text.secondary },
  divider: {
    height: size.hairline,
    backgroundColor: colors.border.divider,
    marginTop: space.sm,
    marginHorizontal: space.md,
  },
  actions: { flexDirection: 'row', paddingHorizontal: space.xs, paddingVertical: 6 },
  action: {
    flex: 1,
    height: 36,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    borderRadius: radius.sm,
  },
  actionLabel: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },
});

export default Composer;
