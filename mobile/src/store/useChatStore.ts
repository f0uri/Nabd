import { create } from 'zustand';
import { dataApi } from '../api';
import type { LocalFile } from '../api/client';
import { normalizeMessage } from '../api/client';
import { chatError } from '../utils/chatErrors';
import { realtime } from '../api/realtime';
import { useAuthStore } from './useAuthStore';
import { useFeedStore } from './useFeedStore';
import type {
  Conversation,
  Message,
  MessagePage,
  ReactionKind,
  RealtimeEvent,
  Translation,
} from '../types';

/**
 * حالة الدردشة (Zustand) — كل الشاشات كتتحدّث من هنا بلا reload.
 *
 * - التحديث اللحظي عبر WebSocket (`realtime`): رسالة جديدة · تعديل · حذف ·
 *   تفاعل · قراءة · «يكتب الآن…». إلا طاح الاتصال كيبقى polling احتياطي.
 * - كل عملية (إرسال · تعديل · حذف · تفاعل) **متفائلة**: كتبان فوراً وكتتراجع
 *   بوحدها إلا فشل الخادم.
 */

interface ChatState {
  conversations: Conversation[];
  loading: boolean;
  refreshing: boolean;
  error: string | null;
  openError: string | null;
  unread: number;

  /** رسائل كل محادثة (مفتاح = conversationId) */
  threads: Record<string, Message[]>;
  /** مؤشر تحميل الرسائل الأقدم */
  nextCursor: Record<string, string | null>;
  /** المحادثة المفتوحة دابا */
  activeId: string | null;
  threadLoading: boolean;
  threadError: string | null;
  sending: boolean;

  /** شاشة الرسائل مفتوحة؟ */
  sheetOpen: boolean;
  pendingUserId: string | null;

  /** «يكتب الآن…» لكل محادثة */
  typing: Record<string, string | null>;
  /** الترجمات المطلوبة (مفتاح = messageId) */
  translations: Record<string, Translation>;
  /** متصل بالـ WebSocket؟ */
  live: boolean;
  /** مسودة مشاركة (منشور كتشاركو فـ رسالة) */
  draft: string | null;
  setDraft: (text: string | null) => void;

  init: () => void;
  dispose: () => void;

  loadConversations: (search?: string, background?: boolean) => Promise<void>;
  refresh: () => Promise<void>;
  loadUnread: () => Promise<void>;

  openChat: (userId?: string | null) => void;
  closeChat: () => void;
  consumePending: () => void;

  openWith: (userId: string) => Promise<Conversation | null>;
  openConversation: (conversationId: string) => Promise<void>;
  closeConversation: () => void;
  loadThread: (conversationId: string, cursor?: string | null) => Promise<void>;
  markRead: (conversationId: string) => Promise<void>;

  /** إرسال رسالة — نص و/أو صورة (file) */
  send: (
    conversationId: string,
    text: string,
    replyToId?: string | null,
    options?: { file?: LocalFile; localUri?: string; aspectRatio?: number },
  ) => Promise<boolean>;
  editMessage: (messageId: string, text: string) => Promise<boolean>;
  deleteMessage: (messageId: string) => Promise<boolean>;
  /** تفاعل على رسالة — null كتحيّد التفاعل */
  reactMessage: (
    messageId: string,
    reaction: ReactionKind | null,
  ) => Promise<boolean>;
  translateMessage: (
    messageId: string,
    to?: string,
  ) => Promise<Translation | null>;
  clearTranslation: (messageId: string) => void;
  sendTyping: (conversationId: string) => void;

  reset: () => void;
}

/** معرف المستخدم الحالي (من غير ما نخلّيو الستور يتعلّق بالـ auth) */
const meId = () => useAuthStore.getState().user?.id ?? null;

const replaceInThread = (
  threads: Record<string, Message[]>,
  conversationId: string,
  message: Message,
): Record<string, Message[]> => {
  const thread = threads[conversationId];
  if (!thread) return threads;
  const exists = thread.some((item) => item.id === message.id);
  return {
    ...threads,
    [conversationId]: exists
      ? thread.map((item) => (item.id === message.id ? message : item))
      : [...thread, message],
  };
};

/** ترتيب المحادثات: الأحدث فوق */
const bumpConversation = (
  conversations: Conversation[],
  conversationId: string,
  patch: Partial<Conversation>,
): Conversation[] => {
  const target = conversations.find((c) => c.id === conversationId);
  if (!target) return conversations;
  const updated = { ...target, ...patch };
  return [updated, ...conversations.filter((c) => c.id !== conversationId)];
};

