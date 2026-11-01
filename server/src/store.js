/**
 * طبقة البيانات — PostgreSQL.
 *
 * - المستخدمون والصداقات وعدد المنشورات: كيتقراو من القاعدة عند الإقلاع وكيتخزّنو فكاش
 *   صغير فالذاكرة (باش publicUser / areFriends يبقاو سريعين وبلا await فكل بلاصة).
 *   كل كتابة كتمشي للقاعدة + الكاش فنفس الوقت.
 * - المنشورات · التفاعلات · التعليقات · الإشعارات · الوسائط: كلشي كيتقرا ويكتب مباشرة فـ SQL.
 *
 * كل الدوال اللي كتعالج المنشورات/التعليقات/الإشعارات async.
 */
import { randomUUID } from 'node:crypto';
import { hashPassword } from './auth.js';
import { normalizeUsername, rethrowUsernameConflict } from './usernames.js';
import { query, queryOne } from './db.js';
import { publishToUser } from './bus.js';

const shortId = (prefix) => `${prefix}_${randomUUID().slice(0, 8)}`;
const iso = (value) => (value instanceof Date ? value.toISOString() : value);

const REACTION_KINDS = ['like', 'love', 'haha', 'wow', 'sad', 'angry'];
const emptyReactions = () => ({ like: 0, love: 0, haha: 0, wow: 0, sad: 0, angry: 0 });

/* ============================ الكاش ============================ */

/** id → user (بما فيه passwordHash — ما كيخرجش للعميل أبداً) */
export const users = new Map();

/** userId → Set<userId> */
export const friendships = new Map();

/** authorId → عدد المنشورات */
const postCounts = new Map();

const rowToUser = (row) => ({
  id: row.id,
  name: row.name,
  username: row.username,
  email: row.email,
  passwordHash: row.password_hash,
  color: row.color,
  initial: row.initial,
  avatarMediaId: row.avatar_media_id,
  verified: row.verified,
  bio: row.bio,
  isAdmin: row.is_admin === true,
  createdAt: iso(row.created_at),
});

const cacheUser = (row) => {
  const user = rowToUser(row);
  users.set(user.id, user);
  return user;
};

const linkCache = (a, b) => {
  if (!friendships.has(a)) friendships.set(a, new Set());
  if (!friendships.has(b)) friendships.set(b, new Set());
  friendships.get(a).add(b);
  friendships.get(b).add(a);
};

const unlinkCache = (a, b) => {
  friendships.get(a)?.delete(b);
  friendships.get(b)?.delete(a);
};

/** تحميل الكاش من PostgreSQL — كيتنادى مرة واحدة عند الإقلاع */
export async function loadCaches() {
  users.clear();
  friendships.clear();
  postCounts.clear();

  const userRows = await query('select * from users order by created_at asc');
  userRows.forEach(cacheUser);

  const pairs = await query('select user_a, user_b from friendships');
  pairs.forEach(({ user_a, user_b }) => linkCache(user_a, user_b));

  const counts = await query('select author_id, count(*)::int as n from posts group by author_id');
  counts.forEach(({ author_id, n }) => postCounts.set(author_id, Number(n)));

  return { users: users.size, friendships: pairs.length };
}

export const postsCountOf = (userId) => postCounts.get(userId) ?? 0;

/* ======================= إعدادات التطبيق (مفتاح/قيمة) ======================= */

export async function getSetting(key) {
  const row = await queryOne('select value from app_settings where key = $1', [key]);
  return row?.value ?? null;
}

export async function setSetting(key, value) {
  await query(
    `insert into app_settings (key, value, updated_at) values ($1, $2, now())
     on conflict (key) do update set value = excluded.value, updated_at = now()`,
    [key, value],
  );
  return value;
}

export async function deleteSetting(key) {
  const row = await queryOne('delete from app_settings where key = $1 returning key', [key]);
  return !!row;
}

/** كيخلي مستخدم أدمن (ولا كيحيد الصفة) */
export async function setUserAdmin(userId, isAdmin = true) {
  const row = await queryOne('update users set is_admin = $2 where id = $1 returning *', [userId, isAdmin]);
  return row ? cacheUser(row) : null;
}

/** كيتأكد أن كاين أدمن — إلا ما كانش، أقدم مستخدم كيولّي أدمن */
export async function ensureAdminExists() {
  const existing = await queryOne('select id from users where is_admin = true limit 1');
  if (existing) return null;

  const row = await queryOne('select * from users order by created_at asc limit 1');
  if (!row) return null;

  await queryOne('update users set is_admin = true where id = $1', [row.id]);
  const user = cacheUser({ ...row, is_admin: true });
  return user;
}

/* ============================ المستخدمون ============================ */

export const findUserByEmail = (email) => {
  const clean = email.trim().toLowerCase();
  return [...users.values()].find((u) => u.email === clean) ?? null;
};

