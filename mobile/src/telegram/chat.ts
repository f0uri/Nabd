import type {
  Author,
  Conversation,
  Media,
  Message,
  MessagePage,
  ReactionKind,
} from '../types';
import type { LocalFile } from '../api/client';
import { digest } from './password';

const kinds: ReactionKind[] = ['like', 'love', 'haha', 'wow', 'sad', 'angry'];
const uid = (v: unknown): v is string =>
  typeof v === 'string' && /^[a-f0-9]{32}$/.test(v);
const plain = (v: any): v is Record<string, any> =>
  !!v &&
  typeof v === 'object' &&
  !Array.isArray(v) &&
  !Object.keys(v).some((k) =>
    ['__proto__', 'constructor', 'prototype'].includes(k),
  );
const date = (v: unknown) =>
  typeof v === 'string' && Number.isFinite(Date.parse(v));
const fail = (code: string): never => {
  throw Error(code);
};
const threadId = (members: string[]) =>
  digest(`nabd-chat-v1:${members.join(':')}`).slice(0, 32);
type StoredMessage = {
  id: string;
  senderId: string;
  text: string;
  createdAt: string;
  read: boolean;
  edited: boolean;
  deleted: boolean;
  replyToId: string | null;
  reactions: Record<string, ReactionKind>;
  media: Media | null;
};
export type StoredChat = {
  id: string;
  members: [string, string];
  createdAt: string;
  messages: StoredMessage[];
};
export type ChatAccount = {
  profile: Author;
  chatV1?: Record<string, StoredChat>;
};
type ChatVault = { users: Record<string, ChatAccount> };

/** Optional ACCOUNT extension, not a root-schema change/migration. The original v1
 * reader accepts/preserves extra account fields. Reads never initialize chatV1.
 * This shared JSON is NOT confidential or a trusted authorization boundary.
 */
export function validateChats(users: Record<string, ChatAccount>) {
  const invalid = () => fail('TELEGRAM_INVALID_VAULT');
  const seenMessages = new Set<string>();
  for (const [owner, account] of Object.entries(users)) {
    if (account.chatV1 === undefined) continue;
    if (!plain(account.chatV1)) return invalid();
    for (const [key, chat] of Object.entries(account.chatV1)) {
      if (
        !plain(chat) ||
        !uid(key) ||
        chat.id !== key ||
        !Array.isArray(chat.members) ||
        chat.members.length !== 2 ||
        !chat.members.every((m) => uid(m) && Object.hasOwn(users, m)) ||
        chat.members[0] >= chat.members[1] ||
        chat.members[0] !== owner ||
        threadId(chat.members) !== key ||
        !date(chat.createdAt) ||
        !Array.isArray(chat.messages)
      )
        return invalid();
      const prior = new Set<string>();
      for (const message of chat.messages) {
        if (
          !plain(message) ||
          !uid(message.id) ||
          seenMessages.has(message.id) ||
          !chat.members.includes(message.senderId) ||
          typeof message.text !== 'string' ||
          message.text.length > 5000 ||
          !date(message.createdAt) ||
          typeof message.read !== 'boolean' ||
          typeof message.edited !== 'boolean' ||
          typeof message.deleted !== 'boolean' ||
          !plain(message.reactions) ||
          !(message.replyToId === null || prior.has(message.replyToId)) ||
          Object.entries(message.reactions).some(
            ([id, kind]) => !chat.members.includes(id) || !kinds.includes(kind),
          ) ||
          (message.deleted &&
            (message.text !== '' ||
              message.media !== null ||
              Object.keys(message.reactions).length > 0)) ||
          (!message.deleted && !message.text.trim() && !message.media)
        )
          return invalid();
        if (message.media !== null) {
          const media = message.media;
          if (
            !plain(media) ||
            !uid(media.id) ||
            media.kind !== 'image' ||
            typeof media.uri !== 'string' ||
            media.uri.length > Math.ceil((128 * 1024) / 3) * 4 + 32 ||
            !/^data:image\/(jpeg|png|webp);base64,[a-zA-Z0-9+/=]+$/.test(
              media.uri,
            ) ||
            !Number.isFinite(media.aspectRatio) ||
            media.aspectRatio <= 0 ||
            !Number.isSafeInteger(media.width) ||
            media.width < 0 ||
            !Number.isSafeInteger(media.height) ||
            media.height < 0
          )
            return invalid();
        }
        prior.add(message.id);
        seenMessages.add(message.id);
      }
    }
  }
}

