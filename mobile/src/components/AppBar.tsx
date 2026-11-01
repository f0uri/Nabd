import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useChatStore } from '../store/useChatStore';
import { useUiStore } from '../store/useUiStore';
import IconButton from './atoms/IconButton';
import { colors, space } from '../theme';

interface AppBarProps {
  onOpenChat?: () => void;
  unreadMessages?: number;
  onCompose?: () => void;
}

/** White, compact social header. All shortcuts retain their real destinations. */
export const AppBar: React.FC<AppBarProps> = ({ onOpenChat, unreadMessages, onCompose }) => {
  const storeUnread = useChatStore((s) => s.unread);
  const openChat = useChatStore((s) => s.openChat);
  const setTab = useUiStore((s) => s.setTab);
  return (
    <View style={styles.bar}>
      <View style={styles.brand}>
        <IconButton name="menu-outline" label="القائمة" variant="plain" onPress={() => setTab('menu')} />
        <Text style={styles.name} accessibilityRole="header">نبض</Text>
      </View>
      <View style={styles.actions}>
        {onCompose && <IconButton name="add-circle-outline" label="إنشاء منشور" variant="plain" onPress={onCompose} />}
        <IconButton name="search-outline" label="بحث" variant="plain" onPress={() => setTab('search')} />
        <IconButton name="chatbubble-ellipses" label="الرسائل" variant="plain"
          badge={(unreadMessages ?? storeUnread) || undefined} onPress={onOpenChat ?? (() => openChat(null))} />
      </View>
    </View>
  );
};
const styles = StyleSheet.create({
  bar: { minHeight: 60, backgroundColor: colors.bg.surface, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.xs },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  name: { fontSize: 34, lineHeight: 46, fontWeight: '800', color: colors.brand.primary, paddingHorizontal: 2 },
  actions: { flexDirection: 'row', alignItems: 'center', gap: 2 },
});
export default AppBar;
export { Ionicons };
