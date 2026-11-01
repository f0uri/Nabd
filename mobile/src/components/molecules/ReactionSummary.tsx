import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import ReactionIcon from '../atoms/ReactionIcon';
import { colors, space, type } from '../../theme';
import { formatCount, topReactions, totalReactions } from '../../utils/format';
import type { Post } from '../../types';

interface ReactionSummaryProps {
  post: Post;
}

/** MOLECULE — ReactionSummary (أيقونات تفاعل متجهية متراكبة + الأعداد) */
export const ReactionSummary: React.FC<ReactionSummaryProps> = ({ post }) => {
  const total = totalReactions(post);
  const top = topReactions(post);

  return (
    <View style={styles.row}>
      <View style={styles.left}>
        {top.length > 0 && (
          <View style={styles.stack}>
            {top.map((kind, index) => (
              <ReactionIcon
                key={kind}
                kind={kind}
                size={19}
                badge
                style={[styles.bubble, index === 0 ? null : styles.overlap]}
              />
            ))}
          </View>
        )}
        <Text style={styles.count}>{formatCount(total)}</Text>
      </View>

      <View style={styles.right}>
        <Text style={styles.count}>{post.commentsCount} تعليق</Text>
        <Text style={styles.count}>{post.shares} مشاركة</Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space.md,
    paddingTop: space.xs,
    paddingBottom: space.sm - 2,
  },
  left: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  stack: { flexDirection: 'row', alignItems: 'center' },
  bubble: { borderWidth: 2, borderColor: colors.bg.surface },
  overlap: { marginRight: -6 },
  right: { flexDirection: 'row', alignItems: 'center', gap: space.sm },
  count: { ...type.micro, color: colors.text.secondary },
});

export default ReactionSummary;
