import React from 'react';
import { useChatStore } from '../store/useChatStore';
import { useUiStore } from '../store/useUiStore';
import { StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import IconButton from './atoms/IconButton';
import { colors, size, space, type } from '../theme';

interface AppBarProps {
  /** كيتنادى ملي المستخدم كيضغط على أيقونة الرسائل */
  onOpenChat?: () => void;
  /** عدّاد الرسائل غير المقروءة */
  unreadMessages?: number;
}

/** شريط التطبيق العلوي — الشعار + أيقونة البحث + أيقونة الرسائل (الإشعارات فالشريط السفلي) */
export const AppBar: React.FC<AppBarProps> = ({
  onOpenChat,
  unreadMessages,
}) => {
  const storeUnread = useChatStore((s) => s.unread);
  const openChat = useChatStore((s) => s.openChat);
  const setTab = useUiStore((s) => s.setTab);
  const messages = unreadMessages ?? storeUnread;
  const openMessages = onOpenChat ?? (() => openChat(null));

  return (
  <View style={styles.bar}>
    <View style={styles.brand}>
      <View style={styles.logo}>
        <Text style={styles.logoText}>ن</Text>
      </View>
      <Text style={styles.name}>نبض</Text>
    </View>

    <View style={styles.actions}>
      <IconButton name="search" label="بحث" onPress={() => setTab('search')} />
      <IconButton
        name="chatbubble-ellipses"
        label="الرسائل"
        badge={messages || undefined}
        onPress={openMessages}
      />
    </View>
  </View>
  );
};

const styles = StyleSheet.create({
  bar: {
    height: size.appBar,
    backgroundColor: colors.bg.surface,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    borderBottomWidth: size.hairline,
    borderBottomColor: colors.border.subtle,
  },
  brand: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  logo: {
    width: 32,
    height: 32,
    borderRadius: 10,
    backgroundColor: colors.brand.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoText: { color: colors.text.inverse, fontSize: 19, fontWeight: '800' },
  name: { ...type.headline, color: colors.brand.primary },
  actions: { flexDirection: 'row', gap: 6 },
});

export default AppBar;
export { Ionicons };
