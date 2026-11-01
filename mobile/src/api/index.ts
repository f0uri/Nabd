import { isTelegramDirect, telegramConfigured, telegramNamespace } from '../telegram/config';
import { telegramApi } from '../telegram/native';
/**
 * طبقة مصدر البيانات (Data Source Layer)
 *
 * أربعة أوضاع، تُضبط من متغيّر البيئة EXPO_PUBLIC_DATA_SOURCE:
 *   graphql → الخادم الحقيقي فقط (يرمي خطأ إذا كان مغلقاً)
 *   telegram → تلغرام مباشر صريح، بلا fallback
 *   mock    → بيانات محلية فقط (للتطوير بلا خادم)
 *   auto    → (الافتراضي) يحاول الخادم مرة واحدة، وإن لم يجيب يرجع للـ mock تلقائياً
 *
 * نفس الواجهة للجميع، لذلك الـ store والشاشات لا تعرف من أين تأتي البيانات.
 */
import { api, ApiError, type LocalFile } from './client';
import { mockApi } from './mockApi';
import type {
  AppNotification,
  AuthPayload,
  Author,
  Comment,
  Conversation,
  Message,
  MessagePage,
  Post,
  ReactionKind,
  Translation,
} from '../types';

export type DataSourceMode = 'auto' | 'graphql' | 'mock' | 'telegram';
export type ResolvedSource = 'graphql' | 'mock' | 'telegram';

const configured = ((process.env.EXPO_PUBLIC_DATA_SOURCE ?? 'auto') as DataSourceMode).trim().toLowerCase();

let resolved: ResolvedSource | null = configured === 'auto' ? null : (configured as ResolvedSource);
let offline = false;

export const setOffline = (value: boolean) => {
  offline = value;
};

export const isOffline = () => offline;

export const getSource = (): ResolvedSource | null => resolved;

export const sourceLabel = (): string =>
  resolved === 'telegram' ? 'تلغرام مباشر · تجريبي' : resolved === 'graphql' ? 'خادم GraphQL' : resolved === 'mock' ? 'بيانات محلية' : 'جارٍ الفحص…';

const guard = () => {
  if (offline) throw new ApiError('OFFLINE', 'لا يوجد اتصال');
};

async function pickSource(): Promise<ResolvedSource> {
  if (resolved) return resolved;
  if (configured === 'mock') {
    resolved = 'mock';
    return resolved;
  }
  try {
    await api.ping();
    resolved = 'graphql';
  } catch {
    resolved = configured === 'graphql' ? 'graphql' : 'mock';
  }
  return resolved;
}

/** إعادة الفحص (يُستدعى من زر «إعادة المحاولة» أو عند سحب القائمة للتحديث) */
export const resetSource = () => {
  if (configured === 'auto') resolved = null;
};

