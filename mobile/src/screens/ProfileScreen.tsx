import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../components/atoms/Avatar';
import * as ImagePicker from 'expo-image-picker';
import type { LocalFile } from '../api/client';
import PostCard from '../components/organisms/PostCard';
import CommentsSheet from '../components/organisms/CommentsSheet';
import SkeletonBlock from '../components/atoms/Skeleton';
import { dataApi } from '../api';
import { useAuthStore } from '../store/useAuthStore';
import { useFeedStore } from '../store/useFeedStore';
import { useStoryStore } from '../store/useStoryStore';
import { colors, radius, shadow, size, space, type } from '../theme';
import type { Author, Post } from '../types';
import { useUiStore } from '../store/useUiStore';

type ProfileTab = 'posts' | 'about' | 'friends' | 'photos';

const TABS: Array<{ key: ProfileTab; label: string }> = [
  { key: 'posts', label: 'المنشورات' },
  { key: 'about', label: 'حول' },
  { key: 'friends', label: 'الأصدقاء' },
  { key: 'photos', label: 'الصور' },
];

/**
 * الملف الشخصي — على نمط فيسبوك:
 * غلاف → صورة رمزية متراكبة → اسم ونبذة → أزرار الإجراءات → تبويبات → محتوى التبويب
 */
export const ProfileScreen: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const updateProfile = useAuthStore((s) => s.updateProfile);
  const authLoading = useAuthStore((s) => s.loading);

  const allPosts = useFeedStore((s) => s.posts);
  const ingestPosts = useFeedStore((s) => s.ingestPosts);
  const toggleReaction = useFeedStore((s) => s.toggleReaction);
  const addComment = useFeedStore((s) => s.addComment);
  const deleteComment = useFeedStore((s) => s.deleteComment);
  const toggleCommentLike = useFeedStore((s) => s.toggleCommentLike);
  const addStory = useStoryStore((s) => s.addStory);
  const showToast = useFeedStore((s) => s.showToast);

  const [tab, setTab] = useState<ProfileTab>('posts');
  const [loadingPosts, setLoadingPosts] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [friends, setFriends] = useState<Author[] | null>(null);
  const [editing, setEditing] = useState(false);
  const [openPostId, setOpenPostId] = useState<string | null>(null);
  const [avatarFile, setAvatarFile] = useState<LocalFile | null>(null);
  const [nameDraft, setNameDraft] = useState('');
  const [bioDraft, setBioDraft] = useState('');

  const myPosts = useMemo(
    () => (user ? allPosts.filter((p) => p.author.id === user.id) : []),
    [allPosts, user],
  );
  const photoPosts = useMemo(() => myPosts.filter((p) => !!p.media), [myPosts]);

  const loadMyPosts = useCallback(async () => {
    if (!user) return;
    try {
      ingestPosts(await dataApi.userPosts(user.id));
    } catch {
      /* نبقى على الموجود */
    } finally {
      setLoadingPosts(false);
      setRefreshing(false);
    }
  }, [user, ingestPosts]);

  useEffect(() => {
    loadMyPosts();
  }, [loadMyPosts]);

  useEffect(() => {
    if (tab !== 'friends' || friends) return;
    let alive = true;
    dataApi
      .friends()
      .then((list) => alive && setFriends(list))
      .catch(() => alive && setFriends([]));
    return () => {
      alive = false;
    };
  }, [tab, friends]);

  /** تغيير صورة الحساب من المعرض → كترفع للخادم وكتتبدّل فكل بلاصة */
  const pickAvatar = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      showToast('خاصنا الإذن للوصول للصور', 'error');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.85,
      allowsEditing: true,
      aspect: [1, 1],
    });
    if (result.canceled || !result.assets?.length) return;
    const asset = result.assets[0];
    setAvatarFile({
      uri: asset.uri,
      name: asset.fileName ?? `avatar_${Date.now()}.jpg`,
      type: asset.mimeType ?? 'image/jpeg',
    });
  };

  const openEditor = () => {
    if (!user) return;
    setNameDraft(user.name);
    setBioDraft(user.bio ?? '');
    setAvatarFile(null);
    setEditing(true);
  };

  const saveEditor = async () => {
    const ok = await updateProfile({
      name: nameDraft.trim(),
      bio: bioDraft.trim(),
      ...(avatarFile ? { file: avatarFile } : {}),
    });
    if (ok) {
      setEditing(false);
      setAvatarFile(null);
      showToast('تم تحديث الملف', 'success');
    } else {
      showToast('ما قدرناش نحفظو التغييرات', 'error');
    }
  };

  if (!user) return null;

  const openPost = allPosts.find((p) => p.id === openPostId) ?? null;

  const memberSince = new Date().getFullYear();

  const renderPost = (post: Post) => (
    <PostCard
      key={post.id}
      post={post}
      onReact={toggleReaction}
      onPickReaction={toggleReaction}
      onOpenComments={setOpenPostId}
      onOpenReel={() => useUiStore.getState().setTab('reels')}
    />
  );

  return (
    <View style={styles.flex}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setRefreshing(true);
              loadMyPosts();
            }}
            tintColor={colors.brand.primary}
          />
        }
      >
        {/* ============ الغلاف ============ */}
        <View style={styles.cover}>
          <View style={styles.coverCircleLg} />
          <View style={styles.coverCircleSm} />
        </View>

        {/* ============ الرأس: صورة + اسم + نبذة ============ */}
        <View style={styles.headCard}>
          <View style={styles.headRow}>
            <View style={styles.avatarCol}>
              <Pressable
                onPress={pickAvatar}
                accessibilityRole="button"
                accessibilityLabel="تغيير صورة الحساب"
                style={({ pressed }) => [styles.avatarRing, pressed && { opacity: 0.85 }]}
              >
                <Avatar initial={user.initial} color={user.color} avatarUrl={user.avatarUrl} size={84} />
                {/* «+» صغيرة فاسفل الصورة من الداخل — للإضافة/التغيير */}
                <View style={styles.avatarEdit}>
                  <Ionicons name="add" size={17} color={colors.text.inverse} />
                </View>
              </Pressable>
              {!user.avatarUrl && (
                <Pressable onPress={pickAvatar} hitSlop={6} accessibilityRole="button">
                  <Text style={styles.addPhoto}>أضف صورة</Text>
                </Pressable>
              )}
            </View>

            <View style={styles.headText}>
              <View style={styles.nameRow}>
                <Text style={styles.name} numberOfLines={1}>
                  {user.name}
                </Text>
                {user.verified && <Ionicons name="checkmark-circle" size={17} color={colors.brand.primary} />}
              </View>
              <Text style={styles.headMeta} numberOfLines={1}>
                {user.friendsCount ?? friends?.length ?? 0} صديق · {user.postsCount ?? myPosts.length} منشور
              </Text>
              {!!user.bio?.trim?.() && (
                <Text style={styles.headBio} numberOfLines={2}>
                  {user.bio}
                </Text>
              )}
            </View>
          </View>

          {/* أزرار الإجراءات */}
          <View style={styles.actionsRow}>
            <Pressable
              onPress={openEditor}
              accessibilityRole="button"
              style={({ pressed }) => [styles.actionPrimary, pressed && { opacity: 0.85 }]}
            >
              <Ionicons name="create-outline" size={16} color={colors.text.inverse} />
              <Text style={styles.actionPrimaryText}>تعديل الملف</Text>
            </Pressable>

            <Pressable
              onPress={async () => {
                const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
                if (!permission.granted) {
                  showToast('خاصنا الإذن للوصول للصور', 'error');
                  return;
                }
                const result = await ImagePicker.launchImageLibraryAsync({
                  mediaTypes: ['images'],
                  quality: 0.85,
                });
                if (result.canceled || !result.assets?.length) {
                  await addStory({ text: 'ستوري جديد من نبض' });
                } else {
                  await addStory({ imageUri: result.assets[0].uri });
                }
                showToast('تزوّدت الستوري — شوفها فالصفحة الرئيسية', 'success');
              }}
              accessibilityRole="button"
              style={({ pressed }) => [styles.actionSecondary, pressed && { opacity: 0.85 }]}
            >
              <Ionicons name="add-circle-outline" size={16} color={colors.text.primary} />
              <Text style={styles.actionSecondaryText}>ستوري</Text>
            </Pressable>

            <Pressable
              onPress={logout}
              accessibilityRole="button"
              accessibilityLabel="تسجيل الخروج"
              style={({ pressed }) => [styles.actionIcon, pressed && { opacity: 0.85 }]}
            >
              <Ionicons name="log-out-outline" size={18} color={colors.text.primary} />
            </Pressable>
          </View>

          {/* التبويبات */}
          <View style={styles.tabsDivider} />
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabs}>
            {TABS.map((item) => {
              const active = tab === item.key;
              return (
                <Pressable
                  key={item.key}
                  onPress={() => setTab(item.key)}
                  accessibilityRole="tab"
                  accessibilityState={{ selected: active }}
                  style={styles.tab}
                >
                  <Text style={[styles.tabText, active && styles.tabTextActive]}>{item.label}</Text>
                  {active && <View style={styles.tabIndicator} />}
                </Pressable>
              );
            })}
          </ScrollView>
        </View>

        {/* ============ محتوى التبويب ============ */}

        {tab === 'posts' &&
          (loadingPosts && myPosts.length === 0 ? (
            <View style={styles.skeletonCard}>
              <View style={styles.skHead}>
                <SkeletonBlock width={40} height={40} radius={20} />
                <View style={{ flex: 1, gap: 7 }}>
                  <SkeletonBlock width="45%" height={11} />
                  <SkeletonBlock width="25%" height={9} />
                </View>
              </View>
              <SkeletonBlock width="100%" height={11} style={{ marginTop: space.sm }} />
              <SkeletonBlock width="70%" height={11} style={{ marginTop: space.xs }} />
            </View>
          ) : myPosts.length === 0 ? (
            <View style={styles.empty}>
              <View style={styles.emptyIcon}>
                <Ionicons name="sparkles-outline" size={26} color={colors.brand.primary} />
              </View>
              <Text style={styles.emptyTitle}>ما زال ما نشرتي حتى شي حاجة</Text>
              <Text style={styles.emptyHint}>اضغط على «فيمَ تفكر» فالرئيسية وشارك أول منشور</Text>
            </View>
          ) : (
            myPosts.map(renderPost)
          ))}

        {tab === 'about' && (
          <View style={styles.listCard}>
            <Text style={styles.listTitle}>المعلومات</Text>
            <InfoRow icon="document-text-outline" label="النبذة" value={user.bio?.trim() || 'ما زال ما زدتي نبذة'} />
            <InfoRow icon="mail-outline" label="البريد" value={user.email ?? '—'} />
            <InfoRow icon="people-outline" label="الأصدقاء" value={`${user.friendsCount ?? friends?.length ?? 0} صديق`} />
            <InfoRow icon="calendar-outline" label="عضو منذ" value={String(memberSince)} />
            <InfoRow icon="globe-outline" label="الخصوصية" value="الملف مرئي للجميع" />
          </View>
        )}

        {tab === 'friends' && (
          <View style={styles.listCard}>
            <Text style={styles.listTitle}>الأصدقاء</Text>
            {friends === null ? (
              Array.from({ length: 3 }).map((_, i) => (
                <View key={i} style={styles.friendRow}>
                  <SkeletonBlock width={44} height={44} radius={22} />
                  <View style={{ flex: 1, gap: 6 }}>
                    <SkeletonBlock width="45%" height={11} />
                    <SkeletonBlock width="25%" height={9} />
                  </View>
                </View>
              ))
            ) : friends.length === 0 ? (
              <Text style={styles.listEmpty}>ما عندك حتى صديق دابا — سير لشاشة «الأصدقاء» وزيد شي واحد</Text>
            ) : (
              friends.map((friend) => (
                <View key={friend.id} style={styles.friendRow}>
                  <Avatar initial={friend.initial} color={friend.color} avatarUrl={friend.avatarUrl} size={44} />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.friendName}>{friend.name}</Text>
                    <Text style={styles.friendMeta}>{friend.friendsCount ?? 0} صديق</Text>
                  </View>
                  <Ionicons name="people" size={18} color={colors.text.tertiary} />
                </View>
              ))
            )}
          </View>
        )}

        {tab === 'photos' &&
          (photoPosts.length === 0 ? (
            <View style={styles.empty}>
              <View style={styles.emptyIcon}>
                <Ionicons name="images-outline" size={26} color={colors.brand.primary} />
              </View>
              <Text style={styles.emptyTitle}>ما كاينة حتى صورة</Text>
              <Text style={styles.emptyHint}>الصور اللي غادي تنشر كتتجمع هنا</Text>
            </View>
          ) : (
            <View style={styles.grid}>
              {photoPosts.map((post) => (
                <View key={post.id} style={styles.gridItem}>
                  <View style={[styles.gridInner, { backgroundColor: '#DCE3EC' }]}>
                    <Ionicons name="image-outline" size={22} color="#8A93A3" />
                  </View>
                </View>
              ))}
            </View>
          ))}

        <View style={{ height: space.xxl }} />
      </ScrollView>

      {/* ============ شاشة التعليقات ============ */}
      <CommentsSheet
        visible={!!openPost}
        post={openPost}
        currentUser={user}
        onClose={() => setOpenPostId(null)}
        onAddComment={addComment}
        onDeleteComment={deleteComment}
        onLikeComment={toggleCommentLike}
      />

      {/* ============ نافذة التعديل ============ */}
      <Modal visible={editing} animationType="fade" transparent onRequestClose={() => setEditing(false)}>
        <Pressable style={styles.backdrop} onPress={() => setEditing(false)} />
        <View style={styles.sheet}>
          <View style={styles.sheetHandle} />
          <Text style={styles.sheetTitle}>تعديل الملف</Text>

          <Pressable
            onPress={pickAvatar}
            accessibilityRole="button"
            accessibilityLabel="تغيير صورة الحساب"
            style={({ pressed }) => [styles.sheetPhotoRow, pressed && { opacity: 0.85 }]}
          >
            <Avatar
              initial={user.initial}
              color={user.color}
              avatarUrl={avatarFile?.uri ?? user.avatarUrl}
              size={56}
            />
            <View style={styles.sheetPhotoText}>
              <Text style={styles.sheetPhotoTitle}>صورة الحساب</Text>
              <Text style={styles.sheetPhotoSub}>اختر صورة من المعرض</Text>
            </View>
            <Ionicons name="chevron-back" size={18} color={colors.text.secondary} />
          </Pressable>

          <Text style={styles.inputLabel}>الاسم</Text>
          <TextInput
            value={nameDraft}
            onChangeText={setNameDraft}
            style={styles.input}
            placeholder="الاسم"
            placeholderTextColor={colors.text.secondary}
            textAlign="right"
            maxLength={40}
          />

          <Text style={styles.inputLabel}>النبذة</Text>
          <TextInput
            value={bioDraft}
            onChangeText={setBioDraft}
            style={[styles.input, styles.inputMultiline]}
            placeholder="اكتب شي حاجة على راسك"
            placeholderTextColor={colors.text.secondary}
            textAlign="right"
            multiline
            maxLength={160}
          />
          <Text style={styles.counter}>{bioDraft.length}/160</Text>

          <View style={styles.sheetActions}>
            <Pressable onPress={saveEditor} disabled={authLoading} style={[styles.sheetBtn, styles.sheetPrimary]}>
              {authLoading ? (
                <ActivityIndicator size="small" color={colors.text.inverse} />
              ) : (
                <Text style={styles.sheetPrimaryText}>حفظ</Text>
              )}
            </Pressable>
            <Pressable onPress={() => setEditing(false)} style={[styles.sheetBtn, styles.sheetSecondary]}>
              <Text style={styles.sheetSecondaryText}>إلغاء</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </View>
  );
};

