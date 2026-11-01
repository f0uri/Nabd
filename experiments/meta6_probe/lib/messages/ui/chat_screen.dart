import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../config.dart';
import '../data/notifications.dart';
import '../provider/probe_provider.dart';
import 'settings_screen.dart';

class MessengerScreen extends ConsumerStatefulWidget {
  final ConfigStore config;
  final ProbeNotifications notifications;
  const MessengerScreen({super.key, required this.config, required this.notifications});
  @override
  ConsumerState<MessengerScreen> createState() => _MessengerScreenState();
}
class _MessengerScreenState extends ConsumerState<MessengerScreen> with WidgetsBindingObserver {
  late final ProbeController controller;
  bool foreground = true;
  @override
  void initState() {
    super.initState();
    controller = ref.read(probeProvider.notifier);
    WidgetsBinding.instance.addObserver(this);
    // Start after the route's first build, not during a provider consumer build.
    WidgetsBinding.instance.addPostFrameCallback((_) { if (mounted) controller.start(); });
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    foreground = state == AppLifecycleState.resumed;
    if (foreground && ModalRoute.of(context)?.isCurrent == true) { controller.start(); }
    else { controller.pause(); }
  }
  Future<void> settings() async {
    controller.pause();
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => Scaffold(
      appBar: AppBar(title: const Text('إعداد الصفحة')),
      body: SettingsScreen(store: widget.config, notifications: widget.notifications))));
    if (mounted && foreground) controller.start();
  }
  @override
  void dispose() {
    controller.pause();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('الرسائل'), actions: [
      IconButton(tooltip: 'إعداد الصفحة', onPressed: settings, icon: const Icon(Icons.settings_outlined)),
    ]), body: const ChatScreen());
}

class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({super.key});
  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}
class _ChatScreenState extends ConsumerState<ChatScreen> {
  final text = TextEditingController();
  final focus = FocusNode();
  @override
  void dispose() { text.dispose(); focus.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    final state = ref.watch(probeProvider);
    return Column(children: [
      Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Row(children: [
        Expanded(child: Text(state.status, style: const TextStyle(fontSize: 12))),
        IconButton(tooltip: 'تحديث', onPressed: state.busy ? null : () => ref.read(probeProvider.notifier).poll(),
          icon: const Icon(Icons.refresh_rounded)),
      ])),
      if (state.busy) const LinearProgressIndicator(minHeight: 2),
      Expanded(child: state.messages.isEmpty
        ? const Center(child: Text('لا رسائل'))
        : ListView.builder(reverse: true, itemCount: state.messages.length,
          itemBuilder: (_, index) {
            final message = state.messages[index];
            return Align(alignment: message.outgoing ? Alignment.centerRight : Alignment.centerLeft,
              child: Container(margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 5),
                padding: const EdgeInsets.all(12), constraints: const BoxConstraints(maxWidth: 330),
                decoration: BoxDecoration(color: message.outgoing ? const Color(0xff123b2e) : const Color(0xff17221c),
                  borderRadius: BorderRadius.circular(16)),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(message.fromName, style: const TextStyle(fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  SelectableText(message.text),
                  if (!message.outgoing && RegExp(r'^\d+$').hasMatch(message.fromId))
                    TextButton.icon(onPressed: state.busy ? null : () async {
                      await ref.read(probeProvider.notifier).selectRecipient(message.fromId);
                      if (mounted) focus.requestFocus();
                    }, icon: const Icon(Icons.reply_rounded, size: 16), label: const Text('الرد')),
                  Text(message.created, textDirection: TextDirection.ltr,
                    style: const TextStyle(fontSize: 10, color: Color(0xffa2b5aa))),
                ])));
          })),
      if (state.recipientId.isNotEmpty) Padding(padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Align(alignment: Alignment.centerRight, child: Text('PSID: ${state.recipientId}',
          textDirection: TextDirection.ltr, style: const TextStyle(fontSize: 12, color: Color(0xffa2b5aa))))),
      SafeArea(top: false, child: Padding(padding: const EdgeInsets.all(12), child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(child: TextField(controller: text, focusNode: focus, minLines: 1, maxLines: 4, maxLength: 2000,
          decoration: const InputDecoration(hintText: 'رسالة', border: OutlineInputBorder()))),
        IconButton(tooltip: 'إرسال', onPressed: state.busy || state.recipientId.isEmpty ? null : () async {
          final body = text.text;
          if (body.trim().isEmpty) return;
          final sent = await ref.read(probeProvider.notifier).send(body);
          if (sent && mounted && text.text == body) text.clear();
        }, icon: const Icon(Icons.send_rounded)),
      ]))),
    ]);
  }
}
