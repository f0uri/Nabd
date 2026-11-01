import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { FlashList } from '@shopify/flash-list';
import { Ionicons } from '@expo/vector-icons';
import AppBar from '../components/AppBar';
import StoryTray from '../components/organisms/StoryTray';
import Composer from '../components/organisms/Composer';
import PostCard from '../components/organisms/PostCard';
import FeedSkeleton from '../components/organisms/FeedSkeleton';
import SkeletonBlock from '../components/atoms/Skeleton';
import * as ImagePicker from 'expo-image-picker';
import CommentsSheet from '../components/organisms/CommentsSheet';
import StoryViewer from '../components/organisms/StoryViewer';
import { useStoryStore } from '../store/useStoryStore';
import { useFeedStore } from '../store/useFeedStore';
import { useChatStore } from '../store/useChatStore';
import { useAuthStore } from '../store/useAuthStore';
import { CURRENT_USER } from '../api/mockApi';
import { colors, radius, space, type } from '../theme';
import type { Post } from '../types';
import { useUiStore } from '../store/useUiStore';

interface FeedScreenProps {
  onCompose?: () => void;
}

export const FeedScreen: React.FC<FeedScreenProps> = ({ onCompose }) => {
  const posts = useFeedStore((s) => s.posts);
  const loading = useFeedStore((s) => s.loading);
  const refreshing = useFeedStore((s) => s.refreshing);
  const loadingMore = useFeedStore((s) => s.loadingMore);
  const error = useFeedStore((s) => s.error);
  const offline = useFeedStore((s) => s.offline);
  const load = useFeedStore((s) => s.load);
  const refresh = useFeedStore((s) => s.refresh);
  const loadMore = useFeedStore((s) => s.loadMore);
  const toggleReaction = useFeedStore((s) => s.toggleReaction);
  const addComment = useFeedStore((s) => s.addComment);
  const deleteComment = useFeedStore((s) => s.deleteComment);
  const toggleCommentLike = useFeedStore((s) => s.toggleCommentLike);
  const authUser = useAuthStore((s) => s.user);
  const currentUser = authUser ?? CURRENT_USER;
  const [openPostId, setOpenPostId] = useState<string | null>(null);
  const [morePostId, setMorePostId] = useState<string | null>(null);
  const deletePost = useFeedStore((s) => s.deletePost);
  const hidePost = useFeedStore((s) => s.hidePost);
  const morePost = posts.find((p) => p.id === morePostId) ?? null;
  const [sharePostId, setSharePostId] = useState<string | null>(null);
  const sharePost = posts.find((p) => p.id === sharePostId) ?? null;
  const setDraft = useChatStore((s) => s.setDraft);
  const openChat = useChatStore((s) => s.openChat);
  const stories = useStoryStore((s) => s.stories);
  const hydrateStories = useStoryStore((s) => s.hydrate);
  const addStory = useStoryStore((s) => s.addStory);
  const markSeen = useStoryStore((s) => s.markSeen);
  const [storyOpen, setStoryOpen] = useState(false);
  const [storyIndex, setStoryIndex] = useState(0);
  const openPost = posts.find((p) => p.id === openPostId) ?? null;
  const showToast = useFeedStore((s) => s.showToast);
  const reconnect = useFeedStore((s) => s.reconnect);

  useEffect(() => {
    load();
    hydrateStories();
  }, [load, hydrateStories]);

  /** إنشاء ستوري: صورة من المعرض أو نص سريع */
  const createStory = useCallback(async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      showToast('خاصنا الإذن للوصول للصور', 'error');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.85 });
    if (result.canceled || !result.assets?.length) {
      // المستخدم لغى — كنقترحو ستوري نصي
      await addStory({ text: 'ستوري جديد من نبض' });
      showToast('تزاد ستوري نصي', 'success');
      return;
    }
    await addStory({ imageUri: result.assets[0].uri });
    showToast('تزوّدت الستوري ديالك', 'success');
  }, [addStory, showToast]);

  const renderItem = useCallback(
    ({ item }: { item: Post }) => (
      <PostCard
        post={item}
        onReact={toggleReaction}
        onPickReaction={toggleReaction}
        onOpenComments={setOpenPostId}
        onMore={() => setMorePostId(item.id)}
        onShare={() => setSharePostId(item.id)}
        onOpenReel={() => useUiStore.getState().setTab('reels')}
      />
    ),
    [toggleReaction],
  );

  const header = (
    <>
      <StoryTray
        onOpenStory={(index) => {
          setStoryIndex(index);
          setStoryOpen(true);
        }}
        onCreate={createStory}
      />
      <Composer onPress={() => (onCompose ? onCompose() : showToast('محرر المنشورات غير متاح', 'info'))} />
      {offline && (
        <View style={styles.offlineBanner}>
          <Ionicons name="cloud-offline-outline" size={15} color="#92400E" />
          <Text style={styles.offlineText}>أنت غير متصل — التغييرات لن تُحفظ</Text>
        </View>
      )}
      {!!error && (
        <View style={styles.errorBox}>
          <Text style={styles.errorText}>{error}</Text>
          <Pressable onPress={reconnect} style={styles.retry} accessibilityRole="button">
            <Text style={styles.retryText}>إعادة المحاولة</Text>
          </Pressable>
        </View>
      )}
    </>
  );

  /** حذف منشورك — بتأكيد */
  const confirmDeletePost = (postId: string) => {
    setMorePostId(null);
    Alert.alert('حذف المنشور', 'هاد المنشور غادي يتحيد بلا رجعة. متأكد؟', [
      { text: 'إلغاء', style: 'cancel' },
      { text: 'حذف', style: 'destructive', onPress: () => deletePost(postId) },
    ]);
  };

  /** مشاركة المنشور: كنحطّو النص كمسودة فـ Messenger */
  const shareSheet = sharePost ? (
    <View style={styles.sheetLayer}>
      <Pressable style={styles.sheetBackdrop} onPress={() => setSharePostId(null)} accessibilityLabel="إغلاق" />
      <View style={styles.sheet}>
        <View style={styles.sheetGrabber} />
        <Text style={styles.sheetTitle}>مشاركة المنشور</Text>

        <Pressable
          onPress={() => {
            const snippet = sharePost.text?.trim() || (sharePost.media ? '(منشور بصورة)' : '');
            setDraft(`شارك منشور ${sharePost.author.name}:\n${snippet.slice(0, 600)}`);
            setSharePostId(null);
            openChat(null);
            showToast('ختار شي صديق من اللائحة باش تصيفط ليه المنشور', 'info');
          }}
          accessibilityRole="button"
          style={({ pressed }) => [styles.sheetRow, pressed && { backgroundColor: colors.bg.canvas }]}
        >
          <Ionicons name="paper-plane-outline" size={19} color={colors.brand.primary} />
          <Text style={styles.sheetRowText}>إرسال فـ رسالة</Text>
        </Pressable>

        <Pressable
          onPress={() => {
            hidePost(sharePost.id);
            setSharePostId(null);
          }}
          accessibilityRole="button"
          style={({ pressed }) => [styles.sheetRow, pressed && { backgroundColor: colors.bg.canvas }]}
        >
          <Ionicons name="eye-off-outline" size={19} color={colors.brand.primary} />
          <Text style={styles.sheetRowText}>إخفاء من الفيد</Text>
        </Pressable>

        <Pressable onPress={() => setSharePostId(null)} style={styles.sheetCancel} accessibilityRole="button">
          <Text style={styles.sheetCancelText}>إلغاء</Text>
        </Pressable>
      </View>
    </View>
  ) : null;

  const moreSheet = morePost ? (
    <View style={styles.sheetLayer}>
      <Pressable style={styles.sheetBackdrop} onPress={() => setMorePostId(null)} accessibilityLabel="إغلاق" />
      <View style={styles.sheet}>
        <View style={styles.sheetGrabber} />
        <Pressable
          onPress={() => {
            hidePost(morePost.id);
            setMorePostId(null);
          }}
          accessibilityRole="button"
          style={({ pressed }) => [styles.sheetRow, pressed && { backgroundColor: colors.bg.canvas }]}
        >
          <Ionicons name="eye-off-outline" size={19} color={colors.brand.primary} />
          <Text style={styles.sheetRowText}>إخفاء المنشور من الفيد</Text>
        </Pressable>

        {morePost.author.isMe && (
          <Pressable
            onPress={() => confirmDeletePost(morePost.id)}
            accessibilityRole="button"
            style={({ pressed }) => [styles.sheetRow, pressed && { backgroundColor: colors.bg.canvas }]}
          >
            <Ionicons name="trash-outline" size={19} color={colors.semantic.danger} />
            <Text style={[styles.sheetRowText, { color: colors.semantic.danger }]}>حذف المنشور</Text>
          </Pressable>
        )}

        <Pressable onPress={() => setMorePostId(null)} style={styles.sheetCancel} accessibilityRole="button">
          <Text style={styles.sheetCancelText}>إلغاء</Text>
        </Pressable>
      </View>
    </View>
  ) : null;

  const commentsSheet = (
    <CommentsSheet
      visible={!!openPost}
      post={openPost}
      currentUser={currentUser}
      onClose={() => setOpenPostId(null)}
      onAddComment={addComment}
      onDeleteComment={deleteComment}
      onLikeComment={toggleCommentLike}
    />
  );

  if (loading) {
    return (
      <View style={styles.flex}>
        <AppBar />
        <ScrollView showsVerticalScrollIndicator={false}>
          {header}
          <FeedSkeleton />
        </ScrollView>
        {commentsSheet}
      </View>
    );
  }

  return (
    <View style={styles.flex}>
      <AppBar />
      <FlashList
        data={posts}
        renderItem={renderItem}
        keyExtractor={(item: Post) => item.id}
        estimatedItemSize={520}
        ListHeaderComponent={header}
        ListEmptyComponent={
          <View style={styles.emptyBox}>
            <View style={styles.emptyIcon}>
              <Ionicons name="newspaper-outline" size={26} color={colors.brand.primary} />
            </View>
            <Text style={styles.emptyTitle}>ما كاين حتى منشور</Text>
            <Text style={styles.emptySub}>
              التغذية كتتكوّن من منشورات الحسابات الحقيقية. بدا أنت بنشر أول حاجة، ولا قلّب على صحابك
              وزيدهم باش تشوف واش نشرو.
            </Text>
            {onCompose && (
              <Pressable onPress={onCompose} accessibilityRole="button" style={styles.emptyBtn}>
                <Text style={styles.emptyBtnText}>أنشئ منشور</Text>
              </Pressable>
            )}
          </View>
        }
        ListFooterComponent={
          loadingMore ? (
            <View style={styles.loadingMore}>
              <SkeletonBlock width="100%" height={140} radius={radius.sm} />
            </View>
          ) : (
            <View style={styles.footer} />
          )
        }
        onEndReached={loadMore}
        onEndReachedThreshold={0.6}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={colors.brand.primary} />}
        showsVerticalScrollIndicator={false}
      />
      {commentsSheet}

      <StoryViewer
        visible={storyOpen}
        stories={stories}
        startIndex={storyIndex}
        currentUser={currentUser}
        onClose={() => setStoryOpen(false)}
        onSeen={markSeen}
        onReply={(story, text) => showToast(`تبعث الرد لـ${story.author.name.split(' ')[0]}: «${text.slice(0, 24)}»`, 'success')}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  sheetLayer: { ...StyleSheet.absoluteFillObject, zIndex: 30, elevation: 30 },
  sheetBackdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: colors.bg.overlay },
  sheet: {
    position: 'absolute',
    start: 0,
    end: 0,
    bottom: 0,
    backgroundColor: colors.bg.surface,
    borderTopStartRadius: radius.lg + 4,
    borderTopEndRadius: radius.lg + 4,
    paddingTop: space.xs,
    paddingHorizontal: space.md,
    paddingBottom: space.lg,
  },
  sheetGrabber: {
    width: 36,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border.divider,
    alignSelf: 'center',
    marginBottom: space.sm,
  },
  sheetRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.sm,
    paddingVertical: space.sm,
    paddingHorizontal: space.xs,
    borderRadius: radius.md,
  },
  sheetRowText: { ...type.body, color: colors.text.primary },
  sheetTitle: { ...type.bodyStrong, color: colors.text.primary, marginBottom: space.xs },
  sheetCancel: {
    marginTop: space.xs,
    paddingVertical: space.sm,
    borderRadius: radius.md,
    backgroundColor: colors.bg.canvas,
    alignItems: 'center',
  },
  sheetCancelText: { ...type.bodyStrong, color: colors.text.primary },
  flex: { flex: 1, backgroundColor: colors.bg.canvas },
  footer: { paddingBottom: space.xl },
  emptyBox: { alignItems: 'center', paddingHorizontal: space.lg, paddingTop: space.lg, gap: 6 },
  emptyIcon: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.brand.soft,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: space.xs,
  },
  emptyTitle: { ...type.bodyStrong, fontSize: 16, color: colors.text.primary },
  emptySub: { ...type.caption, color: colors.text.secondary, textAlign: 'center', lineHeight: 19 },
  emptyBtn: {
    marginTop: space.sm,
    backgroundColor: colors.brand.primary,
    borderRadius: radius.sm,
    paddingHorizontal: space.lg,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyBtnText: { ...type.bodyStrong, color: colors.text.inverse },
  loadingMore: { paddingHorizontal: space.md, paddingVertical: space.sm },
  offlineBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    backgroundColor: '#FEF3C7',
    paddingVertical: 6,
    marginBottom: space.xs,
  },
  offlineText: { ...type.micro, color: '#92400E', fontFamily: type.bodyStrong.fontFamily },
  errorBox: {
    backgroundColor: '#FDECEF',
    marginHorizontal: space.md,
    marginBottom: space.xs,
    borderRadius: radius.sm,
    padding: space.sm,
    gap: 6,
  },
  errorText: { ...type.bodySm, color: '#8C1C2B' },
  retry: { alignSelf: 'flex-start' },
  retryText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.brand.primary },
  devPanel: {
    margin: space.md,
    padding: space.sm,
    backgroundColor: colors.bg.surface,
    borderRadius: radius.md,
    gap: space.xs,
    borderWidth: 1,
    borderColor: colors.border.subtle,
  },
  devRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  devTitle: { ...type.micro, fontFamily: type.bodyStrong.fontFamily, color: colors.text.tertiary },
  chip: { paddingHorizontal: space.xs, paddingVertical: 3, borderRadius: 999 },
  chipText: { ...type.caption, fontFamily: type.bodyStrong.fontFamily },
  devHint: { ...type.caption, color: colors.text.secondary, lineHeight: 16 },
  devBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 6 },
  devBtnText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.brand.primary },
});

export default FeedScreen;
