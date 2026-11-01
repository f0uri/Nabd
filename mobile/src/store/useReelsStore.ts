import { create } from 'zustand';
import { dataApi } from '../api';
import type { LocalFile } from '../api/client';
import type { Post, ReactionKind } from '../types';

/**
 * حالة الريلز (Zustand) — نفس منطق الفيد: تحميل متفائل للتفاعلات + تراجع.
 * التحميل المسبق: كنحفظو 2 فيديو جايين باش السكرول يبقى سلس.
 */
interface ReelsState {
  reels: Post[];
  loading: boolean;
  refreshing: boolean;
  loadingMore: boolean;
  nextCursor: string | null;
  error: string | null;
  /** الفهرس النشيط (اللي كيتقرا دابا) */
  activeIndex: number;
  /** الصوت مسدود؟ (افتراضياً نعم — بحال إنستغرام) */
  muted: boolean;
  publishing: boolean;
  uploadProgress: string | null;
  /** حالة الجلب التلقائي من إنستغرام (بلا تدخل) */
  ingest: {
    enabled: boolean;
    running: boolean;
    lastRunAt: string | null;
    totalsImported: number;
    queueImported: number;
    queuePending: number;
  } | null;
  /** آخر مرة تحمّلو الريلز */
  lastLoadedAt: number;

  load: (options?: { force?: boolean }) => Promise<void>;
  refresh: () => Promise<void>;
  /** كيجيب حالة الجلب التلقائي، وإلا كانت جديد ريلز كيحدّث القائمة بوحدو */
  loadIngestStatus: () => Promise<void>;
  loadMore: () => Promise<void>;
  setActive: (index: number) => void;
  toggleMuted: () => void;
  toggleReaction: (postId: string, reaction?: ReactionKind | null) => Promise<void>;
  addComment: (postId: string, text: string) => Promise<void>;
  deleteReel: (postId: string) => Promise<boolean>;
  /** نشر ريل: فيديو من المعرض + وصف */
  publishReel: (input: { uri: string; fileName?: string | null; mimeType?: string | null; width?: number; height?: number; text: string }) => Promise<boolean>;
  setActiveComment: (postId: string | null) => void;
  activeCommentId: string | null;
  reset: () => void;
}

const patchReel = (reels: Post[], postId: string, patch: Partial<Post>): Post[] =>
  reels.map((reel) => (reel.id === postId ? { ...reel, ...patch } : reel));

