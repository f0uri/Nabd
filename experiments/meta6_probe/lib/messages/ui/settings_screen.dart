import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../config.dart';
import '../data/notifications.dart';
import '../provider/probe_provider.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  final ConfigStore store;
  final ProbeNotifications notifications;
  const SettingsScreen({super.key, required this.store, required this.notifications});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}
class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  final page = TextEditingController(), token = TextEditingController(), recipient = TextEditingController();
  bool background = false, saving = false;
  String status = '';
  @override
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    final config = await widget.store.load();
    if (!mounted) return;
    setState(() { page.text = config.pageId; token.text = config.token;
      recipient.text = config.recipientId; background = config.background; });
  }
  @override
  void dispose() { page.dispose(); token.dispose(); recipient.dispose(); super.dispose(); }
  Future<void> save() async {
    if (!RegExp(r'^\d+$').hasMatch(page.text.trim()) || token.text.trim().isEmpty ||
        (recipient.text.trim().isNotEmpty && !RegExp(r'^\d+$').hasMatch(recipient.text.trim()))) {
      setState(() => status = 'Page ID وPSID أرقام فقط، والتوكن مطلوب. PSID اختياري للاستقبال.');
      return;
    }
    setState(() => saving = true);
    try {
      await widget.store.save(ProbeConfig(pageId: page.text.trim(), token: token.text.trim(),
        recipientId: recipient.text.trim(), background: background));
      await ref.read(probeProvider.notifier).configured();
      if (mounted) setState(() => status = 'حُفظت الإعدادات محليًا. شغّل NetTest ثم جرّب محادثة الصفحة.');
    } catch (_) { if (mounted) setState(() => status = 'تعذر حفظ أو جدولة الإعدادات. لم نعلن نجاحها.'); }
    finally { if (mounted) setState(() => saving = false); }
  }
  @override
  Widget build(BuildContext context) {
    final busy = saving || ref.watch(probeProvider).busy;
    return ListView(padding: const EdgeInsets.all(16), children: [
      const Text('TEST ONLY — صفحة وحساب اختبار فقط', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
      const Text('لا تدخل توكن صفحة إنتاج. هذا التوكن يسمح بالوصول لصندوق الصفحة. أدخله هنا على هاتفك، وليس في المحادثة. الحفظ محلي عبر Android Keystore.'),
      const SizedBox(height: 12),
      TextField(controller: page, enabled: !busy, keyboardType: TextInputType.number,
        decoration: const InputDecoration(labelText: 'Facebook Page ID')),
      TextField(controller: token, enabled: !busy, obscureText: true, autocorrect: false, enableSuggestions: false,
        decoration: const InputDecoration(labelText: 'Page access token — TEST ONLY')),
      TextField(controller: recipient, enabled: !busy, keyboardType: TextInputType.number,
        decoration: const InputDecoration(labelText: 'Recipient PSID — للإرسال')),
      const Padding(padding: EdgeInsets.symmetric(vertical: 12), child: Text('PSID هو معرّف مشارك محادثة الصفحة، وليس رقم هاتف أو Facebook User ID عاديًا. الحساب التجريبي يرسل للصفحة أولًا؛ الرد يخضع لنافذة Meta وصلاحيات التطبيق.')),
      SwitchListTile(value: background, onChanged: busy ? null : (value) => setState(() => background = value),
        title: const Text('مزامنة بالخلفية تقريبًا كل 15 دقيقة'),
        subtitle: const Text('Android وDoze وتوفير البطارية قد يؤخرونها. ليست Push ولا ضمان وصول فوري.')),
      FilledButton(onPressed: busy ? null : save, child: const Text('حفظ الإعدادات')),
      OutlinedButton(onPressed: () async {
        final allowed = await widget.notifications.requestPermission();
        if (mounted) setState(() => status = allowed ? 'إذن الإشعارات متاح.' : 'الإشعارات غير مسموحة؛ الرسائل تبقى في القائمة المحلية.');
      }, child: const Text('السماح بالإشعارات المحلية')),
      if (status.isNotEmpty) Padding(padding: const EdgeInsets.all(8), child: Text(status)),
    ]);
  }
}
