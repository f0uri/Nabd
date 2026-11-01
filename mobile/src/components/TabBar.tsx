import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, size, space } from '../theme';
import { useFeedStore } from '../store/useFeedStore';
import type { TabKey } from '../types';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

/**
 * الشريط السفلي — أيقونات فقط بلا كتابة (نمط التطبيقات العالمية).
 * البحث ما كاينش هنا: كيتفتح من أيقونة البحث اللي فوق فالشريط العلوي.
 */
const TABS: Array<{ key: TabKey; label: string; icon: IoniconName; activeIcon: IoniconName }> = [
  { key: 'feed', label: 'الرئيسية', icon: 'home-outline', activeIcon: 'home' },
  { key: 'friends', label: 'الأصدقاء', icon: 'people-outline', activeIcon: 'people' },
  { key: 'reels', label: 'ريلز', icon: 'film-outline', activeIcon: 'film' },
  { key: 'notifications', label: 'الإشعارات', icon: 'notifications-outline', activeIcon: 'notifications' },
  { key: 'menu', label: 'القائمة', icon: 'menu-outline', activeIcon: 'menu' },
];

interface TabBarProps {
  active: TabKey;
  onChange: (tab: TabKey) => void;
}

export const TabBar: React.FC<TabBarProps> = ({ active, onChange }) => {
  const insets = useSafeAreaInsets();
  // شارة الإشعارات حقيقية (كتتحدّث لحظياً عبر WebSocket)
  const unreadNotifications = useFeedStore((s) => s.unreadNotifications);

  return (
    <View style={[styles.bar, { paddingBottom: Math.max(insets.bottom, space.xs) }]}>
      {TABS.map((tab) => {
        const isActive = tab.key === active;
        return (
          <Pressable
            key={tab.key}
            onPress={() => onChange(tab.key)}
            accessibilityRole="tab"
            accessibilityState={{ selected: isActive }}
            accessibilityLabel={tab.label}
            hitSlop={6}
            style={({ pressed }) => [styles.tab, pressed && { opacity: 0.55 }]}
          >
            <View>
              <Ionicons
                name={isActive ? tab.activeIcon : tab.icon}
                size={isActive ? size.iconLg - 2 : size.iconLg - 4}
                color={isActive ? colors.brand.primary : colors.text.secondary}
              />
              {tab.key === 'notifications' && unreadNotifications > 0 && (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{unreadNotifications > 99 ? '99+' : unreadNotifications}</Text>
                </View>
              )}
            </View>
            {isActive && <View style={styles.indicator} />}
          </Pressable>
        );
      })}
    </View>
  );
};

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.bg.surface,
    borderTopWidth: size.hairline,
    borderTopColor: colors.border.divider,
    paddingTop: space.xs + 2,
  },
  tab: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 2, gap: 4 },
  indicator: { width: 18, height: 2.5, borderRadius: 2, backgroundColor: colors.brand.primary },
  badge: {
    position: 'absolute',
    top: -4,
    end: -8,
    minWidth: 17,
    height: 17,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.semantic.danger,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: colors.bg.surface,
  },
  badgeText: { color: '#FFFFFF', fontSize: 9, fontWeight: '700' },
});

export default TabBar;
