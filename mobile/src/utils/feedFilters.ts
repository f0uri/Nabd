import type { Post } from '../types';

export type FeedFilter = 'all' | 'friends' | 'photos' | 'videos';
export const FEED_FILTERS: ReadonlyArray<{ key: FeedFilter; label: string }> = [
  { key: 'all', label: 'الكل' },
  { key: 'friends', label: 'الأصدقاء' },
  { key: 'photos', label: 'الصور' },
  { key: 'videos', label: 'الفيديو' },
];

/** A view of loaded posts, never a write/reorder/reset of the shared feed.
 * Legacy image records have no kind; absent media is not a photo.
 */
export function filterFeed(posts: Post[], filter: FeedFilter): Post[] {
  switch (filter) {
    case 'friends': return posts.filter((post) => post.author.isFriend === true);
    case 'photos': return posts.filter((post) => post.media && post.media.kind !== 'video');
    case 'videos': return posts.filter((post) => post.media?.kind === 'video');
    default: return posts;
  }
}
