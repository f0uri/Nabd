import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
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

type ProfileTab = 'posts' | 'reels' | 'about' | 'friends' | 'photos';

const TABS: Array<{ key: ProfileTab; label: string }> = [
  { key: 'posts', label: 'الكل' },
  { key: 'reels', label: 'ريلز' },
  { key: 'photos', label: 'الصور' },
  { key: 'about', label: 'حول' },
  { key: 'friends', label: 'الأصدقاء' },
];

/**
 * الملف الشخصي — على نمط فيسبوك:
 * غلاف → صورة رمزية متراكبة → اسم ونبذة → أزرار الإجراءات → تبويبات → محتوى التبويب
 */
export const ProfileScreen: React.FC<{ onCompose?: () => void }> = ({ onCompose }) => {
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
  const photoPosts = useMemo(() => myPosts.filter((p) => p.media && p.media.kind !== 'video'), [myPosts]);

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

  const profileOptions = () => Alert.alert('خيارات الملف', undefined, [
    { text: 'تعديل الملف', onPress: openEditor },
    { text: 'الأصدقاء', onPress: () => setTab('friends') },
    { text: 'تسجيل الخروج', style: 'destructive', onPress: () => Alert.alert('تسجيل الخروج', 'هل تريد تسجيل الخروج من نبض؟', [
      { text: 'إلغاء', style: 'cancel' }, { text: 'خروج', style: 'destructive', onPress: () => { void logout(); } },
    ]) },
    { text: 'إلغاء', style: 'cancel' },
  ]);
  const editAvatar = () => { openEditor(); void pickAvatar(); };

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
          <View style={styles.coverFade} />
          <View style={styles.coverToolbar}>
            <Pressable onPress={() => useUiStore.getState().setTab('menu')} accessibilityRole="button" accessibilityLabel="القائمة" style={styles.coverButton}>
              <Ionicons name="menu-outline" size={28} color={colors.text.inverse} />
            </Pressable>
            <View style={styles.coverActions}>
              <Pressable onPress={openEditor} accessibilityRole="button" accessibilityLabel="تعديل الملف" style={styles.coverButton}>
                <Ionicons name="pencil-outline" size={25} color={colors.text.inverse} />
              </Pressable>
              <Pressable onPress={() => useUiStore.getState().setTab('search')} accessibilityRole="button" accessibilityLabel="البحث" style={styles.coverButton}>
                <Ionicons name="search-outline" size={27} color={colors.text.inverse} />
              </Pressable>
              <Pressable onPress={profileOptions} accessibilityRole="button" accessibilityLabel="خيارات الملف" style={styles.coverButton}>
                <Ionicons name="ellipsis-horizontal-circle-outline" size={28} color={colors.text.inverse} />
              </Pressable>
            </View>
          </View>
        </View>

        {/* ============ الرأس: صورة + اسم + نبذة ============ */}
        <View style={styles.headCard}>
          <View style={styles.headRow}>
            <View style={styles.avatarCol}>
              <Pressable
                onPress={editAvatar}
                accessibilityRole="button"
                accessibilityLabel="تغيير صورة الحساب"
                style={({ pressed }) => [styles.avatarRing, pressed && { opacity: 0.85 }]}
              >
                <Avatar initial={user.initial} color={user.color} avatarUrl={user.avatarUrl} size={154} />
                {/* Opens the real profile editor before choosing a picture. */}
                <View style={styles.avatarEdit}>
                  <Ionicons name="camera" size={23} color={colors.text.primary} />
                </View>
              </Pressable>
              {!user.avatarUrl && (
                <Pressable onPress={editAvatar} hitSlop={6} accessibilityRole="button">
                  <Text style={styles.addPhoto}>أضف صورة</Text>
                </Pressable>
              )}
            </View>

            <View style={styles.headText}>
              <View style={styles.nameRow}>
                <Text style={styles.name} numberOfLines={2}>
                  {user.name}
                </Text>
                {user.verified && <Ionicons name="checkmark-circle" size={17} color={colors.brand.primary} />}
              </View>
              <Text style={styles.headMeta} numberOfLines={1}>
                {user.friendsCount ?? friends?.length ?? 0} صديق · {user.postsCount ?? myPosts.length} منشور
              </Text>
              {!!user.bio?.trim?.() && (
                <Text style={styles.headBio}>
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

            <Pressable onPress={onCompose ?? openEditor} accessibilityRole="button" accessibilityLabel="إنشاء منشور"
              style={({ pressed }) => [styles.actionSecondary, pressed && { opacity: 0.85 }]}>
              <Ionicons name="add" size={23} color={colors.text.primary} />
              <Text style={styles.actionSecondaryText}>إنشاء</Text>
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
                  style={[styles.tab, active && styles.tabActive]}
                >
                  <Text style={[styles.tabText, active && styles.tabTextActive]}>{item.label}</Text>
                </Pressable>
              );
            })}
          </ScrollView>
        </View>

        <View style={styles.profileTools}>
          <Pressable onPress={async () => {
            const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
            if (!permission.granted) { showToast('خاصنا الإذن للوصول للصور', 'error'); return; }
            const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.85 });
            if (result.canceled || !result.assets?.length) return;
            await addStory({ imageUri: result.assets[0].uri });
            showToast('تمت إضافة الستوري', 'success');
          }} style={styles.profileTool} accessibilityRole="button" accessibilityLabel="إضافة ستوري">
            <Ionicons name="add-circle-outline" size={21} color={colors.brand.primary} />
            <Text style={styles.profileToolText}>إضافة ستوري</Text>
          </Pressable>
          <Pressable onPress={() => setTab('about')} style={styles.profileTool} accessibilityRole="button" accessibilityLabel="معلوماتي">
            <Ionicons name="information-circle-outline" size={21} color={colors.text.secondary} />
            <Text style={styles.profileToolText}>معلوماتي</Text>
          </Pressable>
        </View>
        {tab === 'reels' && (myPosts.some((p) => p.media?.kind === 'video') ?
          myPosts.filter((p) => p.media?.kind === 'video').map(renderPost) :
          <View style={styles.empty}>
            <Ionicons name="play-circle-outline" size={42} color={colors.brand.primary} />
            <Text style={styles.emptyTitle}>الريلز التي تنشرها ستظهر هنا</Text>
          </View>)}

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
            <InfoRow icon="document-text-outline" label="المنشورات" value={String(user.postsCount ?? myPosts.length)} />
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
                <Pressable key={post.id} style={styles.gridItem} onPress={() => setOpenPostId(post.id)} accessibilityRole="button" accessibilityLabel={post.media?.alt ?? 'فتح تعليقات الصورة'}>
                  <Image source={{ uri: post.media!.uri }} style={styles.gridInner} resizeMode="cover" />
                </Pressable>
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
  flex: { flex: 1, backgroundColor: colors.bg.surface },

  /* Neutral cover: cover uploads are not supported by the backend yet. */
  cover: { height: 244, backgroundColor: '#A4A6A9', overflow: 'hidden' },
  coverFade: { position: 'absolute', start: 0, end: 0, bottom: 0, height: 104, backgroundColor: 'rgba(255,255,255,0.18)' },
  coverToolbar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.xs, paddingTop: space.xs },
  coverActions: { flexDirection: 'row', gap: 2 },
  coverButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  profileTools: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: space.md, paddingVertical: space.md, backgroundColor: colors.bg.surface },
  profileTool: { flexDirection: 'row', alignItems: 'center', gap: 6, minHeight: 44, paddingHorizontal: 6 },
  profileToolText: { ...type.bodySm, color: colors.text.secondary },

  /* رأس الملف */
  headCard: { backgroundColor: colors.bg.surface, paddingBottom: 0, marginTop: -18, borderTopStartRadius: 26, borderTopEndRadius: 26 },
  headRow: { alignItems: 'center', paddingHorizontal: space.md },
  avatarCol: { alignItems: 'center' },
  avatarRing: {
    padding: 4,
    borderRadius: 999,
    backgroundColor: colors.bg.surface,
    marginTop: -92,
  },
  headText: { width: '100%', paddingTop: space.sm, minWidth: 0, alignItems: 'center' },
  addPhoto: {
    ...type.caption,
    fontFamily: type.bodyStrong.fontFamily,
    color: colors.brand.primary,
    textAlign: 'center',
    marginTop: 6,
  },
  avatarEdit: {
    position: 'absolute', bottom: 8, end: 4, width: 38, height: 38, borderRadius: 19,
    backgroundColor: colors.border.subtle, borderWidth: 3, borderColor: colors.bg.surface,
    alignItems: 'center', justifyContent: 'center',
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
  nameRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  name: { ...type.display, fontSize: 26, fontWeight: '700', color: colors.text.primary, flexShrink: 1, textAlign: 'center' },
  headMeta: { ...type.body, fontWeight: '600', color: colors.text.primary, marginTop: 6, textAlign: 'center' },
  headBio: { ...type.body, fontSize: 16, color: colors.text.primary, marginTop: space.md, marginBottom: space.sm, lineHeight: 26, textAlign: 'center' },

  actionsRow: { flexDirection: 'row', gap: space.xs, paddingHorizontal: space.md, marginTop: space.lg, marginBottom: space.sm },
  actionPrimary: {
    flex: 1,
    height: 44,
    borderRadius: radius.sm,
    backgroundColor: colors.brand.primary,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  actionPrimaryText: { ...type.body, fontFamily: type.bodyStrong.fontFamily, color: colors.text.inverse },
  actionSecondary: {
    flex: 1,
    height: 44,
    borderRadius: radius.sm,
    backgroundColor: colors.border.subtle,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  actionSecondaryText: { ...type.body, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  /* تبويبات */
  tabsDivider: { height: size.hairline, backgroundColor: colors.border.subtle, marginTop: space.sm },
  tabs: { paddingHorizontal: space.sm, paddingVertical: space.sm, gap: 6 },
  tab: { paddingHorizontal: space.md, paddingVertical: 9, minHeight: 44, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center' },
  tabActive: { backgroundColor: colors.brand.soft },
  tabText: { ...type.body, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },
  tabTextActive: { color: colors.brand.primary },
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
