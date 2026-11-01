import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../atoms/Avatar';
import { colors, radius, size, space, type } from '../../theme';
import { formatCount } from '../../utils/format';
import type { Author, Comment } from '../../types';

interface CommentBarProps {
  comments: Comment[];
  currentUser: Author;
  commentsCount?: number;
  /** يفتح شاشة التعليقات الكاملة */
  onOpen: () => void;
}

/**
 * MOLECULE — CommentBar (نسخة مختصرة داخل بطاقة المنشور)
 * كتعرض آخر تعليقين + «عرض كل التعليقات» + حقل وهمي كيفتح الشيت.
 * الكتابة والإعجاب على التعليقات كيتمّو فـ CommentsSheet.
 */
export const CommentBar: React.FC<CommentBarProps> = ({ comments, currentUser, commentsCount, onOpen }) => {
  const total = commentsCount ?? comments.length;
  const preview = comments.slice(-2);

  return (
    <View style={styles.wrap}>
      {preview.length > 0 && (
        <View style={styles.previewBox}>
          {preview.map((comment) => (
            <View key={comment.id} style={styles.commentRow}>
              <Avatar
                initial={comment.author.initial}
                color={comment.author.color}
                avatarUrl={comment.author.avatarUrl}
                size={size.avatarSm}
              />
              <View style={styles.bubble}>
                <Text style={styles.commentName}>{comment.author.name}</Text>
                <Text style={styles.commentText} numberOfLines={3}>
                  {comment.text}
                </Text>
              </View>
            </View>
          ))}
        </View>
      )}

      {total > preview.length && (
        <Pressable onPress={onOpen} hitSlop={4} accessibilityRole="button" style={styles.moreLink}>
          <Text style={styles.moreText}>عرض كل التعليقات ({formatCount(total)})</Text>
        </Pressable>
      )}

      <View style={styles.inputRow}>
        <Avatar
          initial={currentUser.initial}
          color={currentUser.color}
          avatarUrl={currentUser.avatarUrl}
          size={size.avatarSm}
        />
        <Pressable
          onPress={onOpen}
          accessibilityRole="button"
          accessibilityLabel="اكتب تعليقاً"
          style={({ pressed }) => [styles.fakeInput, pressed && { opacity: 0.7 }]}
        >
          <Text style={styles.fakePlaceholder}>اكتب تعليقاً…</Text>
        </Pressable>
        <Pressable onPress={onOpen} hitSlop={8} accessibilityRole="button" accessibilityLabel="التعليقات">
          <Ionicons name="chatbubble-ellipses-outline" size={18} color={colors.text.secondary} />
        </Pressable>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  wrap: { paddingHorizontal: space.md, paddingBottom: space.sm, paddingTop: space.xs },
  previewBox: { gap: 6 },
  commentRow: { flexDirection: 'row', gap: space.xs, alignItems: 'flex-start' },
  bubble: {
    backgroundColor: colors.bg.canvas,
    borderRadius: radius.md + 2,
    paddingHorizontal: space.sm,
    paddingVertical: space.xs - 2,
    alignSelf: 'flex-start',
    maxWidth: '100%',
    flexShrink: 1,
  },
  commentName: { ...type.caption, fontFamily: type.bodyStrong.fontFamily, color: colors.text.primary },
  commentText: { ...type.bodySm, fontSize: 13, color: colors.text.primary, marginTop: 1 },
  moreLink: { paddingVertical: 6, paddingHorizontal: 4 },
  moreText: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },
  inputRow: { flexDirection: 'row', alignItems: 'center', gap: space.xs, marginTop: 4 },
  fakeInput: {
    flex: 1,
    backgroundColor: colors.bg.canvas,
    borderRadius: 18,
    paddingHorizontal: space.sm,
    height: 34,
    justifyContent: 'center',
  },
  fakePlaceholder: { ...type.caption, color: colors.text.secondary },
});

export default CommentBar;
