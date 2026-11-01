import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite/sqflite.dart';
import 'package:meta6_probe/main.dart';
import 'package:meta6_probe/messages/data/local_store.dart';
import 'package:meta6_probe/net_test/net_test_screen.dart';
import 'package:meta6_probe/net_test/net_test_service.dart';

class UnusedDatabase implements Database {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
class MemoryStore extends LocalStore {
  final values = <String, String>{};
  final bool failWrites;
  MemoryStore({this.failWrites = false}) : super(UnusedDatabase());
  @override
  Future<void> setValue(String key, String value) async {
    if (failWrites) throw StateError('storage unavailable');
    values[key] = value;
  }
}
class FakeService extends NetTestService {
  final pending = Completer<List<NetResult>>();
  int calls = 0;
  @override
  Future<List<NetResult>> run() { calls++; return pending.future; }
}
List<NetResult> sample() => [
  for (final target in netTargets)
    NetResult(target, target == netTargets.first || target == netTargets.last,
      125, httpCode: target == netTargets.first ? 401 : null,
      error: target == netTargets.last ? null : 'timeout'),
];

void main() {
  testWidgets('app has one surface, no messaging tabs, setup or assistant copy', (tester) async {
    await tester.pumpWidget(ProbeApp(store: MemoryStore()));
    expect(find.text('تشغيل'), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);
    for (final text in ['الرسائل', 'الإعدادات', 'Graph FAIL', 'SMS', 'آخر نتائج محفوظة', 'Run test']) {
      expect(find.textContaining(text), findsNothing);
    }
    expect(find.text('فحص الاتصال'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  for (final failWrites in [false, true]) {
    testWidgets('real probe values stay visible, TCP stays separate, storage failure=$failWrites', (tester) async {
      final store = MemoryStore(failWrites: failWrites);
      final service = FakeService();
      tester.view.physicalSize = const Size(360, 1000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String;
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      await tester.pumpWidget(MaterialApp(theme: ThemeData.dark(), home: Directionality(
        textDirection: TextDirection.rtl,
        child: NetTestScreen(store: store, service: service))));
      await tester.tap(find.byKey(const ValueKey('run-test')));
      await tester.pump();
      expect(service.calls, 1);
      expect(tester.widget<FilledButton>(find.byKey(const ValueKey('run-test'))).onPressed, isNull);
      service.pending.complete(sample());
      await tester.pumpAndSettle();
      expect(find.text('متاح'), findsOneWidget); // HTTP 401 still means reachable.
      expect(find.text('HTTP 401 · 125 ms'), findsOneWidget);
      await tester.tap(find.byTooltip('نسخ النتائج'));
      await tester.pump();
      expect(copied, contains('OK https://graph.facebook.com HTTP 401'));
      expect(copied, contains('port reachable only'));
      await tester.scrollUntilVisible(find.text('TCP متصل'), 250);
      expect(find.text('اتصال TCP'), findsOneWidget);
      expect(find.text('TCP متصل'), findsOneWidget);
      expect(find.text('Graph FAIL'), findsNothing);
      expect(tester.takeException(), isNull);
      if (!failWrites) expect(store.values['graph_reachability'], 'ok');
      await tester.pumpWidget(const SizedBox());
      service.dio.close(force: true);
    });
  }
}