export async function createUser({ name, email, password, color, username, verified = false, bio = '' }) {
  const cleanName = name.trim();
  const cleanEmail = email.trim().toLowerCase();
  const id = `u_${randomUUID().slice(0, 8)}`;

  const handle = username == null ? `user_${randomUUID().replaceAll('-', '')}` : normalizeUsername(username);
  let row;
  try {
    row = await queryOne(
      `insert into users (id, name, email, password_hash, color, initial, verified, bio, username)
       values ($1, $2, $3, $4, $5, $6, $7, $8, $9) returning *`,
      [id, cleanName, cleanEmail, hashPassword(password), color, cleanName.charAt(0), verified, bio, handle],
    );
  } catch (error) { rethrowUsernameConflict(error); }
  return cacheUser(row);
}

export async function updateUserProfile(userId, { name, bio, username }) {
  const current = users.get(userId);
  if (!current) throw new Error('USER_NOT_FOUND');

  const nextName = typeof name === 'string' && name.trim().length >= 2 ? name.trim() : current.name;
  const nextBio = typeof bio === 'string' ? bio.slice(0, 160) : current.bio;

  const nextUsername = username === undefined ? current.username : normalizeUsername(username);
  let row;
  try {
    row = await queryOne(
      `update users set name = $2, initial = $3, bio = $4, username = $5 where id = $1 returning *`,
      [userId, nextName, nextName.charAt(0), nextBio, nextUsername],
    );
  } catch (error) { rethrowUsernameConflict(error); }
  return cacheUser(row);
}

/** تعيين الصورة الشخصية (بعد رفعها للتخزين) */
export async function setUserAvatar(userId, mediaId) {
  const row = await queryOne('update users set avatar_media_id = $2 where id = $1 returning *', [userId, mediaId]);
  if (!row) throw new Error('USER_NOT_FOUND');
  return cacheUser(row);
}

export const publicUser = (user) => user && {
  id: user.id,
  name: user.name,
  username: user.username,
  email: user.email,
  initial: user.initial,
  color: user.color,
  avatarUrl: user.avatarMediaId ? `/media/${user.avatarMediaId}` : null,
  verified: user.verified,
  bio: user.bio,
  isAdmin: user.isAdmin === true,
  friendsCount: friendsOf(user.id).length,
  postsCount: postsCountOf(user.id),
};

export const searchUsers = ({ viewerId, search = '', limit = 20 }) => {
  const handleOnly = search.trim().startsWith('@');
  const term = search.trim().replace(/^@/, '').toLowerCase();
  return [...users.values()]
    .filter((user) => user.id !== viewerId)
    .filter((user) => !term || (handleOnly ? user.username?.includes(term) : user.name.toLowerCase().includes(term) || user.username?.includes(term) || user.email.toLowerCase().includes(term)))
    .sort((a, b) => Number(b.username === term) - Number(a.username === term))
    .slice(0, limit);
};

/** «أشخاص قد تعرفهم»: أصدقاء الأصدقاء أولاً، ثم الباقي */
export const suggestions = ({ viewerId, limit = 10 }) => {
  const myFriends = new Set(friendsOf(viewerId));
  const score = new Map();

  for (const friendId of myFriends) {
    for (const candidate of friendsOf(friendId)) {
      if (candidate === viewerId || myFriends.has(candidate)) continue;
      score.set(candidate, (score.get(candidate) ?? 0) + 1);
    }
  }

  const ranked = [...score.entries()].sort((a, b) => b[1] - a[1]).map(([id]) => id);
  const others = [...users.keys()].filter((id) => id !== viewerId && !myFriends.has(id) && !ranked.includes(id));

  return [...ranked, ...others].slice(0, limit).map((id) => users.get(id)).filter(Boolean);
};

/* ============================ الصداقات ============================ */

export const areFriends = (a, b) => friendships.get(a)?.has(b) ?? false;

export const friendsOf = (userId) => [...(friendships.get(userId) ?? [])];

export async function follow(userId, targetId) {
  if (userId === targetId) throw new Error('CANNOT_FOLLOW_YOURSELF');
  if (!users.has(targetId)) throw new Error('USER_NOT_FOUND');

  const [a, b] = [userId, targetId].sort();
  await query('insert into friendships (user_a, user_b) values ($1, $2) on conflict do nothing', [a, b]);
  linkCache(userId, targetId);
}

export async function unfollow(userId, targetId) {
  const [a, b] = [userId, targetId].sort();
  await query('delete from friendships where user_a = $1 and user_b = $2', [a, b]);
  unlinkCache(userId, targetId);
}

/* ============================ الوسائط ============================ */

export async function insertMedia({ id, provider = null, fileId = null, mime = null, size = null, aspectRatio = 1.3333, alt = null }) {
  const row = await queryOne(
    `insert into media (id, provider, file_id, mime, size, aspect_ratio, alt)
     values ($1, $2, $3, $4, $5, $6, $7) returning *`,
    [id, provider, fileId, mime, size, aspectRatio, alt],
  );
  return {
    id: row.id,
    provider: row.provider,
    fileId: row.file_id,
    mime: row.mime,
    size: row.size === null ? null : Number(row.size),
    aspectRatio: row.aspect_ratio,
    alt: row.alt,
    createdAt: iso(row.created_at),
  };
}

