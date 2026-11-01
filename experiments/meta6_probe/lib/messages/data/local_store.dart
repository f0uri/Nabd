import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';
import 'message.dart';

class LocalStore {
  final Database db;
  LocalStore(this.db);
  static Future<LocalStore> open() async {
    final database = await openDatabase(path.join(await getDatabasesPath(), 'meta6.db'),
      // Use Android's supported connection/journal defaults. PRAGMA setters
      // can return rows and fail through Android SQLite execSQL even when a
      // desktop FFI test accepts them. Each engine owns its own connection.
      version: 1, singleInstance: false, onCreate: createSchema);
    return LocalStore(database);
  }
  static Future<void> createSchema(Database db, int version) async {
    await db.execute('CREATE TABLE messages (local_id INTEGER PRIMARY KEY AUTOINCREMENT,'
      ' message_id TEXT NOT NULL, page_id TEXT NOT NULL, body TEXT NOT NULL,'
      ' from_id TEXT NOT NULL, from_name TEXT NOT NULL, created TEXT NOT NULL,'
      ' notified INTEGER NOT NULL DEFAULT 0, UNIQUE(page_id,message_id))');
    await db.execute('CREATE TABLE state (key TEXT PRIMARY KEY, value TEXT NOT NULL)');
  }
  Future<void> put(List<ProbeMessage> messages) async {
    await db.transaction((txn) async {
      for (final message in messages) {
        await txn.insert('messages', message.row, conflictAlgorithm: ConflictAlgorithm.ignore);
      }
    });
  }
  Future<List<ProbeMessage>> messages(String pageId) async =>
    (await db.query('messages', where: 'page_id=?', whereArgs: [pageId],
      orderBy: 'created DESC, local_id DESC', limit: 300)).map(ProbeMessage.fromRow).toList();
  Future<List<Map<String, Object?>>> pending(String pageId) => db.query('messages',
    where: 'page_id=? AND notified=0', whereArgs: [pageId], orderBy: 'local_id', limit: 100);
  Future<bool> claim(int localId) async => await db.update('messages', {'notified': 1},
    where: 'local_id=? AND notified=0', whereArgs: [localId]) == 1;
  Future<String?> value(String key) async {
    final rows = await db.query('state', where: 'key=?', whereArgs: [key]);
    return rows.isEmpty ? null : rows.first['value'] as String;
  }
  Future<void> setValue(String key, String value) async {
    await db.insert('state', {'key': key, 'value': value}, conflictAlgorithm: ConflictAlgorithm.replace);
  }
}
