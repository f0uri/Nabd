import type {
  AppNotification,
  Author,
  AuthPayload,
  Conversation,
  Message,
  MessagePage,
  Post,
  ReactionKind,
  Translation,
} from '../types';

/**
 * بيانات محلية احتياطية (mock) — كتخدم غير إذا الخادم ماشي متاح.
 * ⚠️ ما كاينش أي حساب أو شخصية تجريبية: القائمة كتبدا خاوية،
 * وكل حساب حقيقي كيتصنع عبر التسجيل (register) على الخادم.
 */

/** مستخدم محلي محايد (ما كيمثلش شخصية) — كيبان غير فحالة offline */
export const CURRENT_USER: Author = {
  id: 'u_local',
  name: 'أنت',
  color: '#1877F2',
  initial: 'أ',
  avatarUrl: null,
  verified: false,
  bio: '',
  email: null,
  friendsCount: 0,
  postsCount: 0,
  isMe: true,
};

export const AUTHORS: Record<string, Author> = { me: CURRENT_USER };

const emptyReactions = (): Record<ReactionKind, number> => ({
  like: 0,
  love: 0,
  haha: 0,
  wow: 0,
  sad: 0,
  angry: 0,
});

let db: Post[] = [];
const users: Author[] = [];
const notifications: AppNotification[] = [];

const simulate = (ms = 220) => new Promise((r) => setTimeout(r, ms));

export const resetMockDb = () => {
  db = [];
  users.length = 0;
  notifications.length = 0;
};

