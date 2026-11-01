import { create } from 'zustand';
import { dataApi, getSource, resetSource, setOffline, type ResolvedSource } from '../api';
import type { LocalFile } from '../api/client';
import { CURRENT_USER } from '../api/mockApi';
import { useAuthStore, authErrorMessage } from './useAuthStore';
import type { Comment, Post, Privacy, ReactionKind } from '../types';

/** المؤلف الحالي: المستخدم المسجّل، وإلا المستخدم التجريبي */
const currentAuthor = (): Post['author'] => useAuthStore.getState().user ?? CURRENT_USER;

interface ToastState {
  id: number;
  message: string;
  tone: 'error' | 'info' | 'success';
}

interface FeedState {
  posts: Post[];
  /** أول تحميل فقط → نعرض Skeleton */
  loading: boolean;
  refreshing: boolean;
  loadingMore: boolean;
  nextCursor: string | null;
  error: string | null;
  toast: ToastState | null;
  offline: boolean;
  /** من أين تأتي البيانات فعلياً: خادم GraphQL أو بيانات محلية */
  source: ResolvedSource | null;
  /** جارٍ رفع منشور جديد */
  publishing: boolean;
  /** عدد الإشعارات غير المقروءة (للشارة ف الشريط العلوي) */
  unreadNotifications: number;
  loadUnreadNotifications: () => Promise<void>;

  load: (options?: { force?: boolean }) => Promise<void>;
  refresh: () => Promise<void>;
  loadMore: () => Promise<void>;
  /** الإعجاب المتفائل: تحديث فوري + تراجع تلقائي عند الفشل */
  /** reaction: null كتحيّد التفاعل، بلا قيمة = «أعجبني» */
  toggleReaction: (postId: string, reaction?: ReactionKind | null) => Promise<void>;
  /** حذف منشور (متفائل + تراجع) */
  deletePost: (postId: string) => Promise<boolean>;
  /** إخفاء منشور من الفيد (محلي فقط) */
  hidePost: (postId: string) => void;
  addComment: (postId: string, text: string) => Promise<void>;
  deleteComment: (postId: string, commentId: string) => Promise<void>;
  toggleCommentLike: (postId: string, commentId: string) => Promise<void>;
  /** إضافة منشورات جاية من شاشة أخرى (مثلاً منشورات الملف الشخصي) لنفس المصدر الموحد */
  ingestPosts: (incoming: Post[]) => void;
  /** نشر منشور جديد (مع رفع الصورة إلى الخادم/تلغرام) */
  createPost: (input: { text: string; privacy?: Privacy; file?: LocalFile; aspectRatio?: number }) => Promise<boolean>;
  toggleOffline: () => void;
  /** إعادة فحص اتصال الخادم (مفيد بعد تشغيله) */
  reconnect: () => Promise<void>;
  showToast: (message: string, tone?: ToastState['tone']) => void;
  dismissToast: () => void;
  reset: () => void;
}

let toastSeq = 0;

const patchPost = (posts: Post[], postId: string, patch: Partial<Post>): Post[] =>
  posts.map((p) => (p.id === postId ? { ...p, ...patch } : p));

/** تبديل تعليق بنسخته الجديدة داخل نفس المنشور */
const replaceComment = (comments: Comment[], next: Comment): Comment[] =>
  comments.map((c) => (c.id === next.id ? next : c));

const errorCode = (error: unknown): string => (error as { code?: string })?.code ?? 'UNKNOWN';