export const useReelsStore = create<ReelsState>((set, get) => ({
  reels: [],
  loading: true,
  refreshing: false,
  loadingMore: false,
  nextCursor: null,
  error: null,
  activeIndex: 0,
  muted: true,
  publishing: false,
  uploadProgress: null,
  activeCommentId: null,
  ingest: null,
  lastLoadedAt: 0,

  load: async (options) => {
    const { reels, loading, lastLoadedAt } = get();
    // كنعاودو التحميل إلا كانت البيانات قديمة (>دقيقتين) — الجلب التلقائي كيزيد ريلز
    const stale = Date.now() - lastLoadedAt > 2 * 60_000;
    if (!loading && !options?.force && reels.length > 0 && !stale) return;
    if (reels.length === 0) set({ loading: true });
    set({ error: null });
    try {
      const page = await dataApi.reels(null);
      set({ reels: page.posts, nextCursor: page.nextCursor, loading: false, lastLoadedAt: Date.now() });
    } catch {
      set({ loading: false, error: 'ما قدرناش نحمّلو الريلز' });
    }
  },

  /* ================= الجلب التلقائي (بلا تدخل من المستخدم) ================= */

  loadIngestStatus: async () => {
    try {
      const state = await dataApi.reelsIngestStatus();
      const previous = get().ingest;
      set({
        ingest: {
          enabled: state.enabled,
          running: state.running,
          lastRunAt: state.lastRunAt,
          totalsImported: state.totalsImported,
          queueImported: state.queueImported,
          queuePending: state.queuePending,
        },
      });
      // إلا جاب الخادم ريلز جديدة → كنحدّثو القائمة بوحدنا
      if (previous && state.queueImported > previous.queueImported) {
        await get().refresh();
      }
    } catch {
      /* الجلب التلقائي ماشي حرج */
    }
  },

  refresh: async () => {
    set({ refreshing: true });
    try {
      const page = await dataApi.reels(null);
      set({ reels: page.posts, nextCursor: page.nextCursor, refreshing: false });
    } catch {
      set({ refreshing: false, error: 'ما قدرناش نحدّثو الريلز' });
    }
  },

  loadMore: async () => {
    const { loadingMore, nextCursor, reels } = get();
    if (loadingMore || !nextCursor) return;
    set({ loadingMore: true });
    try {
      const page = await dataApi.reels(nextCursor);
      const seen = new Set(reels.map((r) => r.id));
      set({
        reels: [...reels, ...page.posts.filter((p) => !seen.has(p.id))],
        nextCursor: page.nextCursor,
        loadingMore: false,
      });
    } catch {
      set({ loadingMore: false });
    }
  },

  setActive: (index) => {
    const { reels } = get();
    const reel = reels[index];
    set({ activeIndex: index });
    // تسجيل المشاهدة (مرة واحدة لكل مستخدم فالخادم)
    if (reel) dataApi.viewReel(reel.id).then((views) => set({ reels: patchReel(get().reels, reel.id, { views }) })).catch(() => {});
    // تحميل المزيد قبل ما يوصل للآخر
    if (index >= reels.length - 2) get().loadMore();
  },

  toggleMuted: () => set({ muted: !get().muted }),

  toggleReaction: async (postId, reaction = 'like') => {
    const reel = get().reels.find((r) => r.id === postId);
    if (!reel) return;

    const previousReaction = reel.myReaction;
    const nextReaction: ReactionKind | null =
      reaction === null ? null : previousReaction === reaction ? null : reaction;

    const nextCounts = { ...reel.reactions };
    if (previousReaction) nextCounts[previousReaction] = Math.max(0, (nextCounts[previousReaction] ?? 0) - 1);
    if (nextReaction) nextCounts[nextReaction] = (nextCounts[nextReaction] ?? 0) + 1;

    set({ reels: patchReel(get().reels, postId, { myReaction: nextReaction, reactions: nextCounts }) });

    try {
      await dataApi.react(postId, nextReaction);
    } catch {
      set({ reels: patchReel(get().reels, postId, { myReaction: previousReaction, reactions: reel.reactions }) });
    }
  },

  addComment: async (postId, text) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    try {
      const comment = await dataApi.addComment(postId, trimmed);
      if (!comment) return;
      set({
        reels: get().reels.map((reel) =>
          reel.id === postId ? { ...reel, comments: [...reel.comments, comment] } : reel,
        ),
      });
    } catch {
      /* الشاشة كتورّي Toast */
    }
  },

  deleteReel: async (postId) => {
    const previous = get().reels;
    set({ reels: previous.filter((r) => r.id !== postId) });
    try {
      await dataApi.deletePost(postId);
      return true;
    } catch {
      set({ reels: previous });
      return false;
    }
  },

  publishReel: async ({ uri, fileName, mimeType, width, height, text }) => {
    set({ publishing: true, uploadProgress: 'كنرفعو الفيديو…' });
    const file: LocalFile = {
      uri,
      name: fileName ?? `reel_${Date.now()}.mp4`,
      type: mimeType ?? 'video/mp4',
    };

    try {
      const post = await dataApi.createPost({
        text: text.trim(),
        privacy: 'public',
        file,
        aspectRatio: width && height ? width / height : 9 / 16,
      });
      set({ reels: [post, ...get().reels], activeIndex: 0, publishing: false, uploadProgress: null });
      return true;
    } catch (error) {
      const message = (error as { code?: string; message?: string })?.code ?? '';
      set({
        publishing: false,
        uploadProgress:
          message === 'VIDEO_TOO_LARGE'
            ? 'الفيديو كبير بزاف (الحد 18MB) — قصّرو ولا اختار جودة أقل'
            : 'ما قدرناش نرفعو الفيديو',
      });
      return false;
    }
  },

  setActiveComment: (postId) => set({ activeCommentId: postId }),

  reset: () =>
    set({
      ingest: null,
      lastLoadedAt: 0,
      reels: [],
      loading: true,
      refreshing: false,
      loadingMore: false,
      nextCursor: null,
      error: null,
      activeIndex: 0,
      publishing: false,
      uploadProgress: null,
      activeCommentId: null,
    }),
}));

export default useReelsStore;
