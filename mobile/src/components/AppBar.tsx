import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useChatStore } from '../store/useChatStore';
import { useUiStore } from '../store/useUiStore';
import IconButton from './atoms/IconButton';
import { colors, size, space } from '../theme';

interface AppBarProps {
  onOpenChat?: () => void;
  unreadMessages?: number;
  onCompose?: () => void;
}

/** Midnight social header. All shortcuts retain their real destinations. */
export const AppBar: React.FC<AppBarProps> = ({ onOpenChat, unreadMessages, onCompose }) => {
  const storeUnread = useChatStore((s) => s.unread);
  const openChat = useChatStore((s) => s.openChat);
  const setTab = useUiStore((s) => s.setTab);
  return (
    <View nativeID="nabd-midnight-ui-v1" style={styles.bar}>
      <View style={styles.brand}>
        <IconButton name="menu-outline" label="القائمة" variant="plain" onPress={() => setTab('menu')} />
        <Ionicons name="pulse" size={24} color={colors.brand.dark} />
        <Text style={styles.name} numberOfLines={1} accessibilityRole="header">نبض</Text>
      </View>
      <View style={styles.actions}>
        {onCompose && <IconButton name="add" label="إنشاء منشور" variant="primary" onPress={onCompose} />}
        <IconButton name="search-outline" label="بحث" variant="plain" onPress={() => setTab('search')} />
        <IconButton name="chatbubble-ellipses" label="الرسائل" variant="plain"
          badge={(unreadMessages ?? storeUnread) || undefined} onPress={onOpenChat ?? (() => openChat(null))} />
      </View>
    </View>
  );
};
const styles = StyleSheet.create({
  bar: { minHeight: size.appBar + 12, backgroundColor: colors.bg.canvas, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.sm, gap: space.xs },
  brand: { flex: 1, minWidth: 0, flexDirection: 'row', alignItems: 'center', gap: 4 },
  name: { fontSize: 30, lineHeight: 44, fontWeight: '900', color: colors.text.primary, flexShrink: 1 },
  actions: { flexShrink: 0, flexDirection: 'row', alignItems: 'center', gap: 6 },
});
export default AppBar;
export { Ionicons };