const backendDataApi = {
  async logout() {},
  resetSource,

  async fetchFeed(cursor: string | null = null, limit = 5) {
    guard();
    const source = await pickSource();
    if (source === 'graphql') {
      try {
        return await api.feed(cursor, limit);
      } catch (error) {
        // إذا سقط الخادم في الوسط: لا نُفقد المستخدم الشاشة — نرجع للبيانات المحلية
        if (configured === 'auto') {
          resolved = 'mock';
          return mockApi.fetchFeed(cursor);
        }
        throw error;
      }
    }
    return mockApi.fetchFeed(cursor);
  },

  async react(postId: string, reaction: ReactionKind | null) {
    guard();
    const source = await pickSource();
    if (source === 'graphql') {
      await api.react(postId, reaction);
      return;
    }
    await mockApi.react(postId, reaction);
  },

  async addComment(postId: string, text: string): Promise<Comment | null> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.addComment(postId, text);
    await mockApi.addComment(postId, text);
    return null;
  },

  async likeComment(postId: string, commentId: string): Promise<Comment | null> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.likeComment(postId, commentId);
    await mockApi.likeComment(postId, commentId);
    return null;
  },

  async deleteComment(postId: string, commentId: string) {
    guard();
    const source = await pickSource();
    if (source === 'graphql') {
      await api.deleteComment(postId, commentId);
      return;
    }
    await mockApi.deleteComment(postId, commentId);
  },

  async createPost(input: {
    text: string;
    privacy?: 'public' | 'friends' | 'private';
    file?: LocalFile;
    aspectRatio?: number;
  }): Promise<Post> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.createPost(input);
    return mockApi.createPost({
      text: input.text,
      privacy: input.privacy,
      fileUri: input.file?.uri,
      aspectRatio: input.aspectRatio,
    });
  },

  /* ================= المصادقة ================= */

  async login(email: string, password: string): Promise<AuthPayload> {
    guard();
    // Automatic read-only demo fallback must never impersonate a successful login.
    return configured === 'mock' ? mockApi.login(email, password) : api.login(email, password);
  },

  async register(name: string, email: string, password: string, file?: LocalFile): Promise<AuthPayload> {
    guard();
    if (configured === 'mock' && file) throw new ApiError('PROFILE_REQUIRES_SERVER');
    return configured === 'mock' ? mockApi.register(name, email, password) : api.register(name, email, password, file);
  },

  async me(): Promise<Author | null> {
    guard();
    if (configured === 'mock') throw new ApiError('PROFILE_REQUIRES_SERVER');
    // Never overwrite a cached real profile with CURRENT_USER after an outage.
    return api.me();
  },

  async updateProfile(input: { name?: string; bio?: string; username?: string; file?: LocalFile }): Promise<Author> {
    guard();
    if (configured === 'mock') throw new ApiError('PROFILE_REQUIRES_SERVER');
    // Profile writes always target the server, even if the feed fell back to demo data.
    return api.updateProfile(input);
  },

  /* ================= المستخدمون والصداقات ================= */

  async users(search = ''): Promise<Author[]> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.users(search);
    return mockApi.users(search);
  },

  async friends(): Promise<Author[]> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.friends();
    return mockApi.friends();
  },

  async suggestions(): Promise<Author[]> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.suggestions();
    return mockApi.suggestions();
  },

  async follow(userId: string) {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.follow(userId);
    return mockApi.follow(userId);
  },

  async unfollow(userId: string) {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.unfollow(userId);
    return mockApi.unfollow(userId);
  },

  /* ================= الإشعارات ================= */

  async fetchNotifications(): Promise<AppNotification[]> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') {
      const data = await api.notifications();
      return data.notifications;
    }
    return mockApi.fetchNotifications();
  },

  /** نفس الإشعارات مع عدّاد غير المقروء (للشارة ف الشريط العلوي) */
  async notifications(): Promise<{ notifications: AppNotification[]; unreadNotificationsCount: number }> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.notifications();
    const notifications = await mockApi.fetchNotifications();
    return { notifications, unreadNotificationsCount: 0 };
  },

  async markNotificationsRead() {
    const source = await pickSource();
    if (source === 'graphql') {
      await api.markNotificationsRead();
      return;
    }
    await Promise.resolve();
  },

  /* ================= منشورات مستخدم ================= */

  /* ================= الدردشة ================= */

  async conversations(search = ''): Promise<Conversation[]> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.conversations(search);
    return mockApi.conversations(search);
  },

  async messages(conversationId: string, cursor: string | null = null): Promise<MessagePage> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.messages(conversationId, cursor);
    return mockApi.messages(conversationId);
  },

  async startConversation(userId: string): Promise<Conversation> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.startConversation(userId);
    return mockApi.startConversation(userId);
  },

  async sendMessage(
    conversationId: string,
    text: string,
    replyToId: string | null = null,
    file?: LocalFile,
  ): Promise<Message> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.sendMessage(conversationId, text, replyToId, file);
    return mockApi.sendMessage(conversationId, text, replyToId, file);
  },

  /* ================= الريلز ================= */

  async reels(cursor: string | null = null): Promise<{ posts: Post[]; nextCursor: string | null }> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.reels(cursor);
    return mockApi.reels(cursor);
  },

  /* ==================== لوحة الأدمن ==================== */

  instagramSessionStatus() {
    return api.instagramSessionStatus();
  },

  saveInstagramSession(cookies: string) {
    return api.saveInstagramSession(cookies);
  },

  testInstagramSession() {
    return api.testInstagramSession();
  },

  clearInstagramSession() {
    return api.clearInstagramSession();
  },

  /** تشغيل دورة جلب دابا (نفس المؤقت) */
  runReelsIngest() {
    return api.runReelsIngest();
  },

  /** حالة الجلب التلقائي ديال ريلز إنستغرام */
  async reelsIngestStatus() {
    const source = await pickSource();
    if (source === 'graphql') return api.reelsIngestStatus();
    return { enabled: false, running: false, intervalMinutes: 0, lastRunAt: null, nextRunAt: null, queuePending: 0, queueImported: 0, totalsImported: 0 };
  },


  async viewReel(postId: string): Promise<number> {
    const source = await pickSource();
    if (source === 'graphql') return api.viewReel(postId);
    return mockApi.viewReel(postId);
  },

  async deletePost(postId: string): Promise<boolean> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.deletePost(postId);
    return mockApi.deletePost(postId);
  },

  /** بحث فالمنشورات */
  async searchPosts(search: string): Promise<Post[]> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.searchPosts(search);
    return mockApi.searchPosts(search);
  },

  async editMessage(messageId: string, text: string): Promise<Message> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.editMessage(messageId, text);
    return mockApi.editMessage(messageId, text);
  },

  async deleteMessage(messageId: string): Promise<Message> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.deleteMessage(messageId);
    return mockApi.deleteMessage(messageId);
  },

  async reactToMessage(messageId: string, reaction: ReactionKind | null): Promise<Message> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.reactToMessage(messageId, reaction);
    return mockApi.reactToMessage(messageId, reaction);
  },

  async translateMessage(messageId: string, to?: string): Promise<Translation> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.translateMessage(messageId, to);
    return mockApi.translateMessage(messageId, to);
  },

  async markConversationRead(conversationId: string) {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.markConversationRead(conversationId);
    return mockApi.markConversationRead(conversationId);
  },

  async unreadMessagesCount(): Promise<number> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') return api.unreadMessagesCount();
    return mockApi.unreadMessagesCount();
  },

  async userPosts(userId: string): Promise<Post[]> {
    guard();
    const source = await pickSource();
    if (source === 'graphql') {
      try {
        return await api.userPosts(userId);
      } catch {
        return [];
      }
    }
    return mockApi.userPosts(userId);
  },
};