export function createTelegramChat<
  V extends ChatVault,
  A extends ChatAccount,
>(deps: {
  read: () => Promise<{ vault: V; self: A }>;
  write: <T>(
    run: (
      vault: V,
      self: A,
    ) =>
      | Promise<{ value: T; changed: boolean }>
      | { value: T; changed: boolean },
  ) => Promise<T>;
  user: (vault: V, id: string, viewer: string) => Author;
  id: () => string;
  now: () => number;
  image: (file: LocalFile) => Promise<string>;
}) {
  const all = (vault: V) =>
    Object.values(vault.users).flatMap((a) => Object.values(a.chatV1 ?? {}));
  const member = (vault: V, self: A, id: string) => {
    const chat = all(vault).find((c) => c.id === id);
    if (!chat || !chat.members.includes(self.profile.id))
      return fail('FORBIDDEN');
    return chat;
  };
  const locate = (vault: V, self: A, id: string) => {
    for (const chat of all(vault)) {
      const message = chat.messages.find((m) => m.id === id);
      if (message && chat.members.includes(self.profile.id))
        return { chat, message };
    }
    return fail('FORBIDDEN');
  };
  function present(
    chat: StoredChat,
    message: StoredMessage,
    viewer: string,
    reply = true,
  ): Message {
    return {
      id: message.id,
      conversationId: chat.id,
      senderId: message.senderId,
      text: message.text,
      createdAt: message.createdAt,
      read: message.read,
      edited: message.edited,
      deleted: message.deleted,
      media: message.media,
      replyTo:
        reply && message.replyToId
          ? present(
              chat,
              chat.messages.find((m) => m.id === message.replyToId)!,
              viewer,
              false,
            )
          : null,
      reactions: kinds
        .map((kind) => ({
          kind,
          count: Object.values(message.reactions).filter((v) => v === kind)
            .length,
          mine: message.reactions[viewer] === kind,
        }))
        .filter((r) => r.count > 0),
    };
  }
  function view(vault: V, chat: StoredChat, viewer: string): Conversation {
    const last = chat.messages[chat.messages.length - 1];
    return {
      id: chat.id,
      createdAt: chat.createdAt,
      lastAt: last?.createdAt ?? chat.createdAt,
      other: deps.user(vault, chat.members.find((m) => m !== viewer)!, viewer),
      lastMessage: last ? present(chat, last, viewer) : null,
      unreadCount: chat.messages.filter(
        (m) => m.senderId !== viewer && !m.read && !m.deleted,
      ).length,
    };
  }
  return {
    async conversations(search = ''): Promise<Conversation[]> {
      const { vault, self } = await deps.read();
      const term = search.trim().replace(/^@/, '').toLowerCase();
      return all(vault)
        .filter((c) => c.members.includes(self.profile.id))
        .map((c) => view(vault, c, self.profile.id))
        .filter(
          (c) =>
            !term ||
            c.other.name.toLowerCase().includes(term) ||
            c.other.username?.includes(term),
        )
        .sort(
          (a, b) =>
            b.lastAt.localeCompare(a.lastAt) || b.id.localeCompare(a.id),
        );
    },
    async startConversation(other: string): Promise<Conversation> {
      return deps.write((vault, self) => {
        if (
          !uid(other) ||
          !Object.hasOwn(vault.users, other) ||
          other === self.profile.id
        )
          return fail('USER_NOT_FOUND');
        const members = [self.profile.id, other].sort() as [string, string];
        const id = threadId(members),
          owner = vault.users[members[0]];
        const existing = owner.chatV1?.[id];
        if (existing)
          return {
            value: view(vault, existing, self.profile.id),
            changed: false,
          };
        const chat: StoredChat = {
          id,
          members,
          createdAt: new Date(deps.now()).toISOString(),
          messages: [],
        };
        owner.chatV1 ??= {};
        owner.chatV1[id] = chat;
        return { value: view(vault, chat, self.profile.id), changed: true };
      });
    },
    async messages(
      id: string,
      cursor: string | null = null,
    ): Promise<MessagePage> {
      const { vault, self } = await deps.read(),
        chat = member(vault, self, id);
      const end =
        cursor === null
          ? chat.messages.length
          : chat.messages.findIndex((m) => m.id === cursor);
      if (end < 0) return fail('VALIDATION');
      const start = Math.max(0, end - 30);
      return {
        messages: chat.messages
          .slice(start, end)
          .map((m) => present(chat, m, self.profile.id)),
        nextCursor: start > 0 ? chat.messages[start].id : null,
      };
    },
    async sendMessage(
      id: string,
      text: string,
      replyToId: string | null = null,
      file?: LocalFile,
    ): Promise<Message> {
      if (
        typeof text !== 'string' ||
        text.trim().length > 5000 ||
        (!text.trim() && !file)
      )
        return fail('VALIDATION');
      return deps.write(async (vault, self) => {
        const chat = member(vault, self, id);
        if (
          replyToId !== null &&
          !chat.messages.some((m) => m.id === replyToId)
        )
          return fail('VALIDATION');
        const media: Media | null = file
          ? {
              id: deps.id(),
              kind: 'image',
              uri: await deps.image(file),
              width: 0,
              height: 0,
              aspectRatio: 4 / 3,
            }
          : null;
        const message: StoredMessage = {
          id: deps.id(),
          senderId: self.profile.id,
          text: text.trim(),
          createdAt: new Date(deps.now()).toISOString(),
          read: false,
          edited: false,
          deleted: false,
          replyToId,
          reactions: {},
          media,
        };
        chat.messages.push(message);
        return {
          value: present(chat, message, self.profile.id),
          changed: true,
        };
      });
    },
    async markConversationRead(id: string) {
      return deps.write((vault, self) => {
        const chat = member(vault, self, id);
        let changed = false;
        for (const m of chat.messages)
          if (m.senderId !== self.profile.id && !m.read) {
            m.read = true;
            changed = true;
          }
        return { value: true, changed };
      });
    },
    async unreadMessagesCount() {
      const { vault, self } = await deps.read();
      return all(vault)
        .filter((c) => c.members.includes(self.profile.id))
        .reduce(
          (count, c) =>
            count +
            c.messages.filter(
              (m) => m.senderId !== self.profile.id && !m.read && !m.deleted,
            ).length,
          0,
        );
    },
    async editMessage(id: string, text: string) {
      if (typeof text !== 'string' || !text.trim() || text.trim().length > 5000)
        return fail('VALIDATION');
      return deps.write((vault, self) => {
        const { chat, message } = locate(vault, self, id);
        if (message.senderId !== self.profile.id || message.deleted)
          return fail('FORBIDDEN');
        message.text = text.trim();
        message.edited = true;
        return {
          value: present(chat, message, self.profile.id),
          changed: true,
        };
      });
    },
    async deleteMessage(id: string) {
      return deps.write((vault, self) => {
        const { chat, message } = locate(vault, self, id);
        if (message.senderId !== self.profile.id) return fail('FORBIDDEN');
        const changed = !message.deleted;
        message.text = '';
        message.media = null;
        message.reactions = {};
        message.deleted = true;
        return { value: present(chat, message, self.profile.id), changed };
      });
    },
    async reactToMessage(id: string, kind: ReactionKind | null) {
      if (kind !== null && !kinds.includes(kind)) return fail('VALIDATION');
      return deps.write((vault, self) => {
        const { chat, message } = locate(vault, self, id);
        if (message.deleted) return fail('FORBIDDEN');
        const previous = message.reactions[self.profile.id] ?? null;
        if (kind === null) delete message.reactions[self.profile.id];
        else message.reactions[self.profile.id] = kind;
        return {
          value: present(chat, message, self.profile.id),
          changed: previous !== kind,
        };
      });
    },
  };
}
