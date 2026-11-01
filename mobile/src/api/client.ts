/**
 * عميل GraphQL الحقيقي
 * - يطلب الحقول المحددة فقط (GraphQL بدل REST = استهلاك أقل للباقة).
 * - يعمل مع الخادم في /server.
 * - يرمي خطأ بكود OFFLINE / TIMEOUT / HTTP_xxx حتى تعرف طبقة الحالة كيفاش تتعامل معه.
 */
import type {
  AppNotification,
  AuthPayload,
  Author,
  Comment,
  Conversation,
  Media,
  Message,
  MessagePage,
  Post,
  ReactionKind,
  Translation,
} from '../types';

export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:4000/graphql';

/** التوكن الحالي (كيضبطو الـ auth store بعد الدخول) */
let authToken: string | null = null;
export const setAuthToken = (token: string | null) => {
  authToken = token;
};
export const getAuthToken = () => authToken;
export const API_ORIGIN = API_URL.replace(/\/graphql\/?$/, '');
export const REQUEST_TIMEOUT = 8000;

/** حقول المصدر (ريلز إنستغرام) */
const SOURCE_FIELDS = `source sourceUrl sourceAuthor`;

const FEED_QUERY = /* GraphQL */ `
  query Feed($cursor: String, $limit: Int) {
    feed(cursor: $cursor, limit: $limit) {
      nextCursor
      posts {
        id
        text
        createdAt
        privacy
        shares
        commentsCount
        myReaction
        ${SOURCE_FIELDS}
        reactions { like love haha wow sad angry }
        author { id name initial color avatarUrl verified }
        media { id uri width height aspectRatio alt kind mime }
        comments { id text createdAt likes likedByMe author { id name initial color avatarUrl verified } }
      }
    }
  }
`;

const REACT_MUTATION = /* GraphQL */ `
  mutation React($postId: ID!, $reaction: ReactionKind) {
    react(postId: $postId, reaction: $reaction) {
      id
      myReaction
      reactions { like love haha wow sad angry }
    }
  }
`;

const ADD_COMMENT_MUTATION = /* GraphQL */ `
  mutation AddComment($postId: ID!, $text: String!) {
    addComment(postId: $postId, text: $text) {
      id
      text
      createdAt
      likes
      likedByMe
      author { id name initial color avatarUrl verified }
    }
  }
`;

const LIKE_COMMENT_MUTATION = /* GraphQL */ `
  mutation LikeComment($postId: ID!, $commentId: ID!) {
    likeComment(postId: $postId, commentId: $commentId) {
      id
      text
      createdAt
      likes
      likedByMe
      author { id name initial color avatarUrl verified }
    }
  }
`;

const DELETE_COMMENT_MUTATION = /* GraphQL */ `
  mutation DeleteComment($postId: ID!, $commentId: ID!) {
    deleteComment(postId: $postId, commentId: $commentId)
  }
`;

const CREATE_POST_MUTATION = /* GraphQL */ `
  mutation CreatePost($text: String!, $privacy: Privacy, $file: File, $aspectRatio: Float) {
    createPost(text: $text, privacy: $privacy, file: $file, aspectRatio: $aspectRatio) {
      id
      text
      createdAt
      privacy
      shares
      commentsCount
      myReaction
      ${SOURCE_FIELDS}
      reactions { like love haha wow sad angry }
      author { id name initial color avatarUrl verified }
      media { id uri width height aspectRatio alt kind mime }
      comments { id text createdAt likes likedByMe author { id name initial color avatarUrl verified } }
    }
  }
`;

const ME_QUERY = /* GraphQL */ `
  query Me {
    me { id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe isAdmin }
  }
`;

const AUTHOR_FIELDS = `id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe isAdmin`;

const LOGIN_MUTATION = /* GraphQL */ `
  mutation Login($email: String!, $password: String!) {
    login(email: $email, password: $password) {
      token
      user { ${AUTHOR_FIELDS} }
    }
  }
`;