export async function getMedia(id) {
  const row = await queryOne('select * from media where id = $1', [id]);
  if (!row) return null;
  return {
    id: row.id,
    provider: row.provider,
    fileId: row.file_id,
    mime: row.mime,
    size: row.size === null ? null : Number(row.size),
    aspectRatio: row.aspect_ratio,
    alt: row.alt,
    createdAt: iso(row.created_at),
  };
}

export const isVideoMime = (mime) => typeof mime === 'string' && mime.startsWith('video/');

const mediaToPostMedia = (row) =>
  row.media_id
    ? {
        id: row.media_id,
        uri: `/media/${row.media_id}`,
        width: null,
        height: null,
        aspectRatio: row.media_aspect ?? 4 / 3,
        alt: row.media_alt ?? null,
        mime: row.media_mime ?? null,
        kind: isVideoMime(row.media_mime) ? 'video' : 'image',
      }
    : null;

/* ============================ المنشورات ============================ */

const POST_SELECT = `
  select p.id, p.author_id, p.text, p.privacy, p.shares, p.created_at, p.media_id,
         p.source, p.source_url, p.source_author,
         m.aspect_ratio as media_aspect, m.alt as media_alt, m.mime as media_mime,
         (select count(*)::int from reel_views rv where rv.post_id = p.id) as views
  from posts p
  left join media m on m.id = p.media_id
`;

/** كيكمّل المنشورات بالتفاعلات والتعليقات فاستعلامات قليلة (بلا N+1) */
async function hydratePosts(rows, viewerId) {
  if (!rows.length) return [];
  const ids = rows.map((r) => r.id);

  const [reactionRows, mineRows, commentRows] = await Promise.all([
    query(
      `select post_id, kind, count(*)::int as n from reactions
       where post_id = any($1::text[]) group by post_id, kind`,
      [ids],
    ),
    viewerId
      ? query(
          `select post_id, kind from reactions where user_id = $1 and post_id = any($2::text[])`,
          [viewerId, ids],
        )
      : Promise.resolve([]),
    query(
      `select c.id, c.post_id, c.author_id, c.text, c.created_at,
              coalesce(l.n, 0)::int as likes,
              exists (select 1 from comment_likes cl where cl.comment_id = c.id and cl.user_id = $1) as liked_by_me
       from comments c
       left join (select comment_id, count(*)::int as n from comment_likes group by comment_id) l on l.comment_id = c.id
       where c.post_id = any($2::text[])
       order by c.created_at asc`,
      [viewerId, ids],
    ),
  ]);

  const reactionsByPost = new Map();
  reactionRows.forEach(({ post_id, kind, n }) => {
    const counts = reactionsByPost.get(post_id) ?? emptyReactions();
    counts[kind] = Number(n);
    reactionsByPost.set(post_id, counts);
  });

  const mineByPost = new Map(mineRows.map((r) => [r.post_id, r.kind]));

  const commentsByPost = new Map();
  commentRows.forEach((c) => {
    const list = commentsByPost.get(c.post_id) ?? [];
    list.push({
      id: c.id,
      postId: c.post_id,
      authorId: c.author_id,
      text: c.text,
      createdAt: iso(c.created_at),
      likes: Number(c.likes),
      likedByMe: Boolean(c.liked_by_me),
    });
    commentsByPost.set(c.post_id, list);
  });

  return rows.map((row) => ({
    id: row.id,
    authorId: row.author_id,
    text: row.text,
    privacy: row.privacy,
    shares: Number(row.shares ?? 0),
    views: Number(row.views ?? 0),
    source: row.source ?? 'nabd',
    sourceUrl: row.source_url ?? null,
    sourceAuthor: row.source_author ?? null,
    createdAt: iso(row.created_at),
    media: mediaToPostMedia(row),
    reactions: reactionsByPost.get(row.id) ?? emptyReactions(),
    myReaction: mineByPost.get(row.id) ?? null,
    comments: commentsByPost.get(row.id) ?? [],
  }));
}

export async function getPost(id, viewerId = null) {
  const row = await queryOne(`${POST_SELECT} where p.id = $1`, [id]);
  if (!row) return null;
  const [post] = await hydratePosts([row], viewerId);
  return post ?? null;
}

export async function createPost({
  authorId,
  text,
  privacy = 'public',
  mediaId = null,
  source = 'nabd',
  sourceUrl = null,
  sourceAuthor = null,
}) {
  const row = await queryOne(
    `insert into posts (id, author_id, text, privacy, media_id, source, source_url, source_author)
     values ($1, $2, $3, $4, $5, $6, $7, $8) returning id`,
    [shortId('p'), authorId, text, privacy, mediaId, source, sourceUrl, sourceAuthor],
  );
  postCounts.set(authorId, (postCounts.get(authorId) ?? 0) + 1);
  return row.id;
}

