import '../../config.dart';
import 'graph_api.dart';
import 'local_store.dart';
import 'notifications.dart';

class SyncService {
  final LocalStore store;
  final ConfigStore configStore;
  final GraphApi api;
  final ProbeNotifications notifications;
  bool _busy = false;
  SyncService(this.store, this.configStore, this.api, this.notifications);
  Future<String> _status(String status) async {
    await store.setValue('status', status);
    return status;
  }
  Future<String?> _gate(ProbeConfig config) async {
    if (!config.ready) return 'أدخل Page ID وتوكن صفحة تجريبية في الإعدادات.';
    final gate = await store.value('graph_reachability');
    if (gate == null) return 'ابدأ بـ Run test في NetTest قبل المراسلة.';
    if (gate != 'ok') return 'Graph FAIL: أوقفنا النقل. انسخ نتيجة NetTest أولًا؛ SMS ليس إثباتًا لنجاح *6.';
    final next = int.tryParse(await store.value('next_attempt') ?? '') ?? 0;
    if (DateTime.now().millisecondsSinceEpoch < next) return 'انتظار قبل إعادة المحاولة لتجنب كثرة الطلبات.';
    return null;
  }
  Future<String> failure(GraphFailure error) async {
    final old = int.tryParse(await store.value('backoff') ?? '') ?? 15;
    final seconds = error.kind == 'rate' ? (old * 2).clamp(60, 900)
      : error.kind == 'network' ? 30 : 60;
    await store.setValue('backoff', '$seconds');
    await store.setValue('next_attempt', '${DateTime.now().millisecondsSinceEpoch + seconds * 1000}');
    return _status(error.safeStatus);
  }
  Future<void> resetBackoff() async {
    await store.setValue('backoff', '15');
    await store.setValue('next_attempt', '0');
  }
  Future<void> _notify(String pageId) async {
    if (!await notifications.allowed) return;
    for (final row in await store.pending(pageId)) {
      final id = row['local_id'] as int;
      // SQLite conditional update avoids duplicate notifications across isolates.
      if (await store.claim(id)) {
        await notifications.show(id, row['from_name'] as String, row['body'] as String);
      }
    }
  }
  Future<String> poll() async {
    if (_busy) return 'المزامنة جارية.';
    _busy = true;
    try {
      final config = await configStore.load();
      final blocked = await _gate(config);
      if (blocked != null) return _status(blocked);
      final messages = await api.receive(config);
      await store.put(messages);
      await resetBackoff();
      await _notify(config.pageId);
      return _status('آخر مزامنة ناجحة: ${DateTime.now().toLocal().toIso8601String()}');
    } on GraphFailure catch (error) { return failure(error); }
    catch (_) { return _status('تعذر إكمال الحفظ أو الإشعار المحلي. الرسائل المحفوظة لم تُمسح.'); }
    finally { _busy = false; }
  }
  Future<String> send(String text) async {
    if (_busy) return 'المزامنة جارية؛ حاول بعدها.';
    _busy = true;
    try {
      final config = await configStore.load();
      final blocked = await _gate(config);
      if (blocked != null) return _status(blocked);
      final message = await api.send(config, text);
      await store.put([message]);
      await resetBackoff();
      return _status('تم الإرسال: Graph رجّع Message ID حقيقي. اطلب الرد من حساب الاختبار.');
    } on GraphFailure catch (error) { return failure(error); }
    catch (_) { return _status('تعذرت العملية. لم نعتبرها إرسالًا ناجحًا.'); }
    finally { _busy = false; }
  }
}
