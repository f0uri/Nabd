import React, { useEffect, useRef, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../atoms/Avatar';
import { colors, radius, shadow, size, space, type } from '../../theme';
import { formatCount } from '../../utils/format';
import type { Author, Comment, Post } from '../../types';

interface CommentsSheetProps {
  visible: boolean;
  post: Post | null;
  currentUser: Author;
  onClose: () => void;
  onAddComment: (postId: string, text: string) => void;
  onDeleteComment?: (postId: string, commentId: string) => void;
  onLikeComment: (postId: string, commentId: string) => void;
}

/**
 * ORGANISM — CommentsSheet
 * شاشة التعليقات كاملة (Bottom Sheet بنمط فيسبوك):
 * معاينة المنشور + قائمة التعليقات (إعجاب/حذف) + حقل الكتابة فوق الكيبورد.
 */
export const CommentsSheet: React.FC<CommentsSheetProps> = ({
  visible,
  post,
  currentUser,
  onClose,
  onAddComment,
  onDeleteComment,
  onLikeComment,
}) => {
  const [text, setText] = useState('');
  const listRef = useRef<FlatList<Comment>>(null);

  useEffect(() => {
    if (!visible) setText('');
  }, [visible]);

  const submit = () => {
    const trimmed = text.trim();
    if (!trimmed || !post) return;
    onAddComment(post.id, trimmed);
    setText('');
    setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 120);
  };

  const comments = post?.comments ?? [];

  const renderComment = ({ item }: { item: Comment }) => {
    const mine = item.author.id === currentUser.id || item.author.isMe;
    return (
      <View style={styles.row}>
        <Avatar
          initial={item.author.initial}
          color={item.author.color}
          avatarUrl={item.author.avatarUrl}
          size={size.avatarSm}
        />
        <View style={styles.rowBody}>
          <View style={styles.bubble}>
            <Text style={styles.authorName}>{item.author.name}</Text>
            <Text style={styles.commentText}>{item.text}</Text>
          </View>

          <View style={styles.metaRow}>
            <Pressable onPress={() => onLikeComment(post!.id, item.id)} hitSlop={8} accessibilityRole="button">
              <Text style={[styles.metaAction, item.likedByMe && styles.metaActionOn]}>أعجبني</Text>
            </Pressable>
            <Text style={styles.metaTime}>{item.createdAt}</Text>
            {!!onDeleteComment && mine && (
              <Pressable onPress={() => onDeleteComment(post!.id, item.id)} hitSlop={8} accessibilityRole="button">
                <Text style={[styles.metaAction, { color: colors.semantic.danger }]}>حذف</Text>
              </Pressable>
            )}

            {item.likes > 0 && (
              <View style={styles.likesPill}>
                <Text style={styles.likesText}>{formatCount(item.likes)}</Text>
                <Ionicons name="thumbs-up" size={11} color={colors.text.inverse} />
              </View>
            )}
          </View>
        </View>
      </View>
    );
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose} statusBarTranslucent>
      <View style={styles.backdrop}>
        <Pressable style={styles.backdropFill} onPress={onClose} accessibilityLabel="إغلاق التعليقات" />

        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          style={styles.sheetWrap}
          keyboardVerticalOffset={0}
        >
          <View style={styles.sheet}>
            <View style={styles.handle} />

            <View style={styles.header}>
              <Text style={styles.title}>
                التعليقات{post && post.commentsCount > 0 ? ` · ${formatCount(post.commentsCount)}` : ''}
              </Text>
              <Pressable onPress={onClose} hitSlop={10} accessibilityRole="button" accessibilityLabel="إغلاق">
                <Ionicons name="close" size={22} color={colors.text.secondary} />
              </Pressable>
            </View>

            {post && (
              <View style={styles.postPreview}>
                <Avatar
                  initial={post.author.initial}
                  color={post.author.color}
                  avatarUrl={post.author.avatarUrl}
                  size={size.avatarSm}
                />
                <View style={styles.previewBody}>
                  <Text style={styles.previewName}>{post.author.name}</Text>
                  <Text style={styles.previewText} numberOfLines={2}>
                    {post.text}
                  </Text>
                </View>
              </View>
            )}

            <FlatList
              ref={listRef}
              data={comments}
              keyExtractor={(item) => item.id}
              renderItem={renderComment}
              contentContainerStyle={styles.listContent}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}
              ListEmptyComponent={
                <View style={styles.emptyBox}>
                  <View style={styles.emptyIcon}>
                    <Ionicons name="chatbubble-ellipses-outline" size={26} color={colors.brand.primary} />
                  </View>
                  <Text style={styles.emptyTitle}>ما كايناش تعليقات</Text>
                  <Text style={styles.emptySub}>كون أنت أول واحد كيعلّق</Text>
                </View>
              }
            />

            <View style={styles.inputBar}>
              <Avatar
                initial={currentUser.initial}
                color={currentUser.color}
                avatarUrl={currentUser.avatarUrl}
                size={size.avatarSm}
              />
              <View style={styles.inputWrap}>
                <TextInput
                  value={text}
                  onChangeText={setText}
                  placeholder="اكتب تعليقاً…"
                  placeholderTextColor={colors.text.secondary}
                  style={styles.input}
                  multiline
                  maxLength={500}
                  accessibilityLabel="حقل كتابة تعليق"
                />
              </View>
              <Pressable
                onPress={submit}
                disabled={!text.trim()}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel="إرسال التعليق"
                style={({ pressed }) => [styles.send, !text.trim() && { opacity: 0.35 }, pressed && { opacity: 0.6 }]}
              >
                <Ionicons name="send" size={18} color={colors.brand.primary} />
              </Pressable>
            </View>
          </View>
        </KeyboardAvoidingView>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  backdropFill: { ...StyleSheet.absoluteFillObject },
  sheetWrap: { maxHeight: '92%' },
  sheet: {
    height: '92%',
    backgroundColor: colors.bg.surface,
    borderTopLeftRadius: radius.lg + 4,
    borderTopRightRadius: radius.lg + 4,
    overflow: 'hidden',
    ...shadow.card,
  },
  handle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border.divider,
    alignSelf: 'center',
    marginTop: space.xs,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border.divider,
  },
  title: { ...type.bodyStrong, fontSize: 16, color: colors.text.primary },
  postPreview: {
    flexDirection: 'row',
    gap: space.xs,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    backgroundColor: colors.bg.canvas,
  },
  previewBody: { flex: 1, minWidth: 0 },
  previewName: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  previewText: { ...type.caption, color: colors.text.secondary, marginTop: 2 },
  listContent: { paddingHorizontal: space.md, paddingTop: space.sm, paddingBottom: space.md, flexGrow: 1 },
  row: { flexDirection: 'row', gap: space.xs, marginBottom: space.sm },
  rowBody: { flex: 1, minWidth: 0 },
  bubble: {
    backgroundColor: colors.bg.canvas,
    borderRadius: radius.md + 2,
    paddingHorizontal: space.sm,
    paddingVertical: space.xs,
    alignSelf: 'flex-start',
    maxWidth: '100%',
  },
  authorName: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  commentText: { ...type.bodySm, color: colors.text.primary, marginTop: 1 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: space.sm, paddingHorizontal: space.sm, paddingTop: 4 },
  metaAction: { ...type.caption, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },
  metaActionOn: { color: colors.brand.primary },
  metaTime: { ...type.caption, color: colors.text.secondary },
  likesPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    backgroundColor: colors.brand.primary,
    borderRadius: 999,
    paddingHorizontal: 5,
    paddingVertical: 1,
  },
  likesText: { ...type.caption, fontSize: 10, color: colors.text.inverse },
  emptyBox: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 48, gap: 4 },
  emptyIcon: {
    width: 54,
    height: 54,
    borderRadius: 27,
    backgroundColor: colors.brand.soft,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: space.xs,
  },
  emptyTitle: { ...type.bodyStrong, color: colors.text.primary },
  emptySub: { ...type.caption, color: colors.text.secondary },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: space.xs,
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border.divider,
    backgroundColor: colors.bg.surface,
  },
  inputWrap: {
    flex: 1,
    backgroundColor: colors.bg.canvas,
    borderRadius: 18,
    paddingHorizontal: space.sm,
    minHeight: 36,
    justifyContent: 'center',
  },
  input: {
    ...type.bodySm,
    color: colors.text.primary,
    paddingTop: 8,
    paddingBottom: 8,
    maxHeight: 96,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
  send: { width: 34, height: 36, alignItems: 'center', justifyContent: 'center' },
});

export default CommentsSheet;
