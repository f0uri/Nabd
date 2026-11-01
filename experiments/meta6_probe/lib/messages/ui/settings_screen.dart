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
  bool background = false, saving = false, loading = true;
  String status = '';
  String savedPage = '';
  @override
  void initState() { super.initState(); load(); }
  Future<void> load() async {
    try {
      final config = await widget.store.load();
      if (!mounted) return;
      setState(() { page.text = config.pageId; token.text = config.token;
        recipient.text = config.recipientId; background = config.background; savedPage = config.pageId; });
    } catch (_) { if (mounted) setState(() => status = 'تعذر تحميل الإعدادات'); }
    finally { if (mounted) setState(() => loading = false); }
  }
  @override
  void dispose() { page.dispose(); token.dispose(); recipient.dispose(); super.dispose(); }
  Future<void> save() async {
    if (!RegExp(r'^\d+$').hasMatch(page.text.trim()) || token.text.trim().isEmpty ||
        (recipient.text.trim().isNotEmpty && !RegExp(r'^\d+$').hasMatch(recipient.text.trim()))) {
      setState(() => status = 'تحقق من Page ID والتوكن وPSID');
      return;
    }
    setState(() => saving = true);
    try {
      await widget.store.save(ProbeConfig(pageId: page.text.trim(), token: token.text.trim(),
        recipientId: recipient.text.trim(), background: background));
      savedPage = page.text.trim();
      await ref.read(probeProvider.notifier).configured();
      if (mounted) setState(() => status = 'تم الحفظ');
    } catch (_) { if (mounted) setState(() => status = 'تعذر إكمال الحفظ أو الجدولة'); }
    finally { if (mounted) setState(() => saving = false); }
  }
  @override
  Widget build(BuildContext context) {
    final busy = loading || saving || ref.watch(probeProvider).busy;
    if (loading) return const Center(child: CircularProgressIndicator());
    return ListView(padding: const EdgeInsets.all(20), children: [
      TextField(controller: page, enabled: !busy, keyboardType: TextInputType.number,
        textDirection: TextDirection.ltr,
        onChanged: (value) { if (value.trim() != savedPage) recipient.clear(); },
        decoration: const InputDecoration(labelText: 'Facebook Page ID')),
      const SizedBox(height: 14),
      TextField(controller: token, enabled: !busy, obscureText: true, autocorrect: false,
        enableSuggestions: false, enableIMEPersonalizedLearning: false,
        textDirection: TextDirection.ltr,
        decoration: const InputDecoration(labelText: 'Page token — اختبار فقط')),
      const SizedBox(height: 14),
      TextField(controller: recipient, enabled: !busy, keyboardType: TextInputType.number,
        textDirection: TextDirection.ltr,
        decoration: const InputDecoration(labelText: 'Recipient PSID')),
      const SizedBox(height: 14),
      SwitchListTile(contentPadding: EdgeInsets.zero, value: background,
        onChanged: busy ? null : (value) => setState(() => background = value),
        title: const Text('الخلفية (حوالي 15 دقيقة)')),
      const SizedBox(height: 14),
      FilledButton(onPressed: busy ? null : save, child: const Text('حفظ')),
      const SizedBox(height: 8),
      OutlinedButton(onPressed: busy ? null : () async {
        try {
          final allowed = await widget.notifications.requestPermission();
          if (mounted) setState(() => status = allowed ? 'الإشعارات مفعّلة' : 'الإشعارات غير مسموحة');
        } catch (_) { if (mounted) setState(() => status = 'تعذر طلب الإذن'); }
      }, child: const Text('الإشعارات')),
      if (status.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Text(status)),
    ]);
  }
}
