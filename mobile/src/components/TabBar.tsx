import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Avatar from './atoms/Avatar';
import { colors, size, space } from '../theme';
import { useFeedStore } from '../store/useFeedStore';
import { useAuthStore } from '../store/useAuthStore';
import type { TabKey } from '../types';

type Icon = React.ComponentProps<typeof Ionicons>['name'];
const TABS: Array<{ key: TabKey; label: string; icon: Icon; activeIcon: Icon }> = [
  { key: 'feed', label: 'الرئيسية', icon: 'home-outline', activeIcon: 'home' },
  { key: 'reels', label: 'ريلز', icon: 'play-circle-outline', activeIcon: 'play-circle' },
  { key: 'friends', label: 'الأصدقاء', icon: 'people-outline', activeIcon: 'people' },
  // Keep an existing destination rather than displaying a nonfunctional marketplace.
  { key: 'menu', label: 'القائمة', icon: 'grid-outline', activeIcon: 'grid' },
  { key: 'notifications', label: 'الإشعارات', icon: 'notifications-outline', activeIcon: 'notifications' },
  { key: 'profile', label: 'ملفي الشخصي', icon: 'person-circle-outline', activeIcon: 'person-circle' },
];
interface TabBarProps { active: TabKey; onChange: (tab: TabKey) => void }
export const TabBar: React.FC<TabBarProps> = ({ active, onChange }) => {
  const insets = useSafeAreaInsets();
  const unread = useFeedStore((s) => s.unreadNotifications);
  const user = useAuthStore((s) => s.user);
  return (
    <View style={[styles.bar, { paddingBottom: Math.max(insets.bottom, space.xs) }]}>
      {TABS.map((tab) => {
        const selected = tab.key === active;
        return (
          <Pressable key={tab.key} onPress={() => onChange(tab.key)} accessibilityRole="tab"
            accessibilityState={{ selected }} accessibilityLabel={tab.label}
            style={({ pressed }) => [styles.tab, pressed && { opacity: 0.5 }]}>
            <View>
              {tab.key === 'profile' && user?.avatarUrl ?
                <Avatar avatarUrl={user.avatarUrl} initial={user.name} size={27} ringColor={selected ? colors.brand.primary : undefined} /> :
                tab.key === 'reels' ? <MaterialCommunityIcons name={selected ? 'movie-open-play' : 'movie-open-play-outline'} size={29} color={selected ? colors.brand.primary : colors.text.primary} /> :
                <Ionicons name={selected ? tab.activeIcon : tab.icon} size={29} color={selected ? colors.brand.primary : colors.text.primary} />}
              {tab.key === 'notifications' && unread > 0 && <View style={styles.badge}>
                <Text style={styles.badgeText}>{unread > 99 ? '99+' : unread}</Text>
              </View>}
            </View>
          </Pressable>
        );
      })}
    </View>
  );
};
const styles = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', backgroundColor: colors.bg.surface, borderTopWidth: size.hairline, borderTopColor: colors.border.subtle, paddingTop: 5 },
  tab: { flex: 1, minHeight: 46, alignItems: 'center', justifyContent: 'center' },
  badge: { position: 'absolute', top: -5, end: -8, minWidth: 19, height: 19, borderRadius: 10, paddingHorizontal: 4, backgroundColor: colors.semantic.danger, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: colors.bg.surface },
  badgeText: { color: colors.text.inverse, fontSize: 9, fontWeight: '700' },
});
export default TabBar;
