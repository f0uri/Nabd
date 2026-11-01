/**
 * عميل التحديث اللحظي — WebSocket مدمج فـ React Native (بلا أي مكتبة زايدة).
 *
 * - كيتصل بـ ws://host:4000/ws?token=<JWT>
 * - إعادة اتصال تلقائية بتراجع تدريجي (1s → 2s → 4s … 15s)
 * - كيعاود يشترك فالمحادثات اللي كانت مفتوحة من بعد أي قطع
 * - إلا كان الخادم ما كيدعمش WebSocket (firewall قديم) التطبيق كيبقى يخدم
 *   بالـ polling العادي فـ ConversationScreen
 */
import { API_URL, getAuthToken } from './client';
import type { RealtimeEvent } from '../types';

const WS_URL = API_URL.replace(/^http/, 'ws').replace(/\/graphql\/?$/, '/ws');

type Handler = (event: RealtimeEvent) => void;
type StatusListener = (connected: boolean) => void;

class Realtime {
  private ws: WebSocket | null = null;
  private rooms = new Set<string>();
  private handlers = new Set<Handler>();
  private statusListeners = new Set<StatusListener>();
  private retry = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private wanted = false;
  connected = false;

  /** كيبدا الاتصال (كيتنادى بعد الدخول) */
  connect() {
    if (process.env.EXPO_PUBLIC_DATA_SOURCE?.trim().toLowerCase() === 'telegram' || getAuthToken()?.startsWith('nabd-tg:')) return;
    this.wanted = true;
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return;

    const token = getAuthToken();
    if (!token) return;

    try {
      this.ws = new WebSocket(`${WS_URL}?token=${encodeURIComponent(token)}`);
    } catch {
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.retry = 0;
      this.connected = true;
      this.statusListeners.forEach((fn) => fn(true));
      // كنعاودو نشتركو فكل المحادثات المفتوحة
      this.rooms.forEach((conversationId) => this.send({ type: 'subscribe', conversationId }));
      this.pingTimer = setInterval(() => this.send({ type: 'ping' }), 25000);
    };

    this.ws.onmessage = (event) => {
      let data: RealtimeEvent;
      try {
        data = JSON.parse(String(event.data));
      } catch {
        return;
      }
      this.handlers.forEach((fn) => fn(data));
    };

    this.ws.onerror = () => {
      /* كيتسد فـ onclose */
    };

    this.ws.onclose = () => {
      this.connected = false;
      this.statusListeners.forEach((fn) => fn(false));
      if (this.pingTimer) clearInterval(this.pingTimer);
      this.pingTimer = null;
      this.ws = null;
      if (this.wanted) this.scheduleReconnect();
    };
  }

  private scheduleReconnect() {
    if (!this.wanted || this.timer) return;
    const delay = Math.min(1000 * 2 ** this.retry, 15000);
    this.retry += 1;
    this.timer = setTimeout(() => {
      this.timer = null;
      this.connect();
    }, delay);
  }

  /** كيتسد نهائياً (تسجيل الخروج) */
  disconnect() {
    this.wanted = false;
    if (this.timer) clearTimeout(this.timer);
    if (this.pingTimer) clearInterval(this.pingTimer);
    this.timer = null;
    this.pingTimer = null;
    this.rooms.clear();
    this.ws?.close();
    this.ws = null;
    this.connected = false;
  }

  /** الاشتراك فمحادثة (بعد التحقق من العضوية فالخادم) */
  subscribe(conversationId: string) {
    this.rooms.add(conversationId);
    if (this.connected) this.send({ type: 'subscribe', conversationId });
  }

  unsubscribe(conversationId: string) {
    this.rooms.delete(conversationId);
    if (this.connected) this.send({ type: 'unsubscribe', conversationId });
  }

  /** «يكتب الآن…» */
  typing(conversationId: string, name: string | null) {
    if (this.connected) this.send({ type: 'typing', conversationId, name });
  }

  private send(payload: Record<string, unknown>) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      try {
        this.ws.send(JSON.stringify(payload));
      } catch {
        /* تجاهل */
      }
    }
  }

  /** كيرجّع دالة لإلغاء التسجيل */
  onEvent(handler: Handler) {
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  onStatus(listener: StatusListener) {
    this.statusListeners.add(listener);
    return () => this.statusListeners.delete(listener);
  }
}

export const realtime = new Realtime();
export default realtime;