const REGISTER_MUTATION = /* GraphQL */ `
  mutation Register($name: String!, $email: String!, $password: String!, $file: File) {
    register(name: $name, email: $email, password: $password, file: $file) {
      token
      user { id name initial color avatarUrl verified bio email friendsCount postsCount }
    }
  }
`;

const UPDATE_PROFILE_MUTATION = /* GraphQL */ `
  mutation UpdateProfile($name: String, $bio: String, $file: File) {
    updateProfile(name: $name, bio: $bio, file: $file) { id name initial color avatarUrl bio }
  }
`;

const USERS_QUERY = /* GraphQL */ `
  query Users($search: String) {
    users(search: $search) { id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe }
  }
`;

const FRIENDS_QUERY = /* GraphQL */ `
  query Friends {
    friends { id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe }
  }
`;

const SUGGESTIONS_QUERY = /* GraphQL */ `
  query Suggestions {
    suggestions { id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe }
  }
`;

const FOLLOW_MUTATION = /* GraphQL */ `
  mutation Follow($userId: ID!) {
    follow(userId: $userId) { id name isFriend friendsCount }
  }
`;

const UNFOLLOW_MUTATION = /* GraphQL */ `
  mutation Unfollow($userId: ID!) {
    unfollow(userId: $userId) { id name isFriend friendsCount }
  }
`;

const NOTIFICATIONS_QUERY = /* GraphQL */ `
  query Notifications {
    notifications { id type text createdAt read postId actor { id name initial color avatarUrl verified } }
    unreadNotificationsCount
  }
`;

const MARK_READ_MUTATION = /* GraphQL */ `
  mutation MarkRead { markNotificationsRead }
`;

const MEDIA_FIELDS = `id uri width height aspectRatio alt kind mime`;

export interface InstagramChannel {
  name: string;
  ok: boolean;
  found: number;
  error?: string | null;
}

export interface InstagramProbe {
  configured: boolean;
  valid: boolean;
  totalCodes: number;
  checkedAt: string;
  channels: InstagramChannel[];
}

export interface InstagramAdminStatus {
  configured: boolean;
  fingerprint?: string | null;
  savedFrom?: string | null;
  savedAt?: string | null;
  autoImport: boolean;
  intervalMinutes: number;
  keepMin: number;
  reelsCount: number;
  queuePending: number;
  queueImported: number;
  lastRunAt?: string | null;
  lastError?: string | null;
  channels: InstagramChannel[];
}


const DELETE_POST_MUTATION = /* GraphQL */ `
  mutation DeletePost($postId: ID!) {
    deletePost(postId: $postId)
  }
`;

const SEARCH_POSTS_QUERY = /* GraphQL */ `
  query SearchPosts($search: String!, $limit: Int) {
    searchPosts(search: $search, limit: $limit) {
      id
      text
      privacy
      shares
      views
      ${SOURCE_FIELDS}
      createdAt
      author { ${AUTHOR_FIELDS} }
      media { ${MEDIA_FIELDS} }
      reactions { like love haha wow sad angry total }
      myReaction
      comments { id text createdAt likes likedByMe author { ${AUTHOR_FIELDS} } }
    }
  }
`;

const REELS_QUERY = /* GraphQL */ `
  query Reels($cursor: String, $limit: Int) {
    reels(cursor: $cursor, limit: $limit) {
      nextCursor
      posts {
        id
        text
        privacy
        shares
        views
        ${SOURCE_FIELDS}
        createdAt
        author { ${AUTHOR_FIELDS} }
        media { ${MEDIA_FIELDS} }
        reactions { like love haha wow sad angry total }
        myReaction
      }
    }
  }
`;

const REELS_INGEST_STATUS_QUERY = /* GraphQL */ `
  query ReelsIngestStatus {
    reelsIngestStatus {
      enabled
      running
      intervalMinutes
      lastRunAt
      nextRunAt
      queuePending
      queueImported
      totalsImported
    }
  }
`;

