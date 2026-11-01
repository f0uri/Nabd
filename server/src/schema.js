import { createPubSub, createSchema } from 'graphql-yoga';
import { GraphQLError } from 'graphql';
import { randomUUID } from 'node:crypto';
import { hashPassword, signToken, verifyPassword } from './auth.js';
import {
  addComment as dbAddComment,
  createPost as dbCreatePost,
  createUser as dbCreateUser,
  deleteComment as dbDeleteComment,
  deletePost as dbDeletePost,
  findPostBySourceUrl,
  findUserByEmail,
  follow as dbFollow,
  friendsOf,
  getOrCreateConversation,
  getPost as dbGetPost,
  listConversations,
  listMessages,
  markConversationRead,
  sendMessage as dbSendMessage,
  editMessage as dbEditMessage,
  deleteMessage as dbDeleteMessage,
  reactToMessage as dbReactToMessage,
  messageTextForTranslation,
  conversationPartner,
  unreadMessagesCount as dbUnreadMessages,
  insertMedia,
  likeComment as dbLikeComment,
  listFeed,
  listUserPosts,
  searchPosts,
  listReels,
  addReelView,
  markNotificationsRead as dbMarkRead,
  notificationsFor,
  postsCountOf,
  publicUser,
  pushNotification,
  react as dbReact,
  searchUsers,
  setUserAvatar,
  suggestions as dbSuggestions,
  unfollow as dbUnfollow,
  unreadNotificationsCount,
  updateUserProfile,
  users,
} from './store.js';
import { storage, storageInfo, mirrorStorage } from './storage/index.js';
import { MAX_TELEGRAM_UPLOAD_BYTES } from './storage/telegram.js';
import { parseShortcode, fetchReelMeta, downloadReelVideo, instagramLimits } from './instagram.js';
import {
  ingestStatus,
  runIngestCycle,
  instagramAdminStatus,
  saveInstagramSessionFromInput,
  clearInstagramSession,
  probeInstagramSession,
} from './ingest.js';
import { translateText, defaultTarget } from './translate.js';
import { publish as publishRealtime } from './realtime.js';

export const pubsub = createPubSub();

