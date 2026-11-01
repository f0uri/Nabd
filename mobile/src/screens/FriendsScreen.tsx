import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import AppBar from '../components/AppBar';
import Avatar from '../components/atoms/Avatar';
import SkeletonBlock from '../components/atoms/Skeleton';
import { dataApi } from '../api';
import { useFeedStore } from '../store/useFeedStore';
import { colors, control, glass, radius, shadow, size, space, type } from '../theme';
import type { Author } from '../types';

interface UserRowProps {
  user: Author;
  busy: boolean;
  onToggleFollow: (user: Author) => void;
  onMessage?: (user: Author) => void;
}

const UserRow: React.FC<UserRowProps> = ({ user, busy, onToggleFollow, onMessage }) => (
  <View style={styles.row}>
    <Avatar initial={user.initial} color={user.color} avatarUrl={user.avatarUrl} size={size.avatarMd} />
    <View style={styles.rowBody}>
      <View style={styles.nameRow}>
        <Text style={styles.name} numberOfLines={1}>
          {user.name}
        </Text>
        {user.verified && <Ionicons name="checkmark-circle" size={13} color={colors.brand.primary} />}
      </View>
      {!!user.username && <Text style={[styles.meta, { writingDirection: 'ltr' }]} selectable>@{user.username}</Text>}
      <Text style={styles.meta} numberOfLines={1}>
        {user.friendsCount ?? 0} صديق{user.bio ? ` · ${user.bio}` : ''}
      </Text>
    </View>

    {onMessage && user.isFriend && (
      <Pressable
        onPress={() => onMessage(user)}
        accessibilityRole="button"
        accessibilityLabel={`إرسال رسالة لـ${user.name}`}
        style={({ pressed }) => [styles.msgBtn, pressed && { opacity: 0.7 }]}
      >
        <Ionicons name="chatbubble-outline" size={20} color={colors.brand.primary} />
      </Pressable>
    )}

    <Pressable
      onPress={() => onToggleFollow(user)}
      disabled={busy}
      accessibilityRole="button"
      accessibilityLabel={user.isFriend ? `إلغاء صداقة ${user.name}` : `إضافة ${user.name}`}
      style={({ pressed }) => [
        styles.followBtn,
        user.isFriend ? styles.followBtnSecondary : styles.followBtnPrimary,
        pressed && { opacity: 0.75 },
      ]}
    >
      {busy ? (
        <ActivityIndicator size="small" color={user.isFriend ? colors.text.secondary : colors.text.inverse} />
      ) : (
        <>
          <Ionicons
            name={user.isFriend ? 'checkmark' : 'person-add-outline'}
            size={14}
            color={user.isFriend ? colors.text.primary : colors.text.inverse}
          />
          <Text style={[styles.followText, user.isFriend && styles.followTextSecondary]}>
            {user.isFriend ? 'أصدقاء' : 'إضافة'}
          </Text>
        </>
      )}
    </Pressable>
  </View>
);

interface FriendsScreenProps {
  /** كيتنادى ملي كيضغط المستخدم على زر الرسالة ف صف شي حد */
  onMessage?: (userId: string) => void;
}