/* ==================== طابور الجلب التلقائي لريلز إنستغرام ==================== */

/** كيزيد كود للطابور (إلا كان كاين ما كيدير والو) → true إلا تزاد */
export async function enqueueIgCode(shortcode, source = 'catalog') {
  const row = await queryOne(
    `insert into ig_queue (shortcode, source) values ($1, $2)
     on conflict (shortcode) do nothing returning shortcode`,
    [shortcode, source],
  );
  return !!row;
}

/** الأكواد اللي خدامين عليها دابا (pending، بالتناوب على المحاولات) */
export async function nextIgQueue(limit = 3, maxAttempts = 3) {
  const rows = await query(
    `select shortcode, source, attempts from ig_queue
     where status = 'pending' and attempts < $1
     order by attempts asc, created_at asc
     limit $2`,
    [maxAttempts, limit],
  );
  return rows;
}

/** كيسجل نتيجة المعالجة */
export async function finishIgCode({ shortcode, status, error = null, postId = null }) {
  await query(
    `update ig_queue
       set status = $2, attempts = attempts + 1, last_error = $3, post_id = $4, updated_at = now()
     where shortcode = $1`,
    [shortcode, status, error, postId],
  );
}

/** كيرجّع للطابور الأكواد اللي فشلت بأخطاء مؤقتة (شبكة/حجب) بعد مدة كافية */
export async function requeueTransientIgCodes(limit = 2, olderThanMinutes = 360) {
  const rows = await query(
    `update ig_queue
       set status = 'pending', attempts = 0, updated_at = now()
     where shortcode in (
       select shortcode from ig_queue
       where status = 'skipped'
         and (last_error like 'INSTAGRAM_%' or last_error like 'fetch failed%')
         and updated_at < now() - ($2 || ' minutes')::interval
       order by updated_at asc
       limit $1
     )
     returning shortcode`,
    [limit, String(olderThanMinutes)],
  );
  return rows.map((row) => row.shortcode);
}

/** شحال من ريل كاين دابا (باش نضمنو البلاصة ما تبقاش خاوية) */
export async function countReels() {
  const row = await queryOne(
    `select count(*)::int as n from posts p join media m on m.id = p.media_id where m.mime like 'video/%'`,
  );
  return Number(row?.n ?? 0);
}

/** إحصائيات الطابور */
export async function igQueueStats() {
  const rows = await query('select status, count(*)::int as n from ig_queue group by status');
  const stats = { pending: 0, imported: 0, failed: 0, skipped: 0 };
  rows.forEach((row) => {
    stats[row.status] = Number(row.n);
  });
  return stats;
}

/** واش الكود معالج من قبل؟ (باش ما نعاودوش نحاولو فيه) */
export async function isIgCodeKnown(shortcode) {
  const row = await queryOne('select status from ig_queue where shortcode = $1', [shortcode]);
  return !!row;
}

/** منشور مستورد من قبل (نفس الرابط) — باش الاستيراد يتعاود بلا تكرار */
export async function findPostBySourceUrl(sourceUrl) {
  const row = await queryOne(`${POST_SELECT} where p.source_url = $1`, [sourceUrl]);
  if (!row) return null;
  const [post] = await hydratePosts([row], null);
  return post ?? null;
}

/** تغذية مرتبة: منشوراتي + منشورات أصدقائي + العامة، الأحدث أولاً */
export async function listFeed({ viewerId, cursor = null, limit = 5 }) {
  const offset = cursor ? Number(cursor) : 0;
  const friendIds = viewerId ? friendsOf(viewerId) : [];

  const rows = await query(
    `${POST_SELECT}
     where coalesce(m.mime, '') not like 'video/%'   -- الفيديوهات عندها تبويب «ريلز»
       and case
             when $1::text is null then p.privacy = 'public'
             else p.author_id = $1
               or p.privacy = 'public'
               or (p.privacy = 'friends' and p.author_id = any($2::text[]))
           end
     order by p.created_at desc, p.id desc
     limit $3 offset $4`,
    [viewerId, friendIds, limit + 1, offset],
  );

  const page = rows.slice(0, limit);
  return {
    posts: await hydratePosts(page, viewerId),
    nextCursor: rows.length > limit ? String(offset + limit) : null,
  };
}

/** الريلز: منشورات فيها فيديو، الأحدث أولاً (بنفس قواعد الخصوصية) */
export async function listReels({ viewerId = null, cursor = null, limit = 6 } = {}) {
  const offset = cursor ? Number(cursor) : 0;
  const friendIds = viewerId ? friendsOf(viewerId) : [];

  const rows = await query(
    `${POST_SELECT}
     where m.mime like 'video/%'
       and case
             when $1::text is null then p.privacy = 'public'
             else p.author_id = $1
               or p.privacy = 'public'
               or (p.privacy = 'friends' and p.author_id = any($2::text[]))
           end
     order by p.created_at desc, p.id desc
     limit $3 offset $4`,
    [viewerId, friendIds, limit + 1, offset],
  );

  const page = rows.slice(0, limit);
  return {
    posts: await hydratePosts(page, viewerId),
    nextCursor: rows.length > limit ? String(offset + limit) : null,
  };
}