const typeDefs = /* GraphQL */ `
  scalar File

  enum ReactionKind {
    like
    love
    haha
    wow
    sad
    angry
  }

  enum Privacy {
    public
    friends
    private
  }

  enum NotificationType {
    message
    like
    comment
    follow
    share
  }

  type Author {
    id: ID!
    name: String!
    username: String!
    initial: String!
    color: String!
    avatarUrl: String
    verified: Boolean!
    bio: String
    email: String
    friendsCount: Int!
    postsCount: Int!
    isFriend: Boolean!
    isMe: Boolean!
   """واش هاد المستخدم أدمن (كيشوف لوحة الأدمن)"""
    isAdmin: Boolean!
  }

  type AuthPayload {
    token: String!
    user: Author!
  }

  enum MediaKind {
    image
    video
  }

  type Media {
    id: ID!
    uri: String!
    width: Int
    height: Int
    aspectRatio: Float
    alt: String
    """image ولا video"""
    kind: MediaKind!
    mime: String
  }

  type ReactionCounts {
    like: Int!
    love: Int!
    haha: Int!
    wow: Int!
    sad: Int!
    angry: Int!
    total: Int!
  }

  type Comment {
    id: ID!
    author: Author!
    text: String!
    createdAt: String!
    likes: Int!
    likedByMe: Boolean!
  }

  type Post {
    id: ID!
    """عدد المشاهدات (للريلز)"""
    views: Int!
    """مصدر المنشور: nabd (داخلي) ولا instagram (ريل مستورد)"""
    source: String!
    """الرابط الأصلي فـ إنستغرام (إلا كان مستورد)"""
    sourceUrl: String
    """حساب صاحب الريل الأصلي فـ إنستغرام"""
    sourceAuthor: String
    author: Author!
    createdAt: String!
    privacy: Privacy!
    text: String!
    media: Media
    reactions: ReactionCounts!
    myReaction: ReactionKind
    comments: [Comment!]!
    commentsCount: Int!
    shares: Int!
  }

  """حالة الجلب التلقائي ديال ريلز إنستغرام"""
  type ReelsIngestStatus {
    enabled: Boolean!
    running: Boolean!
    intervalMinutes: Int!
    lastRunAt: String
    nextRunAt: String
    lastError: String
    queuePending: Int!
    queueImported: Int!
    queueFailed: Int!
    totalsImported: Int!
    """واش كاينة جلسة إنستغرام (كوكيز) — بلا ما نكشفو الكوكيز نفسها"""
    sessionConfigured: Boolean!
  }

  """قناة وحدة من قنوات الجلب (كتظهر للأدمن باش يعرف واش الجلسة خدامة)"""
  type InstagramChannel {
    name: String!
    ok: Boolean!
    found: Int!
    error: String
  }

  """حالة جلسة إنستغرام + الجلب التلقائي (لوحة الأدمن)"""
  type InstagramSessionStatus {
    configured: Boolean!
    """آخر 4 حروف ديال sessionid فقط — الكوكيز ما كتوصلش للتطبيق أبداً"""
    fingerprint: String
    savedFrom: String
    savedAt: String
    autoImport: Boolean!
    intervalMinutes: Int!
    keepMin: Int!
    reelsCount: Int!
    queuePending: Int!
    queueImported: Int!
    lastRunAt: String
    lastError: String
    channels: [InstagramChannel!]!
  }

  """نتيجة اختبار الجلسة (بعد ما كتحطها)"""
  type InstagramProbe {
    configured: Boolean!
    valid: Boolean!
    totalCodes: Int!
    checkedAt: String!
    channels: [InstagramChannel!]!
  }

  type FeedPage {
    posts: [Post!]!
    nextCursor: String
  }

  type Notification {
    id: ID!
    actor: Author!
    type: NotificationType!
    postId: ID
    text: String!
    createdAt: String!
    read: Boolean!
  }

  type MessageReaction {
    kind: ReactionKind!
    count: Int!
    mine: Boolean!
  }

  type Message {
    id: ID!
    conversationId: ID!
    senderId: ID!
    text: String!
    createdAt: String!
    read: Boolean!
    """تعدّل بعد الإرسال؟"""
    edited: Boolean!
    """محذوفة (tombstone — النص كيجي فارغ)"""
    deleted: Boolean!
    """الرسالة المردود عليها (null إلا ماشي رد)"""
    replyTo: Message
    """التفاعلات مجمّعة حسب النوع"""
    reactions: [MessageReaction!]!
    """صورة مرفقة فالرسالة"""
    media: Media
  }

  type Translation {
    text: String!
    from: String!
    to: String!
    provider: String!
    cached: Boolean!
  }

  type Conversation {
    id: ID!
    createdAt: String!
    lastAt: String!
    other: Author!
    lastMessage: Message
    unreadCount: Int!
  }

  type MessagePage {
    messages: [Message!]!
    nextCursor: String
  }

  type StorageInfo {
    provider: String!
    configured: Boolean!
    note: String
  }

  type Query {
    feed(cursor: String, limit: Int = 5): FeedPage!
    post(id: ID!): Post
    userPosts(userId: ID!, limit: Int = 20): [Post!]!
    """بحث فالمنشورات (باحترام الخصوصية)"""
    searchPosts(search: String!, limit: Int = 20): [Post!]!
    """الريلز (منشورات فيديو) — الأحدث أولاً"""
    reels(cursor: String, limit: Int = 6): FeedPage!
    """حالة الجلب التلقائي ديال ريلز إنستغرام"""
    reelsIngestStatus: ReelsIngestStatus!
    """لوحة الأدمن: حالة جلسة إنستغرام والجلب"""
    instagramSessionStatus: InstagramSessionStatus!
    me: Author
    user(id: ID!): Author
    users(search: String, limit: Int = 20): [Author!]!
    friends: [Author!]!
    suggestions(limit: Int = 10): [Author!]!
    notifications: [Notification!]!
    unreadNotificationsCount: Int!
    conversations(search: String, limit: Int = 50): [Conversation!]!
    messages(conversationId: ID!, cursor: String, limit: Int = 30): MessagePage!
    unreadMessagesCount: Int!
    storage: StorageInfo!
  }

  type Mutation {
    register(name: String!, email: String!, password: String!, file: File, username: String): AuthPayload!
    login(email: String!, password: String!): AuthPayload!
    updateProfile(name: String, bio: String, file: File, username: String): Author!
    follow(userId: ID!): Author!
    unfollow(userId: ID!): Author!
    react(postId: ID!, reaction: ReactionKind): Post!
    addComment(postId: ID!, text: String!): Comment!
    deleteComment(postId: ID!, commentId: ID!): Boolean!
    likeComment(postId: ID!, commentId: ID!): Comment!
    createPost(text: String!, privacy: Privacy = public, file: File, aspectRatio: Float): Post!
    """تسجيل مشاهدة ريل (واحدة لكل مستخدم) → العدد الجديد"""
    viewReel(postId: ID!): Int!
    """تشغيل دورة جلب تلقائي دابا (نفس اللي كيدير المؤقت) → عدد الريلز المستوردة"""
    runReelsIngest: Int!
    """
    الأدمن كيحط كوكيز إنستغرام من التطبيق (نص الكوكيز كامل ولا sessionid بوحدها).
    كتتحقق فوراً وكتبدا الجلب إلا كانت صالحة.
    """
    saveInstagramSession(cookies: String!): InstagramProbe!
    """الأدمن كيحيد جلسة إنستغرام"""
    clearInstagramSession: Boolean!
    """اختبار الجلسة الحالية بلا ما نبدّلو"""
    testInstagramSession: InstagramProbe!
    """
    استيراد ريل من إنستغرام (رابط عام) — كيتحمّل وكيتخزّن عندنا ونستعملوه فالريلز.
    إلا كان مستورد من قبل، كيرجّع نفس المنشور بلا ما يعاود يحمّلو.
    """
    importInstagramReel(url: String!): Post!
    """حذف منشورك (بلا رجعة)"""
    deletePost(postId: ID!): Boolean!
    markNotificationsRead: Boolean!
    startConversation(userId: ID!): Conversation!
    sendMessage(conversationId: ID!, text: String!, replyToId: ID, file: File): Message!
    editMessage(messageId: ID!, text: String!): Message!
    deleteMessage(messageId: ID!): Message!
    """تفاعل على رسالة — reaction: null كتحيّد التفاعل"""
    reactToMessage(messageId: ID!, reaction: ReactionKind): Message!
    """ترجمة رسالة — to اختياري (افتراضي: عربي↔فرنسي حسب لغة النص)"""
    translateMessage(messageId: ID!, to: String): Translation!
    markConversationRead(conversationId: ID!): Boolean!
  }

  type Subscription {
    postCreated: Post!
    postReacted(postId: ID!): Post!
    messageAdded(conversationId: ID!): Message!
    """أي تغيير على رسالة (تعديل · حذف · تفاعل)"""
    messageChanged(conversationId: ID!): Message!
  }
`;