/** مؤقتات «يكتب الآن…» */
const typingTimers: Record<string, ReturnType<typeof setTimeout>> = {};
let unsubRealtime: (() => void) | null = null;
let unsubStatus: (() => void) | null = null;
let lastTypingSent = 0;
let epoch = 0,
  listRequest = 0,
  temporaryId = 0;
const threadRequests = new Map<string, symbol>();
let inited = false;

export const useChatStore = create<ChatState>((set, get) => ({
  conversations: [],
  loading: false,
  refreshing: false,
  error: null,
  openError: null,
  unread: 0,
  threads: {},
  nextCursor: {},
  activeId: null,
  threadLoading: false,
  threadError: null,
  sending: false,
  sheetOpen: false,
  pendingUserId: null,
  typing: {},
  translations: {},
  live: false,
  draft: null,

  setDraft: (text) => set({ draft: text }),

  /* ------------------------------ الحياة ------------------------------ */

  init: () => {
    if (inited) return;
    inited = true;

    unsubRealtime = realtime.onEvent((event) => handleEvent(event, set, get));
    unsubStatus = realtime.onStatus((connected) => set({ live: connected }));
    realtime.connect();
    get().loadUnread();
  },

  dispose: () => {
    unsubRealtime?.();
    unsubStatus?.();
    unsubRealtime = null;
    unsubStatus = null;
    inited = false;
    realtime.disconnect();
    get().reset();
  },

  /* --------------------------- قائمة المحادثات --------------------------- */

  loadConversations: async (search = '', background = false) => {
    const request = ++listRequest;
    if (!background) set({ loading: true, error: null });
    try {
      const list = await dataApi.conversations(search);
      if (request === listRequest)
        set({ conversations: list, loading: false, error: null });
    } catch (error) {
      if (request === listRequest)
        set({
          loading: false,
          error: chatError(error, 'ما قدرناش نحمّلو المحادثات'),
        });
    }
  },

  refresh: async () => {
    const generation = epoch;
    set({ refreshing: true });
    await get().loadConversations('', true);
    if (generation === epoch) set({ refreshing: false });
  },

  loadUnread: async () => {
    const generation = epoch;
    try {
      const unread = await dataApi.unreadMessagesCount();
      if (generation === epoch) set({ unread });
    } catch {
      /* غير عدّاد */
    }
  },

  /* ------------------------------ الشاشة ------------------------------ */

  openChat: (userId = null) =>
    set({ sheetOpen: true, pendingUserId: userId ?? null }),
  closeChat: () =>
    set({ sheetOpen: false, pendingUserId: null, openError: null }),
  consumePending: () => set({ pendingUserId: null }),

  openWith: async (userId) => {
    const generation = epoch;
    set({ openError: null });
    try {
      const conversation = await dataApi.startConversation(userId);
      if (generation !== epoch) return null;
      set({
        openError: null,
        conversations: [
          conversation,
          ...get().conversations.filter((c) => c.id !== conversation.id),
        ],
      });
      return conversation;
    } catch (error) {
      if (generation === epoch)
        set({ openError: chatError(error, 'ما قدرناش نفتحو المحادثة') });
      return null;
    }
  },

  openConversation: async (conversationId) => {
    set({ activeId: conversationId });
    if (threadRequests.has(conversationId)) return;
    const request = Symbol();
    threadRequests.set(conversationId, request);
    const current = () =>
      threadRequests.get(conversationId) === request &&
      get().activeId === conversationId;
    set({
      threadLoading: !get().threads[conversationId]?.length,
      threadError: null,
    });
    realtime.subscribe(conversationId);
    try {
      const page: MessagePage = await dataApi.messages(conversationId, null);
      if (!current()) return;
      const existing = get().threads[conversationId] ?? [];
      const combined = new Map(
        existing.map((message) => [message.id, message]),
      );
      for (const message of page.messages) combined.set(message.id, message);
      set({
        threads: { ...get().threads, [conversationId]: [...combined.values()] },
        nextCursor: {
          ...get().nextCursor,
          [conversationId]: Object.prototype.hasOwnProperty.call(
            get().nextCursor,
            conversationId,
          )
            ? get().nextCursor[conversationId]
            : page.nextCursor,
        },
        threadLoading: false,
      });
      await get().markRead(conversationId);
    } catch (error) {
      if (current())
        set({
          threadLoading: false,
          threadError: chatError(error, 'تعذّر تحميل الرسائل. أعد المحاولة.'),
        });
    } finally {
      if (threadRequests.get(conversationId) === request)
        threadRequests.delete(conversationId);
    }
  },

  closeConversation: () => {
    const activeId = get().activeId;
    if (activeId) realtime.unsubscribe(activeId);
    set({ activeId: null });
  },

  /** تحميل رسائل أقدم (للتمرير لفوق) */
  loadThread: async (conversationId, cursor = null) => {
    if (!cursor) return;
    const generation = epoch;
    try {
      const page = await dataApi.messages(conversationId, cursor);
      if (generation !== epoch) return;
      set({
        threads: {
          ...get().threads,
          [conversationId]: [
            ...new Map(
              [...page.messages, ...(get().threads[conversationId] ?? [])].map(
                (m) => [m.id, m],
              ),
            ).values(),
          ],
        },
        nextCursor: { ...get().nextCursor, [conversationId]: page.nextCursor },
      });
    } catch {
      /* تجاهل */
    }
  },

  markRead: async (conversationId) => {
    const generation = epoch;
    try {
      await dataApi.markConversationRead(conversationId);
      if (generation !== epoch) return;
      set({
        conversations: get().conversations.map((c) =>
          c.id === conversationId ? { ...c, unreadCount: 0 } : c,
        ),
        threads: {
          ...get().threads,
          [conversationId]: (get().threads[conversationId] ?? []).map((m) =>
            m.senderId !== meId() ? { ...m, read: true } : m,
          ),
        },
      });
      await get().loadUnread();
    } catch {
      /* تجاهل */
    }
  },

  /* ------------------------------- الإرسال ------------------------------- */

  send: async (conversationId, text, replyToId = null, options = {}) => {
    const trimmed = text.trim();
    const { file, localUri, aspectRatio } = options;
    if (!trimmed && !file) return false;

    const generation = epoch;
    const senderId = meId() ?? 'me';
    const tempId = `tmp_${Date.now()}_${++temporaryId}`;
    const parent = replyToId
      ? ((get().threads[conversationId] ?? []).find(
          (m) => m.id === replyToId,
        ) ?? null)
      : null;

    const optimistic: Message = {
      id: tempId,
      conversationId,
      senderId,
      text: trimmed,
      createdAt: new Date().toISOString(),
      read: false,
      edited: false,
      deleted: false,
      replyTo: parent,
      reactions: [],
      // صورة متفائلة: كنعرضو الملف المحلي فوراً
      media: localUri
        ? {
            id: tempId,
            uri: localUri,
            width: 0,
            height: 0,
            aspectRatio: aspectRatio ?? 4 / 3,
            alt: undefined,
          }
        : null,
    };

    const before = get().threads[conversationId] ?? [];
    set({
      threads: { ...get().threads, [conversationId]: [...before, optimistic] },
      sending: true,
    });

    try {
      const saved = await dataApi.sendMessage(
        conversationId,
        trimmed,
        replyToId,
        file,
      );
      if (generation !== epoch) return false;
      set({
        threads: {
          ...get().threads,
          [conversationId]: [
            ...new Map(
              (get().threads[conversationId] ?? [])
                .map((m) => (m.id === tempId ? saved : m))
                .map((m) => [m.id, m]),
            ).values(),
          ],
        },
        conversations: bumpConversation(get().conversations, conversationId, {
          lastMessage: saved,
          lastAt: saved.createdAt,
        }),
        sending: false,
      });
      return true;
    } catch (error) {
      if (generation !== epoch) return false;
      useFeedStore
        .getState()
        .showToast(
          chatError(error, 'لم يتم إرسال الرسالة. أعد المحاولة.'),
          'error',
        );
      // تراجع: كنحيّدو الرسالة الوهمية
      set({
        threads: {
          ...get().threads,
          [conversationId]: (get().threads[conversationId] ?? []).filter(
            (m) => m.id !== tempId,
          ),
        },
        sending: false,
      });
      return false;
    }
  },

  /* ------------------------------- التعديل ------------------------------- */

  editMessage: async (messageId, text) => {
    const generation = epoch;
    const trimmed = text.trim();
    if (!trimmed) return false;

    const location = findMessage(get().threads, messageId);
    if (!location) return false;
    const { conversationId, message: previous } = location;

    // متفائل
    set({
      threads: {
        ...get().threads,
        [conversationId]: (get().threads[conversationId] ?? []).map((m) =>
          m.id === messageId ? { ...m, text: trimmed, edited: true } : m,
        ),
      },
      translations: omit(get().translations, messageId),
    });

    try {
      const saved = await dataApi.editMessage(messageId, trimmed);
      if (generation !== epoch) return false;
      set({
        threads: replaceInThread(get().threads, conversationId, saved),
        conversations: patchLastMessage(
          get().conversations,
          conversationId,
          saved,
        ),
      });
      return true;
    } catch {
      if (generation !== epoch) return false;
      set({
        threads: replaceInThread(get().threads, conversationId, previous),
      });
      return false;
    }
  },

  /* -------------------------------- الحذف -------------------------------- */

  deleteMessage: async (messageId) => {
    const generation = epoch;
    const location = findMessage(get().threads, messageId);
    if (!location) return false;
    const { conversationId, message: previous } = location;

    set({
      threads: {
        ...get().threads,
        [conversationId]: (get().threads[conversationId] ?? []).map((m) =>
          m.id === messageId
            ? { ...m, deleted: true, text: '', reactions: [], replyTo: null }
            : m,
        ),
      },
      translations: omit(get().translations, messageId),
    });

    try {
      const saved = await dataApi.deleteMessage(messageId);
      if (generation !== epoch) return false;
      set({
        threads: replaceInThread(get().threads, conversationId, saved),
        conversations: patchLastMessage(
          get().conversations,
          conversationId,
          saved,
        ),
      });
      return true;
    } catch {
      if (generation !== epoch) return false;
      set({
        threads: replaceInThread(get().threads, conversationId, previous),
      });
      return false;
    }
  },

  /* ------------------------------- التفاعل ------------------------------- */

  reactMessage: async (messageId, reaction) => {
    const generation = epoch;
    const location = findMessage(get().threads, messageId);
    if (!location) return false;
    const { conversationId, message: previous } = location;
    const kind = reaction ?? null;
    const viewer = meId();

    // متفائل: كنحسبو الحالة الجديدة محلياً
    const current = previous.reactions ?? [];
    const mineNow = current.find((r) => r.mine)?.kind ?? null;
    const nextKind = mineNow === kind ? null : kind;

    set({
      threads: {
        ...get().threads,
        [conversationId]: (get().threads[conversationId] ?? []).map((m) =>
          m.id === messageId
            ? { ...m, reactions: toggleReaction(current, nextKind, viewer) }
            : m,
        ),
      },
    });

    try {
      const saved = await dataApi.reactToMessage(messageId, nextKind);
      if (generation !== epoch) return false;
      set({ threads: replaceInThread(get().threads, conversationId, saved) });
      return true;
    } catch {
      if (generation !== epoch) return false;
      set({
        threads: replaceInThread(get().threads, conversationId, previous),
      });
      return false;
    }
  },

  /* ------------------------------- الترجمة ------------------------------- */

  translateMessage: async (messageId, to) => {
    const generation = epoch;
    try {
      const translation = await dataApi.translateMessage(messageId, to);
      if (generation !== epoch) return null;
      set({
        translations: { ...get().translations, [messageId]: translation },
      });
      return translation;
    } catch {
      return null;
    }
  },

  clearTranslation: (messageId) =>
    set({ translations: omit(get().translations, messageId) }),

  /** «يكتب الآن…» — كنصيفطو مرة كل ثانيتين ماكس */
  sendTyping: (conversationId) => {
    const now = Date.now();
    if (now - lastTypingSent < 2000) return;
    lastTypingSent = now;
    realtime.typing(conversationId, useAuthStore.getState().user?.name ?? null);
  },

  reset: () => {
    epoch++;
    listRequest++;
    threadRequests.clear();
    set({
      conversations: [],
      threads: {},
      nextCursor: {},
      translations: {},
      typing: {},
      loading: false,
      refreshing: false,
      threadLoading: false,
      threadError: null,
      openError: null,
      sending: false,
      error: null,
      unread: 0,
      activeId: null,
      draft: null,
      sheetOpen: false,
      pendingUserId: null,
      live: false,
    });
  },
}));

