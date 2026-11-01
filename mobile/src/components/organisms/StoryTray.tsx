import React from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../atoms/Avatar';
import { useStoryStore } from '../../store/useStoryStore';
import { useAuthStore } from '../../store/useAuthStore';
import { CURRENT_USER } from '../../api/mockApi';
import { colors, radius, space, type } from '../../theme';
import type { Story } from '../../types';

interface StoryTrayProps {
  onOpenStory: (index: number) => void;
  onCreate: () => void;
}

/** ORGANISM — StoryTray (شريط الستوري الحقيقي: صور + إنشاء + مشاهدة) */
export const StoryTray: React.FC<StoryTrayProps> = ({ onOpenStory, onCreate }) => {
  const { width, fontScale } = useWindowDimensions();
  const cardWidth = Math.min(140, Math.max(112, width * 0.28));
  const cardSize = { width: cardWidth, height: cardWidth * 1.55 + Math.max(0, fontScale - 1) * type.bodySm.lineHeight * 2 };
  const stories = useStoryStore((s) => s.stories);
  const user = useAuthStore((s) => s.user) ?? CURRENT_USER;

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.tray}
      accessibilityLabel="شريط الستوري"
    >
      {/* إنشاء ستوري */}
      <Pressable
        onPress={onCreate}
        accessibilityRole="button"
        accessibilityLabel="أنشئ ستوري"
        style={({ pressed }) => [styles.card, cardSize, styles.createCard, pressed && styles.pressed]}
      >
        <View style={styles.createPhoto}>
          {user.avatarUrl ? <Image source={{ uri: user.avatarUrl }} style={styles.cover} resizeMode="cover" /> :
            <Avatar initial={user.initial} color={user.color} size={78} />}
        </View>
        <View style={styles.plus}>
          <Ionicons name="add" size={27} color={colors.text.inverse} />
        </View>
        <Text style={styles.createLabel} numberOfLines={1}>أنشئ ستوري</Text>
      </Pressable>

      {stories.map((story, index) => {
        const isMine = story.author.id === user.id || story.author.isMe;
        return (
          <Pressable
            key={story.id}
            onPress={() => onOpenStory(index)}
            accessibilityRole="button"
            accessibilityLabel={`ستوري ${story.author.name}`}
            style={({ pressed }) => [
              styles.card,
              cardSize,
              { backgroundColor: story.colors[0] },
              pressed && styles.pressed,
            ]}
          >
            {/* صورة الستوري كخلفية، أو صورة رمزية كبيرة للستوري النصي */}
            {story.imageUri ? (
              <Image source={{ uri: story.imageUri }} style={styles.cover} resizeMode="cover" />
            ) : (
              <View style={styles.textArt}>
                <Avatar
                  initial={story.author.initial}
                  color={story.colors[1]}
                  avatarUrl={story.author.avatarUrl}
                  size={58}
                  ringColor="rgba(255,255,255,0.85)"
                />
              </View>
            )}

            <View style={styles.shade} />

            <View style={styles.topRow}>
              <Avatar
                initial={story.author.initial}
                color={story.colors[1]}
                avatarUrl={story.author.avatarUrl}
                size={30}
                ringColor={story.seen ? 'rgba(255,255,255,0.55)' : colors.brand.primary}
              />
              {isMine && (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>أنت</Text>
                </View>
              )}
            </View>

            <Text style={styles.label} numberOfLines={1}>
              {isMine ? 'ستوريك' : story.author.name.split(' ')[0]}
            </Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  tray: { paddingHorizontal: space.sm, paddingTop: space.xs, paddingBottom: space.sm, gap: space.xs, backgroundColor: 'transparent' },
  card: {
    borderRadius: radius.lg,
    overflow: 'hidden',
    justifyContent: 'space-between',
    padding: space.xs,
    borderWidth: 1,
    borderColor: colors.border.subtle,
  },
  pressed: { opacity: 0.85, transform: [{ scale: 0.97 }] },
  createCard: { backgroundColor: colors.bg.skeletonHighlight, alignItems: 'center', padding: 0, paddingBottom: 14 },
  createPhoto: { width: '100%', height: '65%', backgroundColor: colors.bg.skeletonBase, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  plus: {
    position: 'absolute',
    top: '52%',
    width: 42,
    height: 42,
    borderRadius: 21,
    backgroundColor: colors.brand.primary,
    borderWidth: 3,
    borderColor: colors.bg.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cover: { ...StyleSheet.absoluteFillObject },
  textArt: { ...StyleSheet.absoluteFillObject, alignItems: 'center', justifyContent: 'center', paddingBottom: 34 },
  shade: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.18)',
  },
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  badge: {
    backgroundColor: 'rgba(13,28,49,0.80)',
    borderRadius: 999,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  badgeText: { ...type.micro, fontSize: 9, color: colors.text.inverse },
  createLabel: { ...type.bodySm, color: colors.text.primary, fontWeight: '700', textAlign: 'center' },
  label: { paddingHorizontal: space.xs, paddingVertical: 4, borderRadius: radius.sm, backgroundColor: 'rgba(13,28,49,0.72)', ...type.bodySm, fontWeight: '600', fontFamily: type.bodyStrong.fontFamily, color: colors.text.inverse, textShadowColor: 'rgba(0,0,0,0.3)', textShadowRadius: 4 },
});

export default StoryTray;
