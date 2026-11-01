import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path_provider/path_provider.dart';
import 'package:workmanager/workmanager.dart';
import 'config.dart';
import 'messages/data/background.dart';
import 'messages/data/graph_api.dart';
import 'messages/data/local_store.dart';
import 'messages/data/notifications.dart';
import 'messages/data/sync_service.dart';
import 'messages/provider/probe_provider.dart';
import 'messages/ui/chat_screen.dart';
import 'messages/ui/settings_screen.dart';
import 'net_test/net_test_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    final store = await LocalStore.open();
    const config = ConfigStore();
    final notifications = ProbeNotifications();
    await notifications.initialize();
    await Workmanager().initialize(callbackDispatcher);
    final saved = await config.load();
    await scheduleBackground(saved.background);
    await checkWorkerOnce(store);
    final sync = SyncService(store, config, GraphApi(), notifications);
    runApp(ProviderScope(overrides: [probeProvider.overrideWith(
      (_) => ProbeController(store, config, sync))], child: ProbeApp(store: store,
        config: config, notifications: notifications)));
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      // Fixed local CI startup proof only. No token/account/message/network data.
      final cache = await getTemporaryDirectory();
      await File('${cache.path}/meta6-ready.json').writeAsString(jsonEncode({
        'ready': true, 'database': true, 'notifications': true, 'workmanager': true}));
    });
  } catch (_) {
    runApp(const MaterialApp(home: Directionality(textDirection: TextDirection.rtl,
      child: Scaffold(body: Center(child: Text('تعذر تهيئة التطبيق التجريبي. لا توجد نتيجة شبكة أو مراسلة مؤكدة.'))))));
  }
}

class ProbeApp extends StatelessWidget {
  final LocalStore store;
  final ConfigStore config;
  final ProbeNotifications notifications;
  const ProbeApp({super.key, required this.store, required this.config, required this.notifications});
  @override
  Widget build(BuildContext context) => MaterialApp(debugShowCheckedModeBanner: false,
    title: 'Meta6 Lab', theme: ThemeData(useMaterial3: true, colorSchemeSeed: const Color(0xff176b50)),
    builder: (_, child) => Directionality(textDirection: TextDirection.rtl, child: child!),
    home: ProbeHome(store: store, config: config, notifications: notifications));
}

class ProbeHome extends ConsumerStatefulWidget {
  final LocalStore store;
  final ConfigStore config;
  final ProbeNotifications notifications;
  const ProbeHome({super.key, required this.store, required this.config, required this.notifications});
  @override
  ConsumerState<ProbeHome> createState() => _ProbeHomeState();
}
class _ProbeHomeState extends ConsumerState<ProbeHome> with WidgetsBindingObserver {
  int tab = 0;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    ref.read(probeProvider.notifier).start();
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final controller = ref.read(probeProvider.notifier);
    if (state == AppLifecycleState.resumed) { controller.start(); controller.poll(); }
    else { controller.pause(); }
  }
  @override
  void dispose() { WidgetsBinding.instance.removeObserver(this); super.dispose(); }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Meta6 Lab — تجربة مستقلة')),
    body: IndexedStack(index: tab, children: [
      NetTestScreen(store: widget.store), const ChatScreen(),
      SettingsScreen(store: widget.config, notifications: widget.notifications)]),
    bottomNavigationBar: NavigationBar(selectedIndex: tab, onDestinationSelected: (value) => setState(() => tab = value),
      destinations: const [NavigationDestination(icon: Icon(Icons.network_check), label: 'NetTest'),
        NavigationDestination(icon: Icon(Icons.chat_bubble_outline), label: 'الرسائل'),
        NavigationDestination(icon: Icon(Icons.settings_outlined), label: 'الإعدادات')]));
}