/** تسجيل مشاهدة ريل (واحدة لكل مستخدم) وكيرجّع العدد الجديد */
export async function addReelView({ postId, userId }) {
  const post = await queryOne('select id from posts where id = $1', [postId]);
  if (!post) throw new Error('POST_NOT_FOUND');

  if (userId) {
    await query(
      'insert into reel_views (post_id, user_id) values ($1, $2) on conflict (post_id, user_id) do nothing',
      [postId, userId],
    );
  }
  const row = await queryOne('select count(*)::int as n from reel_views where post_id = $1', [postId]);
  return Number(row?.n ?? 0);
}

/** بحث فالمنشورات (نفس قواعد الخصوصية ديال الفيد) */
/** حذف منشور — غير صاحبه */
export async function deletePost({ postId, userId }) {
  const post = await queryOne('select id, author_id from posts where id = $1', [postId]);
  if (!post) throw new Error('POST_NOT_FOUND');
  if (post.author_id !== userId) throw new Error('FORBIDDEN');

  await query('delete from comment_likes where comment_id in (select id from comments where post_id = $1)', [postId]);
  await query('delete from comments where post_id = $1', [postId]);
  await query('delete from reactions where post_id = $1', [postId]);
  await query('delete from notifications where post_id = $1', [postId]);
  await query('delete from posts where id = $1', [postId]);

  postCounts.set(userId, Math.max(0, (postCounts.get(userId) ?? 1) - 1));
  return true;
}

export async function searchPosts({ search = '', viewerId = null, limit = 20 }) {
  const term = search.trim();
  if (!term) return [];
  const friendIds = viewerId ? friendsOf(viewerId) : [];

  const rows = await query(
    `${POST_SELECT}
     where p.text ilike '%' || $1 || '%'
       and case
             when $2::text is null then p.privacy = 'public'
             else p.author_id = $2
               or p.privacy = 'public'
               or (p.privacy = 'friends' and p.author_id = any($3::text[]))
           end
     order by p.created_at desc, p.id desc
     limit $4`,
    [term, viewerId, friendIds, limit],
  );
  return hydratePosts(rows, viewerId);
}

export async function listUserPosts({ authorId, limit = 20, viewerId = null }) {
  const rows = await query(
    `${POST_SELECT} where p.author_id = $1 order by p.created_at desc, p.id desc limit $2`,
    [authorId, limit],
  );
  return hydratePosts(rows, viewerId);
}

/* ============================ التفاعلات ============================ */

export const getMyReaction = (postId, userId) =>
  (userId ? queryOne('select kind from reactions where post_id = $1 and user_id = $2', [postId, userId]) : null);

export async function react({ postId, userId, reaction }) {
  const exists = await queryOne('select id, author_id from posts where id = $1', [postId]);
  if (!exists) throw new Error('POST_NOT_FOUND');

  const previous = await queryOne('select kind from reactions where post_id = $1 and user_id = $2', [postId, userId]);

  if (!reaction) {
    await query('delete from reactions where post_id = $1 and user_id = $2', [postId, userId]);
  } else {
    await query(
      `insert into reactions (post_id, user_id, kind) values ($1, $2, $3)
       on conflict (post_id, user_id) do update set kind = excluded.kind`,
      [postId, userId, reaction],
    );
  }

  return {
    postId,
    authorId: exists.author_id,
    previousKind: previous?.kind ?? null,
    isNew: Boolean(reaction) && (previous?.kind ?? null) !== reaction,
  };
}

/* ============================ التعليقات ============================ */

export async function addComment({ postId, authorId, text }) {
  const post = await queryOne('select id, author_id from posts where id = $1', [postId]);
  if (!post) throw new Error('POST_NOT_FOUND');

  const row = await queryOne(
    `insert into comments (id, post_id, author_id, text) values ($1, $2, $3, $4) returning *`,
    [shortId('c'), postId, authorId, text],
  );

  return {
    id: row.id,
    postId: row.post_id,
    authorId: row.author_id,
    text: row.text,
    createdAt: iso(row.created_at),
    likes: 0,
    likedByMe: false,
    postAuthorId: post.author_id,
  };
}

