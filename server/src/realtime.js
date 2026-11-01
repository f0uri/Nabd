/**
 * التحديث اللحظي — WebSocket خام (بلا أي مكتبة فالتطبيق: RN عندو WebSocket مدمج).
 *
 * العنوان: ws://host:4000/ws?token=<JWT>
 *
 * من الخادم للعميل:
 *   { type: 'ready', userId }
 *   { type: 'message',  event: 'added'|'updated'|'deleted', conversationId, message }
 *   { type: 'read',     conversationId, readerId, readAt }
 *   { type: 'typing',   conversationId, userId, name }
 *   { type: 'notification', notification }   ← إشعار جديد (شارة فورية)
 *   { type: 'error',    code, message }
 *   { type: 'pong' }
 *
 * من العميل للخادم:
 *   { type: 'subscribe',   conversationId }
 *   { type: 'unsubscribe', conversationId }
 *   { type: 'typing',      conversationId }
 *   { type: 'ping' }
 *
 * الذاكرة: كل غرفة = مجموعة اتصالات مفتوحة على محادثة. العضوية كتتحقق مرة
 * واحدة عند الاشتراك (وكتتعاود عند أي إعادة اتصال).
 */
import { WebSocketServer, WebSocket } from 'ws';
import { verifyToken } from './auth.js';
import { conversationPartner } from './store.js';
import { onUserEvent } from './bus.js';

/** ws → { userId, rooms:Set<string>, alive:boolean } */
const clients = new Map();
/** conversationId → Set<ws> */
const rooms = new Map();
let wss = null;

const send = (ws, payload) => {
  if (ws.readyState === WebSocket.OPEN) {
    try {
      ws.send(JSON.stringify(payload));
    } catch {
      /* تجاهل */
    }
  }
};

const joinRoom = (conversationId, ws) => {
  if (!rooms.has(conversationId)) rooms.set(conversationId, new Set());
  rooms.get(conversationId).add(ws);
  clients.get(ws)?.rooms.add(conversationId);
};

const leaveRoom = (conversationId, ws) => {
  rooms.get(conversationId)?.delete(ws);
  if (rooms.get(conversationId)?.size === 0) rooms.delete(conversationId);
  clients.get(ws)?.rooms.delete(conversationId);
};

/** كيبثّ حدث لغرفة محادثة (اختياري: بلا مستخدم معيّن) */
export function publish(conversationId, payload, { exceptUserId = null } = {}) {
  const sockets = rooms.get(conversationId);
  if (!sockets) return 0;

  let delivered = 0;
  for (const ws of sockets) {
    const ctx = clients.get(ws);
    if (!ctx) continue;
    if (exceptUserId && ctx.userId === exceptUserId) continue;
    send(ws, { ...payload, conversationId });
    delivered += 1;
  }
  return delivered;
}

/** حالة القناة (للـ /health) */
export function realtimeInfo() {
  const conversations = rooms.size;
  const sockets = clients.size;
  return { transport: 'websocket', path: '/ws', sockets, conversations };
}

/** حدث موجّه لمستخدم (إشعار جديد مثلاً) — كيوصل لكل أجهزته المفتوحة */
function bindUserEvents() {
  onUserEvent((userId, payload) => {
    for (const [ws, ctx] of clients) {
      if (ctx.userId === userId) send(ws, payload);
    }
  });
}

export function attachRealtime(server) {
  wss = new WebSocketServer({ server, path: '/ws', maxPayload: 64 * 1024 });
  bindUserEvents();

  wss.on('connection', async (ws, req) => {
    const url = new URL(req.url ?? '/ws', `http://${req.headers.host ?? 'localhost'}`);
    const payload = verifyToken(url.searchParams.get('token'));
    if (!payload?.sub) {
      send(ws, { type: 'error', code: 'UNAUTHENTICATED', message: 'توكن غير صالح' });
      ws.close(4001, 'UNAUTHENTICATED');
      return;
    }

    clients.set(ws, { userId: payload.sub, rooms: new Set(), alive: true });
    send(ws, { type: 'ready', userId: payload.sub });

    ws.on('pong', () => {
      const ctx = clients.get(ws);
      if (ctx) ctx.alive = true;
    });

    ws.on('message', async (raw) => {
      let data;
      try {
        data = JSON.parse(String(raw));
      } catch {
        send(ws, { type: 'error', code: 'BAD_JSON', message: 'رسالة غير مفهومة' });
        return;
      }

      const ctx = clients.get(ws);
      if (!ctx) return;

      if (data.type === 'ping') {
        send(ws, { type: 'pong' });
        return;
      }

      if (data.type === 'subscribe' || data.type === 'unsubscribe') {
        const conversationId = String(data.conversationId ?? '');
        if (!conversationId) return;

        if (data.type === 'unsubscribe') {
          leaveRoom(conversationId, ws);
          return;
        }

        try {
          // كيتأكد أنه فعلاً عضو فالمحادثة
          await conversationPartner(conversationId, ctx.userId);
          joinRoom(conversationId, ws);
          send(ws, { type: 'subscribed', conversationId });
        } catch {
          send(ws, { type: 'error', code: 'FORBIDDEN', conversationId, message: 'ماشي عضو فهاد المحادثة' });
        }
        return;
      }

      if (data.type === 'typing') {
        const conversationId = String(data.conversationId ?? '');
        if (!conversationId || !ctx.rooms.has(conversationId)) return;
        publish(
          conversationId,
          { type: 'typing', userId: ctx.userId, name: data.name ?? null },
          { exceptUserId: ctx.userId },
        );
      }
    });

    ws.on('close', () => {
      for (const conversationId of [...(clients.get(ws)?.rooms ?? [])]) leaveRoom(conversationId, ws);
      clients.delete(ws);
    });

    ws.on('error', () => {
      /* كيتسد فـ close */
    });
  });

  // نبضة كل 30 ثانية باش الاتصالات الميتة تتحيّد
  const heartbeat = setInterval(() => {
    for (const [ws, ctx] of clients) {
      if (!ctx.alive) {
        ws.terminate();
        continue;
      }
      ctx.alive = false;
      try {
        ws.ping();
      } catch {
        /* تجاهل */
      }
    }
  }, 30_000);
  heartbeat.unref?.();

  wss.on('close', () => clearInterval(heartbeat));
  console.log('🔌 WebSocket جاهز على /ws');
  return wss;
}
