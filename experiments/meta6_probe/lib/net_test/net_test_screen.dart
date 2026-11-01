import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../messages/data/local_store.dart';
import 'net_test_service.dart';

class NetTestScreen extends StatefulWidget {
  final LocalStore store;
  const NetTestScreen({super.key, required this.store});
  @override
  State<NetTestScreen> createState() => _NetTestScreenState();
}
class _NetTestScreenState extends State<NetTestScreen> {
  final service = NetTestService();
  List<NetResult> results = [];
  String mode = 'غير محدد';
  String saved = '';
  bool running = false;
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async {
    final value = await widget.store.value('net_results') ?? '';
    if (mounted) setState(() => saved = value);
  }
  Future<void> run() async {
    setState(() { running = true; results = []; });
    try {
      final report = await service.run();
      final graph = report.firstWhere((r) => r.target == 'https://graph.facebook.com');
      final text = ['META6 PROBE 0.1.0', DateTime.now().toUtc().toIso8601String(),
        'Network mode (user-declared, not verified): $mode',
        'HTTP responses incl. 3xx/4xx/5xx count as reachable, NOT API authorization.',
        ...report.map((r) => r.plain)].join('\n');
      await widget.store.setValue('graph_reachability', graph.reachable ? 'ok' : 'fail');
      await widget.store.setValue('net_results', text);
      if (mounted) setState(() { results = report; saved = text; });
    } catch (_) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تعذر إكمال الاختبار. لم نسجل نجاحًا.')));
    } finally { if (mounted) setState(() => running = false); }
  }
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(16), children: [
    const Text('اختبار الوصول — NetTest', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
    const Text('لاختبار *6: طفي Wi‑Fi والإنترنت العادي. التطبيق لا يستطيع التأكد من نوع عرض المشغّل. أي جواب HTTP يعني الوصول فقط.'),
    DropdownButton<String>(value: mode, isExpanded: true,
      items: ['غير محدد', '*6 فقط، بلا Wi-Fi أو بيانات عادية', 'Wi-Fi', 'بيانات عادية']
        .map((value) => DropdownMenuItem(value: value, child: Text(value))).toList(),
      onChanged: running ? null : (value) => setState(() => mode = value!)),
    FilledButton.icon(onPressed: running ? null : run,
      icon: running ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.network_check),
      label: Text(running ? 'جاري الاختبار…' : 'Run test')),
    OutlinedButton.icon(onPressed: saved.isEmpty || running ? null : () async {
      await Clipboard.setData(ClipboardData(text: saved));
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('تم نسخ النتائج بلا أي توكن أو رسائل.')));
    }, icon: const Icon(Icons.copy), label: const Text('Copy results')),
    for (final result in results) Card(child: ListTile(
      leading: Icon(result.reachable ? Icons.check_circle : Icons.cancel,
        color: result.reachable ? Colors.green : Colors.red),
      title: Text(result.target, textDirection: TextDirection.ltr, style: const TextStyle(fontSize: 13)),
      subtitle: Text('${result.reachable ? "OK" : "FAIL"} • ${result.httpCode == null ? result.error ?? "TCP_CONNECTED" : "HTTP ${result.httpCode}"} • ${result.elapsedMs} ms'))),
    if (results.any((r) => r.target == 'https://graph.facebook.com' && !r.reachable))
      const Card(child: Padding(padding: EdgeInsets.all(16), child: Text('Graph FAIL: أوقفنا تجربة الرسائل. انسخ النتائج وأرسلها. لم نفعّل قراءة SMS؛ هذا مسار مختلف وليس نجاحًا لـ*6.'))),
    if (saved.isNotEmpty) ExpansionTile(title: const Text('آخر نتائج محفوظة'), children: [
      Padding(padding: const EdgeInsets.all(8), child: SelectableText(saved, textDirection: TextDirection.ltr))]),
  ]);
}
