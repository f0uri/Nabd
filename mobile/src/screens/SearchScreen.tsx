import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
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
import PostCard from '../components/organisms/PostCard';
import CommentsSheet from '../components/organisms/CommentsSheet';
import { dataApi } from '../api';
import { useAuthStore } from '../store/useAuthStore';
import { useChatStore } from '../store/useChatStore';
import { useFeedStore } from '../store/useFeedStore';
import { CURRENT_USER } from '../api/mockApi';
import { colors, control, glass, radius, size, space, type } from '../theme';
import type { Author, Post } from '../types';
import { useUiStore } from '../store/useUiStore';

type SearchTab = 'people' | 'posts';

const TABS: Array<{ key: SearchTab; label: string; icon: 'people-outline' | 'document-text-outline' }> = [
  { key: 'people', label: 'الناس', icon: 'people-outline' },
  { key: 'posts', label: 'المنشورات', icon: 'document-text-outline' },
];

interface SearchScreenProps {
  onMessage?: (userId: string) => void;
}

/**
 * شاشة البحث — الناس والمنشورات.
 * البحث كيتم فـ الخادم (ilike) مع تأخير 350ms باش ما نغرقوهش بالطلبات.
 */
export const SearchScreen: React.FC<SearchScreenProps> = ({ onMessage }) => {
  const [tab, setTab] = useState<SearchTab>('people');
  const [query, setQuery] = useState('');
  const [people, setPeople] = useState<Author[]>([]);
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [openPostId, setOpenPostId] = useState<string | null>(null);

  const ingestPosts = useFeedStore((s) => s.ingestPosts);
  const toggleReaction = useFeedStore((s) => s.toggleReaction);
  const addComment = useFeedStore((s) => s.addComment);
  const deleteComment = useFeedStore((s) => s.deleteComment);
  const toggleCommentLike = useFeedStore((s) => s.toggleCommentLike);
  const showToast = useFeedStore((s) => s.showToast);
  const currentUser = useAuthStore((s) => s.user) ?? CURRENT_USER;
  const openChat = useChatStore((s) => s.openChat);

  const term = query.trim();

  const run = useCallback(
    async (value: string) => {
      if (!value) {
        setPeople([]);
        setPosts([]);
        return;
      }
      setLoading(true);
      try {
        const [foundPeople, foundPosts] = await Promise.all([
          dataApi.users(value),
          dataApi.searchPosts(value),
        ]);
        setPeople(foundPeople);
        setPosts(foundPosts);
        // كنشاركو المنشورات مع الستور الرئيسي باش التفاعلات كتتزامن مع الفيد
        if (foundPosts.length) ingestPosts(foundPosts);
      } catch {
        showToast('ما قدرناش نبحثو دابا', 'error');
      } finally {
        setLoading(false);
      }
    },
    [ingestPosts, showToast],
  );

  useEffect(() => {
    const timer = setTimeout(() => run(term), 350);
    return () => clearTimeout(timer);
  }, [term, run]);

  // المنشورات من الستور (باش التفاعل يبان فوراً)، وإلا من نتائج البحث
  const storePosts = useFeedStore((s) => s.posts);
  const results = useMemo(() => {
    const byId = new Map(storePosts.map((p) => [p.id, p]));
    return posts.map((p) => byId.get(p.id) ?? p);
  }, [posts, storePosts]);

  const toggleFollow = async (target: Author) => {
    setBusyId(target.id);
    setPeople((prev) =>
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
      setPeople((prev) => prev.map((u) => (u.id === target.id ? target : u)));
      showToast('ما قدرناش نحفظ التغيير', 'error');
    } finally {
      setBusyId(null);
    }
  };

  const renderPerson = (person: Author) => (
    <View key={person.id} style={styles.personRow}>
      <Avatar initial={person.name.charAt(0)} avatarUrl={person.avatarUrl} size={size.avatarMd} />
      <View style={{ flex: 1 }}>
        <Text style={styles.personName} numberOfLines={1}>
          {person.name}
        </Text>
        {!!person.username && <Text style={[styles.personMeta, { writingDirection: 'ltr' }]} selectable>@{person.username}</Text>}
        <Text style={styles.personMeta} numberOfLines={1}>
          {person.bio?.trim() || `${person.friendsCount ?? 0} صديق`}
        </Text>
      </View>

      {person.isFriend && (
        <Pressable
          onPress={() => (onMessage ?? openChat)(person.id)}
          accessibilityRole="button"
          accessibilityLabel={`رسالة لـ${person.name}`}
          style={({ pressed }) => [styles.msgBtn, pressed && { opacity: 0.7 }]}
        >
          <Ionicons name="chatbubble-outline" size={20} color={colors.brand.primary} />
        </Pressable>
      )}

      <Pressable
        onPress={() => toggleFollow(person)}
        disabled={busyId === person.id}
        accessibilityRole="button"
        accessibilityLabel={person.isFriend ? 'إزالة من الأصدقاء' : 'إضافة صديق'}
        style={({ pressed }) => [
          styles.followBtn,
          person.isFriend ? styles.followSecondary : styles.followPrimary,
          pressed && { opacity: 0.75 },
        ]}
      >
        {busyId === person.id ? (
          <ActivityIndicator size="small" color={person.isFriend ? colors.text.secondary : colors.text.inverse} />
        ) : (
          <Text style={[styles.followText, person.isFriend && styles.followTextSecondary]}>
            {person.isFriend ? 'أصدقاء' : 'إضافة'}
          </Text>
        )}
      </Pressable>
    </View>
  );

  const emptyMessage = () => {
    if (!term) return 'كتب شي حاجة فوق — اسم واحد ولا كلمة من شي منشور';
    if (loading) return null;
    return tab === 'people' ? 'ما لقيناش شي حد بهاد الاسم' : 'ما لقيناش منشورات بهاد الكلمة';
  };

  return (
    <View style={styles.flex}>
      <AppBar />

      {/* حقل البحث */}
      <View style={styles.searchWrap}>
        <Ionicons name="search" size={16} color={colors.text.secondary} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="الاسم أو @يوزر الحساب أو منشورات"
          placeholderTextColor={colors.text.secondary}
          style={styles.searchInput}
          textAlign="right"
          returnKeyType="search"
          autoCorrect={false}
          accessibilityLabel="حقل البحث"
        />
        {query.length > 0 && (
          <Pressable onPress={() => setQuery('')} hitSlop={8} accessibilityLabel="مسح البحث">
            <Ionicons name="close-circle" size={16} color={colors.text.tertiary} />
          </Pressable>
        )}
      </View>

      {/* التبويبات */}
      <View style={styles.tabs}>
        {TABS.map((item) => {
          const active = item.key === tab;
          return (
            <Pressable
              key={item.key}
              onPress={() => setTab(item.key)}
              accessibilityRole="tab"
              accessibilityState={{ selected: active }}
              accessibilityLabel={item.label}
              style={[styles.tabBtn, active && styles.tabBtnActive]}
            >
              <Ionicons name={item.icon} size={16} color={active ? colors.brand.primary : colors.text.secondary} />
              <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{item.label}</Text>
            </Pressable>
          );
        })}
      </View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ paddingBottom: space.xxl }}
      >
        {loading && people.length === 0 && posts.length === 0 ? (
          <View style={{ padding: space.md, gap: space.sm }}>
            {Array.from({ length: 5 }).map((_, index) => (
              <View key={index} style={styles.personRow}>
                <SkeletonBlock width={size.avatarMd} height={size.avatarMd} radius={size.avatarMd / 2} />
                <View style={{ flex: 1, gap: 7 }}>
                  <SkeletonBlock width="50%" height={11} />
                  <SkeletonBlock width="28%" height={9} />
                </View>
              </View>
            ))}
          </View>
        ) : tab === 'people' ? (
          people.length > 0 ? (
            <>{people.map(renderPerson)}</>
          ) : (
            <EmptyState text={emptyMessage()} icon="people-outline" />
          )
        ) : results.length > 0 ? (
          results.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              onReact={toggleReaction}
              onPickReaction={toggleReaction}
              onOpenComments={setOpenPostId}
              onMore={() => showToast('خيارات المنشور جاية فالتحديث الجاي', 'info')}
              onOpenReel={() => useUiStore.getState().setTab('reels')}
            />
          ))
        ) : (
          <EmptyState text={emptyMessage()} icon="document-text-outline" />
        )}
      </ScrollView>

      <CommentsSheet
        visible={!!openPostId}
        post={results.find((p) => p.id === openPostId) ?? null}
        currentUser={currentUser}
        onClose={() => setOpenPostId(null)}
        onAddComment={addComment}
        onDeleteComment={deleteComment}
        onLikeComment={toggleCommentLike}
      />
    </View>
  );
};