const VIEW_REEL_MUTATION = /* GraphQL */ `
  mutation ViewReel($postId: ID!) {
    viewReel(postId: $postId)
  }
`;

const INSTAGRAM_ADMIN_STATUS_QUERY = /* GraphQL */ `
  query InstagramSessionStatus {
    instagramSessionStatus {
      configured
      fingerprint
      savedFrom
      savedAt
      autoImport
      intervalMinutes
      keepMin
      reelsCount
      queuePending
      queueImported
      lastRunAt
      lastError
      channels { name ok found error }
    }
  }
`;

const SAVE_INSTAGRAM_SESSION_MUTATION = /* GraphQL */ `
  mutation SaveInstagramSession($cookies: String!) {
    saveInstagramSession(cookies: $cookies) {
      configured
      valid
      totalCodes
      checkedAt
      channels { name ok found error }
    }
  }
`;

const TEST_INSTAGRAM_SESSION_MUTATION = /* GraphQL */ `
  mutation TestInstagramSession {
    testInstagramSession {
      configured
      valid
      totalCodes
      checkedAt
      channels { name ok found error }
    }
  }
`;

const RUN_REELS_INGEST_MUTATION = /* GraphQL */ `
  mutation RunReelsIngest {
    runReelsIngest
  }
`;

const CLEAR_INSTAGRAM_SESSION_MUTATION = /* GraphQL */ `
  mutation ClearInstagramSession {
    clearInstagramSession
  }
`;

const USER_POSTS_QUERY = /* GraphQL */ `
  query UserPosts($userId: ID!, $limit: Int) {
    userPosts(userId: $userId, limit: $limit) {
      id
      text
      createdAt
      privacy
      shares
      commentsCount
      myReaction
      ${SOURCE_FIELDS}
      reactions { like love haha wow sad angry }
      author { id name initial color avatarUrl verified }
      media { ${MEDIA_FIELDS} }
      comments { id text createdAt likes likedByMe author { id name initial color avatarUrl verified } }
    }
  }
`;

const MESSAGE_FIELDS = `id conversationId senderId text createdAt read edited deleted
  replyTo { id conversationId senderId text createdAt read edited deleted }
  reactions { kind count mine }
  media { ${MEDIA_FIELDS} }`;

const CONVERSATIONS_QUERY = /* GraphQL */ `
  query Conversations($search: String, $limit: Int) {
    conversations(search: $search, limit: $limit) {
      id
      createdAt
      lastAt
      unreadCount
      other { id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe }
      lastMessage { ${MESSAGE_FIELDS} }
    }
  }
`;

const MESSAGES_QUERY = /* GraphQL */ `
  query Messages($conversationId: ID!, $cursor: String, $limit: Int) {
    messages(conversationId: $conversationId, cursor: $cursor, limit: $limit) {
      nextCursor
      messages { ${MESSAGE_FIELDS} }
    }
  }
`;

const UNREAD_MESSAGES_QUERY = /* GraphQL */ `
  query UnreadMessages { unreadMessagesCount }
`;

const START_CONVERSATION_MUTATION = /* GraphQL */ `
  mutation StartConversation($userId: ID!) {
    startConversation(userId: $userId) {
      id
      createdAt
      lastAt
      unreadCount
      other { id name initial color avatarUrl verified bio email friendsCount postsCount isFriend isMe }
      lastMessage { ${MESSAGE_FIELDS} }
    }
  }
`;

const SEND_MESSAGE_MUTATION = /* GraphQL */ `
  mutation SendMessage($conversationId: ID!, $text: String!, $replyToId: ID, $file: File) {
    sendMessage(conversationId: $conversationId, text: $text, replyToId: $replyToId, file: $file) { ${MESSAGE_FIELDS} }
  }
`;

