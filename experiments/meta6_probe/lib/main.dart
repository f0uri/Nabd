import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'messages/data/graph_api.dart';
import 'messages/data/sync_service.dart';
import 'messages/provider/probe_provider.dart';
import 'messages/ui/chat_screen.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:workmanager/workmanager.dart';
import 'config.dart';
import 'messages/data/background.dart';
import 'messages/data/local_store.dart';
import 'messages/data/notifications.dart';
import 'net_test/net_test_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  Directory? cache;
  String stage = 'cache';
  Future<void> checkpoint(String value, {Object? error}) async {
    stage = value;
    try {
      cache ??= await getTemporaryDirectory();
      await File('${cache!.path}/meta6-phase.json').writeAsString(jsonEncode({
        'stage': stage, if (error != null) 'errorType': error.runtimeType.toString(),
        if (error is PlatformException) 'code': error.code.replaceAll(RegExp(r'[^a-zA-Z0-9_.-]'), '').split('').take(60).join(),
      }));
    } catch (_) { /* Diagnostics must never expose credentials or break startup. */ }
  }
  try {
    await checkpoint('database');
    final store = await LocalStore.open();
    const config = ConfigStore();
    final notifications = ProbeNotifications();
    await checkpoint('notifications');
    await notifications.initialize();
    await checkpoint('workmanager');
    await Workmanager().initialize(callbackDispatcher);
    await checkpoint('secureStorage');
    final saved = await config.load();
    await checkpoint('backgroundSchedule');
    await scheduleBackground(saved.background);
    await checkpoint('backgroundHealthSchedule');
    await checkWorkerOnce(store);
    await checkpoint('firstFrame');
    final sync = SyncService(store, config, GraphApi(), notifications);
    runApp(ProviderScope(overrides: [probeProvider.overrideWith(
      (_) => ProbeController(store, config, sync))], child: ProbeApp(store: store,
      messagesBuilder: (_) => MessengerScreen(config: config, notifications: notifications))));
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      // Fixed local CI startup proof only. No token/account/message/network data.
      final cache = await getTemporaryDirectory();
      await File('${cache.path}/meta6-ready.json').writeAsString(jsonEncode({
        'ready': true, 'database': true, 'notifications': true, 'workmanager': true}));
    });
  } catch (error) {
    await checkpoint(stage, error: error);
    runApp(const MaterialApp(home: Directionality(textDirection: TextDirection.rtl,
      child: Scaffold(body: Center(child: Text('تعذر التشغيل'))))));
  }
}

class ProbeApp extends StatelessWidget {
  final LocalStore store;
  final WidgetBuilder? messagesBuilder;
  const ProbeApp({super.key, required this.store, this.messagesBuilder});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'Meta6 Lab',
    theme: ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: const Color(0xff080e0b),
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff00e8b0),
        brightness: Brightness.dark, primary: const Color(0xff00e8b0),
        onPrimary: const Color(0xff00291e), surface: const Color(0xff080e0b)),
      appBarTheme: const AppBarTheme(backgroundColor: Color(0xff080e0b),
        surfaceTintColor: Colors.transparent, centerTitle: false),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
        textStyle: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)))),
    ),
    builder: (_, child) => Directionality(textDirection: TextDirection.rtl, child: child!),
    home: NetTestScreen(store: store, messagesBuilder: messagesBuilder),
  );
}
