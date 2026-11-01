import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import LikeButton from '../atoms/LikeButton';
import ReactionIcon from '../atoms/ReactionIcon';
import { colors, radius, shadow, size, space, type } from '../../theme';
import type { ReactionKind } from '../../types';

const REACTIONS: Array<{ kind: ReactionKind; label: string }> = [
  { kind: 'like', label: 'أعجبني' },
  { kind: 'love', label: 'أحببته' },
  { kind: 'haha', label: 'هاها' },
  { kind: 'wow', label: 'واو' },
  { kind: 'sad', label: 'أحزنتني' },
  { kind: 'angry', label: 'أغضبتني' },
];

const LABELS: Record<ReactionKind, string> = {
  like: 'أعجبني',
  love: 'أحببته',
  haha: 'هاها',
  wow: 'واو',
  sad: 'أحزنتني',
  angry: 'أغضبتني',
};

interface PostActionsProps {
  myReaction: ReactionKind | null;
  onReact: () => void;
  /** اختيار تفاعل من المنتقي (null = حذف التفاعل) */
  onPickReaction?: (kind: ReactionKind | null) => void;
  onComment: () => void;
  onShare: () => void;
}

/**
 * MOLECULE — PostActions (أعجبني · تعليق · مشاركة)
 * الضغطة المطوّلة على «أعجبني» كتفتح منتقي التفاعلات الستة (بأيقونات متجهة).
 */
export const PostActions: React.FC<PostActionsProps> = ({
  myReaction,
  onReact,
  onPickReaction,
  onComment,
  onShare,
}) => {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [actionHeight, setActionHeight] = useState<number>(size.touchTarget);

  const pick = (kind: ReactionKind) => {
    setPickerOpen(false);
    if (onPickReaction) onPickReaction(myReaction === kind ? null : kind);
    else onReact();
  };

  return (
    <View>
      {/* منتقي التفاعلات — كيبان فوق شريط الأزرار */}
      {pickerOpen && (
        <>
          <Pressable style={styles.backdrop} onPress={() => setPickerOpen(false)} accessibilityLabel="إغلاق" />
          <View style={[styles.picker, { bottom: actionHeight + 6 }]}>
            {REACTIONS.map(({ kind, label }) => {
              const active = myReaction === kind;
              return (
                <Pressable
                  key={kind}
                  onPress={() => pick(kind)}
                  accessibilityRole="button"
                  accessibilityLabel={label}
                  style={({ pressed }) => [styles.pickItem, active && styles.pickItemActive, pressed && { transform: [{ scale: 0.88 }] }]}
                >
                  <ReactionIcon kind={kind} size={24} />
                </Pressable>
              );
            })}
          </View>
        </>
      )}

      <View style={styles.row} onLayout={event => setActionHeight(event.nativeEvent.layout.height)}>
        <LikeButton
          active={myReaction !== null}
          label={myReaction ? LABELS[myReaction] : 'أعجبني'}
          onPress={onReact}
          onLongPress={() => setPickerOpen((open) => !open)}
        />
        <Pressable
          onPress={onComment}
          accessibilityRole="button"
          accessibilityLabel="تعليق"
          style={({ pressed }) => [styles.button, pressed && styles.pressed]}
        >
          <Ionicons name="chatbubble-outline" size={size.iconSm + 2} color={colors.text.secondary} />
          <Text style={styles.label}>تعليق</Text>
        </Pressable>
        <Pressable
          onPress={onShare}
          accessibilityRole="button"
          accessibilityLabel="مشاركة"
          style={({ pressed }) => [styles.button, pressed && styles.pressed]}
        >
          <Ionicons name="arrow-redo-outline" size={size.iconSm + 2} color={colors.text.secondary} />
          <Text style={styles.label}>مشاركة</Text>
        </Pressable>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: space.xs,
    borderTopWidth: size.hairline,
    borderTopColor: colors.border.divider,
  },
  button: {
    flex: 1,
    minHeight: size.touchTarget,
    paddingVertical: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space.xs - 2,
    borderRadius: radius.sm,
  },
  pressed: { opacity: 0.7, backgroundColor: colors.bg.canvas },
  label: { flexShrink: 1, textAlign: 'center', ...type.bodySm, fontFamily: type.bodyStrong.fontFamily, color: colors.text.secondary },

  /* المنتقي */
  backdrop: { ...StyleSheet.absoluteFillObject, zIndex: 8 },
  picker: {
    position: 'absolute',
    bottom: size.touchTarget + 6,
    start: space.xs,
    zIndex: 9,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
    backgroundColor: colors.bg.surface,
    borderRadius: radius.pill,
    paddingHorizontal: space.xs,
    paddingVertical: 6,
    borderWidth: size.hairline,
    borderColor: colors.border.subtle,
    ...(shadow.raised as object),
  },
  pickItem: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  pickItemActive: { backgroundColor: colors.brand.soft },
});

export default PostActions;