const EDIT_MESSAGE_MUTATION = /* GraphQL */ `
  mutation EditMessage($messageId: ID!, $text: String!) {
    editMessage(messageId: $messageId, text: $text) { ${MESSAGE_FIELDS} }
  }
`;

const DELETE_MESSAGE_MUTATION = /* GraphQL */ `
  mutation DeleteMessage($messageId: ID!) {
    deleteMessage(messageId: $messageId) { ${MESSAGE_FIELDS} }
  }
`;

const REACT_TO_MESSAGE_MUTATION = /* GraphQL */ `
  mutation ReactToMessage($messageId: ID!, $reaction: ReactionKind) {
    reactToMessage(messageId: $messageId, reaction: $reaction) { ${MESSAGE_FIELDS} }
  }
`;

const TRANSLATE_MESSAGE_MUTATION = /* GraphQL */ `
  mutation TranslateMessage($messageId: ID!, $to: String) {
    translateMessage(messageId: $messageId, to: $to) { text from to provider cached }
  }
`;

const MARK_CONVERSATION_READ_MUTATION = /* GraphQL */ `
  mutation MarkConversationRead($conversationId: ID!) {
    markConversationRead(conversationId: $conversationId)
  }
`;

export interface LocalFile {
  uri: string;
  name: string;
  type: string;
}

export class ApiError extends Error {
  code: string;
  constructor(code: string, message?: string) {
    super(message ?? code);
    this.code = code;
    this.name = 'ApiError';
  }
}

async function gql<T>(query: string, variables?: Record<string, unknown>, timeout = REQUEST_TIMEOUT): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);

  try {
    const res = await fetch(API_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
      },
      body: JSON.stringify({ query, variables }),
      signal: controller.signal,
    });

    if (!res.ok) throw new ApiError(`HTTP_${res.status}`);

    const json = (await res.json()) as { data?: T; errors?: Array<{ message: string }> };
    if (json.errors?.length) throw new ApiError('GRAPHQL_ERROR', json.errors[0].message);
    if (!json.data) throw new ApiError('EMPTY_RESPONSE');
    return json.data;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if ((error as Error).name === 'AbortError') throw new ApiError('TIMEOUT');
    throw new ApiError('NETWORK_ERROR', (error as Error).message);
  } finally {
    clearTimeout(timer);
  }
}

/* ============ تطبيع البيانات: الخادم → أنواع التطبيق ============ */

const normalizeMedia = (media: Media | null): Media | undefined => {
  if (!media) return undefined;
  const uri = media.uri?.startsWith('http') || media.uri?.startsWith('mock://')
    ? media.uri
    : `${API_ORIGIN}${media.uri ?? ''}`;
  return {
    ...media,
    uri,
    width: media.width ?? 0,
    height: media.height ?? 0,
    aspectRatio: media.aspectRatio ?? 4 / 3,
  };
};

/** `avatarUrl` كتجي من الخادم كنسبة (/media/xxx) → كنحوّلوها لرابط كامل */
export const normalizeAuthor = (author: Author | null): Author | null => {
  if (!author) return null;
  const raw = author.avatarUrl ?? null;
  return { ...author, avatarUrl: raw && raw.startsWith('/') ? `${API_ORIGIN}${raw}` : raw };
};

/** رسالة: كنحوّلو مسار الصورة لرابط كامل (والرد كذلك) */
export const normalizeMessage = (message: Message): Message => {
  const media = normalizeMedia(message.media ?? null);
  return {
    ...message,
    media: media ?? null,
    replyTo: message.replyTo ? normalizeMessage(message.replyTo) : null,
  };
};

const normalizeComment = (comment: Comment): Comment => ({
  ...comment,
  author: normalizeAuthor(comment.author) as Author,
});

const normalizePost = (post: Post): Post => ({
  ...post,
  author: normalizeAuthor(post.author) as Author,
  media: normalizeMedia(post.media ?? null),
  comments: (post.comments ?? []).map(normalizeComment),
});

type UploadResult<T> = T;

