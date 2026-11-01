import 'package:flutter_local_notifications/flutter_local_notifications.dart';

class ProbeNotifications {
  final FlutterLocalNotificationsPlugin plugin;
  ProbeNotifications({FlutterLocalNotificationsPlugin? client})
    : plugin = client ?? FlutterLocalNotificationsPlugin();
  Future<void> initialize() async {
    await plugin.initialize(const InitializationSettings(
      android: AndroidInitializationSettings('probe_message')));
    await plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
      ?.createNotificationChannel(const AndroidNotificationChannel('meta6_text',
        'رسائل التجربة', description: 'رسائل الصفحة التجريبية فقط', importance: Importance.high));
  }
  Future<bool> requestPermission() async => await plugin
    .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
    ?.requestNotificationsPermission() ?? true;
  Future<bool> get allowed async => await plugin
    .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
    ?.areNotificationsEnabled() ?? false;
  Future<void> show(int localId, String sender, String body) => plugin.show(
    localId, sender, body, const NotificationDetails(android: AndroidNotificationDetails(
      'meta6_text', 'رسائل التجربة', importance: Importance.high, priority: Priority.high,
      visibility: NotificationVisibility.private, icon: 'probe_message')));
}