const EmptyState: React.FC<{ text: string | null; icon: 'people-outline' | 'document-text-outline' }> = ({
  text,
  icon,
}) => {
  if (!text) return null;
  return (
    <View style={styles.empty}>
      <View style={styles.emptyIcon}>
        <Ionicons name={icon} size={24} color={colors.brand.primary} />
      </View>
      <Text style={styles.emptyText}>{text}</Text>
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
    borderRadius: radius.pill,
    backgroundColor: colors.bg.surface,
    borderWidth: size.hairline,
    borderColor: control.border,
  },
  searchInput: { flex: 1, ...type.bodySm, color: colors.text.primary, paddingVertical: 0 },

  tabs: { flexDirection: 'row', gap: space.xs, paddingHorizontal: space.md, paddingBottom: space.xs },
  tabBtn: {
    flex: 1,
    minHeight: size.touchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    borderRadius: radius.pill,
    backgroundColor: colors.bg.surface,
    borderWidth: size.hairline,
    borderColor: control.border,
  },
  tabBtnActive: { backgroundColor: colors.brand.soft, borderColor: colors.brand.primary },
  tabLabel: { ...type.bodySm, color: colors.text.secondary },
  tabLabelActive: { color: colors.brand.primary, fontFamily: type.bodyStrong.fontFamily },

  personRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    backgroundColor: colors.bg.surface,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    marginBottom: space.xs, marginHorizontal: space.sm, borderWidth: 1, borderColor: glass.border, borderRadius: radius.md,
  },
  personName: { ...type.bodyStrong, color: colors.text.primary },
  personMeta: { ...type.caption, color: colors.text.secondary },
  msgBtn: {
    width: size.touchTarget,
    minHeight: size.touchTarget,
    borderRadius: 14,
    backgroundColor: control.selected, borderWidth: 1, borderColor: control.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  followBtn: {
    minWidth: 76,
    minHeight: size.touchTarget,
    paddingHorizontal: space.sm,
    borderRadius: radius.sm,
    alignItems: 'center',
    justifyContent: 'center',
  },
  followPrimary: { backgroundColor: colors.brand.primary },
  followSecondary: { backgroundColor: colors.bg.canvas },
  followText: { ...type.caption, color: colors.text.inverse, fontFamily: type.bodyStrong.fontFamily },
  followTextSecondary: { color: colors.text.primary },

  empty: { alignItems: 'center', gap: space.xs, paddingTop: space.xxxl, paddingHorizontal: space.lg },
  emptyIcon: {
    width: 52,
    height: 52,
    borderRadius: 26,
    backgroundColor: control.selected, borderWidth: 1, borderColor: control.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyText: { ...type.caption, color: colors.text.secondary, textAlign: 'center' },
});

export default SearchScreen;
