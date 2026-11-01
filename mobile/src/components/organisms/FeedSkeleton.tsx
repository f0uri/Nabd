import React from 'react';
import { StyleSheet, View } from 'react-native';
import SkeletonBlock, { SkeletonLines } from '../atoms/Skeleton';
import { colors, radius, space, size } from '../../theme';

interface CardSkeletonProps {
  withMedia?: boolean;
}

/** هيكل بطاقة منشور — نفس هندسة PostCard بالضبط */
export const PostCardSkeleton: React.FC<CardSkeletonProps> = ({ withMedia = true }) => (
  <View style={styles.card}>
    <View style={styles.inner}>
      <View style={styles.head}>
        <SkeletonBlock width={size.avatarMd} height={size.avatarMd} radius={size.avatarMd / 2} />
        <View style={{ flex: 1, gap: 7 }}>
          <SkeletonBlock width="38%" height={11} />
          <SkeletonBlock width="22%" height={9} />
        </View>
      </View>
      <View style={{ marginTop: space.sm }}>
        <SkeletonLines lines={3} />
      </View>
    </View>

    {withMedia && (
      <SkeletonBlock width="100%" height={200} radius={0} style={{ marginTop: space.sm }} />
    )}

    <View style={styles.footer}>
      <SkeletonBlock width="30%" height={12} />
      <SkeletonBlock width="30%" height={12} />
      <SkeletonBlock width="30%" height={12} />
    </View>
  </View>
);

/**
 * ORGANISM — FeedSkeleton
 * يُعرض بدل القائمة بالكامل قبل وصول أول صفحة من البيانات.
 * الهدف: صفر «قفزة تخطيط» (no layout shift) عند وصول المحتوى.
 */
export const FeedSkeleton: React.FC = () => (
  <View style={styles.wrap}>
    <PostCardSkeleton withMedia />
    <PostCardSkeleton withMedia={false} />
    <PostCardSkeleton withMedia />
  </View>
);

const styles = StyleSheet.create({
  wrap: { paddingTop: space.xs },
  card: {
    backgroundColor: colors.bg.surface,
    marginBottom: space.xs,
  },
  inner: { paddingHorizontal: space.md, paddingTop: space.sm },
  head: { flexDirection: 'row', alignItems: 'center', gap: space.xs },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    paddingVertical: space.sm,
    gap: space.xs,
  },
  composerSkeleton: { height: 64, borderRadius: radius.sm },
});

export default FeedSkeleton;
