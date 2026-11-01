import React from 'react';
import { View, type StyleProp, type ViewStyle } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { colors } from '../../theme';
import type { ReactionKind } from '../../types';

type MCIconName = React.ComponentProps<typeof MaterialCommunityIcons>['name'];

/** تفاعلات بأيقونات متجهية — بلا إيموجي (كما في الواجهات الاحترافية) */
const ICONS: Record<ReactionKind, MCIconName> = {
  like: 'thumb-up',
  love: 'heart',
  haha: 'emoticon-excited',
  wow: 'emoticon-neutral',
  sad: 'emoticon-sad',
  angry: 'emoticon-angry',
};

interface ReactionIconProps {
  kind: ReactionKind;
  size?: number;
  /** داخل دائرة ملونة (كما في شريط التفاعلات) */
  badge?: boolean;
  style?: StyleProp<ViewStyle>;
}

export const ReactionIcon: React.FC<ReactionIconProps> = ({ kind, size = 12, badge = false, style }) => {
  const glyph = size * 0.72;

  if (!badge) {
    return (
      <MaterialCommunityIcons
        name={ICONS[kind]}
        size={size}
        color={colors.reaction[kind]}
        style={style as never}
      />
    );
  }

  const diameter = size;
  return (
    <View
      style={[
        {
          width: diameter,
          height: diameter,
          borderRadius: diameter / 2,
          backgroundColor: colors.reaction[kind],
          alignItems: 'center',
          justifyContent: 'center',
        },
        style,
      ]}
    >
      <MaterialCommunityIcons name={ICONS[kind]} size={glyph} color={colors.text.inverse} />
    </View>
  );
};

export default ReactionIcon;
