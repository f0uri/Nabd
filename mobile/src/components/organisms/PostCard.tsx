import React, { useCallback } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import PostHeader from '../molecules/PostHeader';
import ReactionSummary from '../molecules/ReactionSummary';
import PostActions from '../molecules/PostActions';
import CommentBar from '../molecules/CommentBar';
import PostMedia from './PostMedia';
import { colors, space, type } from '../../theme';
import { CURRENT_USER } from '../../api/mockApi';
import { useAuthStore } from '../../store/useAuthStore';
import type { Post, ReactionKind } from '../../types';

interface PostCardProps {
  post: Post;
  onReact: (postId: string) => void;
  /** اختيار تفاعل محدد من المنتقي */
  onPickReaction?: (postId: string, kind: ReactionKind | null) => void;
  /** يفتح شاشة التعليقات الكاملة لهذا المنشور */
  onOpenComments: (postId: string) => void;
  onMore?: (postId: string) => void;
  onHide?: (postId: string) => void;
  /** مشاركة المنشور (إرسال فـ رسالة…) */
  onShare?: (postId: string) => void;
  /** الانتقال لتبويب الريلز (منشان فيديو ظاهر فالبطاقة) */
  onOpenReel?: () => void;
}

/**
 * ORGANISM — PostCard
 * تُبنى حصرياً من molecules. لا تعرف كيف تُرسم الأزرار، فقط تُركّبها.
 * React.memo: لا يُعاد رسم البطاقة إلا إذا تغيّرت بياناتها هي (أساس أداء الـ feed).
 */
const PostCardBase: React.FC<PostCardProps> = ({
  post,
  onReact,
  onPickReaction,
  onOpenComments,
  onMore,
  onHide,
  onShare,
  onOpenReel,
}) => {
  const currentUser = useAuthStore((s) => s.user) ?? CURRENT_USER;
  const handleReact = useCallback(() => onReact(post.id), [onReact, post.id]);
  const handlePick = useCallback(
    (kind: ReactionKind | null) => onPickReaction?.(post.id, kind === null ? null : kind),
    [onPickReaction, post.id],
  );
  const handleComments = useCallback(() => onOpenComments(post.id), [onOpenComments, post.id]);
  const handleMore = useCallback(() => onMore?.(post.id), [onMore, post.id]);
  const handleHide = useCallback(() => onHide?.(post.id), [onHide, post.id]);
  const handleShare = useCallback(() => (onShare ? onShare(post.id) : onMore?.(post.id)), [onShare, onMore, post.id]);

  return (
    <View style={styles.card}>
      <View style={styles.inner}>
        <PostHeader author={post.author} createdAt={post.createdAt} privacy={post.privacy} onMore={onMore ? handleMore : undefined} onHide={onHide ? handleHide : undefined} />
        {!!post.text && <Text style={styles.body}>{post.text}</Text>}
      </View>

      {post.media && <PostMedia media={post.media} />}

      <ReactionSummary post={post} />
      <PostActions
        myReaction={post.myReaction}
        onReact={handleReact}
        onPickReaction={handlePick}
        onComment={handleComments}
        onShare={handleShare}
      />
      <CommentBar
        comments={post.comments}
        commentsCount={post.commentsCount}
        currentUser={currentUser}
        onOpen={handleComments}
      />
    </View>
  );
};

export const PostCard = React.memo(PostCardBase, (prev, next) => prev.post === next.post);

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.bg.canvas,
    borderBottomWidth: 1,
    borderColor: colors.border.subtle,
    marginBottom: space.md,
    overflow: 'hidden',
  },
  inner: { paddingHorizontal: space.md, paddingTop: space.sm },
  body: {
    ...type.body,
    fontSize: 16,
    lineHeight: 25,
    color: colors.text.primary,
    marginTop: space.sm - 2,
    paddingBottom: space.sm - 2,
  },
});

export default PostCard;