/** شاشة الأصدقاء: البحث · أصدقائي · أشخاص قد تعرفهم */
export const FriendsScreen: React.FC<FriendsScreenProps> = ({ onMessage: onMessageProp }) => {
  const onMessage = useMemo(
    () => (onMessageProp ? (user: Author) => onMessageProp(user.id) : undefined),
    [onMessageProp],
  );
  const showToast = useFeedStore((s) => s.showToast);
  const [users, setUsers] = useState<Author[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search, setSearch] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(
    async (term = '') => {
      try {
        const list = await dataApi.users(term);
        setUsers(list);
      } catch {
        showToast('ما قدرناش نحمّلو قائمة الناس', 'error');
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [showToast],
  );

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const timer = setTimeout(() => load(search), 350);
    return () => clearTimeout(timer);
  }, [search, load]);

  const friends = useMemo(() => users.filter((u) => u.isFriend), [users]);
  const others = useMemo(() => users.filter((u) => !u.isFriend), [users]);

  const toggleFollow = async (target: Author) => {
    setBusyId(target.id);
    // Optimistic: نبدلو الحالة فوراً
    setUsers((prev) =>
      prev.map((u) =>
        u.id === target.id
          ? { ...u, isFriend: !u.isFriend, friendsCount: Math.max(0, (u.friendsCount ?? 0) + (u.isFriend ? -1 : 1)) }
          : u,
      ),
    );

    try {
      if (target.isFriend) await dataApi.unfollow(target.id);
      else await dataApi.follow(target.id);
    } catch {
      setUsers((prev) => prev.map((u) => (u.id === target.id ? target : u)));
      showToast('ما قدرناش نحفظ التغيير', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const renderRow = (user: Author) => (
    <UserRow
      key={user.id}
      user={user}
      busy={busyId === user.id}
      onToggleFollow={toggleFollow}
      onMessage={onMessage}
    />
  );

  return (
    <View style={styles.flex}>
      <AppBar />

      <View style={styles.searchWrap}>
        <Ionicons name="search" size={16} color={colors.text.secondary} />
        <TextInput
          value={search}
          onChangeText={setSearch}
          placeholder="ابحث بالاسم أو @يوزر الحساب…"
          placeholderTextColor={colors.text.secondary}
          style={styles.searchInput}
          textAlign="right"
          returnKeyType="search"
        />
        {search.length > 0 && (
          <Pressable onPress={() => setSearch('')} hitSlop={8} accessibilityLabel="مسح البحث">
            <Ionicons name="close-circle" size={16} color={colors.text.tertiary} />
          </Pressable>
        )}
      </View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setRefreshing(true);
              load(search);
            }}
            tintColor={colors.brand.primary}
          />
        }
      >
        {loading ? (
          <View style={styles.skeletonWrap}>
            {Array.from({ length: 6 }).map((_, i) => (
              <View key={i} style={styles.row}>
                <SkeletonBlock width={size.avatarMd} height={size.avatarMd} radius={size.avatarMd / 2} />
                <View style={{ flex: 1, gap: 7 }}>
                  <SkeletonBlock width="55%" height={11} />
                  <SkeletonBlock width="30%" height={9} />
                </View>
                <SkeletonBlock width={72} height={30} radius={radius.pill} />
              </View>
            ))}
          </View>
        ) : (
          <>
            {friends.length > 0 && (
              <>
                <Text style={styles.sectionTitle}>أصدقائي ({friends.length})</Text>
                {friends.map(renderRow)}
              </>
            )}

            <Text style={styles.sectionTitle}>
              {search ? `نتائج البحث (${others.length})` : 'أشخاص قد تعرفهم'}
            </Text>
            {others.length === 0 ? (
              <View style={styles.empty}>
                <Ionicons name="people-outline" size={26} color={colors.text.tertiary} />
                <Text style={styles.emptyText}>ما لقيناش شي واحد</Text>
              </View>
            ) : (
              others.map(renderRow)
            )}
          </>
        )}
        <View style={{ height: space.xxl }} />
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  searchWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    marginHorizontal: space.md,
    marginVertical: space.xs,
    minHeight: 48, paddingVertical: space.xs,
    paddingHorizontal: space.sm,
    backgroundColor: colors.bg.surface,
    borderRadius: radius.pill,
    borderWidth: 1, borderColor: control.border,
    ...shadow.card,
  },
  searchInput: { flex: 1, ...type.bodySm, color: colors.text.primary, paddingVertical: 0 },
  sectionTitle: {
    ...type.bodySm,
    fontFamily: type.bodyStrong.fontFamily,
    color: colors.text.secondary,
    paddingHorizontal: space.md,
    paddingTop: space.sm,
    paddingBottom: space.xs,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    backgroundColor: colors.bg.surface,
    paddingHorizontal: space.md,
    paddingVertical: space.xs + 2,
    marginBottom: space.xs, marginHorizontal: space.sm, borderWidth: 1, borderColor: glass.border, borderRadius: radius.md,
  },
  rowBody: { flex: 1, minWidth: 0 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  name: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary, flexShrink: 1 },
  meta: { ...type.caption, color: colors.text.secondary, marginTop: 1 },
  msgBtn: {
    width: size.touchTarget,
    minHeight: size.touchTarget,
    borderRadius: 14,
    backgroundColor: control.selected, borderWidth: 1, borderColor: control.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  followBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: space.sm,
    minHeight: size.touchTarget,
    borderRadius: radius.sm,
    minWidth: 84,
    justifyContent: 'center',
  },
  followBtnPrimary: { backgroundColor: colors.brand.primary },
  followBtnSecondary: { backgroundColor: colors.border.subtle },
  followText: { ...type.caption, fontFamily: type.bodyStrong.fontFamily, color: colors.text.inverse },
  followTextSecondary: { color: colors.text.primary },
  skeletonWrap: { paddingTop: space.xs },
  empty: { alignItems: 'center', paddingVertical: space.xxl, gap: space.xs },
  emptyText: { ...type.bodySm, color: colors.text.secondary },
});

export default FriendsScreen;