const InfoRow: React.FC<{ icon: React.ComponentProps<typeof Ionicons>['name']; label: string; value: string }> = ({
  icon,
  label,
  value,
}) => (
  <View style={styles.infoRow}>
    <Ionicons name={icon} size={20} color={colors.text.secondary} />
    <View style={{ flex: 1 }}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue}>{value}</Text>
    </View>
  </View>
);

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg.canvas },

  /* غلاف */
  cover: { height: 150, backgroundColor: colors.brand.dark, overflow: 'hidden' },
  coverCircleLg: {
    position: 'absolute',
    width: 260,
    height: 260,
    borderRadius: 130,
    backgroundColor: 'rgba(255,255,255,0.08)',
    top: -120,
    right: -60,
  },
  coverCircleSm: {
    position: 'absolute',
    width: 140,
    height: 140,
    borderRadius: 70,
    backgroundColor: 'rgba(255,255,255,0.10)',
    bottom: -70,
    left: 40,
  },

  /* رأس الملف */
  headCard: { backgroundColor: colors.bg.surface, paddingBottom: 0, ...shadow.card },
  headRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space.sm, paddingHorizontal: space.md },
  avatarCol: { alignItems: 'center' },
  avatarRing: {
    padding: 4,
    borderRadius: 999,
    backgroundColor: colors.bg.surface,
    marginTop: -42,
    ...shadow.card,
  },
  headText: { flex: 1, paddingTop: space.xs + 2, minWidth: 0 },
  addPhoto: {
    ...type.caption,
    fontFamily: type.bodyStrong.fontFamily,
    color: colors.brand.primary,
    textAlign: 'center',
    marginTop: 6,
  },
  avatarEdit: {
    position: 'absolute',
    bottom: 2,
    left: '50%',
    marginLeft: -13,
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: colors.brand.primary,
    borderWidth: 2,
    borderColor: colors.bg.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sheetPhotoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingVertical: space.xs,
    marginBottom: space.xs,
  },
  sheetPhotoText: { flex: 1, minWidth: 0 },
  sheetPhotoTitle: { ...type.bodyStrong, color: colors.text.primary },
  sheetPhotoSub: { ...type.caption, color: colors.text.secondary, marginTop: 1 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  name: { ...type.headline, fontSize: 22, color: colors.text.primary, flexShrink: 1 },
  headMeta: { ...type.bodySm, color: colors.text.secondary, marginTop: 1 },
  headBio: { ...type.caption, color: colors.text.secondary, marginTop: 3, lineHeight: 15 },

  actionsRow: { flexDirection: 'row', gap: space.xs, paddingHorizontal: space.md, marginTop: space.sm },
  actionPrimary: {
    flex: 1,
    height: 38,
    borderRadius: radius.sm,
    backgroundColor: colors.brand.primary,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  actionPrimaryText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.inverse },
  actionSecondary: {
    flex: 1,
    height: 38,
    borderRadius: radius.sm,
    backgroundColor: colors.border.subtle,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  actionSecondaryText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  actionIcon: {
    width: 42,
    height: 38,
    borderRadius: radius.sm,
    backgroundColor: colors.border.subtle,
    alignItems: 'center',
    justifyContent: 'center',
  },

  /* تبويبات */
  tabsDivider: { height: size.hairline, backgroundColor: colors.border.divider, marginTop: space.sm },
  tabs: { paddingHorizontal: space.xs },
  tab: { paddingHorizontal: space.md, paddingVertical: space.sm, alignItems: 'center' },
  tabText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },
  tabTextActive: { color: colors.brand.primary },
  tabIndicator: {
    position: 'absolute',
    bottom: 0,
    left: space.sm,
    right: space.sm,
    height: 3,
    borderRadius: 3,
    backgroundColor: colors.brand.primary,
  },

  /* قوائم */
  listCard: {
    backgroundColor: colors.bg.surface,
    margin: space.xs,
    borderRadius: radius.md,
    padding: space.md,
    ...shadow.card,
  },
  listTitle: { ...type.title, color: colors.text.primary, marginBottom: space.xs },
  listEmpty: { ...type.bodySm, color: colors.text.secondary, paddingVertical: space.md },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space.sm,
    paddingVertical: space.sm,
    borderBottomWidth: size.hairline,
    borderBottomColor: colors.border.subtle,
  },
  infoLabel: { ...type.caption, color: colors.text.secondary },
  infoValue: { ...type.bodySm, color: colors.text.primary, marginTop: 1 },
  friendRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingVertical: space.xs,
    borderBottomWidth: size.hairline,
    borderBottomColor: colors.border.subtle,
  },
  friendName: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  friendMeta: { ...type.caption, color: colors.text.secondary, marginTop: 1 },

  /* شبكة الصور */
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 2, padding: 2 },
  gridItem: { width: '32.6%', aspectRatio: 1 },
  gridInner: { flex: 1, alignItems: 'center', justifyContent: 'center', borderRadius: 2 },

  /* هيكل وفارغ */
  skeletonCard: { backgroundColor: colors.bg.surface, margin: space.xs, borderRadius: radius.md, padding: space.md },
  skHead: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  empty: { alignItems: 'center', gap: space.xs, paddingVertical: space.xxxl, paddingHorizontal: space.xl },
  emptyIcon: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: colors.brand.soft,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: space.xxs,
  },
  emptyTitle: { ...type.body, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  emptyHint: { ...type.bodySm, color: colors.text.secondary, textAlign: 'center' },

  /* نافذة التعديل */
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.45)' },
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: colors.bg.surface,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: space.md,
    paddingBottom: space.xxl,
    gap: space.xxs,
  },
  sheetHandle: { width: 40, height: 4, borderRadius: 2, backgroundColor: colors.border.divider, alignSelf: 'center', marginBottom: space.sm },
  sheetTitle: { ...type.title, color: colors.text.primary, marginBottom: space.xs },
  inputLabel: { ...type.caption, color: colors.text.secondary, marginTop: space.xs },
  input: {
    ...type.body,
    color: colors.text.primary,
    borderWidth: 1,
    borderColor: colors.border.divider,
    borderRadius: radius.sm,
    paddingHorizontal: space.sm,
    paddingVertical: space.xs,
    backgroundColor: colors.bg.canvas,
    marginTop: 4,
  },
  inputMultiline: { minHeight: 76, textAlignVertical: 'top' },
  counter: { ...type.caption, color: colors.text.tertiary, alignSelf: 'flex-start', marginTop: 2 },
  sheetActions: { flexDirection: 'row', gap: space.xs, marginTop: space.md },
  sheetBtn: { flex: 1, height: 42, borderRadius: radius.sm, alignItems: 'center', justifyContent: 'center' },
  sheetPrimary: { backgroundColor: colors.brand.primary },
  sheetPrimaryText: { ...type.bodyStrong, color: colors.text.inverse },
  sheetSecondary: { backgroundColor: colors.border.subtle },
  sheetSecondaryText: { ...type.bodyStrong, color: colors.text.primary },
});

export default ProfileScreen;