/** رفع ملف واحد عبر GraphQL multipart request spec */
async function uploadFile<T>(query: string, variables: Record<string, unknown>, file: LocalFile): Promise<T> {
  const form = new FormData();
  form.append('operations', JSON.stringify({ query, variables: { ...variables, file: null } }));
  form.append('map', JSON.stringify({ '0': ['variables.file'] }));
  form.append('0', { uri: file.uri, name: file.name, type: file.type } as unknown as Blob);

  const res = await fetch(API_URL, {
    method: 'POST',
    body: form,
    headers: { Accept: 'application/json', ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}) },
  });
  const json = (await res.json()) as { data?: T; errors?: Array<{ message: string }> };
  if (json.errors?.length) throw new ApiError('GRAPHQL_ERROR', json.errors[0].message);
  if (!json.data) throw new ApiError('EMPTY_RESPONSE');
  return json.data;
}

export const api = {
  /** فحص سريع: هل الخادم متاح؟ */
  async ping(): Promise<boolean> {
    await gql<{ me: Author }>(ME_QUERY, undefined, 3000);
    return true;
  },

  me: () => gql<{ me: Author | null }>(ME_QUERY).then((d) => d.me),

  async login(email: string, password: string): Promise<AuthPayload> {
    const data = await gql<{ login: AuthPayload }>(LOGIN_MUTATION, { email, password });
    return data.login;
  },

  async register(name: string, email: string, password: string, file?: LocalFile): Promise<AuthPayload> {
    const payload = file
      ? await uploadFile<{ register: AuthPayload }>(REGISTER_MUTATION, { name, email, password }, file)
      : await gql<{ register: AuthPayload }>(REGISTER_MUTATION, { name, email, password, file: null }, 20000);
    return { ...payload.register, user: normalizeAuthor(payload.register.user) as Author };
  },

  async updateProfile(input: { name?: string; bio?: string; file?: LocalFile }): Promise<Author> {
    const { file, ...rest } = input;
    const payload = file
      ? await uploadFile<{ updateProfile: Author }>(UPDATE_PROFILE_MUTATION, rest, file)
      : await gql<{ updateProfile: Author }>(UPDATE_PROFILE_MUTATION, { ...rest, file: null }, 20000);
    return normalizeAuthor(payload.updateProfile) as Author;
  },

  users: (search = '') => gql<{ users: Author[] }>(USERS_QUERY, { search }).then((d) => d.users),
  friends: () => gql<{ friends: Author[] }>(FRIENDS_QUERY).then((d) => d.friends),
  suggestions: () => gql<{ suggestions: Author[] }>(SUGGESTIONS_QUERY).then((d) => d.suggestions),

  follow: (userId: string) => gql<{ follow: Author }>(FOLLOW_MUTATION, { userId }).then((d) => d.follow),
  unfollow: (userId: string) => gql<{ unfollow: Author }>(UNFOLLOW_MUTATION, { userId }).then((d) => d.unfollow),

  notifications: () =>
    gql<{ notifications: AppNotification[]; unreadNotificationsCount: number }>(NOTIFICATIONS_QUERY),

  markNotificationsRead: () => gql<{ markNotificationsRead: boolean }>(MARK_READ_MUTATION),

  /* ================= الدردشة ================= */

  async conversations(search = '', limit = 50): Promise<Conversation[]> {
    const data = await gql<{ conversations: Conversation[] }>(CONVERSATIONS_QUERY, { search, limit });
    return data.conversations.map((c) => ({
      ...c,
      other: normalizeAuthor(c.other) as Author,
      lastMessage: c.lastMessage ? normalizeMessage(c.lastMessage) : null,
    }));
  },

  async messages(conversationId: string, cursor: string | null = null, limit = 30): Promise<MessagePage> {
    const data = await gql<{ messages: MessagePage }>(MESSAGES_QUERY, { conversationId, cursor, limit });
    return { ...data.messages, messages: data.messages.messages.map(normalizeMessage) };
  },

  async startConversation(userId: string): Promise<Conversation> {
    const data = await gql<{ startConversation: Conversation }>(START_CONVERSATION_MUTATION, { userId });
    return { ...data.startConversation, other: normalizeAuthor(data.startConversation.other) as Author };
  },

  async sendMessage(
    conversationId: string,
    text: string,
    replyToId: string | null = null,
    file?: LocalFile,
  ): Promise<Message> {
    const variables = { conversationId, text, replyToId };
    const data = file
      ? await uploadFile<{ sendMessage: Message }>(SEND_MESSAGE_MUTATION, variables, file)
      : await gql<{ sendMessage: Message }>(SEND_MESSAGE_MUTATION, variables, 15000);
    return normalizeMessage(data.sendMessage);
  },

  async editMessage(messageId: string, text: string): Promise<Message> {
    const data = await gql<{ editMessage: Message }>(EDIT_MESSAGE_MUTATION, { messageId, text }, 15000);
    return normalizeMessage(data.editMessage);
  },

  async deleteMessage(messageId: string): Promise<Message> {
    const data = await gql<{ deleteMessage: Message }>(DELETE_MESSAGE_MUTATION, { messageId }, 15000);
    return normalizeMessage(data.deleteMessage);
  },

  async reactToMessage(messageId: string, reaction: ReactionKind | null): Promise<Message> {
    const data = await gql<{ reactToMessage: Message }>(REACT_TO_MESSAGE_MUTATION, { messageId, reaction });
    return normalizeMessage(data.reactToMessage);
  },

  async translateMessage(messageId: string, to?: string): Promise<Translation> {
    const data = await gql<{ translateMessage: Translation }>(TRANSLATE_MESSAGE_MUTATION, { messageId, to }, 15000);
    return data.translateMessage;
  },

  async markConversationRead(conversationId: string) {
    const data = await gql<{ markConversationRead: boolean }>(MARK_CONVERSATION_READ_MUTATION, { conversationId });
    return data.markConversationRead;
  },

  unreadMessagesCount: () =>
    gql<{ unreadMessagesCount: number }>(UNREAD_MESSAGES_QUERY).then((d) => d.unreadMessagesCount),

  /** الريلز (فيديوهات) */
  async reels(cursor: string | null = null, limit = 6) {
    const data = await gql<{ reels: { posts: Post[]; nextCursor: string | null } }>(
      REELS_QUERY,
      { cursor, limit },
      12000,
    );
    return { posts: data.reels.posts.map(normalizePost), nextCursor: data.reels.nextCursor };
  },

  /* ==================== لوحة الأدمن (جلسة إنستغرام) ==================== */

  instagramSessionStatus: () =>
    gql<{ instagramSessionStatus: InstagramAdminStatus }>(INSTAGRAM_ADMIN_STATUS_QUERY, {}, 12000).then(
      (data) => data.instagramSessionStatus,
    ),

  saveInstagramSession: (cookies: string) =>
    gql<{ saveInstagramSession: InstagramProbe }>(SAVE_INSTAGRAM_SESSION_MUTATION, { cookies }, 60000).then(
      (data) => data.saveInstagramSession,
    ),

  testInstagramSession: () =>
    gql<{ testInstagramSession: InstagramProbe }>(TEST_INSTAGRAM_SESSION_MUTATION, {}, 60000).then(
      (data) => data.testInstagramSession,
    ),

  runReelsIngest: () =>
    gql<{ runReelsIngest: number }>(RUN_REELS_INGEST_MUTATION, {}, 120000).then((data) => data.runReelsIngest),

  clearInstagramSession: () =>
    gql<{ clearInstagramSession: boolean }>(CLEAR_INSTAGRAM_SESSION_MUTATION, {}).then(
      (data) => data.clearInstagramSession,
    ),

  /** حالة الجلب التلقائي ديال ريلز إنستغرام */
  reelsIngestStatus: () =>
    gql<{
      reelsIngestStatus: {
        enabled: boolean;
        running: boolean;
        intervalMinutes: number;
        lastRunAt: string | null;
        nextRunAt: string | null;
        queuePending: number;
        queueImported: number;
        totalsImported: number;
      };
    }>(REELS_INGEST_STATUS_QUERY, {}, 8000).then((data) => data.reelsIngestStatus),

  viewReel: (postId: string) =>
    gql<{ viewReel: number }>(VIEW_REEL_MUTATION, { postId }).then((data) => data.viewReel),

  async searchPosts(search: string, limit = 20) {
    const data = await gql<{ searchPosts: Post[] }>(SEARCH_POSTS_QUERY, { search, limit });
    return data.searchPosts.map(normalizePost);
  },

  async deletePost(postId: string) {
    const data = await gql<{ deletePost: boolean }>(DELETE_POST_MUTATION, { postId });
    return data.deletePost;
  },

  async userPosts(userId: string, limit = 20) {
    const data = await gql<{ userPosts: Post[] }>(USER_POSTS_QUERY, { userId, limit });
    return data.userPosts.map(normalizePost);
  },

  async feed(cursor: string | null, limit = 5) {
    const data = await gql<{ feed: { posts: Post[]; nextCursor: string | null } }>(FEED_QUERY, { cursor, limit });
    return { posts: data.feed.posts.map(normalizePost), nextCursor: data.feed.nextCursor };
  },

  async react(postId: string, reaction: ReactionKind | null) {
    const data = await gql<{ react: { id: string; myReaction: ReactionKind | null; reactions: Record<ReactionKind, number> } }>(
      REACT_MUTATION,
      { postId, reaction },
    );
    return data.react;
  },

  async addComment(postId: string, text: string) {
    const data = await gql<{ addComment: Comment }>(ADD_COMMENT_MUTATION, { postId, text });
    return normalizeComment(data.addComment);
  },

  /**
   * إنشاء منشور — وإذا كان معه صورة/فيديو، رفع متعدد الأجزاء (multipart)
   * حسب مواصفة GraphQL Multipart Request، والخادم يرفعها إلى سحابة تلغرام.
   */
  async createPost(input: {
    text: string;
    privacy?: 'public' | 'friends' | 'private';
    file?: LocalFile;
    aspectRatio?: number;
  }) {
    const { text, privacy = 'public', file, aspectRatio } = input;

    if (!file) {
      const data = await gql<{ createPost: Post }>(
        CREATE_POST_MUTATION,
        { text, privacy, file: null, aspectRatio: aspectRatio ?? null },
        20000,
      );
      return normalizePost(data.createPost);
    }

    const form = new FormData();
    form.append(
      'operations',
      JSON.stringify({ query: CREATE_POST_MUTATION, variables: { text, privacy, file: null, aspectRatio: aspectRatio ?? null } }),
    );
    form.append('map', JSON.stringify({ '0': ['variables.file'] }));
    form.append('0', file as unknown as Blob);

    const res = await fetch(API_URL, {
      method: 'POST',
      body: form,
      headers: { Accept: 'application/json', ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}) },
    });
    const json = (await res.json()) as { data?: { createPost: Post }; errors?: Array<{ message: string }> };
    if (json.errors?.length) throw new ApiError('GRAPHQL_ERROR', json.errors[0].message);
    if (!json.data) throw new ApiError('EMPTY_RESPONSE');
    return normalizePost(json.data.createPost);
  },

  async likeComment(postId: string, commentId: string) {
    const data = await gql<{ likeComment: Comment }>(LIKE_COMMENT_MUTATION, { postId, commentId });
    return normalizeComment(data.likeComment);
  },

  async deleteComment(postId: string, commentId: string) {
    const data = await gql<{ deleteComment: boolean }>(DELETE_COMMENT_MUTATION, { postId, commentId });
    return data.deleteComment;
  },
};