/** تبديل الإعجاب على تعليق */
export async function likeComment({ postId, commentId, userId }) {
  const comment = await queryOne('select * from comments where id = $1 and post_id = $2', [commentId, postId]);
  if (!comment) throw new Error('COMMENT_NOT_FOUND');

  const liked = await queryOne('select 1 from comment_likes where comment_id = $1 and user_id = $2', [commentId, userId]);
  if (liked) await query('delete from comment_likes where comment_id = $1 and user_id = $2', [commentId, userId]);
  else await query('insert into comment_likes (comment_id, user_id) values ($1, $2)', [commentId, userId]);

  const stats = await queryOne(
    'select count(*)::int as n from comment_likes where comment_id = $1',
    [commentId],
  );

  return {
    id: comment.id,
    postId: comment.post_id,
    authorId: comment.author_id,
    text: comment.text,
    createdAt: iso(comment.created_at),
    likes: Number(stats.n),
    likedByMe: !liked,
  };
}

export async function deleteComment({ postId, commentId, userId }) {
  const comment = await queryOne('select * from comments where id = $1 and post_id = $2', [commentId, postId]);
  if (!comment) return false;

  const post = await queryOne('select author_id from posts where id = $1', [postId]);
  if (comment.author_id !== userId && post?.author_id !== userId) throw new Error('FORBIDDEN');

  await query('delete from comments where id = $1', [commentId]);
  return true;
}

/* ============================ الإشعارات ============================ */

const rowToNotification = (row) => ({
  id: row.id,
  userId: row.user_id,
  actorId: row.actor_id,
  type: row.type,
  postId: row.post_id,
  text: row.text,
  read: row.read,
  createdAt: iso(row.created_at),
});

export async function pushNotification({ userId, actorId, type, postId = null, text }) {
  if (!userId || userId === actorId) return null; // ما كنشعّروش المستخدم على راسو
  const row = await queryOne(
    `insert into notifications (id, user_id, actor_id, type, post_id, text)
     values ($1, $2, $3, $4, $5, $6) returning *`,
    [shortId('n'), userId, actorId, type, postId, text],
  );
  const notification = rowToNotification(row);
  // لحظي: الشارة والإشعار كيوصلو بلا reload (WebSocket)
  publishToUser(userId, { type: 'notification', notification });
  return notification;
}

export async function notificationsFor(userId, limit = 50) {
  const rows = await query(
    'select * from notifications where user_id = $1 order by created_at desc limit $2',
    [userId, limit],
  );
  return rows.map(rowToNotification);
}

export async function unreadNotificationsCount(userId) {
  if (!userId) return 0;
  const row = await queryOne('select count(*)::int as n from notifications where user_id = $1 and read = false', [userId]);
  return Number(row?.n ?? 0);
}

export async function markNotificationsRead(userId) {
  await query('update notifications set read = true where user_id = $1 and read = false', [userId]);
  return true;
}

export { REACTION_KINDS, emptyReactions };

/* ============================ الدردشة (Messenger) ============================ */

const pairOf = (a, b) => (a < b ? [a, b] : [b, a]);

/** صف رسالة → شكل GraphQL (بلا التفاعلات/الرد — كيتزادو فـ messagesView) */
const rowToMessage = (row) => ({
  id: row.id,
  conversationId: row.conversation_id,
  senderId: row.sender_id,
  // الرسالة المحذوفة كتبقى فالجدول (tombstone) ولكن النص ما كيتصيفطش
  text: row.deleted_at ? '' : row.text,
  createdAt: iso(row.created_at),
  read: Boolean(row.read_at),
  edited: Boolean(row.edited_at),
  deleted: Boolean(row.deleted_at),
  replyToId: row.reply_to_id ?? null,
  replyTo: null,
  reactions: [],
  mediaId: row.media_id ?? null,
  media: row.media_id
    ? {
        id: row.media_id,
        uri: `/media/${row.media_id}`,
        width: null,
        height: null,
        aspectRatio: 4 / 3,
        alt: null,
        mime: null,
        kind: 'image',
      }
    : null,
});

/** تفاعلات مجموعة رسائل فاستعلام واحد (بلا N+1) */
async function reactionsFor(messageIds, viewerId) {
  const map = new Map();
  if (!messageIds.length) return map;

  const rows = await query(
    'select message_id, user_id, kind from message_reactions where message_id = any($1)',
    [messageIds],
  );

  for (const row of rows) {
    if (!map.has(row.message_id)) map.set(row.message_id, new Map());
    const kinds = map.get(row.message_id);
    const entry = kinds.get(row.kind) ?? { kind: row.kind, count: 0, mine: false };
    entry.count += 1;
    if (row.user_id === viewerId) entry.mine = true;
    kinds.set(row.kind, entry);
  }
  return map;
}