/** كيرفع الملف للتخزين وكيرجع media id (S3/CDN فالمرحلة 4، دابا تلغرام أو قرص محلي) */
const saveUploadedFile = async (file, owner, { aspectRatio = 1, captionPrefix = 'صورة' } = {}) => {
  if (storageInfo.provider === 'telegram' && file.size > MAX_TELEGRAM_UPLOAD_BYTES) throw new Error('UPLOAD_TOO_LARGE');
  const buffer = Buffer.from(await file.arrayBuffer());
  const uploaded = await storage.upload({
    buffer,
    filename: file.name || `upload_${randomUUID()}`,
    mime: file.type || 'application/octet-stream',
    caption: `${owner?.name ?? ''} · ${captionPrefix}`.trim(),
  });
  const mediaId = `m_${randomUUID().slice(0, 8)}`;
  await insertMedia({
    id: mediaId,
    provider: uploaded.provider ?? null,
    fileId: uploaded.fileId ?? null,
    mime: file.type || 'application/octet-stream',
    size: uploaded.size ?? buffer.length,
    aspectRatio,
    alt: captionPrefix,
  });
  return mediaId;
};

const requireAdmin = (userId) => {
  if (!userId) throw new Error('UNAUTHENTICATED');
  const user = users.get(userId);
  if (!user) throw new Error('UNAUTHENTICATED');
  if (!user.isAdmin) throw new Error('FORBIDDEN');
  return user;
};

const mapChannels = (channels = []) =>
  channels.map((channel) => ({
    name: channel.name,
    ok: Boolean(channel.ok),
    found: Number(channel.found ?? 0),
    error: channel.error ?? null,
  }));

const requireUser = (context, userId) => {
  if (!userId) throw new Error('UNAUTHENTICATED');
  const user = users.get(userId);
  if (!user) throw new Error('UNAUTHENTICATED');
  return user;
};