export const authStoragePrefix = isTelegramDirect ? `nabd.telegram.${telegramNamespace}` : 'nabd';
export const dataApi: typeof backendDataApi = isTelegramDirect ? new Proxy(backendDataApi, {
  get(_target, method: keyof typeof backendDataApi) {
    if (method === 'resetSource') return resetSource;
    return async (...args: unknown[]) => {
      guard();
      if (!telegramConfigured) throw new ApiError('TELEGRAM_CONFIG_REQUIRED');
      const action = (telegramApi as unknown as Record<string, (...input: unknown[]) => unknown>)[method];
      if (!Object.prototype.hasOwnProperty.call(telegramApi, method) || typeof action !== 'function') throw new ApiError('TELEGRAM_FEATURE_UNAVAILABLE', 'هاد الميزة ما متوفراش فالوضع المؤقت ديال تلغرام');
      try { return await action(...args); }
      catch (error) {
        const code = (error as Error | null)?.message ?? '';
        const safe = /^(TELEGRAM_[A-Z_]+|INVALID_USERNAME|NAME_TOO_SHORT|INVALID_EMAIL|EMAIL_TAKEN|INVALID_CREDENTIALS|USERNAME_TAKEN|USER_NOT_FOUND|VALIDATION|FORBIDDEN|POST_NOT_FOUND)$/.test(code);
        throw new ApiError(safe ? code : 'TELEGRAM_REQUEST_FAILED');
      }
    };
  },
}) : backendDataApi;
export { ApiError };
