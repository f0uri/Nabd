import useDockInset from '../components/useDockInset';
import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import AppBar from '../components/AppBar';
import Avatar from '../components/atoms/Avatar';
import SkeletonBlock from '../components/atoms/Skeleton';
import { dataApi } from '../api';
import { useFeedStore } from '../store/useFeedStore';
import { useChatStore } from '../store/useChatStore';
import CommentsSheet from '../components/organisms/CommentsSheet';
import { useAuthStore } from '../store/useAuthStore';
import { CURRENT_USER } from '../api/mockApi';
import { colors, glass, radius, size, space, type } from '../theme';
import type { AppNotification, NotificationType } from '../types';

type IoniconName = React.ComponentProps<typeof Ionicons>['name'];

const TYPE_META: Record<NotificationType, { icon: IoniconName; color: string }> = {
  like: { icon: 'thumbs-up', color: colors.reaction.like },
  comment: { icon: 'chatbubble', color: '#197546' },
  follow: { icon: 'person-add', color: '#8E2DE2' },
  share: { icon: 'arrow-redo', color: '#9B4C08' },
  message: { icon: 'chatbubble-ellipses', color: colors.brand.primary },
};

/** شاشة الإشعارات — من الخادم، مع Skeleton بنفس هندسة الصفوف */
export const NotificationsScreen: React.FC = () => {
  const dockInset = useDockInset();
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const openChat = useChatStore((s) => s.openChat);
  const posts = useFeedStore((s) => s.posts);
  const addComment = useFeedStore((s) => s.addComment);
  const deleteComment = useFeedStore((s) => s.deleteComment);
  const toggleCommentLike = useFeedStore((s) => s.toggleCommentLike);
  const showToast = useFeedStore((s) => s.showToast);
  const currentUser = useAuthStore((s) => s.user) ?? CURRENT_USER;
  const [openPostId, setOpenPostId] = useState<string | null>(null);

  const load = useCallback(async (markRead = false) => {
    try {
      const list = await dataApi.fetchNotifications();
      setItems(list);
      if (markRead && list.some((n) => !n.read)) {
        await dataApi.markNotificationsRead();
        setItems(list.map((n) => ({ ...n, read: true })));
        // نحدّثو شارة الشريط العلوي بلا ما نعاودو نقراو القائمة
        useFeedStore.setState({ unreadNotifications: 0 });
      }
    } catch {
      setItems([]);
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    load(true);
  }, [load]);

  const unread = items?.filter((n) => !n.read).length ?? 0;

  /* فتح الإشعار: رسالة → المحادثة · تفاعل/تعليق → شاشة التعليقات */
  const handleOpen = (item: AppNotification) => {
    if (item.type === 'message') {
      openChat(item.actor.id);
      return;
    }
    if (item.postId) {
      const post = posts.find((p) => p.id === item.postId);
      if (post) {
        setOpenPostId(post.id);
        return;
      }
    }
    showToast(`${item.actor.name} ${item.text}`, 'info');
  };

  return (
    <View style={styles.flex}>
      <AppBar />
      <View style={styles.titleRow}>
        <Text style={styles.title}>الإشعارات</Text>
        {unread > 0 && (
          <View style={styles.unreadPill}>
            <Text style={styles.unreadPillText}>{unread} جديد</Text>
          </View>
        )}
      </View>

      <ScrollView
        contentContainerStyle={{ paddingBottom: dockInset }}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setRefreshing(true);
              load(false);
            }}
            tintColor={colors.brand.primary}
          />
        }
      >
        {items === null
          ? Array.from({ length: 6 }).map((_, i) => (
              <View key={i} style={styles.row}>
                <SkeletonBlock width={size.avatarMd} height={size.avatarMd} radius={size.avatarMd / 2} />
                <View style={{ flex: 1, gap: 7 }}>
                  <SkeletonBlock width="70%" height={11} />
                  <SkeletonBlock width="35%" height={9} />
                </View>
              </View>
            ))
          : items.length === 0
            ? (
              <View style={styles.empty}>
                <Ionicons name="notifications-off-outline" size={28} color={colors.text.tertiary} />
                <Text style={styles.emptyText}>ما كاين حتى إشعار دابا</Text>
                <Text style={styles.emptyHint}>ملي شي واحد يتفاعل مع منشوراتك غادي يبان هنا</Text>
              </View>
            )
            : items.map((item) => {
                const meta = TYPE_META[item.type];
                return (
                  <Pressable
                    key={item.id}
                    onPress={() => handleOpen(item)}
                    accessibilityRole="button"
                    style={({ pressed }) => [styles.row, !item.read && styles.unreadRow, pressed && { opacity: 0.9 }]}
                    accessibilityLabel={`${item.actor.name} ${item.text}`}
                  >
                    <View>
                      <Avatar initial={item.actor.initial} color={item.actor.color} avatarUrl={item.actor.avatarUrl} size={size.avatarMd} />
                      <View style={[styles.typeBadge, { backgroundColor: meta.color }]}>
                        <Ionicons name={meta.icon} size={12} color={colors.text.inverse} />
                      </View>
                    </View>

                    <View style={styles.body}>
                      <Text style={styles.text}>
                        <Text style={styles.name}>{item.actor.name}</Text> {item.text}
                      </Text>
                      <Text style={styles.time}>{item.createdAt}</Text>
                    </View>

                    {!item.read && <View style={styles.dot} />}
                  </Pressable>
                );
              })}
        <View style={{ height: space.xxl }} />
      </ScrollView>

      <CommentsSheet
        visible={!!openPostId}
        post={posts.find((p) => p.id === openPostId) ?? null}
        currentUser={currentUser}
        onClose={() => setOpenPostId(null)}
        onAddComment={addComment}
        onDeleteComment={deleteComment}
        onLikeComment={toggleCommentLike}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    backgroundColor: colors.bg.surface,
  },
  title: { ...type.display, fontWeight: '800', color: colors.text.primary },
  unreadPill: { backgroundColor: colors.brand.soft, borderRadius: radius.pill, paddingHorizontal: space.xs, paddingVertical: 2 },
  unreadPillText: { ...type.caption, color: colors.brand.dark, fontFamily: type.bodyStrong.fontFamily },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    backgroundColor: colors.bg.surface,
    marginBottom: space.xs, marginHorizontal: space.sm, borderRadius: radius.md, borderWidth: 1, borderColor: glass.border,
  },
  unreadRow: { backgroundColor: colors.brand.soft, borderColor: colors.brand.primary },
  typeBadge: {
    position: 'absolute',
    bottom: -2,
    left: -2,
    width: 22,
    height: 22,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: colors.bg.surface,
  },
  body: { flex: 1 },
  text: { ...type.bodySm, color: colors.text.primary },
  name: { fontFamily: type.bodyStrong.fontFamily },
  time: { ...type.caption, color: colors.text.secondary, marginTop: 2 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.brand.primary },
  empty: { alignItems: 'center', paddingVertical: space.xxxl, gap: space.xs, paddingHorizontal: space.xl },
  emptyText: { ...type.body, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  emptyHint: { ...type.bodySm, color: colors.text.secondary, textAlign: 'center' },
});

export default NotificationsScreen;
