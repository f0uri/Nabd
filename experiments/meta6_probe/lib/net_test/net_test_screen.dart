import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../messages/data/local_store.dart';
import 'net_test_service.dart';

class NetTestScreen extends StatefulWidget {
  final LocalStore store;
  final NetTestService? service;
  const NetTestScreen({super.key, required this.store, this.service});

  @override
  State<NetTestScreen> createState() => _NetTestScreenState();
}

class _NetTestScreenState extends State<NetTestScreen> {
  late final NetTestService service = widget.service ?? NetTestService();
  List<NetResult> results = [];
  String report = '';
  bool running = false;
  bool failed = false;

  Future<void> run() async {
    if (running) return;
    setState(() { running = true; failed = false; results = []; report = ''; });
    try {
      final next = await service.run();
      final graph = next.where((r) => r.target == netTargets.first);
      final text = ['META6 PROBE 0.2.0', DateTime.now().toUtc().toIso8601String(),
        'Network/carrier plan: not verified',
        'HTTP response = reachable only; TCP connection = port reachable only.',
        ...next.map((r) => r.plain)].join('\n');
      // Keep the transport gate and plain-text diagnostics off the simple UI.
      try {
        await widget.store.setValue('graph_reachability',
          graph.isNotEmpty && graph.first.reachable ? 'ok' : 'fail');
        await widget.store.setValue('net_results', text);
      } catch (_) { /* A storage failure must not hide actual probe results. */ }
      if (mounted) setState(() { results = next; report = text; });
    } catch (_) {
      if (mounted) setState(() { failed = true; });
    } finally {
      if (mounted) setState(() { running = false; });
    }
  }

  @override
  void dispose() {
    if (widget.service == null) service.dio.close(force: true);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final byTarget = {for (final r in results) r.target: r};
    final websites = netTargets.where((target) => target.startsWith('https:')).toList();
    // Successful HTTPS hosts first; a TCP connection is never listed as a website.
    websites.sort((a, b) {
      final order = (byTarget[b]?.reachable == true ? 1 : 0)
        .compareTo(byTarget[a]?.reachable == true ? 1 : 0);
      return order == 0 ? netTargets.indexOf(a).compareTo(netTargets.indexOf(b)) : order;
    });
    return Scaffold(
      appBar: AppBar(title: const Text('فحص الاتصال'), actions: [
        IconButton(tooltip: 'نسخ النتائج',
          onPressed: report.isEmpty || running ? null : () => Clipboard.setData(ClipboardData(text: report)),
          icon: const Icon(Icons.copy_rounded)),
        const SizedBox(width: 8),
      ]),
      body: SafeArea(child: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 600),
        child: ListView(padding: const EdgeInsets.fromLTRB(20, 16, 20, 28), children: [
          SizedBox(height: 58, child: FilledButton.icon(
            key: const ValueKey('run-test'),
            onPressed: running ? null : run,
            icon: running
              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.play_arrow_rounded, size: 27),
            label: Text(running ? 'جارٍ الفحص' : 'تشغيل'),
          )),
          if (failed) const Padding(padding: EdgeInsets.only(top: 16),
            child: Text('تعذر الفحص', style: TextStyle(color: Color(0xffff8888)))),
          const SizedBox(height: 26),
          const _SectionTitle('المواقع', trailing: 'HTTPS'),
          const SizedBox(height: 10),
          for (final target in websites) _ResultTile(target: target,
            result: byTarget[target], running: running),
          const SizedBox(height: 20),
          const _SectionTitle('اتصال TCP', trailing: '5228'),
          const SizedBox(height: 10),
          _ResultTile(target: netTargets.last, result: byTarget[netTargets.last],
            running: running, tcp: true),
        ]),
      ))),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String title;
  final String trailing;
  const _SectionTitle(this.title, {required this.trailing});
  @override
  Widget build(BuildContext context) => Row(children: [
    Expanded(child: Text(title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600))),
    Text(trailing, textDirection: TextDirection.ltr,
      style: const TextStyle(fontSize: 12, color: Color(0xff91a39c))),
  ]);
}

class _ResultTile extends StatelessWidget {
  final String target;
  final NetResult? result;
  final bool running;
  final bool tcp;
  const _ResultTile({required this.target, required this.result, required this.running, this.tcp = false});

  @override
  Widget build(BuildContext context) {
    final host = Uri.parse(target).host;
    final color = result == null ? const Color(0xff91a39c)
      : result!.reachable ? const Color(0xff00e8b0) : const Color(0xffff8888);
    final status = running ? 'جارٍ الفحص' : result == null ? '—'
      : result!.reachable ? (tcp ? 'TCP متصل' : 'متاح') : 'لم يصل';
    return Container(
      key: ValueKey(target),
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
      decoration: BoxDecoration(color: const Color(0xff111b17),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xff24342c))),
      child: Row(children: [
        Icon(running ? Icons.more_horiz_rounded : result == null ? Icons.language_rounded
          : result!.reachable ? Icons.check_circle_rounded : Icons.cancel_outlined,
          size: 23, color: color),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(host, textDirection: TextDirection.ltr,
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
          const SizedBox(height: 6),
          Wrap(spacing: 12, runSpacing: 4, children: [
            Text(status, style: TextStyle(color: color, fontSize: 12)),
            if (result != null && !running) Text(
              '${result!.httpCode == null ? '' : 'HTTP ${result!.httpCode} · '}${result!.elapsedMs} ms',
              textDirection: TextDirection.ltr,
              style: const TextStyle(color: Color(0xff91a39c), fontSize: 12)),
          ]),
        ])),
      ]),
    );
  }
}
