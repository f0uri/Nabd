import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:meta6_probe/config.dart';
import 'package:meta6_probe/main.dart';
import 'package:meta6_probe/messages/data/graph_api.dart';
import 'package:meta6_probe/messages/data/message.dart';
import 'package:meta6_probe/messages/data/notifications.dart';
import 'package:meta6_probe/messages/data/sync_service.dart';
import 'package:meta6_probe/messages/provider/probe_provider.dart';
import 'package:meta6_probe/messages/ui/chat_screen.dart';
import 'net_test_screen_test.dart' show MemoryStore;

class MemoryConfig extends ConfigStore {
  ProbeConfig current;
  MemoryConfig(this.current);
  @override
  Future<ProbeConfig> load() async => current;
  @override
  Future<void> save(ProbeConfig config) async { current = config; }
}
class InboxStore extends MemoryStore {
  final inbox = <ProbeMessage>[];
  bool failReads = false;
  bool failMessages = false;
  @override
  Future<String?> value(String key) async => values[key];
  @override
  Future<List<ProbeMessage>> messages(String pageId) async {
    if (failReads) throw StateError('read failed');
    return inbox.where((m) => m.pageId == pageId).toList().reversed.toList();
  }
  @override
  Future<void> put(List<ProbeMessage> messages) async {
    if (failMessages) throw StateError('disk full');
    for (final message in messages) {
      if (!inbox.any((m) => m.pageId == message.pageId && m.id == message.id)) inbox.add(message);
    }
  }
}
class SilentNotifications extends ProbeNotifications {
  @override
  Future<bool> get allowed async => false;
}
class RecordingApi extends GraphApi {
  int receives = 0, sends = 0;
  bool reject = false;
  String? lastRecipient;
  @override
  Future<List<ProbeMessage>> receive(ProbeConfig config) async {
    receives++;
    return [const ProbeMessage(id: 'in1', pageId: '123', text: 'اختبار وارد',
      fromId: '456', fromName: 'مشارك', created: '2026-11-01T10:00:00Z')];
  }
  @override
  Future<ProbeMessage> send(ProbeConfig config, String text) async {
    sends++;
    if (reject) throw const GraphFailure('permission', httpCode: 403, apiCode: 200);
    lastRecipient = config.recipientId;
    return ProbeMessage(id: 'm_sent', pageId: config.pageId, text: text, fromId: config.pageId,
      fromName: 'صفحة', created: '2026-11-01T10:01:00Z');
  }
}

void main() {
  late InboxStore store;
  late MemoryConfig config;
  late RecordingApi api;
  late SilentNotifications notifications;
  late SyncService sync;
  late ProbeController controller;
  setUp(() {
    store = InboxStore();
    config = MemoryConfig(const ProbeConfig(pageId: '123', token: 'synthetic_test_token'));
    api = RecordingApi();
    notifications = SilentNotifications();
    sync = SyncService(store, config, api, notifications);
    controller = ProbeController(store, config, sync, schedule: (_) async {});
  });
  tearDown(() { if (controller.mounted) controller.dispose(); api.dio.close(force: true); });

  test('Graph FAIL blocks actual send and receive, independent of credentials', () async {
    store.values['graph_reachability'] = 'fail';
    await controller.poll();
    expect(await controller.send('اختبار'), isFalse);
    expect(api.receives, 0);
    expect(api.sends, 0);
    expect(controller.state.busy, isFalse);
  });
  test('storage failures release busy state instead of wedging the UI', () async {
    store.values['graph_reachability'] = 'ok';
    store.failReads = true;
    await controller.poll();
    expect(controller.state.busy, isFalse);
    expect(controller.state.status, 'تعذرت المزامنة');
  });
  test('Meta acceptance remains accepted if local persistence fails', () async {
    store.values['graph_reachability'] = 'ok';
    await controller.selectRecipient('456');
    store.failMessages = true;
    expect(await controller.send('اختبار'), isTrue);
    expect(api.sends, 1);
    expect(controller.state.status, contains('m_sent'));
    expect(controller.state.status, contains('تعذر الحفظ المحلي'));
  });
  testWidgets('simple home opens inbox; Reply selects PSID; confirmed send clears composer', (tester) async {
    store.values['graph_reachability'] = 'ok';
    await tester.pumpWidget(ProviderScope(overrides: [probeProvider.overrideWith((_) => controller)],
      child: ProbeApp(store: store, messagesBuilder: (_) => MessengerScreen(config: config, notifications: notifications))));
    expect(find.byType(NavigationBar), findsNothing);
    expect(api.receives, 0); // No Page polling on the network-only home.
    await tester.tap(find.byTooltip('تجربة الرسائل'));
    await tester.pumpAndSettle();
    expect(find.text('اختبار وارد'), findsOneWidget);
    expect(tester.widget<IconButton>(find.byTooltip('إرسال')).onPressed, isNull);
    await tester.tap(find.text('الرد'));
    await tester.pumpAndSettle();
    expect(config.current.recipientId, '456');
    expect(find.text('PSID: 456'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'اختبار صادر');
    await tester.tap(find.byTooltip('إرسال'));
    await tester.pumpAndSettle();
    expect(api.lastRecipient, '456');
    expect(find.text('تم الإرسال: m_sent'), findsOneWidget);
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, isEmpty);
    expect(find.text('synthetic_test_token'), findsNothing);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('rejected send preserves typed text and is not marked accepted', (tester) async {
    store.values['graph_reachability'] = 'ok';
    await controller.selectRecipient('456');
    api.reject = true;
    await tester.pumpWidget(ProviderScope(overrides: [probeProvider.overrideWith((_) => controller)],
      child: const MaterialApp(home: Scaffold(body: ChatScreen()))));
    await tester.enterText(find.byType(TextField), 'لا تمسح النص');
    await tester.tap(find.byTooltip('إرسال'));
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, 'لا تمسح النص');
    expect(controller.state.status, isNot(startsWith('تم الإرسال:')));
    expect(controller.state.busy, isFalse);
    await tester.pumpWidget(const SizedBox());
  });
}
