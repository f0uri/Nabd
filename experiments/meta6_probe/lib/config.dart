import 'package:flutter_secure_storage/flutter_secure_storage.dart';

// TEST ONLY. Never use a production Page or real-user inbox in this prototype.
// A token in an APK is extractable. A small authorized proxy can be considered
// later; it is NOT used here because this experiment tests Meta-only transport.
// Empty defaults are intentional: enter credentials ON THE PHONE, never in chat.
const testPageId = String.fromEnvironment('META6_PAGE_ID');
const testPageToken = String.fromEnvironment('META6_PAGE_TOKEN');
const testRecipientId = String.fromEnvironment('META6_RECIPIENT_ID');
const graphVersion = 'v21.0';
const foregroundInterval = Duration(seconds: 15);
const backgroundTask = 'meta6.page.poll';

class ProbeConfig {
  final String pageId;
  final String token;
  final String recipientId;
  final bool background;
  const ProbeConfig({this.pageId = testPageId, this.token = testPageToken,
    this.recipientId = testRecipientId, this.background = false});
  bool get ready => RegExp(r'^\d+$').hasMatch(pageId) && token.isNotEmpty;
}

class ConfigStore {
  final FlutterSecureStorage storage;
  const ConfigStore([this.storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true))]);
  Future<ProbeConfig> load() async => ProbeConfig(
    pageId: await storage.read(key: 'page') ?? testPageId,
    token: await storage.read(key: 'token') ?? testPageToken,
    recipientId: await storage.read(key: 'recipient') ?? testRecipientId,
    background: await storage.read(key: 'background') == 'true');
  Future<void> save(ProbeConfig config) async {
    await storage.write(key: 'page', value: config.pageId);
    await storage.write(key: 'token', value: config.token);
    await storage.write(key: 'recipient', value: config.recipientId);
    await storage.write(key: 'background', value: '${config.background}');
  }
}