export const mockApi = {
  async fetchFeed(cursor: string | null = null, limit = 5) {
    await simulate();
    const start = cursor ? Number(cursor) : 0;
    const slice = db.slice(start, start + limit);
    return {
      posts: slice.map((p) => ({ ...p })),
      nextCursor: start + limit < db.length ? String(start + limit) : null,
    };
  },

  async userPosts(userId: string): Promise<Post[]> {
    await simulate();
    return db.filter((p) => p.author.id === userId);
  },

  async react(postId: string, reaction: ReactionKind | null) {
    await simulate(120);
    const post = db.find((p) => p.id === postId);
    if (!post) throw new Error('POST_NOT_FOUND');
    if (post.myReaction) post.reactions[post.myReaction] = Math.max(0, post.reactions[post.myReaction] - 1);
    post.myReaction = reaction;
    if (reaction) post.reactions[reaction] += 1;
  },

  async addComment(postId: string, text: string) {
    await simulate(120);
    const post = db.find((p) => p.id === postId);
    if (!post) throw new Error('POST_NOT_FOUND');
    post.comments = [
      ...post.comments,
      {
        id: `c_${Date.now()}`,
        author: CURRENT_USER,
        text,
        createdAt: 'الآن',
        likes: 0,
        likedByMe: false,
      },
    ];
    post.commentsCount += 1;
    return { ok: true };
  },

  async likeComment(postId: string, commentId: string) {
    await simulate(100);
    const post = db.find((p) => p.id === postId);
    const comment = post?.comments.find((c) => c.id === commentId);
    if (!post || !comment) throw new Error('COMMENT_NOT_FOUND');
    comment.likedByMe = !comment.likedByMe;
    comment.likes = Math.max(0, comment.likes + (comment.likedByMe ? 1 : -1));
    return { ok: true };
  },

  async deleteComment(postId: string, commentId: string) {
    await simulate(100);
    const post = db.find((p) => p.id === postId);
    if (!post) throw new Error('POST_NOT_FOUND');
    post.comments = post.comments.filter((c) => c.id !== commentId);
    post.commentsCount = Math.max(0, post.commentsCount - 1);
    return { ok: true };
  },

  async createPost(input: {
    text: string;
    privacy?: 'public' | 'friends' | 'private';
    fileUri?: string;
    aspectRatio?: number;
  }): Promise<Post> {
    await simulate();
    const post: Post = {
      id: `p_${Date.now()}`,
      author: CURRENT_USER,
      createdAt: 'الآن',
      privacy: input.privacy ?? 'public',
      text: input.text,
      media: input.fileUri
        ? {
            id: `m_${Date.now()}`,
            uri: input.fileUri,
            width: 0,
            height: 0,
            aspectRatio: input.aspectRatio ?? 4 / 3,
            alt: input.text.slice(0, 60),
          }
        : undefined,
      reactions: { ...emptyReactions() },
      myReaction: null,
      comments: [],
      commentsCount: 0,
      shares: 0,
    };
    db = [post, ...db];
    return { ...post };
  },

  async login(email: string, _password: string): Promise<AuthPayload> {
    await simulate();
    return { token: 'mock-token', user: { ...CURRENT_USER, email } };
  },

  async register(name: string, email: string, _password: string): Promise<AuthPayload> {
    await simulate();
    const user: Author = { ...CURRENT_USER, name, email, initial: name.trim().charAt(0) || 'أ' };
    return { token: 'mock-token', user };
  },

  async me(): Promise<Author> {
    await simulate(80);
    return CURRENT_USER;
  },

  async updateProfile(_input: { name?: string; bio?: string }): Promise<Author> {
    throw new Error('PROFILE_REQUIRES_SERVER');
  },

  async users(search = ''): Promise<Author[]> {
    await simulate(120);
    const q = search.trim();
    return users.filter((u) => (q ? u.name.includes(q) : true));
  },

  async friends(): Promise<Author[]> {
    await simulate(120);
    return [];
  },

  async suggestions(): Promise<Author[]> {
    await simulate(120);
    return users.slice(0, 6);
  },

  async follow(userId: string): Promise<Author | null> {
    await simulate(120);
    return users.find((u) => u.id === userId) ?? null;
  },

  async unfollow(userId: string): Promise<Author | null> {
    await simulate(120);
    return users.find((u) => u.id === userId) ?? null;
  },

  /* ================= الدردشة (محلي: فارغة) ================= */

  async conversations(_search = ''): Promise<Conversation[]> {
    await simulate(120);
    return [];
  },

  async messages(_conversationId: string): Promise<MessagePage> {
    await simulate(120);
    return { messages: [], nextCursor: null };
  },

  async startConversation(userId: string): Promise<Conversation> {
    await simulate(120);
    return {
      id: `cv_local_${userId}`,
      createdAt: new Date().toISOString(),
      lastAt: new Date().toISOString(),
      other: { ...CURRENT_USER, id: userId },
      lastMessage: null,
      unreadCount: 0,
    };
  },

  async reels(_cursor: string | null = null): Promise<{ posts: Post[]; nextCursor: string | null }> {
    await simulate(120);
    return { posts: [], nextCursor: null };
  },

  async viewReel(_postId: string): Promise<number> {
    return 0;
  },

  async deletePost(_postId: string): Promise<boolean> {
    await simulate(120);
    return true;
  },

  /** فالمحلي ما كايناش منشورات — البحث كيرجّع خاوي (بلا شخصيات تجريبية) */
  async searchPosts(_search: string): Promise<Post[]> {
    await simulate(120);
    return [];
  },

  async sendMessage(
    conversationId: string,
    text: string,
    replyToId: string | null = null,
    _file?: { uri: string; name: string; type: string },
  ): Promise<Message> {
    await simulate(120);
    return {
      id: `msg_${Date.now()}`,
      conversationId,
      senderId: CURRENT_USER.id,
      text,
      replyTo: replyToId ? null : null,
      edited: false,
      deleted: false,
      reactions: [],
      createdAt: new Date().toISOString(),
      read: false,
    };
  },

  async editMessage(messageId: string, text: string): Promise<Message> {
    await simulate(120);
    return {
      id: messageId,
      conversationId: '',
      senderId: CURRENT_USER.id,
      text,
      createdAt: new Date().toISOString(),
      read: false,
      edited: true,
      deleted: false,
      reactions: [],
    };
  },

  async deleteMessage(messageId: string): Promise<Message> {
    await simulate(120);
    return {
      id: messageId,
      conversationId: '',
      senderId: CURRENT_USER.id,
      text: '',
      createdAt: new Date().toISOString(),
      read: false,
      edited: false,
      deleted: true,
      reactions: [],
    };
  },

  async reactToMessage(messageId: string, reaction: ReactionKind | null): Promise<Message> {
    await simulate(80);
    return {
      id: messageId,
      conversationId: '',
      senderId: CURRENT_USER.id,
      text: '',
      createdAt: new Date().toISOString(),
      read: false,
      reactions: reaction ? [{ kind: reaction, count: 1, mine: true }] : [],
    };
  },

  async translateMessage(_messageId: string, to = 'fr'): Promise<Translation> {
    await simulate(200);
    return { text: 'الترجمة كتخدم غير ملي كيكون الخادم متصل.', from: 'ar', to, provider: 'mock', cached: false };
  },

  async markConversationRead(_conversationId: string): Promise<boolean> {
    await simulate(60);
    return true;
  },

  async unreadMessagesCount(): Promise<number> {
    await simulate(60);
    return 0;
  },

  async fetchNotifications(): Promise<AppNotification[]> {
    await simulate(120);
    return [...notifications];
  },

  async markNotificationsRead(): Promise<boolean> {
    await simulate(80);
    notifications.forEach((n) => (n.read = true));
    return true;
  },
};

export default mockApi;
