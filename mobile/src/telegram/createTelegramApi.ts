import type { Author, Post, ReactionKind, AuthPayload } from '../types';
import type { LocalFile } from '../api/client';
import type { TelegramTransport, Chat } from './transport';
import { MAX_VAULT_BYTES } from './transport';
import { digest, passwordDigest, utf8 } from './password';

type Account = {
  profile: Author;
  salt: string;
  passwordHash: string;
  sessions: Record<string, number>;
};
type StoredPost = {
  post: Post;
  votes: Record<string, ReactionKind>;
  commentLikes: Record<string, string[]>;
};
type Vault = {
  app: 'nabd-direct-telegram';
  version: 1;
  namespace: string;
  revision: number;
  writeId: string;
  parent: number | null;
  users: Record<string, Account>;
  posts: Record<string, StoredPost>;
  friendships: [string, string][];
};
function fail(code: string): never {
  throw new Error(code);
}
const validHandle = (value: string) => {
  const handle = value.trim().replace(/^@/, '').toLowerCase();
  if (!/^[a-z][a-z0-9_]{2,39}$/.test(handle)) fail('INVALID_USERNAME');
  return handle;
};
const plain = (value: unknown): value is Record<string, any> =>
  !!value &&
  typeof value === 'object' &&
  !Array.isArray(value) &&
  !Object.keys(value).some((key) =>
    ['__proto__', 'constructor', 'prototype'].includes(key),
  );

