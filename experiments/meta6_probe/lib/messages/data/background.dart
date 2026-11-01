import 'dart:convert';
import 'dart:io';
import 'package:flutter/widgets.dart';
import 'package:path_provider/path_provider.dart';
import 'package:workmanager/workmanager.dart';
import '../../config.dart';
import 'graph_api.dart';
import 'local_store.dart';
import 'notifications.dart';
import 'sync_service.dart';

const healthTask = 'meta6.plugin.health';

@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    // Workmanager owns this Flutter engine and registers its native plugins.
    // Do not call DartPluginRegistrant on its root isolate.
    WidgetsFlutterBinding.ensureInitialized();
    if (task != backgroundTask && task != healthTask) return true;
    try {
      const settings = ConfigStore();
      final config = await settings.load();
      if (task == backgroundTask && (!config.background || !config.ready)) return true;
      final db = await LocalStore.open();
      try {
        final notifications = ProbeNotifications();
        await notifications.initialize();
        if (task == healthTask) {
          // One local, credential-free check per install. No HTTP/SMS is sent.
          await db.setValue('worker_health', 'ok');
          final cache = await getTemporaryDirectory();
          await File('${cache.path}/meta6-worker-ready.json').writeAsString(jsonEncode({
            'worker': true, 'database': true, 'notifications': true, 'secureStorage': true}));
        } else {
          await SyncService(db, settings, GraphApi(), notifications).poll();
        }
      } finally { await db.db.close(); }
      return true;
    } catch (_) { return false; }
  });
}

Future<void> checkWorkerOnce(LocalStore store) async {
  if (await store.value('worker_health') == 'ok') return;
  await Workmanager().registerOneOffTask(healthTask, healthTask,
    existingWorkPolicy: ExistingWorkPolicy.keep,
    constraints: Constraints(networkType: NetworkType.notRequired));
}

Future<void> scheduleBackground(bool enabled) async {
  if (!enabled) {
    await Workmanager().cancelByUniqueName(backgroundTask);
    return;
  }
  await Workmanager().registerPeriodicTask(backgroundTask, backgroundTask,
    frequency: const Duration(minutes: 15),
    existingWorkPolicy: ExistingPeriodicWorkPolicy.keep,
    // CONNECTED can require Google's Internet validation, which *6 may block.
    // Let the local scheduled task try Graph directly with bounded timeouts.
    constraints: Constraints(networkType: NetworkType.notRequired),
    backoffPolicy: BackoffPolicy.exponential,
    backoffPolicyDelay: const Duration(minutes: 1));
}
