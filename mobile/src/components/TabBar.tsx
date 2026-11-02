import React from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Avatar from './atoms/Avatar';
import GlassDock from './GlassDock';
import { colors, control, glass, radius, shadow, size, space, type } from '../theme';
import { useFeedStore } from '../store/useFeedStore';
import { useAuthStore } from '../store/useAuthStore';
import type { TabKey } from '../types';

type Icon = React.ComponentProps<typeof Ionicons>['name'];
const TABS: Array<{ key: TabKey; label: string; icon: Icon; activeIcon: Icon; shortLabel?: string }> = [
  { key: 'feed', label: 'الرئيسية', icon: 'home-outline', activeIcon: 'home' },
  { key: 'reels', label: 'ريلز', icon: 'play-circle-outline', activeIcon: 'play-circle' },
  { key: 'friends', label: 'الأصدقاء', shortLabel: 'أصدقاء', icon: 'people-outline', activeIcon: 'people' },
  // Keep an existing destination rather than displaying a nonfunctional marketplace.
  { key: 'menu', label: 'القائمة', icon: 'grid-outline', activeIcon: 'grid' },
  { key: 'notifications', label: 'الإشعارات', shortLabel: 'تنبيهات', icon: 'notifications-outline', activeIcon: 'notifications' },
  { key: 'profile', label: 'ملفي الشخصي', shortLabel: 'حسابي', icon: 'person-circle-outline', activeIcon: 'person-circle' },
];
interface TabBarProps { active: TabKey; onChange: (tab: TabKey) => void }
export const TabBar: React.FC<TabBarProps> = ({ active, onChange }) => {
  const insets = useSafeAreaInsets();
  const { fontScale } = useWindowDimensions();
  const unread = useFeedStore((s) => s.unreadNotifications);
  const user = useAuthStore((s) => s.user);
  return (
    <View nativeID="nabd-midnight-dock-v1" pointerEvents="box-none" style={[styles.dock, { paddingBottom: Math.max(insets.bottom, space.xs) }]}><View style={[styles.bar, { minHeight: size.tabBar + Math.max(0, fontScale - 1) * type.micro.lineHeight }]} accessibilityRole="tablist">
      <GlassDock />
      {TABS.map((tab) => {
        const selected = tab.key === active;
        return (
          <Pressable key={tab.key} onPress={() => onChange(tab.key)} accessibilityRole="tab"
            accessibilityState={{ selected }} accessibilityLabel={tab.label}
            style={({ pressed }) => [styles.tab, selected && styles.selected, pressed && styles.pressed]}>
            <View>
              {tab.key === 'profile' && user?.avatarUrl ?
                <Avatar avatarUrl={user.avatarUrl} initial={user.name} size={27} ringColor={selected ? colors.brand.primary : undefined} /> :
                tab.key === 'reels' ? <MaterialCommunityIcons name={selected ? 'movie-open-play' : 'movie-open-play-outline'} size={26} color={control.icon} /> :
                <Ionicons name={selected ? tab.activeIcon : tab.icon} size={26} color={control.icon} />}
              {tab.key === 'notifications' && unread > 0 && <View style={styles.badge}>
                <Text style={styles.badgeText}>{unread > 99 ? '99+' : unread}</Text>
              </View>}
            </View>
            <Text numberOfLines={1} style={[styles.label, selected && styles.labelSelected]}>{tab.shortLabel ?? tab.label}</Text>
          </Pressable>
        );
      })}
    </View></View>
  );
};
const styles = StyleSheet.create({
  dock: { position: 'absolute', bottom: 0, start: 0, end: 0, paddingTop: space.xs, alignItems: 'center', ...shadow.raised },
  bar: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'transparent', borderWidth: 1, borderColor: glass.border, borderRadius: radius.pill, width: '94%', maxWidth: 640, overflow: 'hidden', padding: 5, minHeight: size.tabBar, ...shadow.card },
  tab: { flex: 1, minHeight: 58, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center', gap: 3, paddingVertical: 4 },
  selected: { backgroundColor: glass.selected, borderWidth: 1, borderColor: glass.stroke },
  pressed: { opacity: 0.9, transform: [{ scale: 0.96 }] },
  label: { ...type.micro, fontSize: 11, color: colors.text.secondary },
  labelSelected: { color: colors.text.primary, fontWeight: '700' },
  badge: { position: 'absolute', top: -5, end: -8, minWidth: 19, height: 19, borderRadius: 10, paddingHorizontal: 4, backgroundColor: colors.brand.primary, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: colors.bg.surface },
  badgeText: { color: colors.text.inverse, fontSize: 9, fontWeight: '700' },
});
export default TabBar;
