import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Dimensions,
  Linking,
  FlatList,
  Image,
  Pressable,
  StyleSheet,
  StatusBar,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import { useVideoPlayer, VideoView } from 'expo-video';
import * as ImagePicker from 'expo-image-picker';
import Avatar from '../components/atoms/Avatar';
import CommentsSheet from '../components/organisms/CommentsSheet';
import { useReelsStore } from '../store/useReelsStore';
import { useAuthStore } from '../store/useAuthStore';
import { useFeedStore } from '../store/useFeedStore';
import { CURRENT_USER } from '../api/mockApi';
import { colors, radius, size, space, type } from '../theme';
import type { Post } from '../types';

/** الحد الأقصى للفيديو (باش يبقى يقدر يتDownload من تلغرام: 20MB) */
const MAX_BYTES = 18 * 1024 * 1024;

const formatCount = (value: number): string => {
  if (!value) return '0';
  if (value < 1000) return String(value);
  if (value < 1_000_000) return `${(value / 1000).toFixed(value < 10_000 ? 1 : 0)} ألف`;
  return `${(value / 1_000_000).toFixed(1)} مليون`;
};

/** عنصر ريل وحد — مشغل فيديو كيخدم غير إلا كان هو النشيط */
const ReelItem: React.FC<{
  reel: Post;
  height: number;
  isActive: boolean;
  muted: boolean;
  onDelete: (reel: Post) => void;
}> = ({ reel, height, isActive, muted, onDelete }) => {
  const [paused, setPaused] = useState(false);
  const [progress, setProgress] = useState(0);
  const uri = reel.media?.uri ?? '';

  const player = useVideoPlayer(uri, (instance) => {
    instance.loop = true;
    instance.muted = muted;
    instance.timeUpdateEventInterval = 0.25;
  });

  // تشغيل/إيقاف حسب العنصر النشيط
  useEffect(() => {
    if (!player) return;
    if (isActive && !paused) {
      player.play();
    } else {
      player.pause();
      if (!isActive) setPaused(false);
    }
  }, [isActive, paused, player]);

  useEffect(() => {
    if (player) player.muted = muted;
  }, [muted, player]);

  // شريط التقدم
  useEffect(() => {
    if (!isActive) return;
    const timer = setInterval(() => {
      const duration = player?.duration || 0;
      if (duration > 0) setProgress(Math.min(1, (player.currentTime || 0) / duration));
    }, 250);
    return () => clearInterval(timer);
  }, [isActive, player]);

  const toggleReaction = useReelsStore((s) => s.toggleReaction);
  const setActiveComment = useReelsStore((s) => s.setActiveComment);
  const likedCount = (reel.reactions?.like ?? 0) + (reel.reactions?.love ?? 0);

  return (
    <View style={[styles.reelWrap, { height }]}>
      <Pressable
        onPress={() => setPaused((value) => !value)}
        accessibilityRole="button"
        accessibilityLabel={paused ? 'تشغيل الفيديو' : 'إيقاف الفيديو'}
        style={StyleSheet.absoluteFill}
      >
        <VideoView player={player} style={StyleSheet.absoluteFill} contentFit="cover" nativeControls={false} />
      </Pressable>

      {/* شريط التقدم */}
      <View style={styles.progressTrack}>
        <View style={[styles.progressFill, { width: `${Math.max(2, progress * 100)}%` }]} />
      </View>

      {paused && (
        <View style={styles.pauseBadge} pointerEvents="none">
          <Ionicons name="play" size={34} color="rgba(255,255,255,0.9)" />
        </View>
      )}

      {/* عمود الإجراءات */}
      <View style={styles.actions}>
        <Pressable
          onPress={() => toggleReaction(reel.id, 'love')}
          accessibilityRole="button"
          accessibilityLabel="أحببته"
          style={({ pressed }) => [styles.actionBtn, pressed && { transform: [{ scale: 0.9 }] }]}
        >
          <MaterialCommunityIcons
            name={reel.myReaction ? 'heart' : 'heart-outline'}
            size={28}
            color={reel.myReaction ? colors.reaction.love : '#FFFFFF'}
          />
          <Text style={styles.actionCount}>{formatCount(likedCount)}</Text>
        </Pressable>

        <Pressable
          onPress={() => setActiveComment(reel.id)}
          accessibilityRole="button"
          accessibilityLabel="التعليقات"
          style={({ pressed }) => [styles.actionBtn, pressed && { transform: [{ scale: 0.9 }] }]}
        >
          <Ionicons name="chatbubble-ellipses-outline" size={26} color="#FFFFFF" />
          <Text style={styles.actionCount}>{formatCount(reel.comments?.length ?? 0)}</Text>
        </Pressable>

        <Pressable
          onPress={() => toggleReaction(reel.id, 'like')}
          accessibilityRole="button"
          accessibilityLabel="أعجبني"
          style={({ pressed }) => [styles.actionBtn, pressed && { transform: [{ scale: 0.9 }] }]}
        >
          <Ionicons name={reel.myReaction === 'like' ? 'thumbs-up' : 'thumbs-up-outline'} size={24} color="#FFFFFF" />
          <Text style={styles.actionCount}>إعجاب</Text>
        </Pressable>

        <View style={styles.actionBtn}>
          <Ionicons name="eye-outline" size={22} color="rgba(255,255,255,0.9)" />
          <Text style={styles.actionCount}>{formatCount(reel.views ?? 0)}</Text>
        </View>

        {reel.author.isMe && (
          <Pressable
            onPress={() => onDelete(reel)}
            accessibilityRole="button"
            accessibilityLabel="حذف الريل"
            style={({ pressed }) => [styles.actionBtn, pressed && { transform: [{ scale: 0.9 }] }]}
          >
            <Ionicons name="trash-outline" size={22} color="#FFFFFF" />
          </Pressable>
        )}
      </View>

      {/* معلومات الريل */}
      <View style={styles.info} pointerEvents="box-none">
        <View style={styles.authorRow}>
          <Avatar initial={reel.author.name.charAt(0)} avatarUrl={reel.author.avatarUrl} size={34} ringColor="#FFFFFF" />
          <Text style={styles.authorName} numberOfLines={1}>
            {reel.author.name}
          </Text>
        </View>
        {reel.source === 'instagram' && (
          <Pressable
            onPress={() => reel.sourceUrl && Linking.openURL(reel.sourceUrl)}
            accessibilityRole="link"
            accessibilityLabel="فتح الريل الأصلي فـ إنستغرام"
            style={styles.sourceRow}
          >
            <Ionicons name="logo-instagram" size={13} color="#FFFFFF" />
            <Text style={styles.sourceText} numberOfLines={1}>
              من إنستغرام{reel.sourceAuthor ? ` · @${reel.sourceAuthor}` : ''}
            </Text>
            <Ionicons name="open-outline" size={11} color="rgba(255,255,255,0.75)" />
          </Pressable>
        )}
        {!!reel.text && (
          <Text style={styles.caption} numberOfLines={2}>
            {reel.text}
          </Text>
        )}
      </View>
    </View>
  );
};

