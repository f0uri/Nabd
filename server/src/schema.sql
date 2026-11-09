-- ============================================================
-- نبض · مخطط PostgreSQL
-- كل الجداول كتتعاود تتصنع بأمان (IF NOT EXISTS) — كيتنفّذ عند الإقلاع.
-- ============================================================

/* ---------------------------- المستخدمون ---------------------------- */
create table if not exists users (
  id              text primary key,
  name            text not null,
  email           text not null unique,
  password_hash   text not null,
  color           text not null default '#1877F2',
  initial         text,
  avatar_media_id text,
  verified        boolean not null default false,
  bio             text not null default '',
  created_at      timestamptz not null default now()
);

/* ---------------------------- الوسائط ---------------------------- */
create table if not exists media (
  id           text primary key,
  provider     text,
  file_id      text,
  mime         text,
  size         bigint,
  aspect_ratio real default 1.3333,
  alt          text,
  created_at   timestamptz not null default now()
);

/* ---------------------------- الصداقات ---------------------------- */
-- العلاقة متبادلة: كنخزّنو غير جهة واحدة (user_a < user_b)
create table if not exists friendships (
  user_a     text not null references users(id) on delete cascade,
  user_b     text not null references users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_a, user_b),
  check (user_a < user_b)
);

/* ---------------------------- المنشورات ---------------------------- */
create table if not exists posts (
  id         text primary key,
  author_id  text not null references users(id) on delete cascade,
  text       text not null default '',
  privacy    text not null default 'public' check (privacy in ('public','friends','private')),
  media_id   text references media(id) on delete set null,
  shares     integer not null default 0,
  created_at timestamptz not null default now()
);

/* ---------------------------- التفاعلات ---------------------------- */
create table if not exists reactions (
  post_id    text not null references posts(id) on delete cascade,
  user_id    text not null references users(id) on delete cascade,
  kind       text not null check (kind in ('like','love','haha','wow','sad','angry')),
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

/* ---------------------------- التعليقات ---------------------------- */
create table if not exists comments (
  id         text primary key,
  post_id    text not null references posts(id) on delete cascade,
  author_id  text not null references users(id) on delete cascade,
  text       text not null,
  created_at timestamptz not null default now()
);

create table if not exists comment_likes (
  comment_id text not null references comments(id) on delete cascade,
  user_id    text not null references users(id) on delete cascade,
  primary key (comment_id, user_id)
);

/* ---------------------------- الإشعارات ---------------------------- */
create table if not exists notifications (
  id         text primary key,
  user_id    text not null references users(id) on delete cascade,
  actor_id   text not null references users(id) on delete cascade,
  type       text not null,
  post_id    text,
  text       text not null,
  read       boolean not null default false,
  created_at timestamptz not null default now()
);

/* ---------------------------- الفهارس ---------------------------- */
create index if not exists posts_created_idx    on posts (created_at desc);
create index if not exists posts_author_idx     on posts (author_id, created_at desc);
create index if not exists comments_post_idx    on comments (post_id, created_at);
create index if not exists reactions_post_idx   on reactions (post_id);
create index if not exists notif_user_idx       on notifications (user_id, created_at desc);
create index if not exists notif_unread_idx     on notifications (user_id) where read = false;

/* ---------------------------- الدردشة (Messenger) ---------------------------- */
-- محادثة واحدة بين كل جوج مستخدمين (user_a < user_b)
create table if not exists conversations (
  id          text primary key,
  user_a      text not null references users(id) on delete cascade,
  user_b      text not null references users(id) on delete cascade,
  created_at  timestamptz not null default now(),
  last_at     timestamptz not null default now(),
  unique (user_a, user_b),
  check (user_a < user_b)
);

create table if not exists messages (
  id              text primary key,
  conversation_id text not null references conversations(id) on delete cascade,
  sender_id       text not null references users(id) on delete cascade,
  text            text not null,
  created_at      timestamptz not null default now(),
  read_at         timestamptz
);

create index if not exists messages_conv_idx  on messages (conversation_id, created_at desc);
create index if not exists conv_last_idx      on conversations (last_at desc);

/* ------------------- الدردشة: تعديل · حذف · رد · تفاعلات ------------------- */
alter table messages add column if not exists edited_at   timestamptz;
alter table messages add column if not exists deleted_at  timestamptz;
alter table messages add column if not exists reply_to_id text references messages(id) on delete set null;

create table if not exists message_reactions (
  message_id text not null references messages(id) on delete cascade,
  user_id    text not null references users(id) on delete cascade,
  kind       text not null check (kind in ('like','love','haha','wow','sad','angry')),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id)
);

alter table messages add column if not exists media_id text references media(id) on delete set null;

create index if not exists msg_react_idx on message_reactions (message_id);
create index if not exists messages_reply_idx on messages (reply_to_id);

/* ------------------------------ الريلز (فيديو) ------------------------------ */
create table if not exists reel_views (
  post_id    text not null references posts(id) on delete cascade,
  user_id    text not null references users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (post_id, user_id)
);

create index if not exists reel_views_post_idx on reel_views (post_id);

/* ------------------------- مصدر المنشور (ريلز إنستغرام) ------------------------- */
alter table posts add column if not exists source        text not null default 'nabd';
alter table posts add column if not exists source_url    text;
alter table posts add column if not exists source_author text;

create unique index if not exists posts_source_url_key on posts (source_url) where source_url is not null;

/* ---------------------- طابور الجلب التلقائي لريلز إنستغرام ---------------------- */
create table if not exists ig_queue (
  shortcode   text primary key,
  source      text not null default 'catalog',   -- catalog | account | search | manual
  status      text not null default 'pending',   -- pending | imported | failed | skipped
  attempts    int  not null default 0,
  last_error  text,
  post_id     text references posts(id) on delete set null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index if not exists ig_queue_status_idx on ig_queue (status, created_at);

/* ---------------------- إعدادات التطبيق (لوحة الأدمن) ---------------------- */
create table if not exists app_settings (
  key        text primary key,
  value      text,
  updated_at timestamptz not null default now()
);

alter table users add column if not exists is_admin boolean not null default false;

/* Stable handles for existing accounts; never derive identity from duplicate display names.
   Re-running startup preserves all previously chosen handles and avatar references. */
alter table users add column if not exists username text;
update users set username = 'u_' || md5(id) where username is null;
create unique index if not exists users_username_lower_key on users (lower(username));
alter table users alter column username set not null;

/* ===== رموز التحقق بالإيميل (تفعيل الحساب + استعادة كلمة المرور) =====
   الرمز عمره ما كيتخزّن صريح: غير توقيع sha256 فـ code_hash. */
create table if not exists email_codes (
  id          text primary key,
  email       text not null,
  purpose     text not null check (purpose in ('verify', 'reset')),
  code_hash   text not null,
  expires_at  timestamptz not null,
  attempts    integer not null default 0,
  used_at     timestamptz,
  request_ip  text,
  created_at  timestamptz not null default now()
);
create unique index if not exists email_codes_email_purpose_key on email_codes (email, purpose);
create index if not exists email_codes_created_idx on email_codes (created_at);

/* ===== الدخول بحساب Google =====
   الحسابات اللي كتدخل غير بـ Google ما عندهاش كلمة مرور: password_hash كيبقى NULL.
   google_id هو المعرّف الدائم ديال الحساب عند Google (sub). */
alter table users alter column password_hash drop not null;
alter table users add column if not exists google_id text;
alter table users add column if not exists avatar_url text;
create unique index if not exists users_google_id_key on users (google_id) where google_id is not null;
