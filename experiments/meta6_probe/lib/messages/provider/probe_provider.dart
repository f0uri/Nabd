import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../config.dart';
import '../data/background.dart';
import '../data/local_store.dart';
import '../data/message.dart';
import '../data/sync_service.dart';

class ProbeState {
  final List<ProbeMessage> messages;
  final String status;
  final String recipientId;
  final bool busy;
  const ProbeState({this.messages = const [], this.status = '', this.recipientId = '', this.busy = false});
  ProbeState copyWith({List<ProbeMessage>? messages, String? status, String? recipientId, bool? busy}) =>
    ProbeState(messages: messages ?? this.messages, status: status ?? this.status,
      recipientId: recipientId ?? this.recipientId, busy: busy ?? this.busy);
}
class ProbeController extends StateNotifier<ProbeState> {
  final LocalStore store;
  final ConfigStore configStore;
  final SyncService sync;
  final Future<void> Function(bool) schedule;
  Timer? _timer;
  bool _active = false;
  ProbeController(this.store, this.configStore, this.sync, {this.schedule = scheduleBackground}) : super(const ProbeState());
  Future<void> refresh({String? result}) async {
    final config = await configStore.load();
    final messages = await store.messages(config.pageId);
    final status = result ?? await store.value('status') ?? state.status;
    if (mounted) state = state.copyWith(messages: messages, status: status, recipientId: config.recipientId);
  }
  Future<void> poll() async {
    if (!mounted || state.busy) return;
    state = state.copyWith(busy: true);
    try {
      final status = await sync.poll();
      if (!mounted) return;
      state = state.copyWith(status: status);
      await refresh(result: status);
    } catch (_) {
      if (mounted) state = state.copyWith(status: 'تعذرت المزامنة');
    } finally {
      if (mounted) state = state.copyWith(busy: false);
    }
  }
  Future<bool> send(String text) async {
    if (!mounted || state.busy) return false;
    state = state.copyWith(busy: true);
    bool accepted = false;
    try {
      final result = await sync.send(text);
      accepted = result.startsWith('تم الإرسال:');
      if (!mounted) return accepted;
      state = state.copyWith(status: result);
      await refresh(result: result);
    } catch (_) {
      if (mounted) state = state.copyWith(status: 'تعذرت العملية؛ تحقق من المحادثة قبل إعادة الإرسال');
    } finally {
      if (mounted) state = state.copyWith(busy: false);
    }
    return accepted;
  }
  Future<void> selectRecipient(String id) async {
    if (!mounted || state.busy || !RegExp(r'^\d+$').hasMatch(id)) return;
    state = state.copyWith(busy: true);
    try {
      final config = await configStore.load();
      await configStore.save(ProbeConfig(pageId: config.pageId, token: config.token,
        recipientId: id, background: config.background));
      if (mounted) state = state.copyWith(recipientId: id);
    } catch (_) {
      if (mounted) state = state.copyWith(status: 'تعذر اختيار المستلم');
    } finally {
      if (mounted) state = state.copyWith(busy: false);
    }
  }
  Future<void> configured() async {
    await sync.resetBackoff();
    await refresh();
    final config = await configStore.load();
    await schedule(config.background);
    if (_active) start();
  }
  void start() {
    _active = true;
    _timer?.cancel();
    unawaited(poll());
    _timer = Timer.periodic(foregroundInterval, (_) => unawaited(poll()));
  }
  void pause() { _active = false; _timer?.cancel(); }
  @override
  void dispose() { _timer?.cancel(); super.dispose(); }
}
final probeProvider = StateNotifierProvider<ProbeController, ProbeState>(
  (_) => throw StateError('Probe services must be initialized before rendering'));