export function createTelegramApi(deps: {
  transport: TelegramTransport;
  namespace: string;
  random: () => string;
  getToken: () => string | null;
  avatar: (file: LocalFile) => Promise<string>;
  now?: () => number;
}) {
  const { transport, namespace } = deps;
  const now = deps.now ?? Date.now;
  let queue: Promise<unknown> = Promise.resolve();
  const id = () => deps.random().replace(/-/g, '');
  const empty = (): Vault => ({
    app: 'nabd-direct-telegram',
    version: 1,
    namespace,
    revision: 0,
    writeId: id(),
    parent: null,
    users: {},
    posts: {},
    friendships: [],
  });
  function validate(value: unknown): Vault {
    const invalid = () => fail('TELEGRAM_INVALID_VAULT');
    const uid = (v: unknown): v is string =>
      typeof v === 'string' && /^[a-f0-9]{32}$/.test(v);
    const count = (v: unknown) => Number.isSafeInteger(v) && (v as number) >= 0;
    const image = (v: unknown) =>
      typeof v === 'string' &&
      v.length <= Math.ceil((128 * 1024) / 3) * 4 + 32 &&
      /^data:image\/(jpeg|png|webp);base64,[a-zA-Z0-9+/=]+$/.test(v);
    const date = (v: unknown) =>
      typeof v === 'string' && Number.isFinite(Date.parse(v));
    if (!plain(value)) return invalid();
    if (
      value.app !== 'nabd-direct-telegram' ||
      value.version !== 1 ||
      value.namespace !== namespace ||
      !count(value.revision) ||
      !uid(value.writeId) ||
      !(value.parent === null || (count(value.parent) && value.parent > 0)) ||
      !plain(value.users) ||
      !plain(value.posts) ||
      !Array.isArray(value.friendships)
    )
      return invalid();
    if (utf8(JSON.stringify(value)).length > MAX_VAULT_BYTES)
      fail('TELEGRAM_VAULT_FULL');
    if (
      Object.keys(value).some(
        (key) =>
          ![
            'app',
            'version',
            'namespace',
            'revision',
            'writeId',
            'parent',
            'users',
            'posts',
            'friendships',
          ].includes(key),
      )
    )
      return invalid();
    const handles = new Set<string>();
    const emails = new Set<string>();
    for (const [key, user] of Object.entries(
      value.users as Record<string, Account>,
    )) {
      if (
        !uid(key) ||
        !plain(user) ||
        !plain(user.profile) ||
        user.profile.id !== key ||
        typeof user.profile.name !== 'string' ||
        user.profile.name.length < 2 ||
        user.profile.name.length > 40 ||
        typeof user.profile.email !== 'string' ||
        user.profile.email.length > 254 ||
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(user.profile.email) ||
        user.profile.email !== user.profile.email.toLowerCase() ||
        typeof user.profile.username !== 'string' ||
        !/^[a-z][a-z0-9_]{2,39}$/.test(user.profile.username) ||
        !/^[a-f0-9]{32}$/.test(user.salt) ||
        !/^[a-f0-9]{64}$/.test(user.passwordHash) ||
        !plain(user.sessions)
      )
        return invalid();
      if (
        typeof user.profile.color !== 'string' ||
        !/^#[a-fA-F0-9]{6}$/.test(user.profile.color) ||
        typeof user.profile.initial !== 'string' ||
        (user.profile.bio != null &&
          (typeof user.profile.bio !== 'string' ||
            user.profile.bio.length > 160)) ||
        (user.profile.avatarUrl != null && !image(user.profile.avatarUrl))
      )
        return invalid();
      if (
        Object.entries(user.sessions).some(
          ([key, expiry]) => !/^[a-f0-9]{64}$/.test(key) || !count(expiry),
        )
      )
        return invalid();
      if (handles.has(user.profile.username) || emails.has(user.profile.email))
        return invalid();
      handles.add(user.profile.username);
      emails.add(user.profile.email);
    }
    const exists = (key: unknown) =>
      uid(key) && Object.prototype.hasOwnProperty.call(value.users, key);
    for (const pair of value.friendships)
      if (
        !Array.isArray(pair) ||
        pair.length !== 2 ||
        pair[0] === pair[1] ||
        pair.some((key) => !exists(key))
      )
        return invalid();
    const kinds: ReactionKind[] = [
      'like',
      'love',
      'haha',
      'wow',
      'sad',
      'angry',
    ];
    for (const [pid, stored] of Object.entries(
      value.posts as Record<string, StoredPost>,
    )) {
      if (
        !uid(pid) ||
        !plain(stored) ||
        !plain(stored.post) ||
        stored.post.id !== pid ||
        !exists(stored.post.author?.id) ||
        !plain(stored.votes) ||
        !plain(stored.commentLikes) ||
        !Array.isArray(stored.post.comments) ||
        typeof stored.post.text !== 'string' ||
        stored.post.text.length > 5000 ||
        stored.post.privacy !== 'public' ||
        !date(stored.post.createdAt) ||
        !plain(stored.post.reactions) ||
        !count(stored.post.shares) ||
        stored.post.commentsCount !== stored.post.comments.length
      )
        return invalid();
      if (
        Object.entries(stored.votes).some(
          ([key, vote]) => !exists(key) || !kinds.includes(vote),
        ) ||
        kinds.some(
          (kind) =>
            !count(stored.post.reactions[kind]) ||
            stored.post.reactions[kind] !==
              Object.values(stored.votes).filter((v) => v === kind).length,
        )
      )
        return invalid();
      const media = stored.post.media;
      if (
        media &&
        (!plain(media) ||
          !uid(media.id) ||
          media.kind !== 'image' ||
          !image(media.uri) ||
          !Number.isFinite(media.aspectRatio) ||
          media.aspectRatio <= 0 ||
          !count(media.width) ||
          !count(media.height))
      )
        return invalid();
      const seen = new Set<string>();
      for (const comment of stored.post.comments) {
        if (
          !plain(comment) ||
          !uid(comment.id) ||
          seen.has(comment.id) ||
          !exists(comment.author?.id) ||
          typeof comment.text !== 'string' ||
          comment.text.length > 2000 ||
          !date(comment.createdAt) ||
          !count(comment.likes)
        )
          return invalid();
        seen.add(comment.id);
        const likes = stored.commentLikes[comment.id] ?? [];
        if (
          !Array.isArray(likes) ||
          new Set(likes).size !== likes.length ||
          likes.some((key) => !exists(key)) ||
          likes.length !== comment.likes
        )
          return invalid();
      }
      if (Object.keys(stored.commentLikes).some((key) => !seen.has(key)))
        return invalid();
    }
    return value as Vault;
  }
  function pointer(chat: Chat): number | null {
    const pin = chat.pinned_message;
    if (!pin) return null;
    if (
      pin.document?.file_name !== 'nabd-vault.json' ||
      pin.caption !== 'nabd-direct-telegram-v1' ||
      !pin.document.file_id ||
      !Number.isSafeInteger(pin.message_id) ||
      pin.message_id <= 0
    )
      fail('TELEGRAM_FOREIGN_PIN');
    if (pin.document.file_size! > MAX_VAULT_BYTES) fail('TELEGRAM_VAULT_FULL');
    return pin.message_id;
  }
  async function load() {
    const chat = await transport.chat();
    const messageId = pointer(chat);
    if (!messageId) return fail('TELEGRAM_VAULT_MISSING');
    return {
      vault: validate(
        await transport.read(chat.pinned_message!.document!.file_id),
      ),
      messageId,
    };
  }
  async function commit(vault: Vault, parent: number | null) {
    vault.revision++;
    vault.writeId = id();
    vault.parent = parent;
    const serialized = JSON.stringify(validate(vault));
    if (pointer(await transport.chat()) !== parent) fail('TELEGRAM_CONFLICT');
    const result = await transport.upload(serialized);
    if (pointer(await transport.chat()) !== parent) fail('TELEGRAM_CONFLICT');
    await transport.pin(result.messageId);
    const confirmed = await load();
    if (
      confirmed.messageId !== result.messageId ||
      confirmed.vault.writeId !== vault.writeId
    )
      fail('TELEGRAM_CONFLICT');
    // No deletion of the previous snapshot. Cross-device writes are NOT atomic;
    // these checks detect some races, not all possible lost updates.
  }
  function serial<T>(run: () => Promise<T>): Promise<T> {
    const next = queue.then(run, run);
    queue = next.catch(() => undefined);
    return next;
  }
  function actor(vault: Vault): Account {
    const parts = deps.getToken()?.split(':');
    if (
      !parts ||
      parts.length !== 4 ||
      parts[0] !== 'nabd-tg' ||
      parts[1] !== namespace
    )
      return fail('TELEGRAM_SESSION_REQUIRED');
    const account = /^[a-f0-9]{32}$/.test(parts[2])
      ? vault.users[parts[2]]
      : undefined;
    if (
      !account ||
      !/^[a-f0-9]{64}$/.test(parts[3]) ||
      (account.sessions[digest(parts[3])] ?? 0) <= now()
    )
      return fail('TELEGRAM_SESSION_REQUIRED');
    return account;
  }
  function publicUser(vault: Vault, account: Account, viewer?: string): Author {
    const profile = account.profile;
    return {
      id: profile.id,
      name: profile.name,
      username: profile.username,
      email: profile.id === viewer ? profile.email : undefined,
      color: profile.color,
      initial: profile.initial,
      avatarUrl: profile.avatarUrl ?? null,
      bio: profile.bio ?? '',
      verified: false,
      isAdmin: false,
      friendsCount: vault.friendships.filter((p) => p.includes(profile.id))
        .length,
      postsCount: Object.values(vault.posts).filter(
        (p) => p.post.author.id === profile.id,
      ).length,
      isMe: profile.id === viewer,
      isFriend:
        !!viewer &&
        vault.friendships.some(
          (p) => p.includes(viewer) && p.includes(profile.id),
        ),
    };
  }
  function session(vault: Vault, account: Account): AuthPayload {
    const secret = id() + id();
    account.sessions = Object.fromEntries(
      Object.entries(account.sessions).filter(([, expiry]) => expiry > now()),
    );
    if (Object.keys(account.sessions).length >= 8)
      delete account.sessions[Object.keys(account.sessions)[0]];
    account.sessions[digest(secret)] = now() + 30 * 24 * 60 * 60 * 1000;
    return {
      token: `nabd-tg:${namespace}:${account.profile.id}:${secret}`,
      user: publicUser(vault, account, account.profile.id),
    };
  }
  function mutate<T>(run: (vault: Vault, self: Account) => Promise<T> | T) {
    return serial(async () => {
      const { vault, messageId } = await load();
      const self = actor(vault);
      const result = await run(vault, self);
      await commit(vault, messageId);
      return result;
    });
  }
  function presented(vault: Vault, stored: StoredPost, viewer: string): Post {
    return {
      ...stored.post,
      author: publicUser(vault, vault.users[stored.post.author.id], viewer),
      comments: stored.post.comments.map((c) => ({
        ...c,
        author: publicUser(vault, vault.users[c.author.id], viewer),
        likedByMe: (stored.commentLikes[c.id] ?? []).includes(viewer),
      })),
      myReaction: stored.votes[viewer] ?? null,
    };
  }
  function visible(vault: Vault, post: Post, viewer: string) {
    return (
      post.author.id === viewer ||
      post.privacy === 'public' ||
      (post.privacy === 'friends' &&
        vault.friendships.some(
          (p) => p.includes(post.author.id) && p.includes(viewer),
        ))
    );
  }
  async function posts() {
    const { vault } = await load();
    const self = actor(vault);
    return Object.values(vault.posts)
      .filter((s) => visible(vault, s.post, self.profile.id))
      .map((s) => presented(vault, s, self.profile.id))
      .sort(
        (a, b) =>
          b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id),
      );
  }
  return {
    async status() {
      await load();
      return true;
    },
    async initialize() {
      return serial(async () => {
        if (pointer(await transport.chat()) !== null)
          fail('TELEGRAM_ALREADY_INITIALIZED');
        await commit(empty(), null);
        return true;
      });
    },
    async register(
      name: string,
      email: string,
      password: string,
      file?: LocalFile,
    ): Promise<AuthPayload> {
      name = name.trim();
      email = email.trim().toLowerCase();
      if (name.length < 2 || name.length > 40) fail('NAME_TOO_SHORT');
      if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
        fail('INVALID_EMAIL');
      if (password.length < 8 || password.length > 128)
        fail('TELEGRAM_PASSWORD_LENGTH');
      const salt = id();
      const hash = await passwordDigest(password, salt);
      const picture = file ? await deps.avatar(file) : null;
      return serial(async () => {
        const { vault, messageId } = await load();
        if (Object.values(vault.users).some((u) => u.profile.email === email))
          fail('EMAIL_TAKEN');
        const uid = id();
        let username = `user_${uid.slice(0, 16)}`;
        if (
          Object.values(vault.users).some(
            (u) => u.profile.username === username,
          )
        )
          username = `user_${id().slice(0, 24)}`;
        const account: Account = {
          profile: {
            id: uid,
            name,
            email,
            username,
            color: '#1877F2',
            initial: name.charAt(0),
            avatarUrl: picture,
            bio: '',
          },
          salt,
          passwordHash: hash,
          sessions: {},
        };
        vault.users[uid] = account;
        const payload = session(vault, account);
        await commit(vault, messageId);
        return payload;
      });
    },
    async login(email: string, password: string): Promise<AuthPayload> {
      const initial = await load();
      const account = Object.values(initial.vault.users).find(
        (u) => u.profile.email === email.trim().toLowerCase(),
      );
      if (
        !account ||
        password.length > 128 ||
        (await passwordDigest(password, account.salt)) !== account.passwordHash
      )
        return fail('INVALID_CREDENTIALS');
      return serial(async () => {
        const { vault, messageId } = await load();
        const current = vault.users[account.profile.id];
        if (!current || current.passwordHash !== account.passwordHash)
          return fail('INVALID_CREDENTIALS');
        const payload = session(vault, current);
        await commit(vault, messageId);
        return payload;
      });
    },
    async me() {
      const { vault } = await load();
      const self = actor(vault);
      return publicUser(vault, self, self.profile.id);
    },
    async logout() {
      return mutate((vault, self) => {
        const secret = deps.getToken()!.split(':')[3];
        delete self.sessions[digest(secret)];
        return true;
      });
    },
    async updateProfile(input: {
      name?: string;
      bio?: string;
      username?: string;
      file?: LocalFile;
    }) {
      const picture = input.file ? await deps.avatar(input.file) : undefined;
      return mutate((vault, self) => {
        if (input.name !== undefined) {
          const name = input.name.trim();
          if (name.length < 2 || name.length > 40) fail('NAME_TOO_SHORT');
          self.profile.name = name;
          self.profile.initial = name.charAt(0);
        }
        if (input.username !== undefined) {
          const username = validHandle(input.username);
          if (
            Object.values(vault.users).some(
              (u) => u !== self && u.profile.username === username,
            )
          )
            fail('USERNAME_TAKEN');
          self.profile.username = username;
        }
        if (input.bio !== undefined) self.profile.bio = input.bio.slice(0, 160);
        if (picture !== undefined) self.profile.avatarUrl = picture;
        return publicUser(vault, self, self.profile.id);
      });
    },
    async users(search = '') {
      const { vault } = await load();
      const viewer = actor(vault).profile.id;
      const term = search.trim().replace(/^@/, '').toLowerCase();
      return Object.values(vault.users)
        .filter(
          (u) =>
            u.profile.id !== viewer &&
            (!term ||
              u.profile.username!.includes(term) ||
              (!search.trim().startsWith('@') &&
                u.profile.name.toLowerCase().includes(term))),
        )
        .sort(
          (a, b) =>
            Number(b.profile.username === term) -
            Number(a.profile.username === term),
        )
        .map((u) => publicUser(vault, u, viewer));
    },
    async friends() {
      const { vault } = await load();
      const viewer = actor(vault).profile.id;
      return Object.values(vault.users)
        .filter(
          (u) =>
            u.profile.id !== viewer &&
            vault.friendships.some(
              (p) => p.includes(viewer) && p.includes(u.profile.id),
            ),
        )
        .map((u) => publicUser(vault, u, viewer));
    },
    async suggestions() {
      const { vault } = await load();
      const viewer = actor(vault).profile.id;
      return Object.values(vault.users)
        .filter(
          (u) =>
            u.profile.id !== viewer &&
            !vault.friendships.some(
              (p) => p.includes(viewer) && p.includes(u.profile.id),
            ),
        )
        .slice(0, 10)
        .map((u) => publicUser(vault, u, viewer));
    },
    async follow(uid: string) {
      return mutate((vault, self) => {
        if (!vault.users[uid] || uid === self.profile.id)
          fail('USER_NOT_FOUND');
        if (
          !vault.friendships.some(
            (p) => p.includes(uid) && p.includes(self.profile.id),
          )
        )
          vault.friendships.push(
            [uid, self.profile.id].sort() as [string, string],
          );
        return publicUser(vault, vault.users[uid], self.profile.id);
      });
    },
    async unfollow(uid: string) {
      return mutate((vault, self) => {
        if (!vault.users[uid]) fail('USER_NOT_FOUND');
        vault.friendships = vault.friendships.filter(
          (p) => !(p.includes(uid) && p.includes(self.profile.id)),
        );
        return publicUser(vault, vault.users[uid], self.profile.id);
      });
    },
    async fetchFeed(cursor: string | null = null, limit = 5) {
      const list = await posts();
      const found = cursor ? list.findIndex((p) => p.id === cursor) + 1 : 0;
      const page = list.slice(found, found + Math.max(1, Math.min(limit, 20)));
      return {
        posts: page,
        nextCursor:
          found + page.length < list.length ? page[page.length - 1].id : null,
      };
    },
    async userPosts(uid: string) {
      return (await posts()).filter((p) => p.author.id === uid);
    },
    async searchPosts(search: string) {
      return (await posts()).filter((p) =>
        p.text.toLowerCase().includes(search.trim().toLowerCase()),
      );
    },
    async createPost(input: {
      text: string;
      privacy?: 'public' | 'friends' | 'private';
      file?: LocalFile;
      aspectRatio?: number;
    }) {
      if (input.privacy && input.privacy !== 'public')
        fail('TELEGRAM_PUBLIC_ONLY');
      if (input.text.length > 5000) fail('TELEGRAM_TEXT_TOO_LONG');
      const picture = input.file ? await deps.avatar(input.file) : undefined;
      if (!input.text.trim() && !picture) fail('VALIDATION');
      return mutate((vault, self) => {
        const pid = id();
        const post: Post = {
          id: pid,
          text: input.text.trim(),
          privacy: input.privacy ?? 'public',
          createdAt: new Date(now()).toISOString(),
          author: publicUser(vault, self, self.profile.id),
          reactions: { like: 0, love: 0, haha: 0, wow: 0, sad: 0, angry: 0 },
          myReaction: null,
          comments: [],
          commentsCount: 0,
          shares: 0,
          ...(picture
            ? {
                media: {
                  id: id(),
                  uri: picture,
                  width: 0,
                  height: 0,
                  aspectRatio: input.aspectRatio ?? 1,
                  kind: 'image' as const,
                  mime: input.file!.type,
                },
              }
            : {}),
        };
        vault.posts[pid] = { post, votes: {}, commentLikes: {} };
        return post;
      });
    },
    async deletePost(pid: string) {
      return mutate((vault, self) => {
        if (vault.posts[pid]?.post.author.id !== self.profile.id)
          fail('FORBIDDEN');
        delete vault.posts[pid];
        return true;
      });
    },
    async react(pid: string, reaction: ReactionKind | null) {
      return mutate((vault, self) => {
        const stored = vault.posts[pid];
        if (!stored || !visible(vault, stored.post, self.profile.id))
          fail('POST_NOT_FOUND');
        const old = stored.votes[self.profile.id];
        if (old) stored.post.reactions[old]--;
        if (reaction) {
          if (!(reaction in stored.post.reactions)) fail('VALIDATION');
          stored.votes[self.profile.id] = reaction;
          stored.post.reactions[reaction]++;
        } else delete stored.votes[self.profile.id];
      });
    },
    async addComment(pid: string, text: string) {
      return mutate((vault, self) => {
        const stored = vault.posts[pid];
        if (!stored || !visible(vault, stored.post, self.profile.id))
          fail('POST_NOT_FOUND');
        if (!text.trim() || text.length > 2000) fail('VALIDATION');
        const comment = {
          id: id(),
          text: text.trim(),
          author: publicUser(vault, self, self.profile.id),
          createdAt: new Date(now()).toISOString(),
          likes: 0,
          likedByMe: false,
        };
        stored.post.comments.push(comment);
        stored.post.commentsCount++;
        return comment;
      });
    },
    async deleteComment(pid: string, cid: string) {
      return mutate((vault, self) => {
        const post = vault.posts[pid]?.post;
        const comment = post?.comments.find((c) => c.id === cid);
        if (
          !post ||
          !comment ||
          (comment.author.id !== self.profile.id &&
            post.author.id !== self.profile.id)
        )
          fail('FORBIDDEN');
        post.comments = post.comments.filter((c) => c.id !== cid);
        post.commentsCount = post.comments.length;
        delete vault.posts[pid].commentLikes[cid];
      });
    },
    async likeComment(pid: string, cid: string) {
      return mutate((vault, self) => {
        const stored = vault.posts[pid];
        const comment = stored?.post.comments.find((c) => c.id === cid);
        if (
          !stored ||
          !comment ||
          !visible(vault, stored.post, self.profile.id)
        )
          fail('POST_NOT_FOUND');
        const likes = stored.commentLikes[cid] ?? [];
        stored.commentLikes[cid] = likes.includes(self.profile.id)
          ? likes.filter((uid) => uid !== self.profile.id)
          : [...likes, self.profile.id];
        comment.likes = stored.commentLikes[cid].length;
        return {
          ...comment,
          likedByMe: stored.commentLikes[cid].includes(self.profile.id),
        };
      });
    },
    async reels() {
      return { posts: [], nextCursor: null };
    },
    async unreadMessagesCount() {
      return 0;
    },
    async fetchNotifications() {
      return [];
    },
    async notifications() {
      return { notifications: [], unreadNotificationsCount: 0 };
    },
    async reelsIngestStatus() {
      return {
        enabled: false,
        running: false,
        intervalMinutes: 0,
        lastRunAt: null,
        nextRunAt: null,
        queuePending: 0,
        queueImported: 0,
        totalsImported: 0,
      };
    },
  };
}