export const useFeedStore = create<FeedState>((set, get) => ({
  posts: [],
  loading: true,
  refreshing: false,
  loadingMore: false,
  nextCursor: null,
  error: null,
  toast: null,
  offline: false,
  source: getSource(),
  publishing: false,
  unreadNotifications: 0,

  /** عدّاد شارة الإشعارات: قراءة خفيفة بلا ما نحفظو القائمة */
  loadUnreadNotifications: async () => {
    try {
      const { unreadNotificationsCount } = await dataApi.notifications();
      set({ unreadNotifications: unreadNotificationsCount });
    } catch {
      /* تجاهل — غير شارة */
    }
  },

  showToast: (message, tone = 'error') => set({ toast: { id: ++toastSeq, message, tone } }),
  dismissToast: () => set({ toast: null }),

  load: async (options) => {
    const { posts, loading } = get();
    if (!loading && !options?.force && posts.length > 0) return;

    set({ loading: true, error: null });
    try {
      const page = await dataApi.fetchFeed(null);
      set({
        posts: page.posts,
        nextCursor: page.nextCursor,
        loading: false,
        error: null,
        source: getSource(),
      });
    } catch (error) {
      const offline = errorCode(error) === 'OFFLINE';
      set({
        loading: false,
        source: getSource(),
        error: offline
          ? 'لا يوجد اتصال. تحقق من الشبكة وحاول مرة أخرى.'
          : errorCode(error).startsWith('TELEGRAM_') ? authErrorMessage(error) : 'ما قدرناش نجيبو المنشورات. تحقق من الاتصال أو من تشغيل الخادم.',
      });
    }
  },

  refresh: async () => {
    set({ refreshing: true, error: null });
    try {
      resetSource();
      const page = await dataApi.fetchFeed(null);
      set({ posts: page.posts, nextCursor: page.nextCursor, refreshing: false, source: getSource() });
    } catch {
      set({ refreshing: false, error: 'فشل التحديث — المعروض هو آخر نسخة محفوظة.' });
    }
  },

  loadMore: async () => {
    const { nextCursor, loadingMore, posts } = get();
    if (loadingMore || !nextCursor) return;
    set({ loadingMore: true });
    try {
      const page = await dataApi.fetchFeed(nextCursor);
      const known = new Set(posts.map((p) => p.id));
      set({
        posts: [...posts, ...page.posts.filter((p) => !known.has(p.id))],
        nextCursor: page.nextCursor,
        loadingMore: false,
      });
    } catch {
      set({ loadingMore: false });
      get().showToast('ما قدرناش نحمّلو المزيد', 'error');
    }
  },

  /**
   * ===== Optimistic UI =====
   * 1. نطبق التغيير على الحالة المحلية فوراً (قبل أي طلب شبكة).
   * 2. نرسل الطلب في الخلفية (إلى الخادم أو للبيانات المحلية).
   * 3. إذا فشل → نرجع للحالة السابقة + رسالة قصيرة.
   */
  toggleReaction: async (postId, reaction = 'like') => {
    const post = get().posts.find((p) => p.id === postId);
    if (!post) return;

    const previousReaction = post.myReaction;
    // null = حذف التفاعل · نفس التفاعل = حذف · تفاعل آخور = تبديل
    const nextReaction: ReactionKind | null =
      reaction === null ? null : previousReaction === reaction ? null : reaction;

    const nextCounts = { ...post.reactions };
    if (previousReaction) nextCounts[previousReaction] = Math.max(0, (nextCounts[previousReaction] ?? 0) - 1);
    if (nextReaction) nextCounts[nextReaction] = (nextCounts[nextReaction] ?? 0) + 1;

    // (1) تحديث فوري
    set({ posts: patchPost(get().posts, postId, { myReaction: nextReaction, reactions: nextCounts }) });

    try {
      await dataApi.react(postId, nextReaction);
    } catch (error) {
      // (3) تراجع تلقائي
      set({
        posts: patchPost(get().posts, postId, {
          myReaction: previousReaction,
          reactions: { ...post.reactions },
        }),
      });
      const code = errorCode(error);
      get().showToast(
        code === 'OFFLINE'
          ? 'لا يوجد اتصال — تم التراجع عن التفاعل'
          : code === 'TIMEOUT'
            ? 'الخادم بطيء — تم التراجع'
            : 'ما قدرناش نحفظ تفاعلك',
        'error',
      );
    }
  },

  hidePost: (postId) => {
    set({ posts: get().posts.filter((p) => p.id !== postId) });
    get().showToast('خبّيناه من الفيد', 'info');
  },

  deletePost: async (postId) => {
    const previous = get().posts;
    set({ posts: previous.filter((p) => p.id !== postId) });
    try {
      await dataApi.deletePost(postId);
      get().showToast('تحيد المنشور', 'success');
      return true;
    } catch {
      set({ posts: previous });
      get().showToast('ما قدرناش نحيدو المنشور', 'error');
      return false;
    }
  },

  addComment: async (postId, text) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    const post = get().posts.find((p) => p.id === postId);
    if (!post) return;

    const tempId = `tmp_${Date.now()}`;
    const optimisticComment: Comment & { author: Comment['author'] } = {
      id: tempId,
      author: { ...currentAuthor(), isMe: true },
      text: trimmed,
      createdAt: 'الآن',
      likes: 0,
      likedByMe: false,
    };

    set({
      posts: patchPost(get().posts, postId, {
        comments: [...post.comments, optimisticComment],
        commentsCount: post.commentsCount + 1,
      }),
    });

    try {
      const saved = await dataApi.addComment(postId, trimmed);
      if (saved) {
        // استبدال التعليق المؤقت بالنسخة الحقيقية من الخادم (id دائم)
        const current = get().posts.find((p) => p.id === postId);
        if (!current) return;
        set({
          posts: patchPost(get().posts, postId, {
            comments: current.comments.map((c) => (c.id === tempId ? saved : c)),
          }),
        });
      }
    } catch {
      set({
        posts: patchPost(get().posts, postId, {
          comments: post.comments,
          commentsCount: post.commentsCount,
        }),
      });
      get().showToast('ما قدرناش ننشرو التعليق', 'error');
    }
  },

  /** حذف تعليق — التغيير ينعكس فوراً في كل الشاشات (نفس المصدر) */
  deleteComment: async (postId, commentId) => {
    const post = get().posts.find((p) => p.id === postId);
    if (!post) return;
    const before = post.comments;
    const beforeCount = post.commentsCount;

    set({
      posts: patchPost(get().posts, postId, {
        comments: before.filter((c) => c.id !== commentId),
        commentsCount: Math.max(0, beforeCount - 1),
      }),
    });

    try {
      await dataApi.deleteComment(postId, commentId);
    } catch {
      set({ posts: patchPost(get().posts, postId, { comments: before, commentsCount: beforeCount }) });
      get().showToast('ما قدرناش نحذفو التعليق', 'error');
    }
  },

  ingestPosts: (incoming) => {
    if (!incoming.length) return;
    const existing = new Map(get().posts.map((p) => [p.id, p]));
    incoming.forEach((post) => {
      if (!existing.has(post.id)) existing.set(post.id, post);
    });
    set({ posts: [...existing.values()] });
  },

  /** إعجاب على تعليق — Optimistic + تراجع عند الفشل */
  toggleCommentLike: async (postId, commentId) => {
    const post = get().posts.find((p) => p.id === postId);
    const comment = post?.comments.find((c) => c.id === commentId);
    if (!post || !comment) return;

    const previous = { likes: comment.likes, likedByMe: comment.likedByMe };
    const next: Comment = {
      ...comment,
      likedByMe: !comment.likedByMe,
      likes: Math.max(0, comment.likes + (comment.likedByMe ? -1 : 1)),
    };
    set({ posts: patchPost(get().posts, postId, { comments: replaceComment(post.comments, next) }) });

    try {
      const saved = await dataApi.likeComment(postId, commentId);
      if (saved) {
        const cur = get().posts.find((p) => p.id === postId);
        if (cur) set({ posts: patchPost(get().posts, postId, { comments: replaceComment(cur.comments, saved) }) });
      }
    } catch {
      const cur = get().posts.find((p) => p.id === postId);
      if (cur) {
        set({
          posts: patchPost(get().posts, postId, {
            comments: replaceComment(cur.comments, { ...comment, ...previous }),
          }),
        });
      }
      get().showToast('ما قدرناش نسجلو الإعجاب', 'error');
    }
  },

  createPost: async (input) => {
    const { text, privacy = 'public', file, aspectRatio } = input;
    if (!text.trim() && !file) return false;

    const tempId = `tmp_post_${Date.now()}`;
    const optimistic: Post = {
      id: tempId,
      author: CURRENT_USER,
      createdAt: 'الآن',
      privacy,
      text,
      media: file
        ? {
            id: `tmp_media_${Date.now()}`,
            uri: file.uri,
            width: 0,
            height: 0,
            aspectRatio: aspectRatio ?? 4 / 3,
            alt: text.slice(0, 60),
          }
        : undefined,
      reactions: { like: 0, love: 0, haha: 0, wow: 0, sad: 0, angry: 0 },
      myReaction: null,
      comments: [],
      commentsCount: 0,
      shares: 0,
    };

    // Optimistic: المنشور كيبان فوراً فالقائمة
    set({ publishing: true, posts: [optimistic, ...get().posts] });

    try {
      const saved = await dataApi.createPost({ text, privacy, file, aspectRatio });
      set({
        publishing: false,
        posts: get().posts.map((p) => (p.id === tempId ? saved : p)),
      });
      get().showToast('تم نشر المنشور', 'success');
      return true;
    } catch (error) {
      set({ publishing: false, posts: get().posts.filter((p) => p.id !== tempId) });
      get().showToast(errorCode(error).startsWith('TELEGRAM_') ? authErrorMessage(error) : 'ما قدرناش ننشرو المنشور', 'error');
      return false;
    }
  },

  toggleOffline: () => {
    const offline = !get().offline;
    setOffline(offline);
    set({ offline });
    get().showToast(offline ? 'تم تفعيل وضع «بلا اتصال»' : 'رجع الاتصال', offline ? 'info' : 'success');
  },

  reconnect: async () => {
    resetSource();
    get().showToast('كنحاول الاتصال من جديد…', 'info');
    await get().load({ force: true });
    const source = getSource();
    set({ source });
    if (source === 'graphql') get().showToast('متصل بالخادم', 'success');
    else if (source === 'telegram') get().showToast(get().error ? 'تعذر تحديث نسخة تلغرام؛ ما كاينش تحويل لبيانات محلية' : 'تم تحديث نسخة تلغرام', get().error ? 'error' : 'success');
    else get().showToast('الخادم ما جاوبش — كنستعملو البيانات المحلية', 'error');
  },

  reset: () => {
    setOffline(false);
    resetSource();
    set({
      posts: [],
      loading: true,
      error: null,
      nextCursor: null,
      offline: false,
      toast: null,
      source: getSource(),
      publishing: false,
      unreadNotifications: 0,
    });
  },
}));
