import 'dart:ui';
import 'package:flutter/widgets.dart';
import 'package:workmanager/workmanager.dart';
import '../../config.dart';
import 'graph_api.dart';
import 'local_store.dart';
import 'notifications.dart';
import 'sync_service.dart';

@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    WidgetsFlutterBinding.ensureInitialized();
    DartPluginRegistrant.ensureInitialized();
    if (task != backgroundTask) return true;
    try {
      const settings = ConfigStore();
      final config = await settings.load();
      if (!config.background || !config.ready) return true;
      final db = await LocalStore.open();
      try {
        final notifications = ProbeNotifications();
        await notifications.initialize();
        await SyncService(db, settings, GraphApi(), notifications).poll();
      } finally { await db.db.close(); }
      return true;
    } catch (_) { return false; }
  });
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