/** يحوّل صف المنشور إلى شكل GraphQL حسب المستخدم الحالي */
const mapPost = (post) => ({
  ...post,
  author: publicUser(users.get(post.authorId)),
  commentsCount: post.comments.length,
});

const mapComment = (comment) => ({
  ...comment,
  likes: comment.likes ?? 0,
  author: publicUser(users.get(comment.authorId)),
});

const resolvers = {
  Query: {
    feed: async (_parent, args, { userId }) => {
      const page = await listFeed({ viewerId: userId, cursor: args.cursor, limit: Math.min(args.limit ?? 5, 20) });
      return { posts: page.posts.map(mapPost), nextCursor: page.nextCursor };
    },

    post: async (_parent, { id }, { userId }) => {
      const post = await dbGetPost(id, userId);
      return post ? mapPost(post) : null;
    },

    userPosts: async (_parent, { userId, limit }, context) => {
      const posts = await listUserPosts({ authorId: userId, limit, viewerId: context.userId });
      return posts.map(mapPost);
    },

    instagramSessionStatus: async (_parent, _args, { userId }) => {
      requireAdmin(userId);
      const state = await instagramAdminStatus();
      return { ...state, channels: mapChannels(state.channels) };
    },

    reelsIngestStatus: async () => {
      const state = await ingestStatus();
      return {
        enabled: state.enabled,
        running: state.running,
        intervalMinutes: state.intervalMinutes,
        lastRunAt: state.lastRunAt,
        nextRunAt: state.nextRunAt,
        lastError: state.lastError,
        queuePending: state.queue.pending ?? 0,
        queueImported: state.queue.imported ?? 0,
        queueFailed: state.queue.failed ?? 0,
        totalsImported: state.totals.imported ?? 0,
        sessionConfigured: Boolean(state.sources?.session?.configured),
      };
    },

    reels: async (_parent, { cursor, limit }, { userId }) => {
      const page = await listReels({ viewerId: userId, cursor, limit: Math.min(limit ?? 6, 20) });
      return { posts: page.posts.map(mapPost), nextCursor: page.nextCursor };
    },

    searchPosts: async (_parent, { search, limit }, { userId }) => {
      const posts = await searchPosts({ search, viewerId: userId, limit: Math.min(limit ?? 20, 40) });
      return posts.map(mapPost);
    },

    me: (_parent, _args, { userId }) => (userId ? publicUser(users.get(userId)) : null),

    user: (_parent, { id }) => publicUser(users.get(id)),

    users: (_parent, { search, limit }, { userId }) =>
      searchUsers({ viewerId: userId, search: search ?? '', limit }).map(publicUser),

    friends: (_parent, _args, { userId }) => {
      if (!userId) return [];
      return friendsOf(userId).map((id) => publicUser(users.get(id))).filter(Boolean);
    },

    suggestions: (_parent, { limit }, { userId }) => {
      if (!userId) return [];
      return dbSuggestions({ viewerId: userId, limit }).map(publicUser);
    },

    notifications: async (_parent, _args, { userId }) => {
      if (!userId) return [];
      const list = await notificationsFor(userId, 50);
      return list.map((n) => ({ ...n, actor: publicUser(users.get(n.actorId)) }));
    },

    unreadNotificationsCount: (_parent, _args, { userId }) => unreadNotificationsCount(userId),

    conversations: async (_parent, { search, limit }, { userId }) => {
      if (!userId) return [];
      return listConversations(userId, { search: search ?? '', limit: Math.min(limit ?? 50, 100) });
    },

    messages: async (_parent, { conversationId, cursor, limit }, { userId }) => {
      requireUser(null, userId);
      return listMessages({ conversationId, userId, cursor, limit: Math.min(limit ?? 30, 100) });
    },

    unreadMessagesCount: (_parent, _args, { userId }) => dbUnreadMessages(userId),

    storage: () => storageInfo,
  },

  Mutation: {
    register: async (_parent, { name, email, password, file, username }) => {
      const cleanName = name.trim();
      const cleanEmail = email.trim().toLowerCase();

      if (cleanName.length < 2) throw new Error('NAME_TOO_SHORT');
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) throw new Error('INVALID_EMAIL');
      if (password.length < 6) throw new Error('PASSWORD_TOO_SHORT');

      if (findUserByEmail(cleanEmail)) throw new Error('EMAIL_TAKEN');

      const palette = ['#1877F2', '#DD2A7B', '#11998E', '#8E2DE2', '#F7971E', '#E41E3F', '#396AFC'];
      let user = await dbCreateUser({
        name: cleanName,
        username,
        email: cleanEmail,
        password,
        color: palette[users.size % palette.length],
      });

      // صورة الحساب (اختيارية) — المستخدم كيرفعها بنفسه
      if (file) {
        const mediaId = await saveUploadedFile(file, user, { captionPrefix: 'صورة الحساب' });
        user = await setUserAvatar(user.id, mediaId);
      }

      return { token: signToken({ sub: user.id }), user: publicUser(user) };
    },

    login: (_parent, { email, password }) => {
      const user = findUserByEmail(email);
      if (!user || !verifyPassword(password, user.passwordHash)) throw new Error('INVALID_CREDENTIALS');
      return { token: signToken({ sub: user.id }), user: publicUser(user) };
    },

    updateProfile: async (_parent, { name, bio, file, username }, { userId }) => {
      let user = requireUser(null, userId);
      user = await updateUserProfile(user.id, { name, bio, username });
      if (file) {
        const mediaId = await saveUploadedFile(file, user, { captionPrefix: 'صورة الحساب' });
        user = await setUserAvatar(user.id, mediaId);
      }
      return publicUser(user);
    },

    follow: async (_parent, { userId: targetId }, { userId }) => {
      requireUser(null, userId);
      await dbFollow(userId, targetId);
      await pushNotification({ userId: targetId, actorId: userId, type: 'follow', text: 'بدأ متابعتك' });
      return publicUser(users.get(targetId));
    },

    unfollow: async (_parent, { userId: targetId }, { userId }) => {
      requireUser(null, userId);
      await dbUnfollow(userId, targetId);
      return publicUser(users.get(targetId));
    },

    react: async (_parent, { postId, reaction }, { userId }) => {
      requireUser(null, userId);
      const result = await dbReact({ postId, userId, reaction });

      if (result.isNew) {
        await pushNotification({
          userId: result.authorId,
          actorId: userId,
          type: 'like',
          postId,
          text: 'أعجب بمنشورك',
        });
      }

      const post = await dbGetPost(postId, userId);
      const mapped = mapPost(post);
      await pubsub.publish(`postReacted:${postId}`, { postReacted: mapped });
      return mapped;
    },

    addComment: async (_parent, { postId, text }, { userId }) => {
      requireUser(null, userId);
      const comment = await dbAddComment({ postId, authorId: userId, text });
      await pushNotification({
        userId: comment.postAuthorId,
        actorId: userId,
        type: 'comment',
        postId,
        text: `علّق: «${text.slice(0, 60)}»`,
      });
      return mapComment(comment);
    },

    deleteComment: async (_parent, { postId, commentId }, { userId }) => {
      requireUser(null, userId);
      return dbDeleteComment({ postId, commentId, userId });
    },

    likeComment: async (_parent, { postId, commentId }, { userId }) => {
      requireUser(null, userId);
      return mapComment(await dbLikeComment({ postId, commentId, userId }));
    },

    createPost: async (_parent, { text, privacy, file, aspectRatio }, { userId }) => {
      const user = requireUser(null, userId);
      let media = null;

      if (file) {
        const isVideo = (file.type ?? '').startsWith('video/');
        // الفيديوهات: حد 18MB باش تقدر تتDownload من تلغرام (حد Bot API = 20MB)
        if (isVideo && file.size > 18 * 1024 * 1024) throw new Error('VIDEO_TOO_LARGE');

        const ratio = aspectRatio ?? (isVideo ? 9 / 16 : 4 / 3);
        const mediaId = await saveUploadedFile(file, user, {
          aspectRatio: ratio,
          captionPrefix: (text ?? '').slice(0, 120) || (isVideo ? 'ريل' : 'منشور'),
        });
        media = {
          id: mediaId,
          uri: `/media/${mediaId}`,
          width: null,
          height: null,
          aspectRatio: ratio,
          alt: text?.slice(0, 80) ?? null,
          kind: isVideo ? 'video' : 'image',
          mime: file.type ?? null,
        };
      }

      const postId = await dbCreatePost({ authorId: user.id, text: text ?? '', privacy, mediaId: media?.id ?? null });
      const post = await dbGetPost(postId, userId);
      const mapped = mapPost(post);
      await pubsub.publish('postCreated', { postCreated: mapped });
      return mapped;
    },

    viewReel: async (_parent, { postId }, { userId }) => addReelView({ postId, userId }),

    saveInstagramSession: async (_parent, { cookies }, { userId }) => {
      requireAdmin(userId);
      const probe = await saveInstagramSessionFromInput(cookies);
      return { ...probe, channels: mapChannels(probe.channels) };
    },

    clearInstagramSession: async (_parent, _args, { userId }) => {
      requireAdmin(userId);
      return clearInstagramSession();
    },

    testInstagramSession: async (_parent, _args, { userId }) => {
      requireAdmin(userId);
      const probe = await probeInstagramSession();
      return { ...probe, channels: mapChannels(probe.channels) };
    },

    runReelsIngest: async (_parent, _args, { userId }) => {
      requireAdmin(userId);
      const cycle = await runIngestCycle('app');
      return cycle.imported ?? 0;
    },

    importInstagramReel: async (_parent, { url }, { userId }) => {
      const user = requireUser(null, userId);
      const shortcode = parseShortcode(url);
      if (!shortcode) throw new Error('INVALID_INSTAGRAM_URL');

      const permalink = `https://www.instagram.com/reel/${shortcode}/`;
      const existing = await findPostBySourceUrl(permalink);
      if (existing) return mapPost(existing);

      // 1) نقراو معلومات الريل من إنستغرام
      const meta = await fetchReelMeta(shortcode);

      // 2) نحمّلو الفيديو ونخزّنوه عندنا (mirror) — روابط CDN كتنتهي صلاحيتها
      const file = await downloadReelVideo(meta.videoUrl, { maxBytes: instagramLimits.maxBytes });
      const uploaded = await mirrorStorage.upload({
        buffer: file.buffer,
        filename: `${shortcode}.mp4`,
        mime: file.mime,
        caption: `إنستغرام · ${meta.author ?? ''}`.trim(),
      });

      const aspectRatio = meta.width && meta.height ? meta.width / meta.height : 9 / 16;
      const mediaId = `m_${randomUUID().slice(0, 8)}`;
      await insertMedia({
        id: mediaId,
        provider: uploaded.provider ?? null,
        fileId: uploaded.fileId ?? null,
        mime: file.mime,
        size: uploaded.size ?? file.size,
        aspectRatio,
        alt: meta.caption.slice(0, 80) || 'ريل من إنستغرام',
      });

      const text = meta.caption.trim() || `ريل من @${meta.author ?? 'إنستغرام'}`;
      const postId = await dbCreatePost({
        authorId: user.id,
        text,
        privacy: 'public',
        mediaId,
        source: 'instagram',
        sourceUrl: permalink,
        sourceAuthor: meta.author,
      });

      const post = await dbGetPost(postId, user.id);
      return mapPost(post);
    },

    deletePost: async (_parent, { postId }, { userId }) => {
      requireUser(null, userId);
      return dbDeletePost({ postId, userId });
    },

    startConversation: async (_parent, { userId: otherId }, { userId }) => {
      requireUser(null, userId);
      return getOrCreateConversation(userId, otherId);
    },

    sendMessage: async (_parent, { conversationId, text, replyToId, file }, { userId }) => {
      const user = requireUser(null, userId);

      // صورة مرفقة (اختياري) — كتترفع لتلغرام وكترتبط بالرسالة
      let mediaId = null;
      if (file) {
        mediaId = await saveUploadedFile(file, user, {
          aspectRatio: 4 / 3,
          captionPrefix: 'صورة فالدردشة',
        });
      }

      const { message, otherId } = await dbSendMessage({
        conversationId,
        senderId: userId,
        text,
        replyToId,
        mediaId,
      });

      // إشعار للطرف الآخر (كيبان فالإشعارات، والعدّاد كيتحدّث فوراً)
      await pushNotification({
        userId: otherId,
        actorId: userId,
        type: 'message',
        text: message.text ? `رسالة: «${message.text.slice(0, 60)}»` : 'رسالة: صورة',
      });

      // لحظي: WebSocket + اشتراك GraphQL
      publishRealtime(conversationId, { type: 'message', event: 'added', message });
      await pubsub.publish(`messageAdded:${conversationId}`, { messageAdded: message });
      return message;
    },

    editMessage: async (_parent, { messageId, text }, { userId }) => {
      requireUser(null, userId);
      const { message, conversationId } = await dbEditMessage({ messageId, userId, text });
      publishRealtime(conversationId, { type: 'message', event: 'updated', message });
      await pubsub.publish(`messageChanged:${conversationId}`, { messageChanged: message });
      return message;
    },

    deleteMessage: async (_parent, { messageId }, { userId }) => {
      requireUser(null, userId);
      const { message, conversationId } = await dbDeleteMessage({ messageId, userId });
      publishRealtime(conversationId, { type: 'message', event: 'deleted', message });
      await pubsub.publish(`messageChanged:${conversationId}`, { messageChanged: message });
      return message;
    },

    reactToMessage: async (_parent, { messageId, reaction }, { userId }) => {
      requireUser(null, userId);
      const { message, conversationId } = await dbReactToMessage({ messageId, userId, reaction: reaction ?? null });
      publishRealtime(conversationId, { type: 'message', event: 'updated', message });
      await pubsub.publish(`messageChanged:${conversationId}`, { messageChanged: message });
      return message;
    },

    translateMessage: async (_parent, { messageId, to }, { userId }) => {
      requireUser(null, userId);
      const text = await messageTextForTranslation(messageId, userId);
      const target = defaultTarget(text, to);
      try {
        return await translateText(text, target);
      } catch (error) {
        console.error('[translate]', error.detail ?? error.message);
        throw new GraphQLError('الترجمة غير متاحة دابا — تحقق من اتصال الخادم بالإنترنت', {
          extensions: { code: 'TRANSLATION_UNAVAILABLE' },
        });
      }
    },

    markConversationRead: async (_parent, { conversationId }, { userId }) => {
      requireUser(null, userId);
      const result = await markConversationRead(conversationId, userId);
      if (result.changed > 0) {
        // إشعار قراءة للطرف الآخر (باش تبان «تم القراءة» فوراً)
        publishRealtime(conversationId, { type: 'read', readerId: userId, readAt: result.readAt });
      }
      return true;
    },

    markNotificationsRead: async (_parent, _args, { userId }) => {
      requireUser(null, userId);
      return dbMarkRead(userId);
    },
  },

  Subscription: {
    postCreated: {
      subscribe: () => pubsub.subscribe('postCreated'),
      resolve: (payload) => payload.postCreated,
    },
    postReacted: {
      subscribe: (_parent, { postId }) => pubsub.subscribe(`postReacted:${postId}`),
      resolve: (payload) => payload.postReacted,
    },
    messageAdded: {
      subscribe: async (_parent, { conversationId }, { userId }) => {
        requireUser(null, userId);
        await conversationPartner(conversationId, userId); // كيتأكد أنه عضو فالمحادثة
        return pubsub.subscribe(`messageAdded:${conversationId}`);
      },
      resolve: (payload) => payload.messageAdded,
    },
    messageChanged: {
      subscribe: async (_parent, { conversationId }, { userId }) => {
        requireUser(null, userId);
        await conversationPartner(conversationId, userId);
        return pubsub.subscribe(`messageChanged:${conversationId}`);
      },
      resolve: (payload) => payload.messageChanged,
    },
  },

  Post: {
    comments: (post) => post.comments.map(mapComment),
    media: (post) =>
      post.media
        ? {
            ...post.media,
            kind: post.media.kind ?? ((post.media.mime ?? '').startsWith('video/') ? 'video' : 'image'),
          }
        : null,
  },

  ReactionCounts: {
    total: (reactions) =>
      Object.entries(reactions).reduce(
        (sum, [key, value]) => (['like', 'love', 'haha', 'wow', 'sad', 'angry'].includes(key) ? sum + (value ?? 0) : sum),
        0,
      ),
  },

  Author: {
    isFriend: (author, _args, { userId }) => {
      if (!userId || !author?.id) return false;
      return friendsOf(userId).includes(author.id);
    },
    isMe: (author, _args, { userId }) => Boolean(userId && author?.id === userId),
    friendsCount: (author) => (author?.id ? friendsOf(author.id).length : 0),
    postsCount: (author) => (author?.id ? postsCountOf(author.id) : 0),
  },

  Notification: {
    actor: (notification) => publicUser(users.get(notification.actorId)),
  },
};

export const schema = createSchema({ typeDefs, resolvers });
export default schema;
