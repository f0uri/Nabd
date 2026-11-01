import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../provider/probe_provider.dart';

class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({super.key});
  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}
class _ChatScreenState extends ConsumerState<ChatScreen> {
  final text = TextEditingController();
  @override
  void dispose() { text.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    final state = ref.watch(probeProvider);
    return Column(children: [
      Card(margin: const EdgeInsets.all(12), child: ListTile(
        title: Text(state.status, style: const TextStyle(fontSize: 13)),
        subtitle: const Text('صفحة تجريبية مشتركة، وليست حساب نبض. التحديث كل 15 ثانية أثناء فتح التطبيق.'),
        trailing: IconButton(onPressed: state.busy ? null : () => ref.read(probeProvider.notifier).poll(),
          icon: const Icon(Icons.refresh)))),
      if (state.busy) const LinearProgressIndicator(),
      Expanded(child: state.messages.isEmpty
        ? const Center(child: Text('لا رسائل محلية. أرسل رسالة إلى الصفحة من حساب Facebook تجريبي، ثم حدّث.'))
        : ListView.builder(reverse: true, itemCount: state.messages.length,
          itemBuilder: (_, index) {
            final message = state.messages[index];
            return Align(alignment: message.outgoing ? Alignment.centerRight : Alignment.centerLeft,
              child: Container(margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                padding: const EdgeInsets.all(12), constraints: const BoxConstraints(maxWidth: 330),
                decoration: BoxDecoration(color: message.outgoing ? const Color(0xffd9f5e8) : const Color(0xffedf0f5),
                  borderRadius: BorderRadius.circular(14)),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(message.fromName, style: const TextStyle(fontWeight: FontWeight.bold)),
                  SelectableText(message.text),
                  if (!message.outgoing && message.fromId.isNotEmpty)
                    TextButton.icon(onPressed: () async {
                      await Clipboard.setData(ClipboardData(text: message.fromId));
                      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('نُسخ PSID. ألصقه في خانة المستلم بالإعدادات.')));
                    }, icon: const Icon(Icons.copy, size: 14), label: const Text('نسخ PSID للرد')),
                  Text(message.created,
                    textDirection: TextDirection.ltr, style: const TextStyle(fontSize: 10)),
                ])));
          })),
      SafeArea(top: false, child: Padding(padding: const EdgeInsets.all(12), child: Row(children: [
        Expanded(child: TextField(controller: text, minLines: 1, maxLines: 4, maxLength: 2000,
          decoration: const InputDecoration(hintText: 'رد نصي إلى PSID المحفوظ', border: OutlineInputBorder()))),
        IconButton(onPressed: state.busy ? null : () async {
          final body = text.text;
          if (body.trim().isEmpty) return;
          final sent = await ref.read(probeProvider.notifier).send(body);
          if (sent && mounted && text.text == body) text.clear();
        }, icon: const Icon(Icons.send)),
      ]))),
    ]);
  }
}
