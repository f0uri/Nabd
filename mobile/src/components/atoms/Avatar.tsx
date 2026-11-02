import React from 'react';
import { Image, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

interface AvatarProps {
  /** اسم صاحب الحساب — كيتستعمل غير للوصف الصوتي (accessibility) */
  initial?: string;
  /** لون احتياطي قديم (ما بقاش كيتستعمل؛ الرمز الافتراضي رمادي بحال فيسبوك) */
  color?: string;
  /** رابط صورة الحساب الحقيقية (من الخادم) */
  avatarUrl?: string | null;
  size?: number;
  /** حلقة حول الصورة (للستوري أو الحساب النشط) */
  ringColor?: string;
  style?: StyleProp<ViewStyle>;
}

/** ألوان الرمز الافتراضي — نفس منطق فيسبوك: خلفية رمادية فاتحة وسيلويت أغمق */
const DEFAULT_BG = '#30323A';
const DEFAULT_FIGURE = '#A8ABB7';

/**
 * ATOM — Avatar
 * - عند وجود صورة (رفعها صاحب الحساب): كتعرض الصورة.
 * - بلا صورة: كتعرض **السيلويت الرمادي الافتراضي** (شخص رأسه وأكتافه) بحال فيسبوك،
 *   بلا حروف ولا صور مولّدة.
 */
export const Avatar: React.FC<AvatarProps> = ({ initial, avatarUrl, size = 40, ringColor, style }) => {
  const ring = ringColor ? 2.5 : 0;
  const inner = size;

  return (
    <View
      accessibilityRole="image"
      accessibilityLabel={avatarUrl ? initial ?? 'صورة الحساب' : 'لا توجد صورة للحساب'}
      style={[
        styles.ring,
        {
          width: size + ring * 2,
          height: size + ring * 2,
          borderRadius: (size + ring * 2) / 2,
          borderWidth: ring,
          borderColor: ringColor ?? 'transparent',
        },
        style,
      ]}
    >
      <View style={[styles.core, { width: inner, height: inner, borderRadius: inner / 2 }]}>
        {avatarUrl ? (
          <Image
            source={{ uri: avatarUrl }}
            style={{ width: inner, height: inner }}
            resizeMode="cover"
            accessibilityIgnoresInvertColors
          />
        ) : (
          <View style={[styles.fallback, { width: inner, height: inner }]}>
            {/* الرأس */}
            <View
              style={{
                width: inner * 0.34,
                height: inner * 0.34,
                borderRadius: inner * 0.17,
                backgroundColor: DEFAULT_FIGURE,
                marginTop: inner * 0.19,
              }}
            />
            {/* الأكتاف */}
            <View
              style={{
                width: inner * 0.66,
                height: inner * 0.38,
                borderTopLeftRadius: inner * 0.33,
                borderTopRightRadius: inner * 0.33,
                backgroundColor: DEFAULT_FIGURE,
                marginTop: inner * 0.06,
              }}
            />
          </View>
        )}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  ring: { alignItems: 'center', justifyContent: 'center' },
  core: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden', backgroundColor: DEFAULT_BG },
  fallback: { backgroundColor: DEFAULT_BG, alignItems: 'center', justifyContent: 'flex-start', overflow: 'hidden' },
});

export default Avatar;
