import React from 'react';
import { Image, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import Avatar from '../atoms/Avatar';
import { useStoryStore } from '../../store/useStoryStore';
import { useAuthStore } from '../../store/useAuthStore';
import { CURRENT_USER } from '../../api/mockApi';
import { colors, radius, shadow, space, type } from '../../theme';
import type { Story } from '../../types';

interface StoryTrayProps {
  onOpenStory: (index: number) => void;
  onCreate: () => void;
}

/** ORGANISM — StoryTray (شريط الستوري الحقيقي: صور + إنشاء + مشاهدة) */
export const StoryTray: React.FC<StoryTrayProps> = ({ onOpenStory, onCreate }) => {
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
        style={({ pressed }) => [styles.card, styles.createCard, pressed && styles.pressed]}
      >
        <View style={styles.createPhoto}>
          <Avatar initial={user.initial} color={user.color} avatarUrl={user.avatarUrl} size={40} />
        </View>
        <View style={styles.plus}>
          <Ionicons name="add" size={16} color={colors.text.inverse} />
        </View>
        <Text style={[styles.label, { color: colors.text.primary }]}>أنشئ ستوري</Text>
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
                size={28}
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
  tray: { paddingHorizontal: space.md, paddingTop: space.xs, paddingBottom: space.sm, gap: space.xs },
  card: {
    width: 104,
    height: 178,
    borderRadius: radius.md,
    overflow: 'hidden',
    justifyContent: 'space-between',
    padding: space.xs,
    ...shadow.card,
  },
  pressed: { opacity: 0.85, transform: [{ scale: 0.97 }] },
  createCard: { backgroundColor: colors.bg.surface, alignItems: 'center' },
  createPhoto: { width: '100%', height: 108, alignItems: 'center', justifyContent: 'center' },
  plus: {
    position: 'absolute',
    bottom: 42,
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: colors.brand.primary,
    borderWidth: 2,
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
    backgroundColor: 'rgba(0,0,0,0.35)',
    borderRadius: 999,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  badgeText: { ...type.micro, fontSize: 9, color: colors.text.inverse },
  label: { ...type.caption, fontFamily: type.bodyStrong.fontFamily, color: colors.text.inverse },
});

export default StoryTray;
