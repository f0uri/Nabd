import React, { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import AppBar from '../components/AppBar';
import Avatar from '../components/atoms/Avatar';
import { getSource } from '../api';
import { useUpdateStore } from '../store/useUpdateStore';
import { useAuthStore } from '../store/useAuthStore';
import { useChatStore } from '../store/useChatStore';
import { useFeedStore } from '../store/useFeedStore';
import { colors, control, glass, radius, shadow, size, space, type } from '../theme';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

interface MenuScreenProps {
  onOpenProfile: () => void;
  onOpenAdmin: () => void;
  onOpenFriends: () => void;
  onOpenNotifications: () => void;
  onOpenChat: () => void;
}

interface RowProps {
  icon: IoniconName;
  label: string;
  hint?: string;
  danger?: boolean;
  badge?: number;
  onPress: () => void;
}

const Row: React.FC<RowProps> = ({ icon, label, hint, danger, badge, onPress }) => (
  <Pressable
    onPress={onPress}
    accessibilityRole="button"
    accessibilityLabel={label}
    style={({ pressed }) => [styles.row, pressed && { backgroundColor: colors.bg.canvas }]}
  >
    <View style={[styles.rowIcon, danger && styles.rowIconDanger]}>
      <Ionicons name={icon} size={19} color={danger ? colors.semantic.danger : colors.brand.primary} />
    </View>
    <View style={{ flex: 1 }}>
      <Text style={[styles.rowLabel, danger && { color: colors.semantic.danger }]}>{label}</Text>
      {hint ? <Text style={styles.rowHint}>{hint}</Text> : null}
    </View>
    {!!badge && badge > 0 && (
      <View style={styles.badge}>
        <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
      </View>
    )}
    <Ionicons name="chevron-back" size={16} color={colors.text.tertiary} />
  </Pressable>
);

/**
 * شاشة القائمة (Menu) — بطاقة الحساب + اختصارات + تسجيل الخروج.
 * على نمط فيسبوك: كل شي فالبلاصة، وبلا إيموجي ف الواجهة.
 */
export const MenuScreen: React.FC<MenuScreenProps> = ({
  onOpenProfile,
  onOpenAdmin,
  onOpenFriends,
  onOpenNotifications,
  onOpenChat,
}) => {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const unreadMessages = useChatStore((s) => s.unread);
  const unreadNotifications = useFeedStore((s) => s.unreadNotifications);
  const showToast = useFeedStore((s) => s.showToast);
  const [signingOut, setSigningOut] = useState(false);

  const confirmLogout = () => {
    Alert.alert('تسجيل الخروج', 'واش متأكد بغيتي تخرج من حسابك؟', [
      { text: 'إلغاء', style: 'cancel' },
      {
        text: 'خروج',
        style: 'destructive',
        onPress: async () => {
          setSigningOut(true);
          try {
            await logout();
          } finally {
            setSigningOut(false);
          }
        },
      },
    ]);
  };

  return (
    <View style={styles.flex}>
      <AppBar />
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: space.xxl }}>
        {/* بطاقة الحساب */}
        <Pressable
          onPress={onOpenProfile}
          accessibilityRole="button"
          accessibilityLabel="افتح ملفي الشخصي"
          style={({ pressed }) => [styles.card, pressed && { opacity: 0.85 }]}
        >
          <Avatar initial={user?.name?.charAt(0)} avatarUrl={user?.avatarUrl} size={size.avatarLg} />
          <View style={{ flex: 1 }}>
            <Text style={styles.name} numberOfLines={1}>
              {user?.name ?? 'حسابي'}
            </Text>
            <Text style={styles.email} numberOfLines={1}>
              {user?.email ?? ''}
            </Text>
            <Text style={styles.meta}>
              {user?.friendsCount ?? 0} صديق · {user?.postsCount ?? 0} منشور
            </Text>
          </View>
          <Ionicons name="chevron-back" size={18} color={colors.text.tertiary} />
        </Pressable>

        {/* اختصارات سريعة */}
        <View style={styles.quick}>
          <QuickAction icon="chatbubble-ellipses-outline" label="الرسائل" badge={unreadMessages} onPress={onOpenChat} />
          <QuickAction
            icon="notifications-outline"
            label="الإشعارات"
            badge={unreadNotifications}
            onPress={onOpenNotifications}
          />
          <QuickAction icon="people-outline" label="الأصدقاء" onPress={onOpenFriends} />
        </View>

        <Text style={styles.section}>حسابي</Text>
        <View style={styles.group}>
          <Row icon="person-outline" label="ملفي الشخصي" hint="منشوراتك · صورك · أصدقاؤك" onPress={onOpenProfile} />
          <Row icon="chatbubble-ellipses-outline" label="الرسائل" badge={unreadMessages} onPress={onOpenChat} />
          <Row icon="people-outline" label="الأصدقاء" onPress={onOpenFriends} />
        </View>

        {user?.isAdmin && (
          <>
            <Text style={styles.section}>الإدارة</Text>
            <View style={styles.group}>
              <Row
                icon="shield-checkmark"
                label="لوحة الأدمن"
                hint="كوكيز إنستغرام · جلب الريلز التلقائي"
                onPress={onOpenAdmin}
              />
            </View>
          </>
        )}

        <Text style={styles.section}>التطبيق</Text>
        <View style={styles.group}>
          <Row icon="cloud-download-outline" label="تحديث التطبيق" hint="البحث عن تحديث جديد لنبض"
            onPress={() => { void useUpdateStore.getState().check(true); }} />
          <Row
            icon="shield-checkmark-outline"
            label="الخصوصية"
            hint="كل منشور كيتحكم فيه صاحبو (عام · أصدقاء · خاص)"
            onPress={() => showToast('الخصوصية كتتحكم فيها من كل منشور، وفالملف الشخصي', 'info')}
          />
          <Row
            icon="cloud-outline"
            label="التخزين"
            hint="معلومات حفظ بيانات التطبيق"
            onPress={() => showToast(getSource() === 'telegram' ? 'التطبيق يستخدم تخزين تلغرام المباشر.' : getSource() === 'graphql' ? 'البيانات محفوظة لدى خدمة التطبيق.' : 'التطبيق في الوضع المحلي.', 'info')}
          />
          <Row
            icon="information-circle-outline"
            label="حول نبض"
            hint="الإصدار 1.0"
            onPress={() => showToast('نبض · الإصدار 1.0', 'info')}
          />
        </View>

        <View style={styles.group}>
          <Row
            icon="log-out-outline"
            label={signingOut ? 'كنخرجو…' : 'تسجيل الخروج'}
            danger
            onPress={confirmLogout}
          />
        </View>

        <Text style={styles.footer}>نبض · الإصدار 1.0</Text>
      </ScrollView>
    </View>
  );
};