/** كيكمّل الرسائل: التفاعلات + الرسالة المردود عليها (استعلامين للمجموعة كاملة) */
async function decorateMessages(messages, viewerId) {
  if (!messages.length) return messages;

  const ids = messages.map((m) => m.id);
  const replyIds = [...new Set(messages.map((m) => m.replyToId).filter(Boolean))];
  const mediaIds = [...new Set(messages.map((m) => m.mediaId).filter(Boolean))];

  const [reactions, replies, media] = await Promise.all([
    reactionsFor(ids, viewerId),
    replyIds.length ? query('select * from messages where id = any($1)', [replyIds]) : Promise.resolve([]),
    mediaIds.length
      ? query('select id, aspect_ratio, alt, mime from media where id = any($1)', [mediaIds])
      : Promise.resolve([]),
  ]);

  const replyMap = new Map(replies.map((row) => [row.id, rowToMessage(row)]));
  const mediaMap = new Map(media.map((row) => [row.id, row]));

  return messages.map((message) => {
    const meta = message.mediaId ? mediaMap.get(message.mediaId) : null;
    return {
      ...message,
      reactions: [...(reactions.get(message.id)?.values() ?? [])],
      replyTo: message.replyToId ? replyMap.get(message.replyToId) ?? null : null,
      media: message.mediaId
        ? {
            id: message.mediaId,
            uri: `/media/${message.mediaId}`,
            width: null,
            height: null,
            aspectRatio: meta?.aspect_ratio ?? 4 / 3,
            alt: meta?.alt ?? null,
            mime: meta?.mime ?? null,
            kind: isVideoMime(meta?.mime) ? 'video' : 'image',
          }
        : null,
    };
  });
}

/** محادثة بين جوج — كتتصنع إلا ماكانتش */
export async function getOrCreateConversation(userId, otherId) {
  if (userId === otherId) throw new Error('CANNOT_MESSAGE_YOURSELF');
  if (!users.has(otherId)) throw new Error('USER_NOT_FOUND');

  const [a, b] = pairOf(userId, otherId);
  const existing = await queryOne('select * from conversations where user_a = $1 and user_b = $2', [a, b]);
  if (existing) return conversationView(existing, userId);

  const row = await queryOne(
    'insert into conversations (id, user_a, user_b) values ($1, $2, $3) on conflict (user_a, user_b) do update set last_at = conversations.last_at returning *',
    [shortId('cv'), a, b],
  );
  return conversationView(row, userId);
}

/** شكل المحادثة للعميل: الطرف الآخر + آخر رسالة + عدد غير المقروء */
async function conversationView(row, viewerId) {
  const otherId = row.user_a === viewerId ? row.user_b : row.user_a;

  const [last, unread] = await Promise.all([
    queryOne('select * from messages where conversation_id = $1 order by created_at desc limit 1', [row.id]),
    queryOne(
      'select count(*)::int as n from messages where conversation_id = $1 and sender_id <> $2 and read_at is null',
      [row.id, viewerId],
    ),
  ]);

  return {
    id: row.id,
    createdAt: iso(row.created_at),
    lastAt: iso(row.last_at),
    other: publicUser(users.get(otherId)),
    lastMessage: last ? rowToMessage(last) : null,
    unreadCount: Number(unread?.n ?? 0),
  };
}

/** كل محادثاتي مرتبة بالأحدث + بحث بالاسم */
export async function listConversations(userId, { search = '', limit = 50 } = {}) {
  const rows = await query(
    `select * from conversations
     where (user_a = $1 or user_b = $1)
       and ($2 = '' or exists (
             select 1 from users u
             where u.id = case when conversations.user_a = $1 then conversations.user_b else conversations.user_a end
               and (u.name ilike '%' || $2 || '%' or u.email ilike '%' || $2 || '%')
           ))
     order by last_at desc
     limit $3`,
    [userId, search.trim(), limit],
  );
  return Promise.all(rows.map((row) => conversationView(row, userId)));
}

export async function assertMember(conversationId, userId) {
  const row = await queryOne('select * from conversations where id = $1', [conversationId]);
  if (!row) throw new Error('CONVERSATION_NOT_FOUND');
  if (row.user_a !== userId && row.user_b !== userId) throw new Error('FORBIDDEN');
  return row;
}

/** صف رسالة + التحقق أن المستخدم عضو فالمحادثة */
async function messageRow(messageId, userId) {
  const row = await queryOne('select * from messages where id = $1', [messageId]);
  if (!row) throw new Error('MESSAGE_NOT_FOUND');
  const conversation = await assertMember(row.conversation_id, userId);
  return { row, conversation };
}

/** رسائل المحادثة (الأحدث الأخير) + حقل nextCursor للتمرير للأقدم */
export async function listMessages({ conversationId, userId, cursor = null, limit = 30 }) {
  await assertMember(conversationId, userId);
  const offset = cursor ? Number(cursor) : 0;

  const rows = await query(
    `select * from messages where conversation_id = $1 order by created_at desc limit $2 offset $3`,
    [conversationId, limit, offset],
  );
  const nextOffset = offset + rows.length;

  const messages = rows.map(rowToMessage).reverse();
  return {
    messages: await decorateMessages(messages, userId),
    nextCursor: rows.length === limit ? String(nextOffset) : null,
  };
}

