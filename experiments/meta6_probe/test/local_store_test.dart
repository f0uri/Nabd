import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:meta6_probe/messages/data/local_store.dart';
import 'package:meta6_probe/messages/data/message.dart';

void main() {
  late LocalStore store;
  setUpAll(sqfliteFfiInit);
  setUp(() async {
    store = LocalStore(await databaseFactoryFfi.openDatabase(inMemoryDatabasePath,
      options: OpenDatabaseOptions(version: 1, onCreate: LocalStore.createSchema)));
  });
  tearDown(() async => store.db.close());
  ProbeMessage incoming(String page) => ProbeMessage(id: 'm1', pageId: page,
    text: 'سلام', fromId: '456', fromName: 'اختبار', created: '2026-11-01T10:00:00Z');
  test('duplicate message IDs persist once within each Page, not across Pages', () async {
    await store.put([incoming('123'), incoming('123'), incoming('999')]);
    expect((await store.messages('123')).length, 1);
    expect((await store.messages('999')).length, 1);
  });
  test('conditional SQLite notification claim wins once', () async {
    await store.put([incoming('123')]);
    final id = (await store.pending('123')).single['local_id'] as int;
    final claims = await Future.wait([store.claim(id), store.claim(id)]);
    expect(claims.where((value) => value).length, 1);
    expect(await store.pending('123'), isEmpty);
  });
  test('sent messages do not generate incoming notifications', () async {
    await store.put([const ProbeMessage(id: 'sent', pageId: '123', text: 'جواب',
      fromId: '123', fromName: 'Page', created: '2026-11-01T10:00:00Z')]);
    expect(await store.pending('123'), isEmpty);
    expect((await store.messages('123')).single.outgoing, isTrue);
  });
}
