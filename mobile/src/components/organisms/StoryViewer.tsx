import React, { useEffect, useRef, useState } from 'react';
import {
  Animated,
  Easing,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Avatar from '../atoms/Avatar';
import { colors, space, type } from '../../theme';
import type { Author, Story } from '../../types';

const STORY_DURATION = 5000;

interface StoryViewerProps {
  visible: boolean;
  stories: Story[];
  startIndex: number;
  currentUser: Author;
  onClose: () => void;
  onSeen: (id: string) => void;
  onReply: (story: Story, text: string) => void;
}

/**
 * ORGANISM — StoryViewer
 * عرض ستوري كامل الشاشة: أشرطة تقدّم، تنقّل باللمس (يمين/يسار)، رد سريع.
 */
export const StoryViewer: React.FC<StoryViewerProps> = ({
  visible,
  stories,
  startIndex,
  currentUser,
  onClose,
  onSeen,
  onReply,
}) => {
  const insets = useSafeAreaInsets();
  const [index, setIndex] = useState(startIndex);
  const [reply, setReply] = useState('');
  const progress = useRef(new Animated.Value(0)).current;
  const animation = useRef<Animated.CompositeAnimation | null>(null);
  const story = stories[index];

  // إعادة الضبط عند الفتح أو التنقّل
  useEffect(() => {
    if (visible) setIndex(startIndex);
  }, [visible, startIndex]);

  useEffect(() => {
    if (!visible || !story) return;
    onSeen(story.id);

    progress.setValue(0);
    animation.current?.stop();
    animation.current = Animated.timing(progress, {
      toValue: 1,
      duration: STORY_DURATION,
      easing: Easing.linear,
      useNativeDriver: false,
    });
    animation.current.start(({ finished }) => {
      if (finished) goNext();
    });

    return () => animation.current?.stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [index, visible, story?.id]);

  const goNext = () => {
    if (index >= stories.length - 1) {
      onClose();
      return;
    }
    setIndex((i) => i + 1);
    setReply('');
  };

  const goPrev = () => {
    if (index === 0) {
      progress.setValue(0);
      return;
    }
    setIndex((i) => i - 1);
    setReply('');
  };

  const sendReply = () => {
    const trimmed = reply.trim();
    if (!trimmed || !story) return;
    onReply(story, trimmed);
    setReply('');
  };

  if (!story) return null;

  return (
    <Modal visible={visible} animationType="fade" transparent={false} onRequestClose={onClose} statusBarTranslucent>
      <View style={[styles.root, { backgroundColor: story.colors[0] }]}>
        {story.imageUri ? (
          <Image source={{ uri: story.imageUri }} style={StyleSheet.absoluteFill} resizeMode="cover" />
        ) : (
          <>
            <View style={[styles.tintBottom, { backgroundColor: story.colors[1], opacity: 0.55 }]} />
            <View style={styles.textArt}>
              <Avatar
                initial={story.author.initial}
                color={story.colors[1]}
                avatarUrl={story.author.avatarUrl}
                size={110}
                ringColor="rgba(255,255,255,0.9)"
              />
              {!!story.text && <Text style={styles.storyText}>{story.text}</Text>}
            </View>
          </>
        )}

        {/* أشرطة التقدّم */}
        <View style={[styles.progressRow, { marginTop: insets.top + space.xs }]}>
          {stories.map((s, i) => (
            <View key={s.id} style={styles.progressTrack}>
              {i < index && <View style={styles.progressDone} />}
              {i === index && (
                <Animated.View
                  style={[
                    styles.progressDone,
                    {
                      width: progress.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] }),
                    },
                  ]}
                />
              )}
            </View>
          ))}
        </View>

        {/* الرأس */}
        <View style={styles.header}>
          <Avatar
            initial={story.author.initial}
            color={story.colors[1]}
            avatarUrl={story.author.avatarUrl}
            size={34}
          />
          <View style={styles.headerText}>
            <Text style={styles.authorName}>{story.author.name}</Text>
            <Text style={styles.time}>{story.createdAt}</Text>
          </View>
          <Pressable onPress={onClose} hitSlop={12} accessibilityRole="button" accessibilityLabel="إغلاق الستوري">
            <Ionicons name="close" size={26} color={colors.text.inverse} />
          </Pressable>
        </View>

        {/* مناطق التنقّل */}
        <Pressable style={styles.tapLeft} onPress={goPrev} accessibilityLabel="الستوري السابق" />
        <Pressable style={styles.tapRight} onPress={goNext} accessibilityLabel="الستوري التالي" />

        {/* الرد السريع */}
        <View style={[styles.replyBar, { paddingBottom: insets.bottom + space.sm }]}>
          <Avatar
            initial={currentUser.initial}
            color={currentUser.color}
            avatarUrl={currentUser.avatarUrl}
            size={32}
          />
          <View style={styles.replyInputWrap}>
            <TextInput
              value={reply}
              onChangeText={setReply}
              placeholder={`رد على ${story.author.name.split(' ')[0]}…`}
              placeholderTextColor="rgba(255,255,255,0.75)"
              style={styles.replyInput}
              maxLength={200}
              returnKeyType="send"
              onSubmitEditing={sendReply}
              accessibilityLabel="رد على الستوري"
            />
          </View>
          <Pressable
            onPress={sendReply}
            disabled={!reply.trim()}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel="إرسال الرد"
            style={({ pressed }) => [!reply.trim() && { opacity: 0.4 }, pressed && { opacity: 0.6 }]}
          >
            <Ionicons name="send" size={20} color={colors.text.inverse} />
          </Pressable>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  root: { flex: 1 },
  tintBottom: { position: 'absolute', left: 0, right: 0, bottom: 0, height: '55%' },
  textArt: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: space.lg, gap: space.md },
  storyText: {
    ...type.headline,
    fontSize: 22,
    color: colors.text.inverse,
    textAlign: 'center',
    lineHeight: 32,
  },
  progressRow: { flexDirection: 'row', gap: 4, paddingHorizontal: space.sm },
  progressTrack: {
    flex: 1,
    height: 3,
    borderRadius: 2,
    backgroundColor: 'rgba(255,255,255,0.35)',
    overflow: 'hidden',
  },
  progressDone: { height: 3, backgroundColor: colors.text.inverse, width: '100%' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    paddingHorizontal: space.sm,
    paddingTop: space.sm,
  },
  headerText: { flex: 1, minWidth: 0 },
  authorName: { ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.inverse },
  time: { ...type.micro, color: 'rgba(255,255,255,0.85)' },
  tapLeft: { position: 'absolute', top: 90, bottom: 110, left: 0, width: '35%' },
  tapRight: { position: 'absolute', top: 90, bottom: 110, right: 0, width: '65%' },
  replyBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space.xs,
    paddingHorizontal: space.md,
    paddingTop: space.sm,
  },
  replyInputWrap: {
    flex: 1,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.6)',
    paddingHorizontal: space.md,
    justifyContent: 'center',
  },
  replyInput: {
    ...type.bodySm,
    color: colors.text.inverse,
    textAlign: 'right',
    writingDirection: 'rtl',
  },
});

export default StoryViewer;