/* ------------------------------ مساعدات ------------------------------ */

const omit = <T>(record: Record<string, T>, key: string): Record<string, T> => {
  const next = { ...record };
  delete next[key];
  return next;
};

const findMessage = (
  threads: Record<string, Message[]>,
  messageId: string,
): { conversationId: string; message: Message } | null => {
  for (const [conversationId, thread] of Object.entries(threads)) {
    const message = thread.find((m) => m.id === messageId);
    if (message) return { conversationId, message };
  }
  return null;
};

/** آخر رسالة فالقائمة كتبدّل غير إلا كانت هي نفسها */
const patchLastMessage = (
  conversations: Conversation[],
  conversationId: string,
  message: Message,
): Conversation[] =>
  conversations.map((c) =>
    c.id === conversationId && c.lastMessage?.id === message.id
      ? { ...c, lastMessage: message }
      : c,
  );

/** تفاعل واحد لكل مستخدم: كنحيّدو القديم ونزيدو الجديد */
const toggleReaction = (
  reactions: NonNullable<Message['reactions']>,
  kind: ReactionKind | null,
  viewer: string | null,
): NonNullable<Message['reactions']> => {
  const cleaned = reactions
    .map((r) => (r.mine ? { ...r, count: r.count - 1, mine: false } : r))
    .filter((r) => r.count > 0);

  if (!kind) return cleaned;

  const existing = cleaned.find((r) => r.kind === kind);
  if (existing) {
    return cleaned.map((r) =>
      r.kind === kind
        ? { ...r, count: r.count + 1, mine: r.mine || Boolean(viewer) }
        : r,
    );
  }
  return [...cleaned, { kind, count: 1, mine: Boolean(viewer) }];
};