/**
 * شاشة الريلز — فيديوهات عمودية بحال إنستغرام:
 * سكرول كامل الشاشة · تشغيل تلقائي للريل النشيط فقط · صوت مسدود افتراضياً · تفاعل وتعليقات.
 */
export const ReelsScreen: React.FC = () => {
  const reels = useReelsStore((s) => s.reels);
  const loading = useReelsStore((s) => s.loading);
  const refreshing = useReelsStore((s) => s.refreshing);
  const error = useReelsStore((s) => s.error);
  const activeIndex = useReelsStore((s) => s.activeIndex);
  const muted = useReelsStore((s) => s.muted);
  const load = useReelsStore((s) => s.load);
  const refresh = useReelsStore((s) => s.refresh);
  const setActive = useReelsStore((s) => s.setActive);
  const toggleMuted = useReelsStore((s) => s.toggleMuted);
  const publishReel = useReelsStore((s) => s.publishReel);
  const publishing = useReelsStore((s) => s.publishing);
  const uploadProgress = useReelsStore((s) => s.uploadProgress);
  const activeCommentId = useReelsStore((s) => s.activeCommentId);
  const setActiveComment = useReelsStore((s) => s.setActiveComment);
  const addReelComment = useReelsStore((s) => s.addComment);
  const deleteReel = useReelsStore((s) => s.deleteReel);
  const toggleCommentLike = useFeedStore((s) => s.toggleCommentLike);
  const currentUser = useAuthStore((s) => s.user) ?? CURRENT_USER;
  const showToast = useFeedStore((s) => s.showToast);

  const [height, setHeight] = useState(Dimensions.get('window').height - size.tabBar - 24);
  const [caption, setCaption] = useState('');
  const loadIngestStatus = useReelsStore((s) => s.loadIngestStatus);
  const ingest = useReelsStore((s) => s.ingest);
  const [pending, setPending] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const listRef = useRef<FlatList<Post>>(null);

  // تحميل الريلز + متابعة الجلب التلقائي (كل دقيقة) بلا أي تدخل من المستخدم
  useEffect(() => {
    load({ force: true });
    loadIngestStatus();
    const timer = setInterval(loadIngestStatus, 60_000);
    return () => clearInterval(timer);
  }, [load, loadIngestStatus]);

  /** اختيار فيديو من المعرض */
  const pickVideo = useCallback(async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      showToast('خاصنا الإذن للوصول للفيديوهات', 'error');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['videos'],
      quality: 0.7,
      videoMaxDuration: 60,
    });
    if (result.canceled || !result.assets?.length) return;

    const asset = result.assets[0];
    if (asset.fileSize && asset.fileSize > MAX_BYTES) {
      showToast('الفيديو كبير بزاف (الحد 18MB) — اختار واحد أقصر ولا أخف', 'error');
      return;
    }
    if (asset.duration && asset.duration > 61000) {
      showToast('الحد الأقصى للريل = 60 ثانية', 'error');
      return;
    }
    setPending(asset);
  }, [showToast]);

  const submitReel = async () => {
    if (!pending) return;
    const ok = await publishReel({
      uri: pending.uri,
      fileName: pending.fileName,
      mimeType: pending.mimeType,
      width: pending.width,
      height: pending.height,
      text: caption,
    });
    if (ok) {
      setPending(null);
      setCaption('');
      listRef.current?.scrollToOffset({ offset: 0, animated: true });
      showToast('تنشر الريل ديالك', 'success');
    } else {
      showToast(uploadProgress ?? 'ما قدرناش نرفعو الفيديو', 'error');
    }
  };

  const renderItem = useCallback(
    ({ item, index }: { item: Post; index: number }) => (
      <ReelItem
        reel={item}
        height={height}
        isActive={index === activeIndex}
        muted={muted}
        onDelete={handleDelete}
      />
    ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [height, activeIndex, muted],
  );

  const handleDelete = useCallback(
    async (reel: Post) => {
      Alert.alert('حذف الريل', 'هاد الريل غادي يتحيد بلا رجعة. متأكد؟', [
        { text: 'إلغاء', style: 'cancel' },
        {
          text: 'حذف',
          style: 'destructive',
          onPress: async () => {
            const ok = await deleteReel(reel.id);
            showToast(ok ? 'تحيد الريل' : 'ما قدرناش نحيدو الريل', ok ? 'success' : 'error');
          },
        },
      ]);
    },
    [deleteReel, showToast],
  );

  const onScroll = (offsetY: number) => {
    const index = Math.round(offsetY / Math.max(1, height));
    if (index !== activeIndex && index >= 0 && index < reels.length) setActive(index);
  };

  const commentPost = useMemo(
    () => reels.find((reel) => reel.id === activeCommentId) ?? null,
    [reels, activeCommentId],
  );

  return (
    <View style={styles.flex} onLayout={(event) => setHeight(event.nativeEvent.layout.height)}>
      {loading && reels.length === 0 ? (
        <View style={styles.center}>
          <ActivityIndicator color={colors.brand.primary} />
          <Text style={styles.centerText}>كنحمّلو الريلز…</Text>
        </View>
      ) : reels.length === 0 ? (
        <View style={styles.center}>
          <View style={styles.emptyIcon}>
            <Ionicons name="film-outline" size={30} color={colors.brand.primary} />
          </View>
          <Text style={styles.emptyTitle}>ما كاين حتى ريل دابا</Text>
          <Text style={styles.emptySub}>
            {error ? error : 'الريلز كتتجاب أوتوماتيكياً من إنستغرام — أول فيديو غادي يبان هنا بلا ما تدير والو.'}
          </Text>
          <View style={styles.emptyActions}>
            <Pressable onPress={pickVideo} style={styles.emptyBtn} accessibilityRole="button">
              <Ionicons name="add" size={18} color={colors.text.inverse} />
              <Text style={styles.emptyBtnText}>ارفع ريل</Text>
            </Pressable>
            {ingest?.enabled && (
              <View style={[styles.emptyBtn, styles.emptyBtnGhost]}>
                <ActivityIndicator size="small" color="#FFFFFF" />
                <Text style={styles.emptyBtnText}>كنجيبو…</Text>
              </View>
            )}
          </View>
        </View>
      ) : (
        <FlatList
          ref={listRef}
          data={reels}
          keyExtractor={(item) => item.id}
          renderItem={renderItem}
          pagingEnabled
          showsVerticalScrollIndicator={false}
          decelerationRate="fast"
          snapToInterval={height}
          snapToAlignment="start"
          disableIntervalMomentum
          getItemLayout={(_, index) => ({ length: height, offset: height * index, index })}
          onScroll={(event) => onScroll(event.nativeEvent.contentOffset.y)}
          scrollEventThrottle={32}
          onRefresh={refresh}
          refreshing={refreshing}
          windowSize={3}
          initialNumToRender={1}
          maxToRenderPerBatch={2}
        />
      )}

      {/* الرأس */}
      {/* شاشة سوداء → أيقونات الحالة لازم تكون بيضاء */}
      <StatusBar barStyle="light-content" backgroundColor="#000000" />

      <View style={styles.header} pointerEvents="box-none">
        <View>
          <Text style={styles.headerTitle}>ريلز</Text>
          {ingest?.enabled ? (
            <View style={styles.autoRow}>
              <View style={[styles.autoDot, ingest.running && styles.autoDotBusy]} />
              <Text style={styles.autoText}>
                {ingest.running ? 'كنجيبو ريلز جداد…' : 'جلب تلقائي من إنستغرام'}
              </Text>
            </View>
          ) : null}
        </View>
        <View style={styles.headerActions}>
          <Pressable
            onPress={toggleMuted}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel={muted ? 'تشغيل الصوت' : 'كتم الصوت'}
            style={styles.headerBtn}
          >
            <Ionicons name={muted ? 'volume-mute' : 'volume-high'} size={20} color="#FFFFFF" />
          </Pressable>
          <Pressable
            onPress={pickVideo}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel="ارفع ريل جديد"
            style={styles.headerBtn}
          >
            {publishing ? (
              <ActivityIndicator size="small" color="#FFFFFF" />
            ) : (
              <Ionicons name="add-circle-outline" size={22} color="#FFFFFF" />
            )}
          </Pressable>
        </View>
      </View>

      {/* شاشة وصف الريل قبل النشر */}
      {pending && (
        <View style={styles.uploadLayer}>
          <View style={styles.uploadCard}>
            <Text style={styles.uploadTitle}>ريل جديد</Text>
            <Text style={styles.uploadHint}>
              {pending.duration ? `${Math.round(pending.duration / 1000)} ثانية · ` : ''}
              {pending.fileSize ? `${(pending.fileSize / (1024 * 1024)).toFixed(1)}MB` : ''}
            </Text>

            <TextInput
              value={caption}
              onChangeText={setCaption}
              placeholder="كتب وصف… (اختياري)"
              placeholderTextColor={colors.text.secondary}
              style={styles.uploadInput}
              multiline
              maxLength={300}
              accessibilityLabel="وصف الريل"
            />

            {!!uploadProgress && <Text style={styles.uploadProgress}>{uploadProgress}</Text>}

            <View style={styles.uploadRow}>
              <Pressable
                onPress={() => {
                  setPending(null);
                  setCaption('');
                }}
                style={[styles.uploadBtn, styles.uploadCancel]}
                accessibilityRole="button"
              >
                <Text style={styles.uploadCancelText}>إلغاء</Text>
              </Pressable>
              <Pressable
                onPress={submitReel}
                disabled={publishing}
                style={[styles.uploadBtn, styles.uploadPrimary, publishing && { opacity: 0.6 }]}
                accessibilityRole="button"
              >
                {publishing ? (
                  <ActivityIndicator size="small" color={colors.text.inverse} />
                ) : (
                  <Text style={styles.uploadPrimaryText}>نشر الريل</Text>
                )}
              </Pressable>
            </View>
          </View>
        </View>
      )}

      <CommentsSheet
        visible={!!commentPost}
        post={commentPost}
        currentUser={currentUser}
        onClose={() => setActiveComment(null)}
        onAddComment={addReelComment}
        onLikeComment={toggleCommentLike}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#000000' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space.xs, padding: space.lg },
  centerText: { ...type.caption, color: '#FFFFFF' },
  emptyIcon: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: colors.brand.soft,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: space.xs,
  },
  emptyTitle: { ...type.title, color: '#FFFFFF' },
  emptySub: { ...type.caption, color: 'rgba(255,255,255,0.7)', textAlign: 'center' },
  emptyBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: space.sm,
    backgroundColor: colors.brand.primary,
    paddingHorizontal: space.md,
    height: 40,
    borderRadius: radius.pill,
  },
  emptyBtnText: { ...type.bodyStrong, color: colors.text.inverse },
  emptyActions: { flexDirection: 'row', gap: space.xs, marginTop: space.xs },
  emptyBtnGhost: { backgroundColor: 'rgba(255,255,255,0.14)', borderWidth: 1, borderColor: 'rgba(255,255,255,0.3)' },

  reelWrap: { width: '100%', backgroundColor: '#000000', justifyContent: 'flex-end' },
  progressTrack: {
    position: 'absolute',
    top: 0,
    start: 0,
    end: 0,
    height: 2.5,
    backgroundColor: 'rgba(255,255,255,0.25)',
  },
  progressFill: { height: 2.5, backgroundColor: '#FFFFFF' },
  pauseBadge: {
    position: 'absolute',
    alignSelf: 'center',
    top: '45%',
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: 'rgba(0,0,0,0.45)',
    alignItems: 'center',
    justifyContent: 'center',
  },

  actions: { position: 'absolute', end: space.md, bottom: space.xxxl + 40, alignItems: 'center', gap: space.md },
  actionBtn: { alignItems: 'center', gap: 3 },
  actionCount: { ...type.caption, color: '#FFFFFF' },

  info: { paddingHorizontal: space.md, paddingBottom: space.xl, gap: 6 },
  authorRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  authorName: { ...type.bodyStrong, color: '#FFFFFF', maxWidth: '70%' },
  caption: { ...type.bodySm, color: 'rgba(255,255,255,0.92)' },

  header: {
    position: 'absolute',
    top: 0,
    start: 0,
    end: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    paddingTop: space.xs + 4,
    paddingBottom: space.xs,
    zIndex: 5,
  },
  headerTitle: { ...type.headline, color: '#FFFFFF' },
  autoRow: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 1 },
  autoDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#4ADE80' },
  autoDotBusy: { backgroundColor: '#FBBF24' },
  autoText: { ...type.caption, color: 'rgba(255,255,255,0.72)', fontSize: 10.5 },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  headerBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: 'rgba(0,0,0,0.35)',
    alignItems: 'center',
    justifyContent: 'center',
  },

  uploadLayer: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.65)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: space.lg,
    zIndex: 20,
  },
  uploadCard: {
    width: '100%',
    backgroundColor: colors.bg.surface,
    borderRadius: radius.lg,
    padding: space.md,
    gap: space.xs,
  },
  uploadTitle: { ...type.title, color: colors.text.primary },
  uploadHint: { ...type.caption, color: colors.text.secondary },
  uploadInput: {
    minHeight: 72,
    borderRadius: radius.md,
    backgroundColor: colors.bg.canvas,
    padding: space.sm,
    ...type.bodySm,
    color: colors.text.primary,
    textAlignVertical: 'top',
  },
  uploadProgress: { ...type.caption, color: colors.semantic.danger },
  sourceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(0,0,0,0.42)',
    borderRadius: 999,
    paddingHorizontal: 9,
    paddingVertical: 3,
  },
  sourceText: { ...type.caption, color: '#FFFFFF', maxWidth: 220 },
  uploadRow: { flexDirection: 'row', gap: space.xs, marginTop: space.xs },
  uploadBtn: { flex: 1, height: 42, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  uploadCancel: { backgroundColor: colors.bg.canvas },
  uploadCancelText: { ...type.bodyStrong, color: colors.text.primary },
  uploadPrimary: { backgroundColor: colors.brand.primary },
  uploadPrimaryText: { ...type.bodyStrong, color: colors.text.inverse },

});

export default ReelsScreen;
