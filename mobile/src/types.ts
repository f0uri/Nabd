export type ReactionKind = 'like' | 'love' | 'haha' | 'wow' | 'sad' | 'angry';

export type Privacy = 'public' | 'friends' | 'private';

export interface Author {
  id: string;
  name: string;
  /** Unique account handle; absent only on legacy servers/demo data. */
  username?: string;
  /** لون احتياطي للصورة الرمزية */
  color: string;
  initial: string;
  /** رابط صورة الحساب اللي رفعها المستخدم (نسبة /media/xxx أو رابط كامل) */
  avatarUrl?: string | null;
  verified?: boolean;
  bio?: string | null;
  email?: string | null;
  friendsCount?: number;
  postsCount?: number;
  isFriend?: boolean;
  isMe?: boolean;
  /** أدمن: كيشوف لوحة الأدمن (كوكيز إنستغرام…) */
  isAdmin?: boolean;
}

export type NotificationType = 'like' | 'comment' | 'follow' | 'share' | 'message';

export interface AppNotification {
  id: string;
  actor: Author;
  type: NotificationType;
  postId?: string | null;
  text: string;
  createdAt: string;
  read: boolean;
}

export interface AuthPayload {
  token: string;
  user: Author;
}

export interface Comment {
  id: string;
  author: Author;
  text: string;
  createdAt: string;
  likes: number;
  likedByMe: boolean;
}

export interface Story {
  id: string;
  author: Author;
  /** صورة الستوري (محلية أو من التخزين) */
  imageUri?: string | null;
  /** نص فوق الخلفية إذا كان ستوري نصي */
  text?: string;
  /** تدرّج خلفية الستوري (RGB) */
  colors: [string, string];
  createdAt: string;
  seen: boolean;
}

export interface MessageReaction {
  kind: ReactionKind;
  count: number;
  mine: boolean;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  /** نص الرسالة — فارغ إلا كانت محذوفة (tombstone) */
  text: string;
  createdAt: string;
  read: boolean;
  /** تعدّلت بعد الإرسال */
  edited?: boolean;
  /** محذوفة (كتبان «تم حذف الرسالة») */
  deleted?: boolean;
  /** الرسالة المردود عليها */
  replyTo?: Message | null;
  /** التفاعلات مجمّعة حسب النوع */
  reactions?: MessageReaction[];
  /** صورة مرفقة */
  media?: Media | null;
}

export interface Translation {
  text: string;
  from: string;
  to: string;
  provider: string;
  cached: boolean;
}

/** أحداث الـ WebSocket اللي كيوصلو من الخادم */
export type RealtimeEvent =
  | { type: 'ready'; userId: string }
  | { type: 'subscribed' | 'error'; conversationId?: string; code?: string }
  | { type: 'message'; event: 'added' | 'updated' | 'deleted'; conversationId: string; message: Message }
  | { type: 'read'; conversationId: string; readerId: string; readAt: string }
  | { type: 'typing'; conversationId: string; userId: string; name: string | null }
  | { type: 'notification'; notification: AppNotification }
  | { type: 'pong' };

export interface Conversation {
  id: string;
  createdAt: string;
  lastAt: string;
  other: Author;
  lastMessage: Message | null;
  unreadCount: number;
}

export interface MessagePage {
  messages: Message[];
  nextCursor: string | null;
}

export interface Media {
  id: string;
  /** رابط محلي أو URI من التخزين (S3/CDN/تلغرام) */
  uri: string;
  width: number;
  height: number;
  /** نسبة العرض/الارتفاع لتفادي الوميض عند التحميل */
  aspectRatio: number;
  alt?: string;
  /** نوع الوسيط: صورة أو فيديو (للريلز) */
  kind?: 'image' | 'video';
  mime?: string | null;
}

export interface Post {
  id: string;
  /** عدد المشاهدات (للريلز) */
  views?: number;
  /** مصدر المنشور: nabd (داخلي) ولا instagram (ريل مستورد) */
  source?: 'nabd' | 'instagram' | string;
  /** الرابط الأصلي فـ إنستغرام */
  sourceUrl?: string | null;
  /** حساب صاحب الريل الأصلي فـ إنستغرام */
  sourceAuthor?: string | null;
  author: Author;
  createdAt: string;
  privacy: Privacy;
  text: string;
  media?: Media;
  /** عدد كل نوع تفاعل — يُستعمل لملخص الـ reactions */
  reactions: Record<ReactionKind, number>;
  myReaction: ReactionKind | null;
  comments: Comment[];
  commentsCount: number;
  shares: number;
}

export interface FeedPage {
  posts: Post[];
  nextCursor: string | null;
}

export type TabKey = 'feed' | 'friends' | 'reels' | 'search' | 'notifications' | 'menu' | 'profile';

export interface ApiErrorShape {
  code: string;
  message: string;
}
