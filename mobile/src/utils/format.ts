import type { Post, ReactionKind } from '../types';

export const REACTION_ORDER: ReactionKind[] = ['like', 'love', 'haha', 'wow', 'sad', 'angry'];

/** ألوان التفاعلات (تُستعمل خلف الأيقونة المتجهية) */
export const REACTION_COLOR: Record<ReactionKind, string> = {
  like: '#1877F2',
  love: '#F33E58',
  haha: '#F7B125',
  wow: '#F7B125',
  sad: '#F7B125',
  angry: '#E9710F',
};

export const REACTION_LABEL: Record<ReactionKind, string> = {
  like: 'أعجبني',
  love: 'أحببته',
  haha: 'هاها',
  wow: 'واو',
  sad: 'حزين',
  angry: 'غاضب',
};

export const totalReactions = (post: Post): number =>
  REACTION_ORDER.reduce((sum, key) => sum + (post.reactions[key] ?? 0), 0);

/** أعلى 3 تفاعلات — تُستعمل في ملخص البطاقة */
export const topReactions = (post: Post, limit = 3): ReactionKind[] =>
  REACTION_ORDER.filter((k) => (post.reactions[k] ?? 0) > 0)
    .sort((a, b) => post.reactions[b] - post.reactions[a])
    .slice(0, limit);

export const formatCount = (n: number): string => {
  if (n >= 1000000) return `${(n / 1000000).toFixed(1)} م`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)} ألف`;
  return String(n);
};

export const privacyLabel = (privacy: Post['privacy']): string =>
  privacy === 'public' ? 'العامة' : privacy === 'friends' ? 'الأصدقاء' : 'أنا فقط';
