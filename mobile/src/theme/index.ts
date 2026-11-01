/**
 * NABD DESIGN SYSTEM — theme
 * المصدر الوحيد للحقيقة: مطابق 100% لـ design/tokens.json
 * لا تكتب أي قيمة لون/مسافة خارج هذا الملف.
 */
import { Platform } from 'react-native';

export const colors = {
  brand: {
    primary: '#1877F2',
    dark: '#0C63D4',
    pressed: '#166FE5',
    soft: '#E7F3FF',
  },
  bg: {
    canvas: '#F0F2F5',
    surface: '#FFFFFF',
    overlay: 'rgba(0,0,0,0.55)',
    skeletonBase: '#E4E6EB',
    skeletonHighlight: '#F2F3F5',
  },
  text: {
    primary: '#050505',
    secondary: '#65676B',
    tertiary: '#8A8D91',
    inverse: '#FFFFFF',
    link: '#1877F2',
  },
  border: {
    divider: '#CED0D4',
    subtle: '#E4E6EB',
    focus: '#1877F2',
  },
  reaction: {
    like: '#1877F2',
    love: '#F33E58',
    haha: '#F7B125',
    wow: '#F7B125',
    sad: '#F7B125',
    angry: '#E9710F',
  },
  semantic: {
    success: '#31A24C',
    warning: '#F7B125',
    danger: '#E41E3F',
    info: '#1877F2',
  },
} as const;

/** شبكة 8px — القيم النصفية مسموحة فقط عند 4 */
export const space = {
  none: 0,
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
  xxxl: 40,
  huge: 56,
} as const;

export const radius = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  pill: 999,
} as const;

export const size = {
  icon: 24,
  iconSm: 18,
  iconLg: 28,
  touchTarget: 44,
  avatarXs: 24,
  avatarSm: 32,
  avatarMd: 40,
  avatarLg: 56,
  avatarXl: 96,
  appBar: 56,
  tabBar: 56,
  hairline: Platform.OS === 'ios' ? 0.5 : 1,
} as const;

export const fonts = {
  ui: Platform.select({
    ios: 'System',
    android: 'sans-serif',
    default: 'System',
  }) as string,
  uiBold: Platform.select({
    ios: 'System',
    android: 'sans-serif-medium',
    default: 'System',
  }) as string,
} as const;

/** سلم الخطوط — lineHeight ≈ 1.45× للعربية (مساحة للتشكيل) */
export const type = {
  caption: { fontSize: 11, lineHeight: 14, fontFamily: fonts.ui },
  micro: { fontSize: 12, lineHeight: 16, fontFamily: fonts.ui },
  bodySm: { fontSize: 13, lineHeight: 19, fontFamily: fonts.ui },
  body: { fontSize: 15, lineHeight: 23, fontFamily: fonts.ui },
  bodyStrong: { fontSize: 15, lineHeight: 23, fontFamily: fonts.uiBold },
  title: { fontSize: 17, lineHeight: 25, fontFamily: fonts.uiBold },
  headline: { fontSize: 20, lineHeight: 28, fontFamily: fonts.uiBold },
  display: { fontSize: 28, lineHeight: 36, fontFamily: fonts.uiBold },
} as const;

/** ظل واحد للبطاقات — الفصل الأساسي يتم بالخلفية الرمادية */
export const shadow = {
  card: Platform.select({
    ios: {
      shadowColor: '#000',
      shadowOpacity: 0.06,
      shadowRadius: 8,
      shadowOffset: { width: 0, height: 2 },
    },
    android: { elevation: 2 },
    default: {},
  }),
  raised: Platform.select({
    ios: {
      shadowColor: '#000',
      shadowOpacity: 0.12,
      shadowRadius: 16,
      shadowOffset: { width: 0, height: 6 },
    },
    android: { elevation: 6 },
    default: {},
  }),
} as const;

export const motion = {
  instant: 80,
  fast: 140,
  normal: 220,
  slow: 320,
  skeleton: 1100,
} as const;

export const layout = {
  gutter: space.md,
  cardGap: space.xs,
  contentMaxWidth: 680,
} as const;

export const theme = { colors, space, radius, size, fonts, type, shadow, motion, layout };
export default theme;
