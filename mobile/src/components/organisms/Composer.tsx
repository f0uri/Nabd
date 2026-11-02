import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../atoms/Avatar';
import { CURRENT_USER } from '../../api/mockApi';
import { useAuthStore } from '../../store/useAuthStore';
import { useUiStore } from '../../store/useUiStore';
import { colors, control, glass, radius, space, type } from '../../theme';

export const Composer: React.FC<{ onPress?: () => void }> = ({ onPress }) => {
  const user = useAuthStore((s) => s.user) ?? CURRENT_USER;
  const openProfile = useUiStore((s) => s.openProfile);
  return (
    <View style={styles.card}>
      <Pressable onPress={openProfile} accessibilityRole="button" accessibilityLabel="ملفي الشخصي">
        <Avatar initial={user.name} avatarUrl={user.avatarUrl} size={42} />
      </Pressable>
      <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel="اكتب منشوراً جديداً"
        style={({ pressed }) => [styles.fakeInput, pressed && { opacity: 0.65 }]}>
        <Text style={styles.placeholder}>فيمَ تفكر؟</Text>
      </Pressable>
      <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel="نشر صورة أو فيديو" style={styles.photo}>
        <Ionicons name="image" size={25} color={colors.brand.dark} />
      </Pressable>
    </View>
  );
};
const styles = StyleSheet.create({
  card: { backgroundColor: colors.bg.canvas, marginHorizontal: space.xs, marginBottom: space.sm, flexDirection: 'row', alignItems: 'center', gap: space.xs, paddingHorizontal: space.sm, paddingVertical: space.sm },
  fakeInput: { flex: 1, minHeight: 48, borderRadius: radius.pill, borderWidth: 1, borderColor: glass.stroke, backgroundColor: control.surface, justifyContent: 'center', paddingHorizontal: space.md },
  placeholder: { ...type.body, fontSize: 15, color: colors.text.secondary, textAlign: 'auto' },
  photo: { width: 44, height: 44, borderRadius: 14, backgroundColor: colors.brand.soft, borderWidth: 1, borderColor: colors.brand.primary, alignItems: 'center', justifyContent: 'center' },
});
export default Composer;
