import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../atoms/Avatar';
import { colors, size, space, type } from '../../theme';
import { privacyLabel } from '../../utils/format';
import type { Author, Privacy } from '../../types';

interface PostHeaderProps {
  author: Author;
  createdAt: string;
  privacy: Privacy;
  onMore?: () => void;
  onHide?: () => void;
}

/** MOLECULE — PostHeader (Avatar + اسم + وقت + خصوصية + زر المزيد) */
export const PostHeader: React.FC<PostHeaderProps> = ({ author, createdAt, privacy, onMore, onHide }) => (
  <View style={styles.row}>
    <Avatar initial={author.initial} color={author.color} avatarUrl={author.avatarUrl} size={size.avatarMd} />
    <View style={styles.who}>
      <View style={styles.nameRow}>
        <Text style={styles.name} numberOfLines={1}>
          {author.name}
        </Text>
        {author.verified && (
          <Ionicons name="checkmark-circle" size={14} color={colors.brand.primary} />
        )}
      </View>
      <View style={styles.metaRow}>
        <Text style={styles.meta}>{createdAt}</Text>
        <Text style={styles.dot}>·</Text>
        <Ionicons
          name={privacy === 'public' ? 'globe-outline' : privacy === 'friends' ? 'people-outline' : 'lock-closed-outline'}
          size={11}
          color={colors.text.secondary}
        />
        <Text style={styles.meta}>{privacyLabel(privacy)}</Text>
      </View>
    </View>
    {onMore && <Pressable
      onPress={onMore}
      hitSlop={8}
      accessibilityRole="button"
      accessibilityLabel="خيارات المنشور"
      style={({ pressed }) => [styles.more, pressed && { opacity: 0.6 }]}
    >
      <Ionicons name="ellipsis-horizontal" size={20} color={colors.text.secondary} />
    </Pressable>}
    {onHide && <Pressable onPress={onHide} accessibilityRole="button" accessibilityLabel="إخفاء المنشور" style={styles.more}>
      <Ionicons name="close-outline" size={27} color={colors.text.secondary} />
    </Pressable>}
  </View>
);

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: space.xs },
  who: { flex: 1, minWidth: 0 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  name: { ...type.bodyStrong, color: colors.text.primary, flexShrink: 1 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 3, marginTop: 1 },
  meta: { ...type.micro, color: colors.text.secondary },
  dot: { color: colors.text.tertiary, fontSize: 12 },
  more: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', borderRadius: 16 },
});

export default PostHeader;