/** رسالة واحدة بشكلها الكامل (بعد أي تعديل/حذف/تفاعل) */
export async function messageView(messageId, userId) {
  const { row } = await messageRow(messageId, userId);
  const [view] = await decorateMessages([rowToMessage(row)], userId);
  return view;
}

export async function sendMessage({ conversationId, senderId, text, replyToId = null, mediaId = null }) {
  const conv = await assertMember(conversationId, senderId);
  const clean = String(text ?? '').trim();
  if (!clean && !mediaId) throw new Error('EMPTY_MESSAGE');

  let replyTo = null;
  if (replyToId) {
    const parent = await queryOne('select * from messages where id = $1', [replyToId]);
    // الرد خاصو يكون على رسالة فنفس المحادثة
    if (!parent || parent.conversation_id !== conversationId) throw new Error('REPLY_TARGET_NOT_FOUND');
    if (parent.deleted_at) throw new Error('REPLY_TARGET_DELETED');
    replyTo = parent.id;
  }

  const row = await queryOne(
    'insert into messages (id, conversation_id, sender_id, text, reply_to_id, media_id) values ($1, $2, $3, $4, $5, $6) returning *',
    [shortId('msg'), conversationId, senderId, clean.slice(0, 2000), replyTo, mediaId],
  );
  await query('update conversations set last_at = now() where id = $1', [conversationId]);

  const otherId = conv.user_a === senderId ? conv.user_b : conv.user_a;
  const [message] = await decorateMessages([rowToMessage(row)], senderId);
  return { message, otherId };
}

/** تعديل رسالة — غير صاحبها، وغير المحذوفة */
export async function editMessage({ messageId, userId, text }) {
  const { row } = await messageRow(messageId, userId);
  if (row.sender_id !== userId) throw new Error('FORBIDDEN');
  if (row.deleted_at) throw new Error('MESSAGE_DELETED');

  const clean = text.trim();
  if (!clean) throw new Error('EMPTY_MESSAGE');

  const updated = await queryOne(
    'update messages set text = $2, edited_at = now() where id = $1 returning *',
    [messageId, clean.slice(0, 2000)],
  );
  const [message] = await decorateMessages([rowToMessage(updated)], userId);
  return { message, conversationId: row.conversation_id };
}

/** حذف رسالة — حذف ناعم (tombstone) باش الردود ما تبقاش معلّقة */
export async function deleteMessage({ messageId, userId }) {
  const { row } = await messageRow(messageId, userId);
  if (row.sender_id !== userId) throw new Error('FORBIDDEN');
  if (row.deleted_at) throw new Error('MESSAGE_ALREADY_DELETED');

  const updated = await queryOne(
    'update messages set deleted_at = now(), edited_at = null where id = $1 returning *',
    [messageId],
  );
  await query('delete from message_reactions where message_id = $1', [messageId]);

  const [message] = await decorateMessages([rowToMessage(updated)], userId);
  return { message, conversationId: row.conversation_id };
}

/** تفاعل على رسالة (تفاعل واحد لكل مستخدم — نفس التفاعل كيحيدو) */
export async function reactToMessage({ messageId, userId, reaction = null }) {
  const { row } = await messageRow(messageId, userId);
  if (row.deleted_at) throw new Error('MESSAGE_DELETED');

  if (!reaction) {
    await query('delete from message_reactions where message_id = $1 and user_id = $2', [messageId, userId]);
  } else {
    if (!REACTION_KINDS.includes(reaction)) throw new Error('BAD_REACTION');
    await query(
      `insert into message_reactions (message_id, user_id, kind) values ($1, $2, $3)
       on conflict (message_id, user_id) do update set kind = excluded.kind`,
      [messageId, userId, reaction],
    );
  }

  const [message] = await decorateMessages([rowToMessage(row)], userId);
  return { message, conversationId: row.conversation_id };
}

export async function markConversationRead(conversationId, userId) {
  await assertMember(conversationId, userId);
  const rows = await query(
    'update messages set read_at = now() where conversation_id = $1 and sender_id <> $2 and read_at is null returning id',
    [conversationId, userId],
  );
  return { changed: rows.length, readAt: new Date().toISOString() };
}

export async function unreadMessagesCount(userId) {
  if (!userId) return 0;
  const row = await queryOne(
    `select count(*)::int as n from messages m
     join conversations c on c.id = m.conversation_id
     where (c.user_a = $1 or c.user_b = $1) and m.sender_id <> $1 and m.read_at is null`,
    [userId],
  );
  return Number(row?.n ?? 0);
}

/** الطرف الآخر فمحادثة (للإشعارات والاشتراكات) */
export async function conversationPartner(conversationId, userId) {
  const row = await assertMember(conversationId, userId);
  return row.user_a === userId ? row.user_b : row.user_a;
}

/** نص رسالة للترجمة (بعد التحقق من العضوية) */
export async function messageTextForTranslation(messageId, userId) {
  const { row } = await messageRow(messageId, userId);
  if (row.deleted_at) throw new Error('MESSAGE_DELETED');
  return row.text;
}
