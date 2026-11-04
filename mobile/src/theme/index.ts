/** Nabd Midnight: independently implemented RN styling inspired by YTXTube.
 * Runtime tokens for the mobile app; historical HTML prototypes are separate.
 * Native blur is applied only by GlassDock, not implied by alpha-filled cards.
 */
import { Platform } from 'react-native';

export const colors = {
  brand: { primary: '#D90E3B', dark: '#FF91AA', pressed: '#AD0B30', soft: '#34131D' },
  bg: {
    canvas: '#08090B', surface: '#141519', overlay: 'rgba(0,0,0,0.72)',
    skeletonBase: '#23252B', skeletonHighlight: '#34363D',
  },
  text: { primary: '#FAFAFC', secondary: '#B5B6C0', tertiary: '#989BA8', inverse: '#FFFFFF', link: '#FF91AA' },
  border: { divider: '#303139', subtle: '#292B32', focus: '#FF91AA' },
  reaction: { like: '#D90E3B', love: '#CF1947', haha: '#F7B125', wow: '#F7B125', sad: '#F7B125', angry: '#E9710F' },
  semantic: { success: '#71D9A3', warning: '#F4C970', danger: '#FF8395', info: '#A8C7FA' },
  statusSurface: { danger: '#34151E', warning: '#302619', info: '#1D2638' },
} as const;

/** Filled panels are deliberately not blurred; the native dock is the exception. */
export const glass = {
  surface: '#141519', strong: '#1D1F24', border: '#34363E', stroke: '#777B8A',
  selected: '#34353D', successSurface: '#152C22', successText: '#90E7B8', successBorder: '#3C6D52',
} as const;

export const control = {
  surface: '#24262C', border: '#777B8A', icon: '#ECECF2',
  selected: '#282A30', selectedIcon: '#FAFAFC',
} as const;

/** Dark ink on amber/orange badges; bright ink on the dark feed. */
export const reactionInk = {
  like: '#FF91AA', love: '#FF91AA', haha: '#F7B125', wow: '#F7B125', sad: '#F7B125', angry: '#FFAC69',
} as const;

export const reactionBadgeInk = { like: '#FFFFFF', love: '#FFFFFF', haha: '#714700', wow: '#714700', sad: '#714700', angry: '#592600' } as const;

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
  sm: 12,
  md: 20,
  lg: 28,
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
  appBar: 64,
  tabBar: 72,
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

export const theme = {
  glass,
  control,
  reactionInk,
  reactionBadgeInk,
  colors,
  space,
  radius,
  size,
  fonts,
  type,
  shadow,
  motion,
  layout,
};
export default theme;