/** معالج أحداث الـ WebSocket */
function handleEvent(
  event: RealtimeEvent,
  set: (partial: Partial<ChatState>) => void,
  get: () => ChatState,
) {
  if (event.type === 'message') {
    const conversationId = event.conversationId;
    const message = normalizeMessage(event.message);
    const viewer = meId();
    const state = get();
    const thread = state.threads[conversationId];
    const isActive = state.activeId === conversationId;
    const mine = message.senderId === viewer;

    let nextThread = thread;
    if (thread) {
      if (event.event === 'added') {
        // كنحيّدو النسخة المتفائلة (tmp_) اللي عندها نفس النص والصاحب
        const withoutTemp = thread.filter(
          (m) =>
            !(
              m.id.startsWith('tmp_') &&
              m.senderId === message.senderId &&
              m.text === message.text
            ),
        );
        nextThread = withoutTemp.some((m) => m.id === message.id)
          ? withoutTemp.map((m) => (m.id === message.id ? message : m))
          : [...withoutTemp, message];
      } else {
        nextThread = thread.map((m) => (m.id === message.id ? message : m));
      }
    }

    const patch: Partial<ChatState> = {
      threads: { ...state.threads, [conversationId]: nextThread ?? [] },
    };

    if (event.event === 'added') {
      const unreadBump = !mine && !isActive ? 1 : 0;
      patch.conversations = bumpConversation(
        state.conversations,
        conversationId,
        {
          lastMessage: message,
          lastAt: message.createdAt,
          unreadCount:
            (state.conversations.find((c) => c.id === conversationId)
              ?.unreadCount ?? 0) + unreadBump,
        },
      );
      if (!mine) {
        // الطرف الآخر ولّى ما كيكتبش
        patch.typing = { ...state.typing, [conversationId]: null };
      }
    } else {
      patch.conversations = patchLastMessage(
        state.conversations,
        conversationId,
        message,
      );
      patch.translations = omit(state.translations, message.id);
    }

    set(patch);

    const known = state.conversations.some((c) => c.id === conversationId);

    // إلا كانت المحادثة مفتوحة → كنعلموها مقروءة فوراً
    if (event.event === 'added' && !mine && isActive)
      get().markRead(conversationId);
    if (event.event === 'added' && !mine && !isActive) get().loadUnread();
    // محادثة جديدة ما كانتش فالقائمة (وصلات أول رسالة) → كنعاودو نحمّلو القائمة
    if (event.event === 'added' && !known) get().refresh();
    return;
  }

  if (event.type === 'read') {
    const { conversationId } = event;
    const state = get();
    const thread = state.threads[conversationId];
    if (!thread) return;
    const viewer = meId();
    set({
      threads: {
        ...state.threads,
        [conversationId]: thread.map((m) =>
          m.senderId === viewer ? { ...m, read: true } : m,
        ),
      },
    });
    return;
  }

  if (event.type === 'notification') {
    const notification = event.notification;
    const feed = useFeedStore.getState();
    feed.loadUnreadNotifications();

    // رسالة جديدة فمحادثة ماشي مفتوحة → تنبيه صغير
    const state = get();
    if (notification.type === 'message' && state.activeId === null) {
      feed.showToast(
        `${notification.actor?.name ?? 'شي حد'}: ${notification.text}`,
        'info',
      );
    }
    return;
  }

  if (event.type === 'typing') {
    const { conversationId, name } = event;
    if (event.userId === meId()) return;
    set({ typing: { ...get().typing, [conversationId]: name ?? 'شخص' } });

    clearTimeout(typingTimers[conversationId]);
    typingTimers[conversationId] = setTimeout(() => {
      set({ typing: { ...get().typing, [conversationId]: null } });
    }, 4000);
  }
}

export default useChatStore;
