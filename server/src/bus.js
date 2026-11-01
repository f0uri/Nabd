/**
 * ناقل بسيط لأحداث المستخدم (بلا تبعيات — باش ما يصيرش import cycle
 * بين store.js و realtime.js).
 *
 * store.js كينشر (publishToUser) و realtime.js كيتسمّع (onUserEvent)
 * وكيوصل الحدث لكل اتصالات هاد المستخدم.
 */

const subscribers = new Set();

/** كيرجّع دالة لإلغاء التسجيل */
export function onUserEvent(handler) {
  subscribers.add(handler);
  return () => subscribers.delete(handler);
}

export function publishToUser(userId, payload) {
  if (!userId) return 0;
  let delivered = 0;
  for (const handler of subscribers) {
    try {
      handler(userId, payload);
      delivered += 1;
    } catch {
      /* تجاهل */
    }
  }
  return delivered;
}
