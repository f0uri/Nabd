import React, { useEffect, useRef } from 'react';
import { Animated, View, type DimensionValue, type StyleProp, type ViewStyle } from 'react-native';
import { colors, motion } from '../../theme';

interface SkeletonBlockProps {
  width?: DimensionValue;
  height?: number;
  radius?: number;
  style?: StyleProp<ViewStyle>;
}

/**
 * ATOM — Skeleton
 * نبض هادئ (opacity) بدل دائرة التحميل. useNativeDriver → بلا أي ضغط على الـ JS thread،
 * وهذا مهم جداً على الأجهزة الضعيفة.
 */
export const SkeletonBlock: React.FC<SkeletonBlockProps> = ({ width = '100%', height = 12, radius = 6, style }) => {
  const opacity = useRef(new Animated.Value(0.5)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, {
          toValue: 1,
          duration: motion.skeleton / 2,
          useNativeDriver: true,
        }),
        Animated.timing(opacity, {
          toValue: 0.5,
          duration: motion.skeleton / 2,
          useNativeDriver: true,
        }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [opacity]);

  return (
    <Animated.View
      style={[
        { width, height, borderRadius: radius, backgroundColor: colors.bg.skeletonBase, opacity },
        style,
      ]}
    />
  );
};

/** مجموعة أسطر نصية بنفس هندسة المحتوى الحقيقي */
export const SkeletonLines: React.FC<{ lines?: number; gap?: number }> = ({ lines = 2, gap = 8 }) => (
  <View style={{ gap }}>
    {Array.from({ length: lines }).map((_, i) => (
      <SkeletonBlock key={i} width={i === lines - 1 ? '58%' : '100%'} height={11} />
    ))}
  </View>
);

export default SkeletonBlock;