const QuickAction: React.FC<{ icon: IoniconName; label: string; badge?: number; onPress: () => void }> = ({
  icon,
  label,
  badge,
  onPress,
}) => (
  <Pressable
    onPress={onPress}
    accessibilityRole="button"
    accessibilityLabel={label}
    style={({ pressed }) => [styles.quickItem, pressed && { opacity: 0.7 }]}
  >
    <View style={styles.quickIcon}>
      <Ionicons name={icon} size={22} color={colors.brand.dark} />
      {!!badge && badge > 0 && (
        <View style={styles.quickBadge}>
          <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
        </View>
      )}
    </View>
    <Text style={styles.quickLabel}>{label}</Text>
  </Pressable>
);

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    backgroundColor: glass.surface, borderWidth: 1, borderColor: glass.border,
    margin: space.md,
    padding: space.md,
    borderRadius: radius.lg,
    ...(shadow.card as object),
  },
  name: { ...type.title, color: colors.text.primary },
  email: { ...type.caption, color: colors.text.secondary },
  meta: { ...type.caption, color: colors.text.tertiary, marginTop: 2 },

  quick: {
    flexDirection: 'row',
    gap: space.xs,
    marginHorizontal: space.md,
    marginBottom: space.xs,
  },
  quickItem: {
    flex: 1,
    alignItems: 'center',
    gap: 6,
    backgroundColor: glass.surface,
    borderRadius: radius.md,
    paddingVertical: space.sm,
    borderWidth: 1,
    borderColor: glass.border,
  },
  quickIcon: {
    width: 44,
    height: 44,
    borderRadius: 15,
    backgroundColor: control.selected, borderWidth: 1, borderColor: control.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  quickBadge: {
    position: 'absolute',
    top: -3,
    end: -6,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.semantic.danger,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: colors.bg.surface,
  },
  quickLabel: { ...type.caption, color: colors.text.primary },

  section: {
    ...type.caption,
    color: colors.text.secondary,
    marginTop: space.md,
    marginBottom: space.xs,
    marginHorizontal: space.lg,
  },
  group: {
    backgroundColor: glass.surface, borderWidth: 1, borderColor: glass.border,
    marginHorizontal: space.md,
    borderRadius: radius.md,
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
  },
  rowIcon: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: control.selected, borderWidth: 1, borderColor: control.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  rowIconDanger: { backgroundColor: '#FDECEE' },
  rowLabel: { ...type.body, color: colors.text.primary },
  rowHint: { ...type.caption, color: colors.text.secondary },
  badge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 6,
    backgroundColor: colors.semantic.danger,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { ...type.caption, color: '#FFFFFF', fontFamily: type.bodyStrong.fontFamily },
  footer: {
    ...type.caption,
    color: colors.text.tertiary,
    textAlign: 'center',
    marginTop: space.xl,
    paddingHorizontal: space.lg,
  },
});

export default MenuScreen;
